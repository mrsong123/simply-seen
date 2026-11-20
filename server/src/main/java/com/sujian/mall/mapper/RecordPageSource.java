package com.sujian.mall.mapper;

import com.sujian.mall.common.Problem;
import java.util.*;

/** Fixed SQL projections: callers never choose table, column, join, or sort expressions. */
public record RecordPageSource(
    String kind, String from, String columns, List<String> search, String state,
    String scope, String sort, Set<String> roles, String enrichment, boolean catalog) {
  private static final Map<String, RecordPageSource> ADMIN = new LinkedHashMap<>();
  private static final Map<String, RecordPageSource> MEMBER = new LinkedHashMap<>();
  private static final String ORDER_FIELDS = "t.id,t.member_id,t.state,t.payment_state,t.item_amount,t.freight,t.points_used,t.cash_due,t.cash_refunded,t.points_refunded,t.remark,t.transaction_id,t.expires_at,t.created_at,t.paid_at,t.completed_at,t.carrier,t.tracking_no,t.payment_channel";
  private static final String COMMISSION_FIELDS = "t.id,t.order_id,t.beneficiary_id,t.buyer_id,t.level_no,t.amount,t.original_amount,t.state,t.settled_at,t.created_at";
  private static final String LEDGER_FIELDS = "t.id,t.member_id,t.event_key,t.asset,t.amount,t.balance,t.order_id,t.note,t.created_at";
  private static final String WITHDRAWAL_FIELDS = "t.id,t.member_id,t.gross,t.tax,t.net,t.state,t.reviewer,t.executor,t.channel_id,t.package_info,t.reason,t.created_at";
  private static final String MEMBER_FIELDS = "t.id,t.name,t.invite_code,t.inviter_id,t.points,t.points_held,t.available,t.reserved,t.debt,t.promoter,t.blocked,t.created_at";
  private static final Set<String> ALL = Set.of("ADMIN", "OPERATOR", "FINANCE");
  private static final Set<String> OPERATIONS = Set.of("ADMIN", "OPERATOR");
  private static final Set<String> FINANCE = Set.of("ADMIN", "FINANCE");

  static {
    addAdmin("products", "product t", "t.*", "t.id,t.name,t.subtitle", "t.status", "", "t.id DESC", OPERATIONS, "products");
    addAdmin("orders", "shop_order t LEFT JOIN member m ON m.id=t.member_id", ORDER_FIELDS + ",m.name AS member_name", "t.id,t.member_id,m.name,t.carrier,t.tracking_no", "t.state", "", "t.created_at DESC,t.id DESC", ALL, "");
    addAdmin("refunds", "refund t", "t.id,t.order_id,t.member_id,t.state,t.amount,t.points,t.reason,t.creator,t.reviewer,t.channel_id,t.created_at", "t.id,t.order_id,t.member_id,t.reason", "t.state", "", "t.created_at DESC,t.id DESC", ALL, "");
    addAdmin("members", "member t", MEMBER_FIELDS, "t.id,t.name,t.invite_code", "CASE WHEN t.blocked THEN 'BLOCKED' ELSE 'ACTIVE' END", "", "t.created_at DESC,t.id DESC", ALL, "");
    addAdmin("commissions", "commission t", COMMISSION_FIELDS, "t.id,t.order_id,t.beneficiary_id,t.buyer_id", "t.state", "", "t.created_at DESC,t.id DESC", FINANCE, "");
    addAdmin("ledger", "ledger t", LEDGER_FIELDS, "t.id,t.member_id,t.event_key,t.order_id,t.note", "", "", "t.created_at DESC,t.id DESC", FINANCE, "");
    addAdmin("withdrawals", "withdrawal t", WITHDRAWAL_FIELDS, "t.id,t.member_id,t.channel_id,t.reason", "t.state", "", "t.created_at DESC,t.id DESC", FINANCE, "");
    addAdmin("rules", "rule_version t", "t.id,t.state,t.config,t.creator,t.reviewer,t.created_at", "t.id,t.creator,t.reviewer", "t.state", "", "t.created_at DESC,t.id DESC", ALL, "");
    addAdmin("settlements", "settlement t", "t.id,t.period,t.state,t.budget,t.creator,t.reviewer,t.created_at", "t.id,t.period,t.creator,t.reviewer", "t.state", "", "t.created_at DESC,t.id DESC", ALL, "settlements");
    addAdmin("accounts", "admin_account t", "t.id,t.username,t.role,t.active", "t.id,t.username,t.role", "CASE WHEN t.active THEN 'ACTIVE' ELSE 'DISABLED' END", "", "t.id DESC", Set.of("ADMIN"), "");
    addAdmin("audit", "audit t", "t.id,t.actor_id,t.action,t.resource,t.detail,t.created_at", "t.id,t.actor_id,t.action,t.resource,t.detail", "", "", "t.created_at DESC,t.id DESC", FINANCE, "");
    addAdmin("issues", "issue t", "t.id,t.kind,t.resource,t.detail,t.state,t.created_at", "t.id,t.kind,t.resource,t.detail", "t.state", "", "t.created_at DESC,t.id DESC", FINANCE, "");
    addAdmin("outbox", "outbox t", "t.id,t.kind,t.aggregate_id,t.state,t.attempts,t.next_at,t.last_error,t.created_at", "t.id,t.kind,t.aggregate_id,t.last_error", "t.state", "", "t.created_at DESC,t.id DESC", FINANCE, "");
    addAdmin("reconcile-batches", "reconcile_batch t", "t.id,t.period_day,t.orders_total,t.refunds_total,t.transfers_total,t.diff_count,t.created_at", "t.id,t.period_day", "", "", "t.created_at DESC,t.id DESC", FINANCE, "");
    addMember("orders", "shop_order t", ORDER_FIELDS, "t.id,t.carrier,t.tracking_no", "t.state", "t.member_id=#{memberId}", "orders");
    addMember("ledger", "ledger t", LEDGER_FIELDS, "t.id,t.order_id,t.note", "", "t.member_id=#{memberId}", "");
    addMember("commissions", "commission t", COMMISSION_FIELDS, "t.id,t.order_id", "t.state", "t.beneficiary_id=#{memberId}", "");
    addMember("withdrawals", "withdrawal t", WITHDRAWAL_FIELDS, "t.id,t.channel_id,t.reason", "t.state", "t.member_id=#{memberId}", "");
    addMember("awards", "settlement_award t JOIN settlement s ON s.id=t.settlement_id", "t.id,t.settlement_id,t.member_id,t.sales,t.amount,t.rank_no,s.period,s.state,s.created_at", "t.id,s.period", "s.state", "t.member_id=#{memberId} AND s.state='APPROVED'", "");
    addMember("invites", "member t", "t.id,t.name,t.promoter,t.created_at", "t.id,t.name", "", "t.inviter_id=#{memberId}", "");
    addMember("second-invites", "member t JOIN member parent ON t.inviter_id=parent.id", "t.id,t.name,t.created_at", "t.id,t.name", "", "parent.inviter_id=#{memberId}", "");
  }

  private static void addAdmin(String kind, String from, String fields, String search, String state,
      String scope, String sort, Set<String> roles, String enrichment) {
    ADMIN.put(kind, new RecordPageSource(kind, from, fields, List.of(search.split(",")), state, scope, sort, roles, enrichment, false));
  }

  private static void addMember(String kind, String from, String fields, String search, String state,
      String scope, String enrichment) {
    String sort = kind.equals("awards") ? "s.created_at DESC,t.id DESC" : "t.created_at DESC,t.id DESC";
    MEMBER.put(kind, new RecordPageSource(kind, from, fields, List.of(search.split(",")), state, scope, sort, Set.of("MEMBER"), enrichment, false));
  }

  public static RecordPageSource admin(String kind) { return required(ADMIN, kind); }
  public static RecordPageSource member(String kind) { return required(MEMBER, kind); }
  private static RecordPageSource required(Map<String, RecordPageSource> resources, String kind) {
    var source = resources.get(kind);
    if (source == null) throw new Problem(404, "RECORD_RESOURCE_NOT_FOUND", "记录类型不存在");
    return source;
  }

  public static RecordPageSource products() {
    return new RecordPageSource("public-products", "product t",
        "t.*,(SELECT MIN(s.price) FROM sku s WHERE s.product_id=t.id AND s.active=TRUE) AS price,(SELECT COALESCE(SUM(s.stock-s.held),0) FROM sku s WHERE s.product_id=t.id AND s.active=TRUE) AS stock",
        List.of("t.id", "t.name", "t.subtitle"), "",
        "t.status='ON_SALE' AND EXISTS(SELECT 1 FROM sku s WHERE s.product_id=t.id AND s.active=TRUE)",
        "t.featured DESC,t.id", Set.of(), "", true);
  }
}
