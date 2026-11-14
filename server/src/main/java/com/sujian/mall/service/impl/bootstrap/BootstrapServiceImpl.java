package com.sujian.mall.service.impl.bootstrap;

import com.sujian.mall.common.*;
import com.sujian.mall.config.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapServiceImpl implements BootstrapService {
  private final ProductionConfigurationValidator validator;
  private final AdminBootstrapService admins;
  private final RuleBootstrapService rules;
  private final DemoCatalogBootstrapService catalog;

  public BootstrapServiceImpl(
      ProductionConfigurationValidator validator,
      AdminBootstrapService admins,
      RuleBootstrapService rules,
      DemoCatalogBootstrapService catalog) {
    this.validator = validator;
    this.admins = admins;
    this.rules = rules;
    this.catalog = catalog;
  }

  @Transactional
  public void initialize() {
    validator.validate();
    admins.initialize();
    rules.initialize();
    catalog.initialize();
  }
}
