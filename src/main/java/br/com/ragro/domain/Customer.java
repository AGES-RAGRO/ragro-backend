package br.com.ragro.domain;
 
import br.com.ragro.domain.enums.TypeUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


@Entity
@Table(name = "customers")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class Customer extends User {
	//uuid que faz ref do user id
	//fiscal number, cpf cnpj string
	//timestamps

	public Customer(){
		super();
	}

	@Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 11)
	@Size(max = 11)
    private String fiscalNumber;
}
