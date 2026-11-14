package com.sujian.mall.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.sujian.mall.mapper")
public class MybatisConfiguration {
  @Bean
  ConfigurationCustomizer transactionSafeMapping() {
    return configuration -> {
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setCallSettersOnNulls(true);
      configuration.setJdbcTypeForNull(JdbcType.NULL);
      configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
    };
  }
}
