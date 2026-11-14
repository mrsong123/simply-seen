package com.sujian.mall.integration.aliyun;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bounded in-memory JSON response; exception text never includes supplier payloads. */
final class BoundedJsonSubscriber implements HttpResponse.BodySubscriber<String> {
  private static final int MAX_BYTES = 65536;
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private final CompletableFuture<String> body = new CompletableFuture<>();
  private Flow.Subscription subscription;

  public CompletionStage<String> getBody() {
    return body;
  }

  public void onSubscribe(Flow.Subscription value) {
    subscription = value;
    subscription.request(1);
  }

  public void onNext(List<ByteBuffer> buffers) {
    for (ByteBuffer item : buffers) {
      if (item.remaining() > MAX_BYTES - buffer.size()) {
        subscription.cancel();
        body.completeExceptionally(new IllegalStateException("Provider response exceeds limit"));
        return;
      }
      byte[] data = new byte[item.remaining()];
      item.get(data);
      buffer.writeBytes(data);
    }
    subscription.request(1);
  }

  public void onError(Throwable failure) {
    body.completeExceptionally(new IllegalStateException("Provider response interrupted"));
  }

  public void onComplete() {
    body.complete(buffer.toString(StandardCharsets.UTF_8));
  }
}
