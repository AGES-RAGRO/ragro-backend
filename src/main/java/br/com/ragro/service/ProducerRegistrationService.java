package br.com.ragro.service;

import br.com.ragro.controller.request.AvailabilityRequest;
import br.com.ragro.controller.request.PaymentMethodRequest;
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
import br.com.ragro.service.api.IdentityProviderService;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
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
            identityProviderService.registerProducer(normalizedEmail, request.getPassword());

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

            applyRegistrationPaymentMethod(savedProducer, request);
            applyAvailability(savedProducer, request.getAvailability());

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

    private void applyRegistrationPaymentMethod(Producer savedProducer, ProducerRegistrationRequest request) {
        if (request.getPaymentMethod() != null && request.getPaymentMethod().getType() != null) {
            PaymentMethodRequest pmRequest = request.getPaymentMethod();
            PaymentMethod pm = new PaymentMethod();
            pm.setFarmer(savedProducer);
            pm.setType(pmRequest.getType());
            pm.setPixKeyType(pmRequest.getPixKeyType());
            pm.setPixKey(pmRequest.getPixKey());
            pm.setBankCode(pmRequest.getBankCode());
            pm.setBankName(pmRequest.getBankName());
            pm.setAgency(pmRequest.getAgency());
            pm.setAccountNumber(pmRequest.getAccountNumber());
            pm.setAccountType(pmRequest.getAccountType());
            pm.setHolderName(pmRequest.getHolderName());
            if (pmRequest.getFiscalNumber() != null) {
                pm.setFiscalNumber(digitsOnly(pmRequest.getFiscalNumber()));
            }
            paymentMethodRepository.save(pm);
            return;
        }

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
    }

    private void applyAvailability(Producer producer, java.util.List<AvailabilityRequest> availability) {
        if (availability == null) {
            return;
        }
        for (AvailabilityRequest avail : availability) {
            LocalTime opensAt = parseTime(avail.getOpensAt(), "opensAt");
            LocalTime closesAt = parseTime(avail.getClosesAt(), "closesAt");
            validateTimeRange(opensAt, closesAt);

            FarmerAvailability fa = new FarmerAvailability();
            fa.setFarmer(producer);
            fa.setWeekday(avail.getWeekday());
            fa.setOpensAt(opensAt);
            fa.setClosesAt(closesAt);
            availabilityRepository.save(fa);
        }
    }

    private LocalTime parseTime(String value, String field) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(field + " must be a valid HH:mm value");
        }
    }

    private void validateTimeRange(LocalTime opensAt, LocalTime closesAt) {
        if (!opensAt.isBefore(closesAt)) {
            throw new BusinessException("opensAt must be earlier than closesAt");
        }
    }
}