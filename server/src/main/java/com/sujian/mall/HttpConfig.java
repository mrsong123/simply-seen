package com.sujian.mall;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.MethodParameter;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Configuration
public class HttpConfig implements WebMvcConfigurer {
  final Settings c;

  public HttpConfig(Settings c) {
    this.c = c;
  }

  public void addCorsMappings(CorsRegistry r) {
    String origins = c.get("CORS_ORIGINS", "");
    if (!origins.isBlank())
      r.addMapping("/api/**")
          .allowedOrigins(origins.split(","))
          .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
          .allowedHeaders("Authorization", "Content-Type", "Idempotency-Key")
          .maxAge(3600);
  }

  @Bean
  OncePerRequestFilter headers() {
    return new OncePerRequestFilter() {
      protected void doFilterInternal(
          HttpServletRequest r, HttpServletResponse s, FilterChain chain)
          throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        r.setAttribute("requestId", id);
        s.setHeader("X-Request-Id", id);
        s.setHeader("X-Content-Type-Options", "nosniff");
        s.setHeader("X-Frame-Options", "DENY");
        s.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        s.setHeader("Cache-Control", "no-store");
        if (r.getContentLengthLong() > 1048576) {
          s.sendError(413);
          return;
        }
        chain.doFilter(r, s);
      }
    };
  }

  @RestControllerAdvice
  public static class Errors {
    @ExceptionHandler(Problem.class)
    ResponseEntity<?> problem(Problem p, HttpServletRequest r) {
      return ResponseEntity.status(p.status)
          .body(
              Map.of(
                  "code",
                  p.code,
                  "message",
                  p.getMessage(),
                  "requestId",
                  Objects.toString(r.getAttribute("requestId"), "")));
    }

    @ExceptionHandler({
      IllegalArgumentException.class,
      ClassCastException.class,
      org.springframework.http.converter.HttpMessageNotReadableException.class,
      org.springframework.web.bind.MissingRequestHeaderException.class
    })
    ResponseEntity<?> invalid(Exception e) {
      return ResponseEntity.badRequest()
          .body(Map.of("code", "INVALID_INPUT", "message", "请求字段格式不正确"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception e) {
      return ResponseEntity.status(409)
          .body(Map.of("code", "DATA_CONFLICT", "message", "数据重复或违反约束，请刷新后重试"));
    }

    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    ResponseEntity<?> concurrency(Exception e) {
      return ResponseEntity.status(409)
          .body(Map.of("code", "CONCURRENT_UPDATE", "message", "数据正在变更，请用原请求编号重试"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
      org.slf4j.LoggerFactory.getLogger(Errors.class)
          .error("Unhandled failure: {}", e.getClass().getSimpleName());
      return ResponseEntity.status(500)
          .body(Map.of("code", "INTERNAL_ERROR", "message", "服务暂时不可用，请稍后重试"));
    }
  }

  @RestControllerAdvice(basePackageClasses = Api.class)
  public static class Envelope implements ResponseBodyAdvice<Object> {
    public boolean supports(MethodParameter p, Class<? extends HttpMessageConverter<?>> t) {
      return p.getContainingClass() == Api.class;
    }

    public Object beforeBodyWrite(
        Object body,
        MethodParameter p,
        MediaType t,
        Class<? extends HttpMessageConverter<?>> converter,
        ServerHttpRequest req,
        ServerHttpResponse res) {
      var result = new LinkedHashMap<String, Object>();
      result.put("code", "OK");
      result.put("message", "success");
      result.put("data", body);
      result.put("requestId", res.getHeaders().getFirst("X-Request-Id"));
      return result;
    }
  }
}
