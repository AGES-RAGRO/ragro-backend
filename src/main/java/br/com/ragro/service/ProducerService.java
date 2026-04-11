package br.com.ragro.service;

import br.com.ragro.controller.request.PaymentMethodRequest;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.ProducerProfile;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.AddressMapper;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.PaymentMethodRepository;
import br.com.ragro.repository.ProducerProfileRepository;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProducerService {

  private final UserRepository userRepository;
  private final ProducerRepository producerRepository;
  private final ProducerProfileRepository producerProfileRepository;
  private final AddressRepository addressRepository;
  private final PaymentMethodRepository paymentMethodRepository;
  private final UserService userService;

  public ProducerResponse getProducerById(UUID id) {
    var producer = userRepository
        .findById(id)
        .filter(user -> user.getType() == TypeUser.FARMER)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    return ProducerMapper.toResponse(producer);
  }

  public Page<ProducerResponse> getAllProducers(Pageable pageable) {
    return producerRepository.findAllUsersSortedByRating(pageable).map(ProducerMapper::toResponse);
  }

  @Transactional(readOnly = true)
  public ProducerGetResponse getProducerProfileById(UUID id) {
    User user = userRepository
        .findById(id)
        .filter(u -> u.getType() == TypeUser.FARMER)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    Producer producer = producerRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Dados do produtor não encontrados"));

    ProducerProfile profile = producerProfileRepository.findById(id).orElse(null);
    Address primaryAddress = addressRepository.findByUserIdAndIsPrimaryTrue(id).orElse(null);
    List<PaymentMethod> paymentMethods = paymentMethodRepository.findByFarmerIdAndActiveTrue(id);

    return ProducerMapper.toGetResponse(user, producer, profile, primaryAddress, paymentMethods);
  }

  /**
   * Updates a producer's profile.
   *
   * <p>
   * Authorization rules:
   * <ul>
   * <li>FARMER: can only update their own profile (JWT id must match path id).
   * <li>ADMIN: can update any producer's profile.
   * </ul>
   */
  @Transactional
  public ProducerGetResponse updateProducerProfile(
      UUID id, Jwt jwt, ProducerUpdateRequest request) {

    User authenticated = userService.getAuthenticatedUser(jwt);
    TypeUser role = authenticated.getType();

    // Validação de autenticação
    if (role == TypeUser.FARMER) {
      if (!authenticated.getId().equals(id)) {
        throw new UnauthorizedException("Você não tem permissão para alterar este perfil");
      }
    } else if (role != TypeUser.ADMIN) {
      throw new UnauthorizedException("Acesso restrito a produtores e administradores");
    }

    // Carrega o User alvo (pode ser diferente do autenticado quando quem age é
    // ADMIN)
    User targetUser = userRepository
        .findById(id)
        .filter(u -> u.getType() == TypeUser.FARMER)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    // Atualizar User (name, phone)
    if (request.getName() != null) {
      targetUser.setName(request.getName().trim());
    }
    if (request.getPhone() != null) {
      targetUser.setPhone(request.getPhone().trim());
    }
    userRepository.save(targetUser);

    // Atualizar Producer (farmName, description, avatarS3, displayPhotoS3)
    Producer producer = producerRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Dados do produtor não encontrados"));

    if (request.getFarmName() != null) {
      producer.setFarmName(request.getFarmName().trim());
    }
    if (request.getDescription() != null) {
      producer.setDescription(request.getDescription());
    }
    if (request.getAvatarS3() != null) {
      producer.setAvatarS3(request.getAvatarS3());
    }
    if (request.getDisplayPhotoS3() != null) {
      producer.setDisplayPhotoS3(request.getDisplayPhotoS3());
    }
    producerRepository.save(producer);

    // Upsert ProducerProfile (story, photoUrl, memberSince)
    ProducerProfile profile = producerProfileRepository
        .findById(id)
        .orElseGet(
            () -> {
              ProducerProfile p = new ProducerProfile();
              p.setUser(targetUser);
              return p;
            });

    if (request.getStory() != null) {
      profile.setStory(request.getStory());
    }
    if (request.getPhotoUrl() != null) {
      profile.setPhotoUrl(request.getPhotoUrl());
    }
    if (request.getMemberSince() != null) {
      profile.setMemberSince(request.getMemberSince());
    }
    producerProfileRepository.save(profile);

    // #110 — Upsert Address primário
    Address primaryAddress = null;
    if (request.getAddress() != null) {
      primaryAddress = addressRepository
          .findByUserIdAndIsPrimaryTrue(id)
          .orElseGet(
              () -> {
                Address a = new Address();
                a.setUser(targetUser);
                a.setPrimary(true);
                return a;
              });
      AddressMapper.applyRequest(primaryAddress, request.getAddress());
      addressRepository.save(primaryAddress);
    } else {
      primaryAddress = addressRepository.findByUserIdAndIsPrimaryTrue(id).orElse(null);
    }

    // #110 — Upsert PaymentMethod (por tipo: pix ou bank_account)
    if (request.getPaymentMethod() != null && request.getPaymentMethod().getType() != null) {
      applyPaymentMethod(producer, request.getPaymentMethod());
    }

    List<PaymentMethod> paymentMethods = paymentMethodRepository.findByFarmerIdAndActiveTrue(id);

    return ProducerMapper.toGetResponse(targetUser, producer, profile, primaryAddress, paymentMethods);
  }

  private void applyPaymentMethod(Producer producer, PaymentMethodRequest pmRequest) {
    PaymentMethod pm = paymentMethodRepository
        .findByFarmerIdAndTypeAndActiveTrue(producer.getId(), pmRequest.getType())
        .orElseGet(
            () -> {
              PaymentMethod newPm = new PaymentMethod();
              newPm.setFarmer(producer);
              newPm.setType(pmRequest.getType());
              return newPm;
            });

    if (pmRequest.getPixKeyType() != null)
      pm.setPixKeyType(pmRequest.getPixKeyType());
    if (pmRequest.getPixKey() != null)
      pm.setPixKey(pmRequest.getPixKey());
    if (pmRequest.getBankCode() != null)
      pm.setBankCode(pmRequest.getBankCode());
    if (pmRequest.getBankName() != null)
      pm.setBankName(pmRequest.getBankName());
    if (pmRequest.getAgency() != null)
      pm.setAgency(pmRequest.getAgency());
    if (pmRequest.getAccountNumber() != null)
      pm.setAccountNumber(pmRequest.getAccountNumber());
    if (pmRequest.getAccountType() != null)
      pm.setAccountType(pmRequest.getAccountType());
    if (pmRequest.getHolderName() != null)
      pm.setHolderName(pmRequest.getHolderName());
    if (pmRequest.getFiscalNumber() != null)
      pm.setFiscalNumber(pmRequest.getFiscalNumber());

    paymentMethodRepository.save(pm);
  }

  public ProducerResponse activateProducer(UUID id) {
    var producer = userRepository
        .findById(id)
        .filter(user -> user.getType() == TypeUser.FARMER)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    producer.setActive(true);
    userRepository.saveAndFlush(producer);
    return ProducerMapper.toResponse(producer);
  }

  public ProducerResponse deactivateProducer(UUID id) {
    var producer = userRepository
        .findById(id)
        .filter(user -> user.getType() == TypeUser.FARMER)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));
    producer.setActive(false);
    userRepository.saveAndFlush(producer);
    return ProducerMapper.toResponse(producer);
  }
}
