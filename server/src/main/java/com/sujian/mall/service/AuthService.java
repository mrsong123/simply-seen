package com.sujian.mall.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

public interface AuthService {
  record Actor(long id, String kind, String role) {}

  void limit(String bucket, int max);

  String encodePassword(String value);

  Actor actor(HttpServletRequest req);

  Actor member(HttpServletRequest req);

  Actor admin(HttpServletRequest req, String... roles);

  Map<String, Object> session(long id, String kind);

  Map<String, Object> login(Map<String, Object> body, String ip);

  Map<String, Object> devLogin(Map<String, Object> body);

  Map<String, Object> adminLogin(Map<String, Object> body, String ip);

  Map<String, Object> profile(long id);

  void updateProfile(long id, Map<String, Object> body);

  void promote(long id);

  void logout(HttpServletRequest r);

  void createAdmin(long actor, Map<String, Object> body);

  void password(long id, Map<String, Object> body);
}
