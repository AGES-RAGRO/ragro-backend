package br.com.ragro.service;

import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProducerService {

    private final UserRepository userRepository;
    private final ProducerRepository producerRepository;

    @Transactional(readOnly = true)
    public ProducerGetResponse getProducerById(UUID id) {
        User user = userRepository.findById(id)
                .filter(u -> u.getType() == TypeUser.FARMER)
                .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

        Producer producer = producerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dados do produtor não encontrados"));

        return ProducerMapper.toResponse(user, producer);
    }
}
