package br.com.ragro.service.api;

public interface IdentityProviderService {

  String registerCustomer(String email, String rawPassword);

  String registerProducer(String email, String rawPassword);

  void deleteUser(String userId);
}
