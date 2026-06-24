package br.com.ragro.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRolesConverterTest {

  private KeycloakRolesConverter converter;

  @BeforeEach
  void setUp() {
    converter = new KeycloakRolesConverter();
  }

  @Test
  void convert_whenGroupsClaimMissing_returnsEmptyCollection() {
    Jwt jwt = jwtWithoutGroups();

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities).isEmpty();
  }

  @Test
  void convert_whenGroupsClaimEmptyList_returnsEmptyCollection() {
    Jwt jwt = jwtWithGroups(List.of());

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities).isEmpty();
  }

  @Test
  void convert_whenSingleGroupPresent_mapsToRolePrefixedUppercaseAuthority() {
    Jwt jwt = jwtWithGroups(List.of("farmer"));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_FARMER");
  }

  @Test
  void convert_whenMultipleGroupsPresent_mapsEachToRolePrefixedUppercaseAuthority() {
    Jwt jwt = jwtWithGroups(List.of("farmer", "customer", "admin"));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_FARMER", "ROLE_CUSTOMER", "ROLE_ADMIN");
  }

  @Test
  void convert_whenLowercaseGroup_uppercasesBeforePrefixing() {
    Jwt jwt = jwtWithGroups(List.of("customer"));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_CUSTOMER");
  }

  @Test
  void convert_whenGroupHasLeadingAndTrailingWhitespace_trimsBeforePrefixing() {
    Jwt jwt = jwtWithGroups(List.of("  farmer  "));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_FARMER");
  }

  @Test
  void convert_whenGroupsContainNullEntry_filtersItOut() {
    Jwt jwt = jwtWithGroups(Arrays.asList("farmer", null));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_FARMER");
  }

  @Test
  void convert_whenGroupsContainBlankEntry_filtersItOut() {
    Jwt jwt = jwtWithGroups(List.of("farmer", "  "));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_FARMER");
  }

  @Test
  void convert_whenGroupsContainOnlyNullAndBlankEntries_returnsEmptyCollection() {
    Jwt jwt = jwtWithGroups(Arrays.asList(null, "   ", ""));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities).isEmpty();
  }

  @Test
  void convert_whenDuplicateGroups_collapsesToSingleAuthority() {
    Jwt jwt = jwtWithGroups(List.of("farmer", "farmer", "FARMER", " farmer "));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_FARMER");
    assertThat(authorities).hasSize(1);
  }

  @Test
  void convert_whenMixedValidNullBlankAndDuplicateGroups_producesDistinctCleanAuthorities() {
    Jwt jwt =
        jwtWithGroups(Arrays.asList(" farmer ", "customer", null, "  ", "CUSTOMER", "admin"));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_FARMER", "ROLE_CUSTOMER", "ROLE_ADMIN");
  }

  private Jwt jwtWithGroups(List<String> groups) {
    return Jwt.withTokenValue("t")
        .header("alg", "none")
        .subject("test-subject")
        .claim("groups", groups)
        .build();
  }

  private Jwt jwtWithoutGroups() {
    return Jwt.withTokenValue("t")
        .header("alg", "none")
        .subject("test-subject")
        .claim("email", "test@example.com")
        .build();
  }
}
