package com.sujian.mall.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.context.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
  private final Settings settings;

  public WebConfiguration(Settings settings) {
    this.settings = settings;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    String origins = settings.get("CORS_ORIGINS", "");
    if (!origins.isBlank())
      registry
          .addMapping("/api/**")
          .allowedOrigins(origins.split(","))
          .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
          .allowedHeaders("Authorization", "Content-Type", "Idempotency-Key")
          .maxAge(3600);
  }

  @Bean
  OncePerRequestFilter requestMetadata() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain chain)
          throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        request.setAttribute("requestId", id);
        response.setHeader("X-Request-Id", id);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Cache-Control", "no-store");
        if (request.getContentLengthLong() > 1048576) {
          response.sendError(413);
          return;
        }
        chain.doFilter(request, response);
      }
    };
  }
}
