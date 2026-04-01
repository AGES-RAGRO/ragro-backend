package br.com.ragro.service;

import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.request.CustomerRegisterRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.CustomerMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class CustomerService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CognitoService cognitoService;

    public CustomerService(
            UserService userService,
            UserRepository userRepository,
            AddressRepository addressRepository,
            CognitoService cognitoService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.cognitoService = cognitoService;
    }

    @Transactional
    public CustomerResponse registerCustomer(CustomerRegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("E-mail já cadastrado");
        }

        String cognitoSub = cognitoService.registerUser(request.getName(), request.getEmail());

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setType(TypeUser.CUSTOMER);
        user.setActive(true);
        user.setCognitoSub(cognitoSub);
        User savedUser = userRepository.save(user);

        cognitoService.addToCustomerGroup(cognitoSub);

        Optional.ofNullable(request.getAddress())
                .map(addr -> buildAddress(addr, savedUser))
                .ifPresent(addressRepository::save);

        return CustomerMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getMyCustomer(Jwt jwt) {
        User user = userService.getAuthenticatedUser(jwt);

        if (user.getType() != TypeUser.CUSTOMER) {
            throw new UnauthorizedException("Acesso restrito a consumidores");
        }

        return CustomerMapper.toResponse(user);
    }

    private Address buildAddress(AddressRequest req, User user) {
        Address address = new Address();
        address.setUser(user);
        address.setStreet(req.getStreet());
        address.setNumber(req.getNumber());
        address.setComplement(req.getComplement());
        address.setNeighborhood(req.getNeighborhood());
        address.setCity(req.getCity());
        address.setState(req.getState());
        address.setZipCode(req.getZipCode());
        address.setLatitude(req.getLatitude());
        address.setLongitude(req.getLongitude());
        address.setPrimary(true);
        return address;
    }
}
