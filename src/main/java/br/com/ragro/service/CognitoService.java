package br.com.ragro.service;

public interface CognitoService {

    String registerUser(String name, String email);

    void addToConsumerGroup(String cognitoSub);
}
