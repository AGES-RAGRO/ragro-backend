package br.com.ragro.service;

import br.com.ragro.domain.Producer;
import br.com.ragro.repository.ProducerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProducerService {
    private final ProducerRepository producerRepository;

    public Optional<Producer> findById(UUID id) {
        return producerRepository.findById(id);
    }
}