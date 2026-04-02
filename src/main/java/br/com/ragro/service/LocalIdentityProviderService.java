package br.com.ragro.service;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LocalIdentityProviderService implements IdentityProviderService {

  @Override
  public String registerCustomer(String email, String rawPassword) {
    return "local-" + UUID.randomUUID();
  }
}
