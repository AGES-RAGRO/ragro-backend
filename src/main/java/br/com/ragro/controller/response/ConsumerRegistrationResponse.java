package br.com.ragro.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ConsumerRegistrationResponse {

    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String type;
    private boolean active;
    private String fiscalNumber;
    private AddressResponse address;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
