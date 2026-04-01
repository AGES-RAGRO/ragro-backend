package br.com.ragro.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LocalIdentityProviderService implements IdentityProviderService {

    @Override
    public String registerConsumer(String email, String rawPassword) {
        return "local-" + UUID.randomUUID();
    }
}
