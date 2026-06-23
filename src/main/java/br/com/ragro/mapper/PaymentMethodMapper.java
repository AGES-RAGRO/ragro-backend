package br.com.ragro.mapper;

import br.com.ragro.controller.response.BankInfoResponse;
import br.com.ragro.domain.PaymentMethod;

/** Shared {@link PaymentMethod} mapping — used by the cart and by orders. */
public class PaymentMethodMapper {

  private PaymentMethodMapper() {}

  public static BankInfoResponse toBankInfo(PaymentMethod paymentMethod) {
    if (paymentMethod == null) {
      return null;
    }
    return BankInfoResponse.builder()
        .bankName(paymentMethod.getBankName())
        .bankCode(paymentMethod.getBankCode())
        .agency(paymentMethod.getAgency())
        .account(paymentMethod.getAccountNumber())
        .accountType(paymentMethod.getAccountType())
        .pixKey(paymentMethod.getPixKey())
        .pixType(paymentMethod.getPixKeyType())
        .holderName(paymentMethod.getHolderName())
        .build();
  }
}
