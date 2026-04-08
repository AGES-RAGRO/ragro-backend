package br.com.ragro.service;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

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
    userRepository.saveAndFlush(producer);
    return ProducerMapper.toResponse(producer);
  }

  public ProducerResponse deactivateProducer(UUID id) {
  var producer =
      userRepository
          .findById(id)
          .filter(user -> user.getType() == TypeUser.FARMER)
          .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));
  producer.setActive(false);
    userRepository.saveAndFlush(producer);
    return ProducerMapper.toResponse(producer);
  }
}
