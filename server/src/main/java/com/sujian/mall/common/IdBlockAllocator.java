package com.sujian.mall.common;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.sujian.mall.mapper.IdAllocatorMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

/** Commits a separate database-backed ID range without consuming the business pool. */
@Component
public class IdBlockAllocator {
  private final SqlSessionFactory allocationSessions;
  private long nextId, limitId;

  public IdBlockAllocator(DataSourceProperties properties) {
    var source =
        new DriverManagerDataSource(
            properties.determineUrl(),
            properties.determineUsername(),
            properties.determinePassword());
    var configuration = new MybatisConfiguration();
    configuration.setEnvironment(
        new Environment("id-allocation", new JdbcTransactionFactory(), source));
    configuration.addMapper(IdAllocatorMapper.class);
    allocationSessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
  }

  public synchronized long next() {
    if (nextId >= limitId)
      try (var session = allocationSessions.openSession(false)) {
        var mapper = session.getMapper(IdAllocatorMapper.class);
        long base = mapper.lockNextId();
        mapper.advanceNextId(base + 1000);
        session.commit();
        nextId = base;
        limitId = base + 1000;
      }
    return nextId++;
  }
}
