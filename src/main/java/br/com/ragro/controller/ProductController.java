package br.com.ragro.controller;

import br.com.ragro.controller.request.ProductRequest;
import br.com.ragro.controller.response.ProductCategoryResponse;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/producers/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product operations (requires ROLE_FARMER)")
public class ProductController {

  private final ProductService productService;

  @GetMapping
  @Operation(
      summary = "List farmer products",
      description = "Returns products owned by the authenticated farmer.")
  public ResponseEntity<List<ProductResponse>> getMyProducts(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(productService.getMyProducts(jwt));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get farmer product",
      description = "Returns a product owned by the authenticated farmer.")
  public ResponseEntity<ProductResponse> getMyProductById(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(productService.getMyProductById(id, jwt));
  }

  @PostMapping
  @Operation(
      summary = "Create product",
      description = "Creates a product for the authenticated farmer.")
  public ResponseEntity<ProductResponse> createProduct(
      @Valid @RequestBody ProductRequest request, @AuthenticationPrincipal Jwt jwt) {
    ProductResponse response = productService.createProduct(request, jwt);
    return ResponseEntity.created(URI.create("/producers/products/" + response.getId()))
        .body(response);
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Update product",
      description = "Updates a product owned by the authenticated farmer.")
  public ResponseEntity<ProductResponse> updateProduct(
      @PathVariable UUID id,
      @Valid @RequestBody ProductRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(productService.updateProduct(id, request, jwt));
  }

  @DeleteMapping("/{id}")
  @Operation(
      summary = "Deactivate product",
      description =
          "Soft deletes a product owned by the authenticated farmer by setting active=false.")
  public ResponseEntity<ProductResponse> deactivateProduct(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(productService.deactivateProduct(id, jwt));
  }

  @GetMapping("/categories")
  @Operation(summary = "List product categories", description = "Returns all product categories.")
  public ResponseEntity<List<ProductCategoryResponse>> getCategories() {
    return ResponseEntity.ok(productService.getCategories());
  }

  @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Upload product photo",
      description = "Uploads a photo for a product owned by the authenticated farmer.")
  public ResponseEntity<ProductResponse> uploadProductPhoto(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(productService.updateProductPhoto(id, jwt, file));
  }
}
