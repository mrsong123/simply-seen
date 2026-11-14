import type { RouteLocationNormalized } from "vue-router";
import type { useAuthStore } from "@/store/auth";
import type { useUiStore } from "@/store/ui";
import { safeRedirect } from "@/utils/navigation";
type AuthAccess = Pick<
  ReturnType<typeof useAuthStore>,
  "member" | "admin" | "adminValidated" | "adminProfile" | "fetchAdminProfile"
>;
type Notifications = Pick<ReturnType<typeof useUiStore>, "message">;
export async function accessGuard(
  to: Pick<RouteLocationNormalized, "fullPath" | "name" | "query" | "meta">,
  auth: AuthAccess,
  ui: Notifications,
) {
  if (to.meta.requiresAdmin) {
    if (!auth.admin)
      return { path: "/admin/login", query: { redirect: to.fullPath } };
    try {
      if (!auth.adminValidated) await auth.fetchAdminProfile();
    } catch {
      return { path: "/admin/login", query: { redirect: to.fullPath } };
    }
    const roles = to.meta.roles as string[] | undefined;
    if (roles && !roles.includes(auth.adminProfile?.role)) {
      ui.message("当前账号没有页面访问权限", true);
      return "/admin";
    }
  }
  if (to.meta.requiresMember && !auth.member)
    return { name: "login", query: { redirect: to.fullPath } };
  if (to.name === "login" && auth.member)
    return safeRedirect(to.query.redirect, "/account");
  return true;
}
