export interface PageQuery { page: number; size: number; search: string; state: string; asset?: string }
export interface PageResult<T = any> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
  states: string[];
  summary?: Record<string, number>;
}
export type AdminRecordKind = "products" | "orders" | "refunds" | "members" | "commissions" | "ledger" | "withdrawals" | "rules" | "settlements" | "accounts" | "audit" | "issues" | "outbox" | "reconcile-batches";
export type MemberRecordKind = "orders" | "ledger" | "commissions" | "withdrawals" | "awards" | "invites" | "second-invites";
