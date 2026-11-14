import request from "@/utils/request";
const adminSession = { sessionKind: "admin" as const };
export const adminApi = {
  getProfile: () => request.get("/admin/me", adminSession),
  getSummary: () => request.get("/admin/summary", adminSession),
  getProducts: () => request.get("/admin/products", adminSession),
  saveProduct: (body: unknown) =>
    request.post("/admin/products", body, adminSession),
  saveCategory: (body: unknown) =>
    request.post("/admin/categories", body, adminSession),
  getOrders: () => request.get("/admin/orders", adminSession),
  getOrder: (id: string) => request.get(`/admin/orders/${id}`, adminSession),
  shipOrder: (id: string, body: unknown) =>
    request.post(`/admin/orders/${id}/ship`, body, adminSession),
  getRefunds: () => request.get("/admin/refunds", adminSession),
  reviewRefund: (id: string, body: unknown) =>
    request.post(`/admin/refunds/${id}/review`, body, adminSession),
  getMembers: () => request.get("/admin/members", adminSession),
  blockMember: (id: string | number, body: unknown) =>
    request.post(`/admin/members/${id}/block`, body, adminSession),
  getCommissions: () => request.get("/admin/commissions", adminSession),
  getLedger: () => request.get("/admin/ledger", adminSession),
  getWithdrawals: () => request.get("/admin/withdrawals", adminSession),
  reviewWithdrawal: (id: string, body: unknown) =>
    request.post(`/admin/withdrawals/${id}/review`, body, adminSession),
  executeWithdrawal: (id: string) =>
    request.post(`/admin/withdrawals/${id}/execute`, undefined, adminSession),
  queryWithdrawal: (id: string) =>
    request.post(`/admin/withdrawals/${id}/query`, undefined, adminSession),
  confirmDevWithdrawal: (id: string) =>
    request.post(
      `/admin/dev/withdrawals/${id}/confirm`,
      undefined,
      adminSession,
    ),
  getRules: () => request.get("/admin/rules", adminSession),
  createRule: (body: unknown) =>
    request.post("/admin/rules", body, adminSession),
  approveRule: (id: string | number) =>
    request.post(`/admin/rules/${id}/approve`, undefined, adminSession),
  getSettlements: () => request.get("/admin/settlements", adminSession),
  createSettlement: (body: unknown) =>
    request.post("/admin/settlements", body, adminSession),
  approveSettlement: (id: string | number) =>
    request.post(`/admin/settlements/${id}/approve`, undefined, adminSession),
  getReconciliation: () => request.get("/admin/reconciliation", adminSession),
  reconcile: () =>
    request.post("/admin/reconciliation", undefined, adminSession),
  resolveIssue: (id: string | number, body: unknown) =>
    request.post(`/admin/issues/${id}/resolve`, body, adminSession),
  getAccounts: () => request.get("/admin/accounts", adminSession),
  createAccount: (body: unknown) =>
    request.post("/admin/accounts", body, adminSession),
  setAccountActive: (id: string | number, body: unknown) =>
    request.post(`/admin/accounts/${id}/active`, body, adminSession),
  resetAccountPassword: (id: string | number, body: unknown) =>
    request.post(`/admin/accounts/${id}/reset-password`, body, adminSession),
  getAudit: () => request.get("/admin/audit", adminSession),
  updatePassword: (body: unknown) =>
    request.post("/admin/password", body, adminSession),
};
