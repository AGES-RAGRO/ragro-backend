package br.com.ragro.controller;

import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo do cliente: acesso a produto ativo por id, sem o produtor na rota. Complementa {@code
 * GET /producers/{producerId}/products/{productId}} para fluxos em que o app só tem o productId.
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Customer-facing product catalog")
public class ProductCatalogController {

  private final ProductService productService;

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Get active product by id",
      description =
          "Returns details of an active product without requiring the producer id in the path."
              + " Same response shape as GET /producers/{producerId}/products/{productId}."
              + " Restricted to Customers.")
  public ResponseEntity<ProductResponse> getActiveProductById(@PathVariable UUID id) {
    return ResponseEntity.ok(productService.getActiveProductById(id));
  }
}
