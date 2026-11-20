import { test, expect, type Page } from "@playwright/test";
const profile = { id: 777, name: "支付界面验收", phoneBound: true, phoneMasked: "138****0000", wechatBound: false, invite_code: "QA777", points: 0, available: 0, reserved: 0, debt: 0 };
function order(state = "CREATED") {
  return { id: "SJ_UI_PAYMENT", member_id: 777, state, payment_state: state === "CREATED" ? "PREPAY" : "SUCCESS", payment_channel: "UNSELECTED", expires_at: Date.now() + 300000, cash_due: 12900, freight: 0, points_used: 0, cash_refunded: 0, points_refunded: 0, address: { name: "演示收货人", phone: "13800000000", region: "测试地区", detail: "测试地址" }, refunds: [], items: [{ id: 1, name: "云朵随行杯", image: "/art/cup.svg", quantity: 1, cash_amount: 12900, points_amount: 0 }] };
}
async function setup(page: Page, mode: "native" | "h5") {
  await page.addInitScript(value => localStorage.setItem("sj-member", JSON.stringify({ token: "controlled-ui-session", kind: "MEMBER", profile: value })), profile);
  let paid = false;
  const evidence = { requests: [] as any[] };
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname;
    let data: any;
    if (path === "/api/v1/config") data = { devMode: false, webPaymentMode: mode, phoneLoginEnabled: true };
    else if (path === "/api/v1/me") data = profile;
    else if (path === "/api/v1/rules/public") data = {};
    else if (path === "/api/v1/categories" || path === "/api/v1/cart") data = [];
    else if (path.endsWith("/prepay")) {
      const body = route.request().postDataJSON();
      evidence.requests.push(body);
      data = body.channel === "H5" ? { mode: "H5", h5Url: "https://wx.tenpay.com/controlled-pay", orderNo: "SJ_UI_PAYMENT", expiresAt: Date.now() + 60000 } : { mode: "NATIVE", codeUrl: "weixin://wxpay/bizpayurl?pr=controlled-ui", orderNo: "SJ_UI_PAYMENT", expiresAt: Date.now() + 60000 };
    } else if (path.endsWith("/payment-status")) { paid = true; data = order("PAID"); }
    else if (path === "/api/v1/orders/SJ_UI_PAYMENT") data = order(paid ? "PAID" : "CREATED");
    else return route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ code: "NOT_FOUND", message: "未声明的界面验收请求" }) });
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ code: "OK", data }) });
  });
  return evidence;
}
test("production native payment renders a real QR and waits for backend confirmation", async ({ page }) => {
  const evidence = await setup(page, "native");
  await page.goto("/orders/SJ_UI_PAYMENT");
  await page.getByRole("button", { name: "前往支付", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("heading", { name: "微信扫码支付" })).toBeVisible();
  await expect(dialog.getByRole("img", { name: "微信付款二维码" })).toHaveAttribute("src", /^data:image\/png;base64,/);
  await expect(dialog.getByRole("link", { name: "保存付款码" })).toHaveAttribute("download", "SJ_UI_PAYMENT-payment.png");
  await expect(page.getByRole("heading", { name: "待付款", exact: true })).toBeVisible();
  expect(evidence.requests).toEqual([{ channel: "NATIVE" }]);
  await dialog.getByRole("button", { name: "我已付款，查询结果" }).click();
  await expect(dialog).not.toBeVisible();
  await expect(page.getByRole("heading", { name: "待发货", exact: true })).toBeVisible();
});
test("configured mobile H5 flow follows the secure channel URL", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const evidence = await setup(page, "h5");
  await page.route("https://wx.tenpay.com/controlled-pay", route => route.fulfill({ contentType: "text/html; charset=utf-8", body: "<h1>受控渠道跳转验收</h1>" }));
  await page.goto("/orders/SJ_UI_PAYMENT");
  await page.getByRole("button", { name: "前往支付", exact: true }).click();
  await expect(page.getByRole("dialog").getByRole("heading", { name: "微信支付", exact: true })).toBeVisible();
  expect(evidence.requests).toEqual([{ channel: "H5" }]);
  await page.getByRole("link", { name: "打开微信支付" }).click();
  await expect(page).toHaveURL("https://wx.tenpay.com/controlled-pay");
  await expect(page.getByRole("heading", { name: "受控渠道跳转验收" })).toBeVisible();
});
test("expired orders block a new payment but retain result queries and cancellation", async ({ page }) => {
  const evidence = await setup(page, "native");
  let firstLoad = true;
  await page.route("**/api/v1/orders/SJ_UI_PAYMENT", route => {
    const data = firstLoad ? { ...order(), expires_at: Date.now() - 1000 } : order("PAID");
    firstLoad = false;
    return route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: "OK", data }) });
  });
  await page.goto("/orders/SJ_UI_PAYMENT");
  await expect(page.getByRole("button", { name: "订单已过期", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "取消订单", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "查询支付结果", exact: true }).click();
  await expect(page.getByRole("heading", { name: "待发货", exact: true })).toBeVisible();
  expect(evidence.requests).toEqual([]);
});
