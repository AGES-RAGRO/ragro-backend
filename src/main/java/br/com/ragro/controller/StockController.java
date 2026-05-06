package br.com.ragro.controller;

import br.com.ragro.controller.request.StockEntryRequest;
import br.com.ragro.controller.request.StockExitRequest;
import br.com.ragro.controller.request.StockMovementFilter;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.service.StockMovementService;
import br.com.ragro.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/producers/stock")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Stock operations")
public class StockController {

  private final StockMovementService stockMovementService;
  private final StockService stockService;

  @GetMapping("/{productId}/movements")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Get product movement history",
      description = "Returns a paginated history of stock movements for a product owned by the authenticated farmer.")
  public ResponseEntity<PaginatedResponse<StockMovementResponse>> getProductMovements(
      @PathVariable UUID productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(
        PaginatedResponse.of(stockService.getProductMovements(productId, page, size, jwt)));
  }

  @GetMapping("/movements")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "List stock movements of the authenticated producer",
      description =
          "Returns a paginated list of stock movements for the authenticated producer. "
              + "Filters: productId, reason, type, from, to. Ordered by createdAt desc.")
  public ResponseEntity<PaginatedResponse<StockMovementResponse>> getStockMovements(
      @AuthenticationPrincipal Jwt jwt,
      @ParameterObject @ModelAttribute StockMovementFilter filter,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        stockMovementService.getProducerStockMovements(jwt, filter, PageRequest.of(page, size)));
  }

  @PostMapping("/exit")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Register stock exit",
      description =
          "Registers a stock exit (sale, loss, disposal) for a product owned by the authenticated"
              + " farmer. Blocks the operation if stock would go negative.")
  public ResponseEntity<StockMovementResponse> registerStockExit(
      @Valid @RequestBody StockExitRequest request, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(stockMovementService.registerExit(request, jwt));
  }

  @PostMapping("/entry")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Register stock entry",
      description =
          "Registers a stock entry (restock, canceled sale) for a product owned by the authenticated"
              + " farmer.")
  public ResponseEntity<StockMovementResponse> registerStockEntry(
      @Valid @RequestBody StockEntryRequest request, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(stockMovementService.registerEntry(request, jwt));
  }
}

