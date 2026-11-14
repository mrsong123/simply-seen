import { createRouter, createWebHistory } from "vue-router";
import { storefrontRoutes } from "./storefront-routes";
import { adminRoutes } from "./admin-routes";
import { accessGuard } from "./guards";
import { pinia } from "@/store";
import { useAuthStore } from "@/store/auth";
import { useUiStore } from "@/store/ui";
export const router = createRouter({
  history: createWebHistory(),
  routes: [...adminRoutes, ...storefrontRoutes],
  scrollBehavior: () => ({ top: 0 }),
});
router.beforeEach((to) =>
  accessGuard(to, useAuthStore(pinia), useUiStore(pinia)),
);
