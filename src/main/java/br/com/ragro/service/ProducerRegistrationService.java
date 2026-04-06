package br.com.ragro.service;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProducerRegistrationService {

    private final UserRepository userRepository;
    private final ProducerRepository producerRepository;
    private final IdentityProviderService identityProviderService;

    public ProducerRegistrationService(
            UserRepository userRepository,
            ProducerRepository producerRepository,
            IdentityProviderService identityProviderService) {
        this.userRepository = userRepository;
        this.producerRepository = producerRepository;
        this.identityProviderService = identityProviderService;
    }

    @Transactional
    public ProducerRegistrationResponse register(ProducerRegistrationRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedFiscalNumber = digitsOnly(request.getFiscalNumber());

        validateUniqueness(normalizedEmail, normalizedFiscalNumber);

        String externalUserId =
                identityProviderService.registerCustomer(normalizedEmail, request.getPassword());

        User savedUser;

        try {
            User user = new User();
            user.setName(request.getName().trim());
            user.setEmail(normalizedEmail);
            user.setPhone(normalizedPhone);
            user.setType(TypeUser.FARMER);
            user.setActive(true);
            user.setAuthSub(externalUserId);

            savedUser = userRepository.save(user);

            producerRepository.save(ProducerMapper.toEntity(savedUser, request, normalizedFiscalNumber));

            return ProducerRegistrationResponse.builder()
                    .id(savedUser.getId())
                    .name(savedUser.getName())
                    .email(savedUser.getEmail())
                    .phone(savedUser.getPhone())
                    .active(savedUser.isActive())
                    .createdAt(savedUser.getCreatedAt())
                    .updatedAt(savedUser.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            identityProviderService.deleteUser(externalUserId);
            throw e;
        }
    }

    private void validateUniqueness(String email, String fiscalNumber) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("E-mail already registered");
        }
        if (producerRepository.existsByFiscalNumber(fiscalNumber)) {
            throw new BusinessException("Fiscal number already registered");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        return phone.trim();
    }

    private String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }
}