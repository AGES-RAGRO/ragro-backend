package br.com.ragro.mapper;

import br.com.ragro.controller.response.AddressResponse;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class CustomerMapper {

    public static CustomerResponse toResponse(User user) {
        List<AddressResponse> addresses = user.getAddresses().stream()
                .map(CustomerMapper::toAddressResponse)
                .toList();

        return CustomerResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .addresses(addresses)
                .build();
    }

    public static AddressResponse toAddressResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .street(address.getStreet())
                .number(address.getNumber())
                .complement(address.getComplement())
                .neighborhood(address.getNeighborhood())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .isPrimary(address.isPrimary())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
