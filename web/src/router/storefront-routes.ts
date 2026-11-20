import type { RouteRecordRaw } from "vue-router";
import StoreLayout from "@/layouts/StoreLayout.vue";
export const storefrontRoutes: RouteRecordRaw[] = [
  {
    path: "/",
    component: StoreLayout,
    children: [
      {
        path: "",
        name: "home",
        component: () => import("@/views/storefront/HomeView.vue"),
      },
      {
        path: "catalog",
        name: "catalog",
        component: () => import("@/views/storefront/CatalogView.vue"),
      },
      {
        path: "points",
        name: "points",
        component: () => import("@/views/storefront/CatalogView.vue"),
        props: { points: true },
      },
      {
        path: "product/:id(\\d+)",
        name: "product",
        component: () => import("@/views/storefront/ProductView.vue"),
      },
      {
        path: "login",
        name: "login",
        component: () => import("@/views/storefront/LoginView.vue"),
      },
      {
        path: "cart",
        name: "cart",
        component: () => import("@/views/storefront/CartView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "checkout",
        name: "checkout",
        component: () => import("@/views/storefront/CheckoutView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "orders",
        name: "orders",
        component: () => import("@/views/storefront/OrdersView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "orders/:id",
        name: "order-detail",
        component: () => import("@/views/storefront/OrderDetailView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "account/security",
        name: "account-security",
        component: () => import("@/views/storefront/AccountSecurityView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "account",
        name: "account",
        component: () => import("@/views/storefront/AccountView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "addresses",
        name: "addresses",
        component: () => import("@/views/storefront/AddressesView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "share",
        name: "share",
        component: () => import("@/views/storefront/ShareView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "awards",
        name: "awards",
        component: () => import("@/views/storefront/AwardsView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "withdraw",
        name: "withdraw",
        component: () => import("@/views/storefront/WithdrawView.vue"),
        meta: { requiresMember: true },
      },
      {
        path: "legal/:section(about|privacy|terms|distribution)",
        name: "legal",
        component: () => import("@/views/storefront/LegalView.vue"),
      },
      {
        path: ":pathMatch(.*)*",
        name: "not-found",
        component: () => import("@/views/storefront/NotFoundView.vue"),
      },
    ],
  },
];
