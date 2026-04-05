package br.com.ragro.service;

import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.domain.Producer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProducerService {

    private final ProducerRepository producerRepository;

    public Producer findById(UUID id) {
        return producerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produtor não encontrado!"));
    }
}