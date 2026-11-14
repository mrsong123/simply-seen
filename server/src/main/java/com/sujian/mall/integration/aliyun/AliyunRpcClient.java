package com.sujian.mall.integration.aliyun;

import com.sujian.mall.common.JsonCodec;
import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Aliyun RPC calls; secrets and personal fields remain in the HTTPS POST body only. */
@Component
public class AliyunRpcClient {
  private final Settings settings;
  private final JsonCodec json;
  private final AliyunRpcTransport transport;

  public AliyunRpcClient(Settings settings, JsonCodec json, AliyunRpcTransport transport) {
    this.settings = settings;
    this.json = json;
    this.transport = transport;
  }

  public Map<String, Object> call(
      String prefix, String action, String version, Map<String, String> fields) {
    URI endpoint = endpoint(settings.required(prefix + "_API_URL"));
    var parameters = new TreeMap<String, String>();
    parameters.putAll(fields);
    parameters.put("Action", action);
    parameters.put("Version", version);
    parameters.put("Format", "JSON");
    parameters.put("AccessKeyId", settings.required(prefix + "_ACCESS_KEY_ID"));
    parameters.put("RegionId", settings.required(prefix + "_REGION_ID"));
    parameters.put("SignatureMethod", "HMAC-SHA1");
    parameters.put("SignatureVersion", "1.0");
    parameters.put("SignatureNonce", UUID.randomUUID().toString());
    parameters.put("Timestamp", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
    parameters.put(
        "Signature",
        AliyunRpcSigner.sign(parameters, settings.required(prefix + "_ACCESS_KEY_SECRET")));
    AliyunRpcTransport.Response response =
        transport.post(endpoint, AliyunRpcSigner.form(parameters));
    if (response.status() < 200 || response.status() >= 300)
      throw new Problem(503, "ACCOUNT_PROVIDER_UNAVAILABLE", "账户验证渠道暂不可用，请稍后重试");
    try {
      if (response.body() == null || response.body().length() > 65536)
        throw new IllegalArgumentException();
      Map<String, Object> decoded = json.decode(response.body());
      if (decoded == null) throw new IllegalArgumentException();
      return decoded;
    } catch (RuntimeException ignored) {
      throw new Problem(503, "ACCOUNT_PROVIDER_RESPONSE", "账户验证渠道响应异常，请稍后重试");
    }
  }

  public static String requestId(Map<String, Object> response) {
    Object value = response.get("RequestId");
    if (!(value instanceof String id) || !id.matches("[A-Za-z0-9_-]{1,128}"))
      throw new Problem(503, "ACCOUNT_PROVIDER_RESPONSE", "账户验证渠道缺少有效回执，请稍后重试");
    return id;
  }

  private URI endpoint(String value) {
    try {
      URI uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || (!uri.getPath().isEmpty() && !uri.getPath().equals("/")))
        throw new IllegalArgumentException();
      return uri.getPath().isEmpty() ? URI.create(value + "/") : uri;
    } catch (RuntimeException ignored) {
      throw new Problem(503, "ACCOUNT_PROVIDER_ENDPOINT", "账户验证渠道须配置不含查询参数的HTTPS根地址");
    }
  }
}
