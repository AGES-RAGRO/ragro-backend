package br.com.ragro.service;

import br.com.ragro.controller.request.AvailabilityRequest;
import br.com.ragro.controller.request.PaymentMethodRequest;
import br.com.ragro.controller.request.ProducerFilter;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.domain.specification.ProducerSpecification;
import br.com.ragro.controller.response.MarketplaceProducerResponse;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerPublicProfileResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.controller.response.ProducerReviewsResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.FarmerAvailability;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.ProducerProfile;
import br.com.ragro.domain.Review;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.AddressMapper;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.mapper.ReviewMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.FarmerAvailabilityRepository;
import br.com.ragro.repository.PaymentMethodRepository;
import br.com.ragro.repository.ProducerProfileRepository;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ReviewRepository;
import br.com.ragro.repository.UserRepository;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProducerService {

  private final UserRepository userRepository;
  private final ProducerRepository producerRepository;
  private final ProducerProfileRepository producerProfileRepository;
  private final AddressRepository addressRepository;
  private final FarmerAvailabilityRepository farmerAvailabilityRepository;
  private final PaymentMethodRepository paymentMethodRepository;
  private final UserService userService;
  private final MinioStorageService minioStorageService;
  private final ProducerMapper producerMapper;
  private final ReviewRepository reviewRepository;
  private final ReviewMapper reviewMapper;

  public ProducerResponse getProducerById(UUID id) {
    var producer =
        producerRepository
            .findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    return producerMapper.toResponse(producer.getUser());
  }

  public Page<ProducerResponse> getAllProducers(Pageable pageable) {
    return producerRepository
        .findAllUsersSortedByRating(pageable)
        .map(Producer::getUser)
        .map(producerMapper::toResponse);
  }

  public Page<MarketplaceProducerResponse> getActiveProducers(
      ProducerFilter filter, Pageable pageable) {
    return producerRepository
        .findAll(ProducerSpecification.withFilter(filter), pageable)
        .map(producerMapper::toMarketplaceResponse);
  }

  @Transactional(readOnly = true)
  public ProducerGetResponse getProducerProfileById(UUID id) {
    return getProducerProfileById(id, null);
  }

  @Transactional(readOnly = true)
  public ProducerPublicProfileResponse getPublicProfileById(UUID id) {
    Producer producer =
        producerRepository
            .findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    User user = producer.getUser();
    if (!user.isActive()) {
      throw new NotFoundException("Produtor não encontrado");
    }

    ProducerProfile profile = producerProfileRepository.findById(id).orElse(null);
    Address primaryAddress = addressRepository.findByUserIdAndIsPrimaryTrue(id).orElse(null);
    List<FarmerAvailability> availability =
        farmerAvailabilityRepository.findByFarmerIdAndActiveTrueOrderByWeekdayAsc(id);

    return producerMapper.toPublicProfileResponse(
        user, producer, profile, primaryAddress, availability);
  }

  @Transactional(readOnly = true)
  public ProducerGetResponse getProducerProfileById(UUID id, Jwt jwt) {
    if (jwt != null) {
      User authenticated = userService.getAuthenticatedUser(jwt);
      TypeUser role = authenticated.getType();
      if (role == TypeUser.FARMER && !authenticated.getId().equals(id)) {
        throw new ForbiddenException("Você não tem permissão para visualizar este perfil");
      }
    }

    Producer producer =
        producerRepository
            .findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    User user = producer.getUser();

    ProducerProfile profile = producerProfileRepository.findById(id).orElse(null);
    Address primaryAddress = addressRepository.findByUserIdAndIsPrimaryTrue(id).orElse(null);
    List<PaymentMethod> paymentMethods = paymentMethodRepository.findByFarmerIdAndActiveTrue(id);
    List<FarmerAvailability> availability =
        farmerAvailabilityRepository.findByFarmerIdAndActiveTrueOrderByWeekdayAsc(id);

    return producerMapper.toGetResponse(
        user, producer, profile, primaryAddress, paymentMethods, availability);
  }

  @Transactional
  public ProducerGetResponse updateProducerProfile(
      UUID id, Jwt jwt, ProducerUpdateRequest request) {

    requireFarmerOrAdmin(id, jwt);

    Producer producer =
        producerRepository
            .findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    User targetUser = producer.getUser();

    if (request.getName() != null) {
      targetUser.setName(request.getName().trim());
    }
    if (request.getPhone() != null) {
      targetUser.setPhone(request.getPhone().trim());
    }
    userRepository.save(targetUser);

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

    ProducerProfile profile =
        producerProfileRepository
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

    Address primaryAddress = null;
    if (request.getAddress() != null) {
      primaryAddress =
          addressRepository
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

    if (request.getPaymentMethods() != null && !request.getPaymentMethods().isEmpty()) {
      Set<String> seen = new HashSet<>();
      for (PaymentMethodRequest pm : request.getPaymentMethods()) {
        if (pm.getType() == null) continue;
        if (!seen.add(pm.getType())) {
          throw new BusinessException("Duplicate payment method type: " + pm.getType());
        }
      }
      for (PaymentMethodRequest pm : request.getPaymentMethods()) {
        if (pm.getType() == null) continue;
        applyPaymentMethod(producer, pm);
      }
    }

    if (request.getAvailability() != null) {
      applyAvailability(producer, request.getAvailability());
    }

    List<PaymentMethod> paymentMethods = paymentMethodRepository.findByFarmerIdAndActiveTrue(id);
    List<FarmerAvailability> availability =
        farmerAvailabilityRepository.findByFarmerIdAndActiveTrueOrderByWeekdayAsc(id);

    return producerMapper.toGetResponse(
        targetUser, producer, profile, primaryAddress, paymentMethods, availability);
  }

  @Transactional(readOnly = true)
public ProducerReviewsResponse getProducerReviews(UUID producerId, Pageable pageable) {
    Producer producer = producerRepository
        .findById(producerId)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    Page<Review> reviews = reviewRepository.findAllByFarmerId(producerId, pageable);
    
    List<UUID> customerIds = reviews.getContent().stream()
        .map(Review::getCustomerId)
        .filter(id -> id != null)
        .distinct()
        .toList();
    
    Map<UUID, String> customerNames = userRepository.findAllById(customerIds)
        .stream()
        .collect(Collectors.toMap(User::getId, User::getName));
    
    // Fallback para não encontrados
    reviews.getContent().forEach(review -> 
        customerNames.putIfAbsent(
            review.getCustomerId(), 
            "Cliente " + review.getCustomerId().toString().substring(0, 8)
        )
    );

    return reviewMapper.toPageResponse(reviews, producer, customerNames);
}

  private void applyPaymentMethod(Producer producer, PaymentMethodRequest pmRequest) {
    PaymentMethod pm =
        paymentMethodRepository
            .findByFarmerIdAndTypeAndActiveTrue(producer.getId(), pmRequest.getType())
            .orElseGet(
                () -> {
                  PaymentMethod newPm = new PaymentMethod();
                  newPm.setFarmer(producer);
                  newPm.setType(pmRequest.getType());
                  return newPm;
                });

    if (pmRequest.getPixKeyType() != null) pm.setPixKeyType(pmRequest.getPixKeyType());
    if (pmRequest.getPixKey() != null) pm.setPixKey(pmRequest.getPixKey());
    if (pmRequest.getBankCode() != null) pm.setBankCode(pmRequest.getBankCode());
    if (pmRequest.getBankName() != null) pm.setBankName(pmRequest.getBankName());
    if (pmRequest.getAgency() != null) pm.setAgency(pmRequest.getAgency());
    if (pmRequest.getAccountNumber() != null) pm.setAccountNumber(pmRequest.getAccountNumber());
    if (pmRequest.getAccountType() != null) pm.setAccountType(pmRequest.getAccountType());
    if (pmRequest.getHolderName() != null) pm.setHolderName(pmRequest.getHolderName());
    if (pmRequest.getFiscalNumber() != null) pm.setFiscalNumber(pmRequest.getFiscalNumber());

    paymentMethodRepository.save(pm);
  }

  public ProducerResponse activateProducer(UUID id) {
    var producer =
        userRepository
            .findById(id)
            .filter(user -> user.getType() == TypeUser.FARMER)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    producer.setActive(true);
    userRepository.saveAndFlush(producer);
    return producerMapper.toResponse(producer);
  }

  public ProducerResponse deactivateProducer(UUID id) {
    var producer =
        userRepository
            .findById(id)
            .filter(user -> user.getType() == TypeUser.FARMER)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    producer.setActive(false);
    userRepository.saveAndFlush(producer);
    return producerMapper.toResponse(producer);
  }

  private void applyAvailability(Producer producer, List<AvailabilityRequest> availability) {
    Set<Short> seenWeekdays = new HashSet<>();
    for (AvailabilityRequest item : availability) {
      if (item.getWeekday() == null) {
        throw new BusinessException("weekday is required for each availability slot");
      }
      if (!seenWeekdays.add(item.getWeekday())) {
        throw new BusinessException("Duplicate availability weekday: " + item.getWeekday());
      }
    }

    farmerAvailabilityRepository.deleteByFarmerId(producer.getId());

    for (AvailabilityRequest item : availability) {
      LocalTime opensAt = parseTime(item.getOpensAt(), "opensAt");
      LocalTime closesAt = parseTime(item.getClosesAt(), "closesAt");
      validateTimeRange(opensAt, closesAt);

      FarmerAvailability farmerAvailability = new FarmerAvailability();
      farmerAvailability.setFarmer(producer);
      farmerAvailability.setWeekday(item.getWeekday());
      farmerAvailability.setOpensAt(opensAt);
      farmerAvailability.setClosesAt(closesAt);
      farmerAvailabilityRepository.save(farmerAvailability);
    }
  }

  private LocalTime parseTime(String value, String field) {
    try {
      return LocalTime.parse(value);
    } catch (DateTimeParseException ex) {
      throw new BusinessException(field + " must be a valid HH:mm value");
    }
  }

  private void validateTimeRange(LocalTime opensAt, LocalTime closesAt) {
    if (!opensAt.isBefore(closesAt)) {
      throw new BusinessException("opensAt must be earlier than closesAt");
    }
  }

  @Transactional
  public ProducerGetResponse updateAvatarPhoto(UUID id, Jwt jwt, MultipartFile file) {
    return updateProducerPhoto(id, jwt, file, PhotoTarget.AVATAR);
  }

  @Transactional
  public ProducerGetResponse updateCoverPhoto(UUID id, Jwt jwt, MultipartFile file) {
    return updateProducerPhoto(id, jwt, file, PhotoTarget.COVER);
  }

  private ProducerGetResponse updateProducerPhoto(
      UUID id, Jwt jwt, MultipartFile file, PhotoTarget target) {

    requireFarmerOrAdmin(id, jwt);

    Producer producer =
        producerRepository
            .findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    String oldKey = target.currentKey(producer);
    String newKey = minioStorageService.upload(file, target.folder);
    target.assignKey(producer, newKey);
    producerRepository.save(producer);

    minioStorageService.delete(oldKey);

    return getProducerProfileById(id, jwt);
  }

  private void requireFarmerOrAdmin(UUID id, Jwt jwt) {
    User authenticated = userService.getAuthenticatedUser(jwt);
    TypeUser role = authenticated.getType();

    if (role == TypeUser.FARMER) {
      if (!authenticated.getId().equals(id)) {
        throw new ForbiddenException("Você não tem permissão para alterar este perfil");
      }
    } else if (role != TypeUser.ADMIN) {
      throw new ForbiddenException("Acesso restrito a produtores e administradores");
    }
  }

  private enum PhotoTarget {
    AVATAR("profile_pic") {
      @Override
      String currentKey(Producer producer) {
        return producer.getAvatarS3();
      }

      @Override
      void assignKey(Producer producer, String key) {
        producer.setAvatarS3(key);
      }
    },
    COVER("background_pic") {
      @Override
      String currentKey(Producer producer) {
        return producer.getDisplayPhotoS3();
      }

      @Override
      void assignKey(Producer producer, String key) {
        producer.setDisplayPhotoS3(key);
      }
    };

    final String folder;

    PhotoTarget(String folder) {
      this.folder = folder;
    }

    abstract String currentKey(Producer producer);

    abstract void assignKey(Producer producer, String key);
  }
}
