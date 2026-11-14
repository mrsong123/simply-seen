package com.sujian.mall.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.sujian.mall.controller")
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {
  @Override
  public boolean supports(
      MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
    return method.getContainingClass().getPackageName().startsWith("com.sujian.mall.controller")
        && !method.getContainingClass().getSimpleName().equals("WechatCallbackController");
  }

  @Override
  public Object beforeBodyWrite(
      Object value,
      MethodParameter method,
      MediaType type,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    return ApiResponse.success(value, response.getHeaders().getFirst("X-Request-Id"));
  }
}
