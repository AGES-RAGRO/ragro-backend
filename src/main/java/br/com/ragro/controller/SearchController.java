package br.com.ragro.controller;

import br.com.ragro.controller.request.SearchRequest;
import br.com.ragro.controller.response.SearchResultResponse;
import br.com.ragro.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Marketplace search operations")
public class SearchController {

  private final SearchService searchService;

  @GetMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Search marketplace products and producers",
      description =
          "Returns a unified list of matching products and producers for the customer marketplace.")
  public ResponseEntity<List<SearchResultResponse>> search(
      @Valid @ModelAttribute SearchRequest request) {
    return ResponseEntity.ok(searchService.search(request));
  }
}
