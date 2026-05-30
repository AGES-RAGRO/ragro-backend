package br.com.ragro.mapper;

import br.com.ragro.controller.response.BankInfoResponse;
import br.com.ragro.controller.response.CartItemResponse;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.domain.Cart;
import br.com.ragro.domain.CartItem;
import br.com.ragro.domain.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public class CartMapper {

  public static CartResponse toResponse(Cart cart) {
    return toResponse(cart, List.of());
  }

  public static CartResponse toResponse(Cart cart, List<PaymentMethod> paymentMethods) {
    List<CartItemResponse> itemResponses = cart.getItems().stream()
        .filter(CartItem::isActive)
        .map(CartMapper::toItemResponse)
        .collect(Collectors.toList());

    BigDecimal total = itemResponses.stream()
        .map(CartItemResponse::getSubtotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return CartResponse.builder()
        .id(cart.getId())
        .farmerId(cart.getFarmer().getId())
        .farmName(cart.getFarmer().getFarmName())
        .items(itemResponses)
        .totalAmount(total)
        .bankInfo(toBankInfo(paymentMethods))
        .build();
  }

  public static CartItemResponse toItemResponse(CartItem item) {
    BigDecimal subtotal = item.getProduct().getPrice().multiply(item.getQuantity());

    return CartItemResponse.builder()
        .id(item.getId())
        .productId(item.getProduct().getId())
        .productName(item.getProduct().getName())
        .unitPrice(item.getProduct().getPrice())
        .unityType(item.getProduct().getUnityType())
        .imageS3(item.getProduct().getImageS3())
        .quantity(item.getQuantity())
        .subtotal(subtotal)
        .build();
  }

  private static BankInfoResponse toBankInfo(List<PaymentMethod> methods) {
    if (methods == null || methods.isEmpty()) {
      return null;
    }
    String bankName = null, bankCode = null, agency = null, account = null,
        accountType = null, pixKey = null, pixType = null, holderName = null;

    for (PaymentMethod pm : methods) {
      if (!StringUtils.hasText(bankName))    bankName    = pm.getBankName();
      if (!StringUtils.hasText(bankCode))    bankCode    = pm.getBankCode();
      if (!StringUtils.hasText(agency))      agency      = pm.getAgency();
      if (!StringUtils.hasText(account))     account     = pm.getAccountNumber();
      if (!StringUtils.hasText(accountType)) accountType = pm.getAccountType();
      if (!StringUtils.hasText(pixKey))      pixKey      = pm.getPixKey();
      if (!StringUtils.hasText(pixType))     pixType     = pm.getPixKeyType();
      if (!StringUtils.hasText(holderName))  holderName  = pm.getHolderName();
    }

    if (!StringUtils.hasText(bankName) && !StringUtils.hasText(pixKey) && !StringUtils.hasText(account)) {
      return null;
    }

    return BankInfoResponse.builder()
        .bankName(bankName)
        .bankCode(bankCode)
        .agency(agency)
        .account(account)
        .accountType(accountType)
        .pixKey(pixKey)
        .pixType(pixType)
        .holderName(holderName)
        .build();
  }
}
