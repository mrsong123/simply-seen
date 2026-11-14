import { test, expect, type Page, type BrowserContext } from "@playwright/test";
import { randomInt } from "node:crypto";
import type { PhoneChallenge } from "../src/types/account";

function testPhone() {
  return "18" + String(randomInt(100000000, 1000000000));
}
function testResidentId() {
  // Synthetic fixture used only with the development verifier; never an asserted real identity.
  const body =
    "110105" +
    String(randomInt(1980, 2000)) +
    String(randomInt(1, 13)).padStart(2, "0") +
    String(randomInt(1, 29)).padStart(2, "0") +
    String(randomInt(1, 1000)).padStart(3, "0");
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  return (
    body +
    "10X98765432"[
      weights.reduce(
        (sum, weight, index) => sum + weight * Number(body[index]),
        0,
      ) % 11
    ]
  );
}
async function profile(page: Page) {
  const token = await page.evaluate(
    () => JSON.parse(localStorage.getItem("sj-member") || "null")?.token,
  );
  const response = await page.request.get("/api/v1/me", {
    headers: { Authorization: "Bearer " + token },
  });
  expect(response.status()).toBe(200);
  const payload = await response.json();
  expect(payload.code).toBe("OK");
  return payload.data;
}
async function loginDialog(page: Page, phone: string) {
  await page.goto("/");
  await page.getByRole("button", { name: "我的账户" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("手机号码", { exact: true }).fill(phone);
  await dialog.getByRole("checkbox").check();
  return dialog;
}
async function sendChallenge(page: Page, path: string) {
  const response = page.waitForResponse(
    (r) => r.request().method() === "POST" && r.url().endsWith(path),
  );
  await page.getByRole("button", { name: "获取验证码", exact: true }).click();
  const received = await response;
  expect(received.status()).toBe(200);
  const payload = await received.json();
  expect(payload.code).toBe("OK");
  const challenge: PhoneChallenge = payload.data;
  expect(challenge.development).toBe(true);
  expect(
    typeof challenge.devCode === "string" && /^\d{6}$/.test(challenge.devCode),
  ).toBe(true);
  await expect(
    page.getByRole("status").filter({ hasText: "开发模拟短信验证码" }),
  ).toBeVisible();
  return { challenge, retryAt: Date.now() + challenge.retryAfter * 1000 };
}
async function submitLogin(page: Page, challenge: PhoneChallenge) {
  await page.getByLabel("短信验证码", { exact: true }).fill(challenge.devCode!);
  await page
    .getByRole("button", { name: "手机号登录 / 注册", exact: true })
    .click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
}
async function logout(page: Page) {
  await page.goto("/account");
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem("sj-member")))
    .toBeNull();
}
async function waitForActualCooldown(page: Page, retryAt: number) {
  // Observe the real server cooldown; do not change application limits or fake its clock.
  const remaining = retryAt - Date.now();
  if (remaining > 0) await page.waitForTimeout(remaining + 300);
}

