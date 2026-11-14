package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.Problem;
import com.sujian.mall.integration.logistics.LogisticsClient;
import java.util.*;
import org.junit.jupiter.api.Test;

class LogisticsTest {
  @Test
  void parsesProviderTracksWithoutExposingProviderCredentials() {
    var rows =
        LogisticsClient.tracks(
            Map.of(
                "status",
                "200",
                "data",
                List.of(
                    Map.of(
                        "time", "2026-11-12 12:00:00", "context", "快件已签收", "phone", "private"))));
    assertEquals(1, rows.size());
    assertEquals(Set.of("time", "context"), rows.getFirst().keySet());
    assertEquals("快件已签收", rows.getFirst().get("context"));
  }

  @Test
  void noProviderResultCannotBeShownAsDelivered() {
    assertThrows(
        Problem.class, () -> LogisticsClient.tracks(Map.of("status", "201", "message", "暂无记录")));
  }
}
