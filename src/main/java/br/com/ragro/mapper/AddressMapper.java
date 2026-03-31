package br.com.ragro.mapper;

import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.response.AddressResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AddressMapper {

    public static Address toEntity(AddressRequest request, User user, boolean isPrimary) {
        Address address = new Address();
        address.setUser(user);
        address.setStreet(request.getStreet());
        address.setNumber(request.getNumber());
        address.setComplement(request.getComplement());
        address.setNeighborhood(request.getNeighborhood());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setZipCode(request.getZipCode());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        address.setPrimary(isPrimary);
        return address;
    }

    public static AddressResponse toResponse(Address entity) {
        return AddressResponse.builder()
                .id(entity.getId())
                .street(entity.getStreet())
                .number(entity.getNumber())
                .complement(entity.getComplement())
                .neighborhood(entity.getNeighborhood())
                .city(entity.getCity())
                .state(entity.getState())
                .zipCode(entity.getZipCode())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .isPrimary(entity.isPrimary())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
