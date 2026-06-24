package br.com.ragro.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.response.AddressResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddressMapperTest {

  private AddressRequest request() {
    AddressRequest r = new AddressRequest();
    r.setStreet("Rua das Flores");
    r.setNumber("123");
    r.setComplement("Apto 4");
    r.setNeighborhood("Centro");
    r.setCity("Porto Alegre");
    r.setState("RS");
    r.setZipCode("90010000");
    r.setLatitude(new BigDecimal("-30.0277"));
    r.setLongitude(new BigDecimal("-51.2287"));
    return r;
  }

  // ─── toEntity ─────────────────────────────────────────────────────────────

  @Test
  void toEntity_mapsEveryFieldAndPrimaryFlag() {
    User user = new User();
    user.setId(UUID.randomUUID());
    AddressRequest r = request();

    Address address = AddressMapper.toEntity(r, user, true);

    assertThat(address).isNotNull();
    assertThat(address.getUser()).isSameAs(user);
    assertThat(address.getStreet()).isEqualTo("Rua das Flores");
    assertThat(address.getNumber()).isEqualTo("123");
    assertThat(address.getComplement()).isEqualTo("Apto 4");
    assertThat(address.getNeighborhood()).isEqualTo("Centro");
    assertThat(address.getCity()).isEqualTo("Porto Alegre");
    assertThat(address.getState()).isEqualTo("RS");
    assertThat(address.getZipCode()).isEqualTo("90010000");
    assertThat(address.getLatitude()).isEqualByComparingTo("-30.0277");
    assertThat(address.getLongitude()).isEqualByComparingTo("-51.2287");
    assertThat(address.isPrimary()).isTrue();
  }

  @Test
  void toEntity_honorsNonPrimaryFlag() {
    Address address = AddressMapper.toEntity(request(), new User(), false);
    assertThat(address.isPrimary()).isFalse();
  }

  // ─── applyRequest (normalization) ─────────────────────────────────────────

  @Test
  void applyRequest_trimsUppercasesStateAndStripsZipToDigits() {
    Address address = new Address();
    AddressRequest r = request();
    r.setStreet("  Rua das Flores  ");
    r.setNumber("  123 ");
    r.setCity("  Porto Alegre ");
    r.setState(" rs ");
    r.setZipCode("90010-000");

    AddressMapper.applyRequest(address, r);

    assertThat(address.getStreet()).isEqualTo("Rua das Flores");
    assertThat(address.getNumber()).isEqualTo("123");
    assertThat(address.getCity()).isEqualTo("Porto Alegre");
    assertThat(address.getState()).isEqualTo("RS");
    assertThat(address.getZipCode()).isEqualTo("90010000");
    assertThat(address.getComplement()).isEqualTo("Apto 4");
    assertThat(address.getNeighborhood()).isEqualTo("Centro");
    assertThat(address.getLatitude()).isEqualByComparingTo("-30.0277");
    assertThat(address.getLongitude()).isEqualByComparingTo("-51.2287");
  }

  @Test
  void applyRequest_blankOptionalFieldsBecomeNull() {
    Address address = new Address();
    AddressRequest r = request();
    r.setComplement("   ");
    r.setNeighborhood("");

    AddressMapper.applyRequest(address, r);

    assertThat(address.getComplement()).isNull();
    assertThat(address.getNeighborhood()).isNull();
  }

  @Test
  void applyRequest_nullOptionalFieldsStayNull() {
    Address address = new Address();
    AddressRequest r = request();
    r.setComplement(null);
    r.setNeighborhood(null);

    AddressMapper.applyRequest(address, r);

    assertThat(address.getComplement()).isNull();
    assertThat(address.getNeighborhood()).isNull();
  }

  // ─── toResponse ───────────────────────────────────────────────────────────

  @Test
  void toResponse_mapsEveryField() {
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
    OffsetDateTime createdAt = OffsetDateTime.now();
    address.setCreatedAt(createdAt);

    AddressResponse response = AddressMapper.toResponse(address);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(address.getId());
    assertThat(response.getStreet()).isEqualTo("Rua das Flores");
    assertThat(response.getNumber()).isEqualTo("123");
    assertThat(response.getComplement()).isEqualTo("Apto 4");
    assertThat(response.getNeighborhood()).isEqualTo("Centro");
    assertThat(response.getCity()).isEqualTo("Porto Alegre");
    assertThat(response.getState()).isEqualTo("RS");
    assertThat(response.getZipCode()).isEqualTo("90010000");
    assertThat(response.getLatitude()).isEqualByComparingTo("-30.0277");
    assertThat(response.getLongitude()).isEqualByComparingTo("-51.2287");
    assertThat(response.isPrimary()).isTrue();
    assertThat(response.getCreatedAt()).isEqualTo(createdAt);
  }
}
