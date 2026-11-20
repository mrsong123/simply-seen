import { request } from "../utils/request";
import type { RecordPage } from "../types/pagination";

export type MemberRecordKind =
  | "orders"
  | "ledger"
  | "commissions"
  | "withdrawals"
  | "awards"
  | "invites"
  | "second-invites";

export function memberRecords<T>(
  kind: MemberRecordKind,
  page: number,
  size: number,
  filters: { search?: string; state?: string; asset?: string } = {},
): Promise<RecordPage<T>> {
  return request.get<RecordPage<T>>("/me/records/" + kind, {
    params: { page, size, ...filters },
  });
}
