package br.com.ragro.service;

import br.com.ragro.controller.response.FavoriteProducerResponse;
import br.com.ragro.domain.FavoriteProducer;
import br.com.ragro.domain.FavoriteProducerId;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ConflictException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.FavoriteProducerRepository;
import br.com.ragro.repository.ProducerRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteProducerService {

  private final FavoriteProducerRepository favoriteProducerRepository;
  private final ProducerRepository producerRepository;
  private final UserService userService;
  private final MinioStorageService minioStorageService;

  @Transactional
  public void favoriteProducer(UUID producerId, Jwt jwt) {
    User customer = getAuthenticatedCustomer(jwt);

    Producer producer =
        producerRepository
            .findById(producerId)
            .filter(candidate -> candidate.getUser().isActive())
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    boolean alreadyFavorited =
        favoriteProducerRepository.existsByIdCustomerIdAndIdProducerId(
            customer.getId(), producerId);

    if (alreadyFavorited) {
      throw new ConflictException("Produtor já favoritado");
    }

    FavoriteProducer favorite =
        new FavoriteProducer(new FavoriteProducerId(customer.getId(), producer.getId()), producer);

    favoriteProducerRepository.save(favorite);
  }

  @Transactional
  public void unfavoriteProducer(UUID producerId, Jwt jwt) {
    User customer = getAuthenticatedCustomer(jwt);

    favoriteProducerRepository.deleteByIdCustomerIdAndIdProducerId(customer.getId(), producerId);
  }

  @Transactional(readOnly = true)
  public List<FavoriteProducerResponse> getFavorites(Jwt jwt) {
    User customer = getAuthenticatedCustomer(jwt);

    return favoriteProducerRepository
        .findByIdCustomerIdOrderByCreatedAtDesc(customer.getId())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private User getAuthenticatedCustomer(Jwt jwt) {
    User user = userService.requireRole(jwt, TypeUser.CUSTOMER, "Apenas customers podem favoritar produtores");
    return user;
  }

  private FavoriteProducerResponse toResponse(FavoriteProducer favorite) {
    Producer producer = favorite.getProducer();

    return FavoriteProducerResponse.builder()
        .producerId(producer.getId())
        .producerName(producer.getUser().getName())
        .farmName(producer.getFarmName())
        .avatarUrl(minioStorageService.composePublicUrl(producer.getAvatarS3()))
        .coverUrl(minioStorageService.composePublicUrl(producer.getDisplayPhotoS3()))
        .averageRating(producer.getAverageRating())
        .build();
  }
}