test("real phone login, masked identity persistence and binding preserve existing membership", async ({
  page,
  browser,
  request,
}) => {
  test.setTimeout(180000);
  const configResponse = await request.get("/api/v1/config");
  expect(configResponse.status()).toBe(200);
  const config = (await configResponse.json()).data;
  expect(
    config.devMode,
    "This test must use the development SMS and identity providers",
  ).toBe(true);
  expect(config.phoneLoginEnabled).toBe(true);
  expect(config.identityVerificationEnabled).toBe(true);
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  let second: BrowserContext | undefined;
  try {
    const phoneA = testPhone();
    let phoneB = testPhone();
    while (phoneB === phoneA) phoneB = testPhone();
    await loginDialog(page, phoneA);
    const first = await sendChallenge(page, "/auth/phone/code");
    await submitLogin(page, first.challenge);
    const initial = await profile(page);
    expect(initial.phoneBound).toBe(true);
    expect(initial.phoneMasked.includes("*")).toBe(true);
    const reused = await request.post("/api/v1/auth/phone", {
      data: {
        phone: phoneA,
        code: first.challenge.devCode,
        challengeId: first.challenge.challengeId,
        accepted: true,
      },
    });
    expect(reused.status()).toBe(400);
    expect((await reused.json()).code).toBe("SMS_CODE_USED");

    await page.goto("/account/security");
    const idNumber = testResidentId();
    await page.getByLabel("真实姓名", { exact: true }).fill("认证测试甲");
    await page.getByLabel("身份证号码", { exact: true }).fill(idNumber);
    await page.getByRole("checkbox").check();
    await page
      .getByRole("button", { name: "提交实名认证", exact: true })
      .click();
    await expect(
      page.getByText("开发模拟 · 认证通过", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText("此结果为开发模拟", { exact: false }),
    ).toBeVisible();
    await expect(page.locator(".identity-summary")).toContainText("*");
    expect(
      await page.evaluate(
        (id) => document.body.textContent?.includes(id),
        idNumber,
      ),
    ).toBe(false);
    expect(
      await page.evaluate(
        (id) =>
          Object.values(localStorage).some((value) =>
            String(value).includes(id),
          ),
        idNumber,
      ),
    ).toBe(false);
    await page.reload();
    await expect(
      page.getByText("开发模拟 · 认证通过", { exact: true }),
    ).toBeVisible();
    expect((await profile(page)).identityStatus).toBe("VERIFIED");

    second = await browser.newContext({ baseURL: process.env.E2E_BASE_URL });
    const existingPage = await second.newPage();
    existingPage.on("pageerror", (error) => errors.push(error.message));
    await existingPage.goto("/");
    await existingPage.getByRole("button", { name: "我的账户" }).click();
    // Original commerce.spec.ts selectors remain visible and usable after phone login was added.
    await existingPage
      .getByLabel("测试身份名称")
      .fill("Existing browser " + Date.now());
    await existingPage
      .getByRole("button", { name: "登录开发账户", exact: true })
      .click();
    await expect(existingPage.getByRole("dialog")).not.toBeVisible();
    const existing = await profile(existingPage);
    expect(existing.phoneBound).toBe(false);
    await existingPage.goto("/account/security");
    await existingPage.getByLabel("手机号码", { exact: true }).fill(phoneB);
    const binding = await sendChallenge(existingPage, "/me/phone/code");
    await existingPage
      .getByLabel("短信验证码", { exact: true })
      .fill(binding.challenge.devCode!);
    await existingPage
      .getByRole("button", { name: "确认绑定手机号", exact: true })
      .click();
    await expect(
      existingPage.getByText("此手机号可用于登录当前会员", { exact: false }),
    ).toBeVisible();
    const bound = await profile(existingPage);
    expect(bound.id).toBe(existing.id);
    expect(bound.invite_code).toBe(existing.invite_code);
    expect(bound.phoneBound).toBe(true);
    await logout(page);
    await logout(existingPage);

    await waitForActualCooldown(page, first.retryAt);
    await loginDialog(page, phoneA);
    const repeat = await sendChallenge(page, "/auth/phone/code");
    await submitLogin(page, repeat.challenge);
    const returned = await profile(page);
    expect(returned.id).toBe(initial.id);
    expect(returned.invite_code).toBe(initial.invite_code);
    expect(returned.identityStatus).toBe("VERIFIED");
    expect(returned.identityDevelopment).toBe(true);

    await waitForActualCooldown(existingPage, binding.retryAt);
    await loginDialog(existingPage, phoneB);
    const boundLogin = await sendChallenge(existingPage, "/auth/phone/code");
    await submitLogin(existingPage, boundLogin.challenge);
    const preserved = await profile(existingPage);
    expect(preserved.id).toBe(existing.id);
    expect(preserved.invite_code).toBe(existing.invite_code);
    expect(preserved.phoneBound).toBe(true);
    expect(errors).toEqual([]);
  } finally {
    await second?.close();
  }
});
