package br.com.ragro.controller;

import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@Tag(name = "Customers", description = "Customer profile endpoints")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping("/me")
  @Operation(
      summary = "Retrieve authenticated customer profile",
      description =
          "Returns the profile of the authenticated customer, including personal data and addresses.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Customer profile retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CustomerResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "User not authenticated or not a customer",
            content = @Content(mediaType = "application/json"))
      })
  public ResponseEntity<CustomerResponse> getMyCustomer(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(customerService.getMyCustomer(jwt));
  }
}
