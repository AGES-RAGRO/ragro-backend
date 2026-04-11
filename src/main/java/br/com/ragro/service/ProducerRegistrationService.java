package br.com.ragro.service;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.mapper.ProducerMapper;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.FarmerAvailability;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.mapper.AddressMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.FarmerAvailabilityRepository;
import br.com.ragro.repository.PaymentMethodRepository;

import java.time.LocalTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProducerRegistrationService {

    private final UserRepository userRepository;
    private final ProducerRepository producerRepository;
    private final IdentityProviderService identityProviderService;
    private final AddressRepository addressRepository;
    private final FarmerAvailabilityRepository availabilityRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    public ProducerRegistrationService(
            UserRepository userRepository,
            ProducerRepository producerRepository,
            IdentityProviderService identityProviderService,
            AddressRepository addressRepository,
            FarmerAvailabilityRepository availabilityRepository,
            PaymentMethodRepository paymentMethodRepository) {
        this.userRepository = userRepository;
        this.producerRepository = producerRepository;
        this.identityProviderService = identityProviderService;
        this.addressRepository = addressRepository;
        this.availabilityRepository = availabilityRepository;
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Transactional
    public ProducerRegistrationResponse register(ProducerRegistrationRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedFiscalNumber = digitsOnly(request.getFiscalNumber());

        validateUniqueness(normalizedEmail, normalizedFiscalNumber);

        String externalUserId =
                identityProviderService.registerCustomer(normalizedEmail, request.getPassword());

        try {
            User user = new User();
            user.setName(request.getName().trim());
            user.setEmail(normalizedEmail);
            user.setPhone(normalizedPhone);
            user.setType(TypeUser.FARMER);
            user.setActive(true);
            user.setAuthSub(externalUserId);

            User savedUser = userRepository.saveAndFlush(user);

            Address address = AddressMapper.toEntity(request.getAddress(), savedUser, true);
            addressRepository.save(address);

            Producer savedProducer = producerRepository.saveAndFlush(
                    ProducerMapper.toEntity(savedUser, request, normalizedFiscalNumber)
            );

            if (request.getBankAccount() != null) {
                PaymentMethod pm = new PaymentMethod();
                pm.setFarmer(savedProducer);
                pm.setType("bank_account");
                pm.setBankName(request.getBankAccount().getBankName());
                pm.setBankCode(request.getBankAccount().getBankCode());
                pm.setAgency(request.getBankAccount().getAgency());
                pm.setAccountNumber(request.getBankAccount().getAccountNumber());
                pm.setHolderName(request.getBankAccount().getHolderName());
                pm.setFiscalNumber(digitsOnly(request.getBankAccount().getFiscalNumber()));
                paymentMethodRepository.save(pm);
            }

            if (request.getAvailability() != null) {
                for (var avail : request.getAvailability()) {
                    FarmerAvailability fa = new FarmerAvailability();
                    fa.setFarmer(savedProducer);
                    fa.setWeekday(avail.getWeekday());
                    fa.setOpensAt(LocalTime.parse(avail.getOpensAt()));
                    fa.setClosesAt(LocalTime.parse(avail.getClosesAt()));
                    availabilityRepository.save(fa);
                }
            }

            return ProducerMapper.toRegistrationResponse(savedUser, savedProducer);

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