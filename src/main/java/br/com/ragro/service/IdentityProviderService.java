package br.com.ragro.service;

public interface IdentityProviderService {

    String registerConsumer(String email, String rawPassword);
}
