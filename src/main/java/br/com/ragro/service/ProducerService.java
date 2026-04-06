package br.com.ragro.service;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

  private final UserRepository userRepository;

  public ProducerService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public ProducerResponse getProducerById(UUID id) {
    var producer =
        userRepository
            .findById(id)
            .filter(user -> user.getType() == TypeUser.FARMER)
            .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    return ProducerMapper.toResponse(producer);
  }
}
