package br.com.ragro.service;

public interface IdentityProviderService {

  String registerCustomer(String email, String rawPassword);
}
