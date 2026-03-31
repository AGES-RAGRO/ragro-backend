package br.com.ragro.mapper;

import br.com.ragro.domain.Customer;
import br.com.ragro.domain.User;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CustomerMapper {

    public static Customer toEntity(User user, String fiscalNumber) {
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFiscalNumber(fiscalNumber);
        return customer;
    }
}
