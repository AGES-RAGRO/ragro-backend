package br.com.ragro.controller;

import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.service.ProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/producers")
@RequiredArgsConstructor
public class ProducerController {

    private final ProducerService producerService;

    @GetMapping("/{id}")
    public ResponseEntity<ProducerGetResponse> getProducerById(@PathVariable UUID id) {
        return ResponseEntity.ok(producerService.getProducerById(id));
    }
}
