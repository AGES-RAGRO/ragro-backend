package br.com.ragro.service;

import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProducerService {

  private final UserRepository userRepository;
  private final ProducerRepository producerRepository;

  public ProducerResponse getProducerById(UUID id) {
    var producer =
        userRepository
            .findById(id)
            .filter(user -> user.getType() == TypeUser.FARMER)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    return ProducerMapper.toResponse(producer);
  }

  public Page<ProducerResponse> getAllProducers(Pageable pageable) {
    return producerRepository.findAllUsersSortedByRating(pageable)
        .map(ProducerMapper::toResponse);
  }

  @Transactional(readOnly = true)
  public ProducerGetResponse getProducerProfileById(UUID id) {
    User user =
        userRepository
            .findById(id)
            .filter(u -> u.getType() == TypeUser.FARMER)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    Producer producer =
        producerRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Dados do produtor não encontrados"));

    return ProducerMapper.toGetResponse(user, producer);
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
