package br.com.ragro.service;

import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.CustomerMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final UserService userService;

    public CustomerService(UserService userService) {
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public CustomerResponse getMyCustomer(Jwt jwt) {
        User user = userService.getAuthenticatedUser(jwt);

        if (user.getType() != TypeUser.CUSTOMER) {
            throw new UnauthorizedException("Acesso restrito a consumidores");
        }

        return CustomerMapper.toResponse(user);
    }
}
