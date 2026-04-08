package br.com.ragro.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ragro.controller.response.AddressResponse;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerMapperTest {

  @Test
  void toResponse_shouldMapAllUserFields() {
    User user = buildUser(List.of());

    CustomerResponse response = CustomerMapper.toResponse(user);

    assertThat(response.getId()).isEqualTo(user.getId());
    assertThat(response.getName()).isEqualTo(user.getName());
    assertThat(response.getEmail()).isEqualTo(user.getEmail());
    assertThat(response.getPhone()).isEqualTo(user.getPhone());
    assertThat(response.isActive()).isEqualTo(user.isActive());
    assertThat(response.getCreatedAt()).isEqualTo(user.getCreatedAt());
    assertThat(response.getUpdatedAt()).isEqualTo(user.getUpdatedAt());
  }

  @Test
  void toResponse_shouldReturnEmptyAddresses_whenUserHasNoAddresses() {
    User user = buildUser(List.of());

    CustomerResponse response = CustomerMapper.toResponse(user);

    assertThat(response.getAddresses()).isEmpty();
  }

  @Test
  void toResponse_shouldMapAddresses() {
    Address address = buildAddress();
    User user = buildUser(List.of(address));

    CustomerResponse response = CustomerMapper.toResponse(user);

    assertThat(response.getAddresses()).hasSize(1);
    AddressResponse addressResponse = response.getAddresses().get(0);
    assertThat(addressResponse.getId()).isEqualTo(address.getId());
    assertThat(addressResponse.getStreet()).isEqualTo(address.getStreet());
    assertThat(addressResponse.getNumber()).isEqualTo(address.getNumber());
    assertThat(addressResponse.getComplement()).isEqualTo(address.getComplement());
    assertThat(addressResponse.getNeighborhood()).isEqualTo(address.getNeighborhood());
    assertThat(addressResponse.getCity()).isEqualTo(address.getCity());
    assertThat(addressResponse.getState()).isEqualTo(address.getState());
    assertThat(addressResponse.getZipCode()).isEqualTo(address.getZipCode());
    assertThat(addressResponse.getLatitude()).isEqualTo(address.getLatitude());
    assertThat(addressResponse.getLongitude()).isEqualTo(address.getLongitude());
    assertThat(addressResponse.isPrimary()).isEqualTo(address.isPrimary());
    assertThat(addressResponse.getCreatedAt()).isEqualTo(address.getCreatedAt());
  }

  @Test
  void toResponse_shouldMapMultipleAddresses() {
    User user = buildUser(List.of(buildAddress(), buildAddress()));

    CustomerResponse response = CustomerMapper.toResponse(user);

    assertThat(response.getAddresses()).hasSize(2);
  }

  private User buildUser(List<Address> addresses) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Maria Silva");
    user.setEmail("maria@example.com");
    user.setPhone("51999999999");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(true);
    user.setAuthSub("auth-sub-123");
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now());
    user.getAddresses().addAll(addresses);
    return user;
  }

  private Address buildAddress() {
    Address address = new Address();
    address.setId(UUID.randomUUID());
    address.setStreet("Rua das Flores");
    address.setNumber("123");
    address.setComplement("Apto 4");
    address.setNeighborhood("Centro");
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setZipCode("90010000");
    address.setLatitude(new BigDecimal("-30.0277"));
    address.setLongitude(new BigDecimal("-51.2287"));
    address.setPrimary(true);
    address.setCreatedAt(OffsetDateTime.now());
    return address;
  }
}
