package br.com.ragro.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "producers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Producer {
    @Id
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String fiscalNumber;
}