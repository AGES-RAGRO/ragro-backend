package br.com.ragro.service;

import br.com.ragro.controller.response.ConsumerResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.ConsumerMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerService {

    private final UserService userService;

    public ConsumerService(UserService userService) {
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public ConsumerResponse getMyConsumer(Jwt jwt) {
        User user = userService.getAuthenticatedUser(jwt);

        if (user.getType() != TypeUser.CUSTOMER) {
            throw new UnauthorizedException("Acesso restrito a consumidores");
        }

        return ConsumerMapper.toResponse(user);
    }
}
