import { request } from "../utils/request";
import type {
  Session,
  Member,
  Ledger,
  Team,
  Withdrawal,
} from "../types/models";
export const memberApi = {
  login: (code: string, inviteCode: string) =>
    request.post<Session>(
      "/auth/wechat",
      { code, inviteCode },
      { anonymous: true },
    ),
  devLogin: (name: string, inviteCode: string) =>
    request.post<Session>(
      "/auth/dev",
      { name, inviteCode },
      { anonymous: true },
    ),
  logout: () => request.post("/auth/logout"),
  profile: () => request.get<Member>("/me"),
  updateName: (name: string) => request.put("/me", { name }),
  ledger: () => request.get<Ledger[]>("/me/ledger"),
  promoter: (accepted: boolean) => request.post("/me/promoter", { accepted }),
  team: () => request.get<Team>("/me/team"),
  withdrawals: () => request.get<Withdrawal[]>("/me/withdrawals"),
  withdraw: (amountFen: number, key: string) =>
    request.post<{ id: string; state: string }>(
      "/me/withdrawals",
      { amountFen },
      { idempotencyKey: key },
    ),
};
