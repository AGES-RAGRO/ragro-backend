package br.com.ragro.service;

import br.com.ragro.controller.response.FavoriteProducerResponse; 
import br.com.ragro.domain.FavoriteProducer;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.repository.FavoriteProducerRepository;
import br.com.ragro.repository.ProducerRepository;
import java.time.OffsetDateTime;
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

    @Transactional
    public void favoriteProducer(UUID producerId, Jwt jwt) {

        User customer = userService.getAuthenticatedUser(jwt);

        Producer producer = producerRepository.findById(producerId)
            .orElseThrow(() ->
                new BusinessException(
                    "Produtor não encontrado"
                )
            );

        if (!producer.getUser().isActive()) {
            throw new BusinessException(
                "Produtor está inativo"
            );
        }

        boolean alreadyFavorited =
            favoriteProducerRepository
                .existsByCustomerIdAndProducerId(
                    customer.getId(),
                    producerId
                );

        if (alreadyFavorited) {
            throw new BusinessException(
                "Produtor já favoritado"
            );
        }

        // salva o produter favorito
        FavoriteProducer favorite = new FavoriteProducer();

        favorite.setCustomerId(customer.getId());
        favorite.setProducerId(producer.getId());
        favorite.setCreatedAt(OffsetDateTime.now());

        favoriteProducerRepository.save(favorite);
    }

    @Transactional
    public void unfavoriteProducer(UUID producerId, Jwt jwt) {

        User customer = userService.getAuthenticatedUser(jwt);

        favoriteProducerRepository
            .deleteByCustomerIdAndProducerId(
                customer.getId(),
                producerId
            );
    }

@Transactional(readOnly = true)
public List<FavoriteProducerResponse> getFavorites(Jwt jwt) {

    User customer = userService.getAuthenticatedUser(jwt);

    return favoriteProducerRepository
        .findByCustomerId(customer.getId())
        .stream()
        .map(favorite -> {

            
            Producer producer = favorite.getProducer();

            return FavoriteProducerResponse.builder()
                .producerId(producer.getId())
                .producerName(producer.getUser().getName())
                .farmName(producer.getFarmName())
                .avatarUrl(producer.getAvatarS3())
                .averageRating(producer.getAverageRating())
                .build();
        })
        .toList();
}
}