package com.sujian.mall.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private ResponseEntity<?> error(
      int status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "code",
                code,
                "message",
                message,
                "requestId",
                Objects.toString(request.getAttribute("requestId"), "")));
  }

  @ExceptionHandler(Problem.class)
  public ResponseEntity<?> business(Problem ex, HttpServletRequest request) {
    return error(ex.status, ex.code, ex.getMessage(), request);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class,
    ClassCastException.class,
    ArithmeticException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    MissingRequestHeaderException.class
  })
  public ResponseEntity<?> input(Exception ex, HttpServletRequest request) {
    String message = "请求字段格式不正确";
    if (ex instanceof MethodArgumentNotValidException validation
        && !validation.getBindingResult().getFieldErrors().isEmpty()) {
      var field = validation.getBindingResult().getFieldErrors().getFirst();
      message = field.getField() + "：" + field.getDefaultMessage();
    }
    return error(400, "INVALID_INPUT", message, request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> conflict(Exception ex, HttpServletRequest request) {
    return error(409, "DATA_CONFLICT", "数据重复或违反约束，请刷新后重试", request);
  }

  @ExceptionHandler(ConcurrencyFailureException.class)
  public ResponseEntity<?> concurrent(Exception ex, HttpServletRequest request) {
    return error(409, "CONCURRENT_UPDATE", "数据正在变更，请用原请求编号重试", request);
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<?> absent(Exception ex, HttpServletRequest request) {
    return error(404, "NOT_FOUND", "请求的资源不存在", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> unexpected(Exception ex, HttpServletRequest request) {
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error(
            "Unhandled failure type={} requestId={}",
            ex.getClass().getSimpleName(),
            request.getAttribute("requestId"));
    return error(500, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试", request);
  }
}
