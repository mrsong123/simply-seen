package com.sujian.mall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TransactionConfiguration {
  @Bean
  TransactionTemplate businessTransactions(PlatformTransactionManager manager) {
    return new TransactionTemplate(manager);
  }
}
