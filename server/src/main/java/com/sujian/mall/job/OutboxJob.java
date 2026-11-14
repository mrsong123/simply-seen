package com.sujian.mall.job;

import com.sujian.mall.service.OutboxProcessingService;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxJob {
  private final OutboxProcessingService processing;

  public OutboxJob(OutboxProcessingService processing) {
    this.processing = processing;
  }

  @Scheduled(
      fixedDelayString = "${JOB_INTERVAL_MS:10000}",
      initialDelayString = "${JOB_INITIAL_DELAY_MS:10000}")
  public void run() {
    processing.run();
  }

  public void refund(String no, long attempt) {
    processing.refund(no, attempt);
  }

  public void refundResult(String no, Map<String, Object> result) {
    processing.refundResult(no, result);
  }

  public void transfer(String no, long attempt) {
    processing.transfer(no, attempt);
  }

  public void validateTransfer(String no, Map<String, Object> result) {
    processing.validateTransfer(no, result);
  }
}
