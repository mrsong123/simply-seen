import { test, expect } from "@playwright/test";
import { readAdminCredentials } from "./support/admin-credentials";
const credentials = readAdminCredentials();
test("customer purchase, payment, fulfillment, refund and administration", async ({
  page,
  context,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "少一些， 更好一些。" }),
  ).toBeVisible();
  await expect(page.locator(".product-card")).toHaveCount(3);
  await expect(page.locator(".product-card").nth(2)).toBeVisible();
  await expect(page.getByRole("link", { name: "认识素见" })).toBeVisible();
  await page.screenshot({
    path: "../docs/screenshots/storefront-desktop.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "我的账户" }).click();
  await page.getByLabel("测试身份名称").fill("Browser " + Date.now());
  await page.getByRole("button", { name: "登录开发账户" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.goto("/addresses");
  await page.getByRole("button", { name: "新增地址" }).click();
  await page.getByLabel("收货人", { exact: true }).fill("浏览器验收");
  await page.getByLabel("手机号码").fill("13800138000");
  await page.getByLabel("省 / 市 / 区").fill("浙江省 杭州市 西湖区");
  await page.getByLabel("详细地址").fill("素见路1号");
  await page.getByRole("button", { name: "保存地址" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(page.getByText("浏览器验收")).toBeVisible();
  await page.goto("/product/1");
  await expect(page.getByRole("heading", { name: "云朵随行杯" })).toBeVisible();
  await page.getByRole("button", { name: "加入购物袋" }).click();
  await expect(page.getByRole("status")).toHaveText("已加入购物袋");
  await page.goto("/checkout");
  await expect(page.getByText("¥135.00", { exact: true })).toBeVisible();
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "提交订单" }).click();
  await expect(page.getByRole("heading", { name: "待付款" })).toBeVisible();
  await page.getByRole("button", { name: "前往支付" }).click();
  await page.getByRole("button", { name: "确认测试支付" }).click();
  await expect(page.getByRole("heading", { name: "待发货" })).toBeVisible();
  const no = page.url().split("/").pop()!;
  await page.screenshot({
    path: "../docs/screenshots/order-confirmed.png",
    fullPage: true,
  });
  const admin = await context.newPage();
  await admin.goto("/admin");
  await admin.getByLabel("账号", { exact: true }).fill(credentials.username);
  await admin.getByLabel("密码", { exact: true }).fill(credentials.password);
  await admin.getByRole("button", { name: "安全登录" }).click();
  await expect(admin.getByRole("heading", { name: "经营概览" })).toBeVisible();
  await admin.screenshot({
    path: "../docs/screenshots/admin-desktop.png",
    fullPage: true,
  });
  await admin.goto("/admin/orders");
  await admin.getByRole("textbox", { name: "搜索管理记录" }).fill(no);
  await admin.getByRole("button", { name: "发货", exact: true }).click();
  await admin.getByLabel("物流公司").fill("顺丰速运");
  await admin.getByLabel("运单号").fill("SF12345678901");
  await admin.getByRole("button", { name: "确认发货" }).click();
  await expect(admin.getByRole("dialog")).not.toBeVisible();
  await expect(
    admin.getByRole("table").getByText("待收货", { exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(page.getByText("顺丰速运 · SF12345678901")).toBeVisible();
  await page.getByRole("button", { name: "确认收货", exact: true }).click();
  await page.getByRole("button", { name: "确认操作" }).click();
  await expect(page.getByRole("heading", { name: "已完成" })).toBeVisible();
  await page.getByRole("button", { name: "申请售后" }).click();
  await page.getByLabel("退款原因").fill("验收退款测试");
  await page.getByRole("button", { name: "提交申请" }).click();
  await expect(page.getByText("待审核", { exact: true })).toBeVisible();
  await admin.goto("/admin/refunds");
  await admin.getByRole("textbox", { name: "搜索管理记录" }).fill(no);
  await admin.getByRole("button", { name: "审核", exact: true }).click();
  await admin.getByLabel("审核依据 / 驳回原因").fill("商品售后核验通过");
  await admin.getByRole("button", { name: "提交审核" }).click();
  await expect(
    admin.getByRole("table").getByText("已成功", { exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "已退款" })).toBeVisible();
  await expect(
    page.getByText("¥135.00", { exact: true }).first(),
  ).toBeVisible();
  await admin.goto("/admin/reconciliation");
  await admin.getByRole("button", { name: "执行对账" }).click();
  await expect(admin.getByRole("status")).toHaveText(
    "对账批次已生成，请检查差异结果",
  );
  await admin.goto("/admin/products");
  const download = admin.waitForEvent("download");
  await admin.getByRole("button", { name: "导出当前页" }).click();
  expect((await download).suggestedFilename()).toContain("商品管理");
  expect(errors).toEqual([]);
});
test("mobile storefront has no horizontal overflow and navigation works", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.locator(".hero")).toBeVisible();
  await expect(page.locator(".product-card")).toHaveCount(3);
  await expect(page.locator(".product-card:visible")).toHaveCount(2);
  await expect(page.getByRole("link", { name: "认识素见" })).toBeVisible();
  await page.screenshot({
    path: "../docs/screenshots/storefront-mobile.png",
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.getByRole("button", { name: "打开导航" }).click();
  await page
    .getByRole("navigation")
    .getByRole("link", { name: "全部好物" })
    .click();
  await expect(
    page.getByRole("heading", { name: "让喜欢，融入日常。" }),
  ).toBeVisible();
  await page.getByLabel("搜索好物").fill("香氛");
  await page.getByLabel("搜索好物").press("Enter");
  await expect(page.locator(".product-card")).toHaveCount(1);
});
