package com.sujian.mall.common;

import com.sujian.mall.config.Settings;

/** The selected product binds its app identifier into the durable order evidence. */
public enum PaymentChannel {
  MINI_PROGRAM("JSAPI", "jsapi", "WX_APP_ID"),
  NATIVE("NATIVE", "native", "WX_NATIVE_APP_ID"),
  H5("MWEB", "h5", "WX_H5_APP_ID");

  private final String tradeType, product, appIdSetting;
  PaymentChannel(String tradeType, String product, String appIdSetting) {
    this.tradeType = tradeType; this.product = product; this.appIdSetting = appIdSetting;
  }
  public String tradeType() { return tradeType; }
  public String product() { return product; }
  public String appId(Settings settings) {
    String configured = settings.get(appIdSetting, "").trim();
    return configured.isBlank() ? settings.required("WX_APP_ID") : configured;
  }
}
