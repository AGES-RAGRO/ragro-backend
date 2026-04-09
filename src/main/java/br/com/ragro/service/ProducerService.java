package br.com.ragro.service;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.ProducerProfile;
import br.com.ragro.domain.User;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.AddressMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

@Service

public class ProducerService {

  private final UserRepository userRepository;

  public ProducerService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public List<ProducerResponse> getAllProducers() {
    return userRepository.findAllByType(TypeUser.FARMER).stream()
        .map(ProducerMapper::toResponse)
        .toList();
  }

  public ProducerResponse getProducerById(UUID id) {
    var producer =
        userRepository
            .findById(id)
            .filter(user -> user.getType() == TypeUser.FARMER)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    return ProducerMapper.toResponse(producer);
  }

  public ProducerResponse activateProducer(UUID id) {
    var producer =
        userRepository
            .findById(id)
            .filter(user -> user.getType() == TypeUser.FARMER)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    producer.setActive(true);
    userRepository.save(producer);
    return ProducerMapper.toResponse(producer);
  }

  public ProducerResponse deactivateProducer(UUID id) {
  var producer =
      userRepository
          .findById(id)
          .filter(user -> user.getType() == TypeUser.FARMER)
          .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));
  producer.setActive(false);
    userRepository.save(producer);
    return ProducerMapper.toResponse(producer);
  }

  @Transactional
  public ProducerResponse updateProducer(UUID id, Jwt jwt, ProducerUpdateRequest request) {
    User requester = userRepository.findByAuthSub(jwt.getClaimAsString("sub"))
        .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));

    // Validation: Admin can update any, Farmer can only update their own
    if (requester.getType() == TypeUser.FARMER && !requester.getId().equals(id)) {
      throw new UnauthorizedException("Produtores só podem alterar o próprio perfil");
    } else if (requester.getType() != TypeUser.FARMER && requester.getType() != TypeUser.ADMIN) {
      throw new UnauthorizedException("Acesso restrito");
    }

    User producer = userRepository.findById(id)
        .filter(user -> user.getType() == TypeUser.FARMER)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    producer.setName(request.getName().trim());
    producer.setPhone(request.getPhone().trim());

    // Update Address
    Address primary = producer.getAddresses().stream()
        .filter(Address::isPrimary)
        .findFirst()
        .orElseGet(() -> {
          Address newAddress = new Address();
          newAddress.setUser(producer);
          newAddress.setPrimary(true);
          producer.getAddresses().add(newAddress);
          return newAddress;
        });

    AddressMapper.applyRequest(primary, request.getAddress());

    // Update ProducerProfile
    ProducerProfile profile = producer.getProducerProfile();
    if (profile == null) {
      profile = new ProducerProfile();
      profile.setUser(producer);
      producer.setProducerProfile(profile);
    }

    if (request.getStory() != null) {
      profile.setStory(request.getStory().trim());
    }
    if (request.getPhotoUrl() != null) {
      profile.setPhotoUrl(request.getPhotoUrl().trim());
    }

    userRepository.save(producer);

    return ProducerMapper.toResponse(producer);
  }
}
