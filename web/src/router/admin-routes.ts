import type { RouteRecordRaw } from "vue-router";
import { adminDefinitions } from "@/config/admin";
const meta = (page: string) => ({
  requiresAdmin: true,
  roles: adminDefinitions[page].roles,
  adminPage: page,
});
export const adminRoutes: RouteRecordRaw[] = [
  {
    path: "/admin/login",
    name: "admin-login",
    component: () => import("@/views/admin/AdminLoginView.vue"),
  },
  {
    path: "/admin",
    component: () => import("@/layouts/AdminLayout.vue"),
    meta: { requiresAdmin: true },
    children: [
      { path: "overview", redirect: { name: "admin-dashboard" } },
      {
        path: "",
        name: "admin-dashboard",
        component: () => import("@/views/admin/AdminDashboardView.vue"),
        meta: meta("overview"),
      },
      {
        path: "products",
        name: "admin-products",
        component: () => import("@/views/admin/AdminProductsView.vue"),
        meta: meta("products"),
      },
      {
        path: "orders",
        name: "admin-orders",
        component: () => import("@/views/admin/AdminOrdersView.vue"),
        meta: meta("orders"),
      },
      {
        path: "refunds",
        name: "admin-refunds",
        component: () => import("@/views/admin/AdminRefundsView.vue"),
        meta: meta("refunds"),
      },
      {
        path: "members",
        name: "admin-members",
        component: () => import("@/views/admin/AdminMembersView.vue"),
        meta: meta("members"),
      },
      {
        path: "commissions",
        name: "admin-commissions",
        component: () => import("@/views/admin/AdminCommissionsView.vue"),
        meta: meta("commissions"),
      },
      {
        path: "ledger",
        name: "admin-ledger",
        component: () => import("@/views/admin/AdminLedgerView.vue"),
        meta: meta("ledger"),
      },
      {
        path: "withdrawals",
        name: "admin-withdrawals",
        component: () => import("@/views/admin/AdminWithdrawalsView.vue"),
        meta: meta("withdrawals"),
      },
      {
        path: "rules",
        name: "admin-rules",
        component: () => import("@/views/admin/AdminRulesView.vue"),
        meta: meta("rules"),
      },
      {
        path: "settlements",
        name: "admin-settlements",
        component: () => import("@/views/admin/AdminSettlementsView.vue"),
        meta: meta("settlements"),
      },
      {
        path: "reconciliation",
        name: "admin-reconciliation",
        component: () => import("@/views/admin/AdminReconciliationView.vue"),
        meta: meta("reconciliation"),
      },
      {
        path: "accounts",
        name: "admin-accounts",
        component: () => import("@/views/admin/AdminAccountsView.vue"),
        meta: meta("accounts"),
      },
      {
        path: "audit",
        name: "admin-audit",
        component: () => import("@/views/admin/AdminAuditView.vue"),
        meta: meta("audit"),
      },
    ],
  },
];
