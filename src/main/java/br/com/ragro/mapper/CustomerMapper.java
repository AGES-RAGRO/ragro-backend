package br.com.ragro.mapper;

import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.User;
import lombok.experimental.UtilityClass;
import org.springframework.lang.NonNull;

@UtilityClass
public class CustomerMapper {

  @NonNull
  public static Customer toEntity(@NonNull User user, @NonNull String fiscalNumber) {
    Customer customer = new Customer();
    customer.setUser(user);
    customer.setFiscalNumber(fiscalNumber);
    return customer;
  }

  @NonNull
  public static CustomerResponse toResponse(@NonNull User user) {
    return CustomerResponse.builder()
        .id(user.getId())
        .name(user.getName())
        .email(user.getEmail())
        .phone(user.getPhone())
        .active(user.isActive())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .addresses(user.getAddresses().stream().map(AddressMapper::toResponse).toList())
        .build();
  }
}
