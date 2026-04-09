package br.com.ragro.service;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.repository.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

  private final UserRepository userRepository;

  public ProducerService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public Page<ProducerResponse> getAllProducers(Pageable pageable) {
    return userRepository.findAllByTypeAndActiveIsTrue(TypeUser.FARMER, pageable)
        .map(ProducerMapper::toResponse);
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
}
