package br.com.ragro.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MockCognitoService implements CognitoService {

    private static final Logger logger = LoggerFactory.getLogger(MockCognitoService.class);

    @Override
    public String registerUser(String name, String email) {
        String cognitoSub = UUID.randomUUID().toString();
        logger.info("[MOCK] Cognito user created — sub: {}", cognitoSub);
        return cognitoSub;
    }

    @Override
    public void addToConsumerGroup(String cognitoSub) {
        logger.info("[MOCK] User added to Cognito group CUSTOMER");
    }
}
