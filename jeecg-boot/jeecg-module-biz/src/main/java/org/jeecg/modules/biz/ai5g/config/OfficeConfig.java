package org.jeecg.modules.biz.ai5g.config;

import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OfficeConfig {
  @Bean(destroyMethod = "stop")
  public LocalOfficeManager officeManager() {
    java.io.File officeHome = new java.io.File("/Applications/LibreOffice.app/Contents");
    LocalOfficeManager manager = LocalOfficeManager.builder()
        .officeHome(officeHome.exists() ? officeHome : null)
        .portNumbers(2002)
        .install()
        .build();
    try { manager.start(); } catch (Exception ignore) {}
    return manager;
  }
}

