package br.com.ragro.controller;

import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.service.ProducerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/farmer")
@RequiredArgsConstructor
@Tag(name = "Producer", description = "Producer operations (requires ROLE_FARMER)")
public class ProducerController {

  private final ProducerService producerService;

  @GetMapping("/{id}")
  @Operation(
      summary = "Get producer by ID",
      description = "Returns consolidated producer profile with user and farmer data")
  public ResponseEntity<ProducerGetResponse> getProducerById(@PathVariable UUID id) {
    return ResponseEntity.ok(producerService.getProducerProfileById(id));
  }
}
