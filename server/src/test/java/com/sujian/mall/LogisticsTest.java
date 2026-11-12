package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class LogisticsTest {
  @Test
  void parsesProviderTracksWithoutExposingProviderCredentials() {
    var rows =
        Logistics.tracks(
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
    assertThrows(Problem.class, () -> Logistics.tracks(Map.of("status", "201", "message", "暂无记录")));
  }
}
