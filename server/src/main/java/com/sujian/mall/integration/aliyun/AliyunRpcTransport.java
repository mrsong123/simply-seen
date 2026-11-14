package com.sujian.mall.integration.aliyun;

import java.net.URI;

/** Transport boundary permits deterministic provider-contract tests without weakening HTTPS. */
public interface AliyunRpcTransport {
  record Response(int status, String body) {}

  Response post(URI endpoint, String form);
}
