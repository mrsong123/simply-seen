import { authStorage } from "./storage";
export const routes = {
  home: "/pages/index/index",
  points: "/pages/catalog/points",
  detail: "/pages/product/detail",
  cart: "/pages/cart/index",
  checkout: "/pages/checkout/index",
  orders: "/pages/orders/index",
  order: "/pages/orders/detail",
  account: "/pages/account/index",
  security: "/pages/account/security",
  identity: "/pages/account/identity",
  awards: "/pages/account/awards",
  addresses: "/pages/addresses/index",
  addressEdit: "/pages/addresses/edit",
  share: "/pages/promotion/index",
  withdraw: "/pages/wallet/withdraw",
  legal: "/pages/legal/index",
} as const;
export type RouteName = keyof typeof routes;
const tabRoutes: RouteName[] = ["home", "points", "cart", "account", "share"];
const protectedRoutes: RouteName[] = [
  "checkout",
  "orders",
  "order",
  "addresses",
  "addressEdit",
  "withdraw",
  "security",
  "identity",
  "awards",
];
export function pathFor(
  name: RouteName,
  query: Record<string, string | number> = {},
) {
  const params = Object.entries(query)
    .map(
      ([key, value]) =>
        encodeURIComponent(key) + "=" + encodeURIComponent(String(value)),
    )
    .join("&");
  return routes[name] + (params ? "?" + params : "");
}
export function navigate(
  name: RouteName,
  query: Record<string, string | number> = {},
) {
  const target = pathFor(name, query);
  if (protectedRoutes.includes(name) && !authStorage.token()) {
    uni.setStorageSync("sj-login-return", target);
    uni.navigateTo({ url: routes.account });
    return;
  }
  if (tabRoutes.includes(name)) uni.reLaunch({ url: target });
  else uni.navigateTo({ url: target });
}
export function requireSession(
  name: RouteName,
  query: Record<string, string | number> = {},
) {
  if (authStorage.token()) return true;
  uni.setStorageSync("sj-login-return", pathFor(name, query));
  uni.redirectTo({ url: routes.account });
  return false;
}
export function afterLogin() {
  const target: string = uni.getStorageSync("sj-login-return");
  uni.removeStorageSync("sj-login-return");
  if (typeof target !== "string") return;
  const path = target.split("?")[0];
  if (Object.values(routes).some((registered) => registered === path))
    uni.redirectTo({ url: target });
}
export function requireCurrentSession(): boolean {
  if (authStorage.token()) return true;
  const pages = typeof getCurrentPages === "function" ? getCurrentPages() : [];
  const current = pages[pages.length - 1];
  const path = current?.route ? "/" + current.route.replace(/^\//, "") : "";
  const found = Object.entries(routes).find(([, registered]) => registered === path);
  if (found) {
    const query: Record<string, string | number> = {};
    const options = (current as typeof current & {
      options?: Record<string, unknown>;
    })?.options;
    for (const [key, value] of Object.entries(options || {}))
      if (typeof value === "string" || typeof value === "number") query[key] = value;
    return requireSession(found[0] as RouteName, query);
  }
  uni.redirectTo({ url: routes.account });
  return false;
}
export function back() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else navigate("account");
}
