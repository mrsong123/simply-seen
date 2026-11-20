import type { MemberRecordKind, PageQuery, PageResult } from "@/types/pagination";
import type { Address, LedgerEntry, Withdrawal } from "@/types/models";
import request from "@/utils/request";
export const memberApi = {
  getRecords: <T = any>(kind: MemberRecordKind, params: PageQuery) => request.get<PageResult<T>>(`/me/records/${kind}`, { params }),
  getAddresses: () => request.get<Address[]>("/addresses"),
  saveAddress: (body: Record<string, any>) => request.post("/addresses", body),
  deleteAddress: (id: number) => request.delete(`/addresses/${id}`),
  getLedger: () => request.get<LedgerEntry[]>("/me/ledger"),
  getTeam: () => request.get("/me/team"),
  acceptPromotion: (accepted: boolean) =>
    request.post("/me/promoter", { accepted }),
  getWithdrawals: () => request.get<Withdrawal[]>("/me/withdrawals"),
  createWithdrawal: (amountFen: number, idempotencyKey: string) =>
    request.post("/me/withdrawals", { amountFen }, { idempotencyKey }),
  getAwards: () => request.get("/me/awards"),
};
