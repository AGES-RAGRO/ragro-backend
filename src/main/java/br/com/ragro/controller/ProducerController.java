package br.com.ragro.controller;

import br.com.ragro.controller.response.ProducerGetResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/producers")
public class ProducerController {

    @GetMapping("/{id}")
    public ResponseEntity<ProducerGetResponse> getProducerById(@PathVariable UUID id) {
        ProducerGetResponse response = ProducerGetResponse.builder()
                .id(id)
                .name("João Silva")
                .email("joao.silva.agro@email.com.br")
                .phone("(11) 98765-4321")
                .fiscalNumber("000.000.000-00")
                .build();

        return ResponseEntity.ok(response);
    }
}