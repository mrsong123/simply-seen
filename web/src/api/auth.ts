import request from "@/utils/request";
import type { PhoneChallenge, PhoneLogin } from "@/types/account";
import type { AuthSession } from "@/utils/session";
export const authApi = {
  sendPhoneCode: (phone: string) =>
    request.post<PhoneChallenge>(
      "/auth/phone/code",
      { phone },
      { sessionKind: "none" },
    ),
  loginPhone: (body: PhoneLogin) =>
    request.post<AuthSession>("/auth/phone", body, { sessionKind: "none" }),
  loginDev: (body: { name: string; inviteCode: string }) =>
    request.post("/auth/dev", body, { sessionKind: "none" }),
  loginAdmin: (body: { username: string; password: string }) =>
    request.post("/auth/admin", body, { sessionKind: "none" }),
  getMember: () => request.get("/me"),
  getAdmin: () => request.get("/admin/me", { sessionKind: "admin" }),
  updateMember: (body: { name: string }) => request.put("/me", body),
  logout: (kind: "member" | "admin") =>
    request.post("/auth/logout", undefined, { sessionKind: kind }),
};
