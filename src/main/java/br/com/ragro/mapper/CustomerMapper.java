package br.com.ragro.mapper;

import br.com.ragro.domain.Customer;
import br.com.ragro.domain.User;
import lombok.experimental.UtilityClass;
import org.springframework.lang.NonNull;

@UtilityClass
public class CustomerMapper {

    @NonNull
    public static Customer toEntity(@NonNull User user, @NonNull String fiscalNumber) {
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFiscalNumber(fiscalNumber);
        return customer;
    }
}
