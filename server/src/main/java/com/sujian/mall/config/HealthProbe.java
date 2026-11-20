package com.sujian.mall.config;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Container readiness probe: no Spring context and no external channel requests. */
public final class HealthProbe {
  public static void main(String[] arguments) {
    try {
      int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
          .timeout(Duration.ofSeconds(3)).GET().build();
      var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
          .send(request, HttpResponse.BodyHandlers.ofString());
      var status = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).path("status").asText();
      System.exit(response.statusCode() == 200 && status.equals("UP") ? 0 : 1);
    } catch (Exception failure) { System.exit(1); }
  }
}
