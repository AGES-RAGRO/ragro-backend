package br.com.ragro.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class CustomerResponse {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<AddressResponse> addresses;
}
