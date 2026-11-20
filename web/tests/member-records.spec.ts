import { test, expect } from "@playwright/test";

test("member asset filters and monthly awards use durable paged endpoints", async ({ page }) => {
  const profile = { id: 888, name: "会员记录界面验收", points: 10, available: 500, reserved: 0, debt: 0 };
  await page.addInitScript(value => localStorage.setItem("sj-member", JSON.stringify({ token: "controlled-records-session", kind: "MEMBER", profile: value })), profile);
  const requests: string[] = [];
  await page.route("**/api/v1/**", route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    let data: unknown;
    if (path === "/api/v1/config") data = { devMode: false, phoneLoginEnabled: true };
    else if (path === "/api/v1/me") data = profile;
    else if (path === "/api/v1/cart" || path === "/api/v1/categories") data = [];
    else if (path === "/api/v1/rules/public") data = {};
    else if (path === "/api/v1/me/records/ledger") {
      requests.push(url.searchParams.get("asset") || "all");
      const commission = url.searchParams.get("asset") === "available";
      data = { items: [{ id: 1, asset: commission ? "available" : "points", note: commission ? "佣金实际变动" : "积分实际变动", amount: commission ? 500 : 10, created_at: Date.now() }], total: 1, page: 1, size: 10, totalPages: 1, states: [], summary: {} };
    } else if (path === "/api/v1/me/records/awards") {
      data = { items: [{ id: 1, period: "2026-10", rank_no: 2, sales: 20000, amount: 500, state: "APPROVED", created_at: Date.now() }], total: 1, page: 1, size: 10, totalPages: 1, states: ["APPROVED"], summary: {} };
    } else return route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ code: "NOT_FOUND", message: "未声明的界面验收请求" }) });
    return route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: "OK", data }) });
  });
  await page.goto("/account");
  await expect(page.getByText("积分实际变动", { exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "资产类型", exact: true }).selectOption("available");
  await expect(page.getByText("佣金实际变动", { exact: true })).toBeVisible();
  expect(requests).toEqual(["all", "available"]);
  await page.getByRole("link", { name: "月度奖励", exact: true }).click();
  await expect(page).toHaveURL(/\/awards$/);
  await expect(page.getByRole("heading", { name: "月度奖励", exact: true })).toBeVisible();
  await expect(page.getByText("2026-10 月度奖励", { exact: true })).toBeVisible();
  await expect(page.getByText("第 2 名 · 有效业绩 ¥200.00", { exact: true })).toBeVisible();
  await expect(page.getByText("¥5.00", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "查看佣金余额与提现", exact: true })).toHaveAttribute("href", "/withdraw");
});
