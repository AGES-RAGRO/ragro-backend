package br.com.ragro.controller;

import br.com.ragro.controller.request.StockEntryRequest;
import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/producers/stock")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Stock movement operations (requires ROLE_FARMER)")
public class StockEntryController {
    private final StockService stockService;

  @PostMapping("/entry")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Record stock entry",
      description =
          "Records a manual stock entry for a product. Updates product stock quantity and creates a stock movement record.")
  public ResponseEntity<StockMovementResponse> recordStockEntry(
      @Valid @RequestBody StockEntryRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    StockMovementResponse response = stockService.recordEntry(request, jwt);
    return ResponseEntity.created(
            URI.create("/producers/stock/entry/" + response.getId()))
        .body(response);
  } 
}
