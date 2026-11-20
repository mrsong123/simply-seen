package com.sujian.mall.service.impl;

import com.sujian.mall.service.*;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Stable commerce API delegates each payment responsibility to its own domain service. */
@Service
public class PaymentServiceImpl implements PaymentService {
  private final PaymentInitiationService initiation;
  private final PaymentSettlementService settlement;
  private final PaymentEvidenceService evidence;
  private final PaymentCancellationService cancellation;
  public PaymentServiceImpl(PaymentInitiationService initiation, PaymentSettlementService settlement,
      PaymentEvidenceService evidence, PaymentCancellationService cancellation) {
    this.initiation = initiation; this.settlement = settlement;
    this.evidence = evidence; this.cancellation = cancellation;
  }
  public void paid(String no, String transaction, long amount) { settlement.paid(no, transaction, amount); }
  public void closeLocal(String no) { settlement.closeLocal(no); }
  public Object prepay(long memberId, String no) { return prepay(memberId, no, "MINI_PROGRAM", ""); }
  public Object prepay(long memberId, String no, String channel, String ip) {
    return initiation.prepay(memberId, no, channel, ip);
  }
  public void synchronizePayment(String no) { evidence.synchronize(no); }
  public void paymentEvidence(Map<String, Object> result) { evidence.apply(result); }
  public void cancel(long memberId, String no) { cancellation.cancel(memberId, no); }
}
