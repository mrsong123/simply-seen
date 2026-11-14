package com.sujian.mall.config;

import com.sujian.mall.service.BootstrapService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupInitializer implements ApplicationRunner {
  private final BootstrapService bootstrap;

  public StartupInitializer(BootstrapService bootstrap) {
    this.bootstrap = bootstrap;
  }

  public void run(ApplicationArguments args) {
    bootstrap.initialize();
  }
}
