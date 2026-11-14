package com.sujian.mall.service.impl.identity;

import com.sujian.mall.entity.MemberIdentity;
import com.sujian.mall.config.Settings;
import java.util.LinkedHashMap;
import java.util.Map;

final class IdentityViews {
  private IdentityViews() {}

  static Map<String, Object> of(MemberIdentity row, Settings settings) {
    var view = new LinkedHashMap<String, Object>();
    view.put("status", row == null ? "UNVERIFIED" : row.getState());
    view.put("nameMasked", row == null ? "" : row.getNameMasked());
    view.put("idNumberMasked", row == null ? "" : row.getIdNumberMasked());
    view.put("verifiedAt", row == null ? null : row.getVerifiedAt());
    view.put("rejectionReason", row == null ? "" : row.getRejectionReason());
    view.put("development", row != null && Boolean.TRUE.equals(row.getDevelopment()));
    view.put("provider", row == null ? "" : row.getProvider());
    view.put("retryAfter", retryAfter(row, settings));
    return view;
  }

  static int pendingSeconds(Settings settings) {
    return Math.max(60, Math.min(600, settings.integer("IDENTITY_PENDING_TTL_SECONDS", 120)));
  }

  static int cooldownSeconds(Settings settings) {
    return Math.max(30, Math.min(300, settings.integer("IDENTITY_RETRY_AFTER_SECONDS", 60)));
  }

  private static long retryAfter(MemberIdentity row, Settings settings) {
    if (row == null) return 0;
    int seconds = switch (row.getState()) {
      case "PENDING" -> pendingSeconds(settings);
      case "REJECTED" -> cooldownSeconds(settings);
      default -> 0;
    };
    if (seconds == 0 || row.getLastAttemptAt() == null) return 0;
    long remaining = Math.max(0, row.getLastAttemptAt() + seconds * 1000L - System.currentTimeMillis());
    return Math.min(seconds, (remaining + 999) / 1000);
  }
}
