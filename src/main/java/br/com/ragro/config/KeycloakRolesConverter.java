package br.com.ragro.config;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  @Override
  public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
    List<String> groups = jwt.getClaimAsStringList("groups");
    if (groups == null || groups.isEmpty()) {
      return List.of();
    }

    return groups.stream()
        .filter(group -> group != null && !group.isBlank())
        .map(String::trim)
        .map(group -> "ROLE_" + group.toUpperCase(Locale.ROOT))
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toSet());
  }
}
