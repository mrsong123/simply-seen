package com.sujian.mall.job;

import com.sujian.mall.service.MaintenanceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceJob {
  private final MaintenanceService maintenance;

  public MaintenanceJob(MaintenanceService maintenance) {
    this.maintenance = maintenance;
  }

  @Scheduled(
      fixedDelayString = "${MAINTENANCE_INTERVAL_MS:60000}",
      initialDelayString = "${JOB_INITIAL_DELAY_MS:10000}")
  public void run() {
    maintenance.maintain();
  }
}
