import request from "@/utils/request";
export const authApi = {
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
