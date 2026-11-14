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
  if (target && target.startsWith("/pages/")) uni.redirectTo({ url: target });
}
export function back() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else navigate("account");
}
