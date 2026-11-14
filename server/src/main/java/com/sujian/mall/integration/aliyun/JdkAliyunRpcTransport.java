package com.sujian.mall.integration.aliyun;

import com.sujian.mall.common.Problem;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Certificate-validated HTTPS, bounded response, fixed total deadline, no redirects or retries. */
@Component
public class JdkAliyunRpcTransport implements AliyunRpcTransport {
  private final HttpClient client;

  public JdkAliyunRpcTransport() {
    var builder =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER);
    String proxy = System.getenv("HTTPS_PROXY");
    if (proxy != null && !proxy.isBlank()) {
      try {
        URI address = URI.create(proxy);
        builder.proxy(
            ProxySelector.of(new InetSocketAddress(address.getHost(), address.getPort())));
      } catch (Exception ignored) {
        throw new Problem(503, "ACCOUNT_PROVIDER_PROXY", "账户验证服务代理配置不可用");
      }
    }
    client = builder.build();
  }

  public Response post(URI endpoint, String form) {
    CompletableFuture<HttpResponse<String>> pending = null;
    try {
      HttpRequest request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(15))
              .header("Accept", "application/json")
              .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
              .POST(HttpRequest.BodyPublishers.ofString(form))
              .build();
      pending = client.sendAsync(request, information -> new BoundedJsonSubscriber());
      HttpResponse<String> response = pending.get(15, TimeUnit.SECONDS);
      return new Response(response.statusCode(), response.body());
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (Exception ignored) {
      throw unavailable();
    } finally {
      if (pending != null && !pending.isDone()) pending.cancel(true);
    }
  }

  private Problem unavailable() {
    return new Problem(503, "ACCOUNT_PROVIDER_UNAVAILABLE", "账户验证服务暂不可用，请稍后重试");
  }
}
