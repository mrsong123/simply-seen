package com.sujian.mall.service.impl;

import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CommerceServiceImpl implements CommerceService {
  private final CatalogService catalogService;
  private final AddressService addressService;
  private final CartService cartService;
  private final CheckoutService checkoutService;
  private final IdempotencyService idempotencyService;
  private final OrderCreationService orderCreationService;
  private final OrderQueryService orderQueryService;
  private final AssetService assetService;
  private final BenefitsService benefitsService;
  private final PaymentService paymentService;
  private final FulfillmentService fulfillmentService;

  public CommerceServiceImpl(
      CatalogService catalogService,
      AddressService addressService,
      CartService cartService,
      CheckoutService checkoutService,
      IdempotencyService idempotencyService,
      OrderCreationService orderCreationService,
      OrderQueryService orderQueryService,
      AssetService assetService,
      BenefitsService benefitsService,
      PaymentService paymentService,
      FulfillmentService fulfillmentService) {
    this.catalogService = catalogService;
    this.addressService = addressService;
    this.cartService = cartService;
    this.checkoutService = checkoutService;
    this.idempotencyService = idempotencyService;
    this.orderCreationService = orderCreationService;
    this.orderQueryService = orderQueryService;
    this.assetService = assetService;
    this.benefitsService = benefitsService;
    this.paymentService = paymentService;
    this.fulfillmentService = fulfillmentService;
  }

  public Map<String, Object> rules() {
    return catalogService.rules();
  }

  public List<Map<String, Object>> products(String category, String search) {
    return catalogService.products(category, search);
  }

  public Map<String, Object> product(long id) {
    return catalogService.product(id);
  }

  public List<Map<String, Object>> addresses(long uid) {
    return addressService.addresses(uid);
  }

  public Object address(long uid, Map<String, Object> b) {
    return addressService.address(uid, b);
  }

  public List<Map<String, Object>> cart(long uid) {
    return cartService.cart(uid);
  }

  public void cart(long uid, long sku, int qty) {
    cartService.cart(uid, sku, qty);
  }

  public Map<String, Object> quote(long uid, Map<String, Object> b) {
    return checkoutService.quote(uid, b);
  }

  public Object idem(
      long uid,
      String operation,
      String key,
      Object body,
      java.util.function.Supplier<Object> action) {
    return idempotencyService.idem(uid, operation, key, body, action);
  }

  public Object create(long uid, String key, Map<String, Object> b) {
    return orderCreationService.create(uid, key, b);
  }

  public List<Map<String, Object>> orders(long uid) {
    return orderQueryService.orders(uid);
  }

  public Map<String, Object> order(long uid, String no) {
    return orderQueryService.order(uid, no);
  }

  public void asset(long uid, String asset, long delta, String event, String order, String note) {
    assetService.asset(uid, asset, delta, event, order, note);
  }

  public void benefits(String no) {
    benefitsService.benefits(no);
  }

  public void paid(String no, String transaction, long amount) {
    paymentService.paid(no, transaction, amount);
  }

  public void closeLocal(String no) {
    paymentService.closeLocal(no);
  }

  public Object prepay(long uid, String no) {
    return paymentService.prepay(uid, no);
  }

  public void synchronizePayment(String no) {
    paymentService.synchronizePayment(no);
  }

  public void paymentEvidence(Map<String, Object> r) {
    paymentService.paymentEvidence(r);
  }

  public void cancel(long uid, String no) {
    paymentService.cancel(uid, no);
  }

  public void ship(long admin, String no, Map<String, Object> body) {
    fulfillmentService.ship(admin, no, body);
  }

  public void receive(long uid, String no) {
    fulfillmentService.receive(uid, no);
  }
}
