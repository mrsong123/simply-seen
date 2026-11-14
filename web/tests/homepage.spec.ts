import { expect, test } from "@playwright/test";

test("homepage shows loading and empty states while preserving the story link", async ({
  page,
}) => {
  let releaseProducts!: () => void;
  const productsReady = new Promise<void>((resolve) => {
    releaseProducts = resolve;
  });
  await page.route("**/api/v1/products", async (route) => {
    await productsReady;
    await route.fulfill({ json: { code: "OK", data: [] } });
  });

  try {
    await page.goto("/");
    await expect(
      page.getByRole("heading", { name: "正在为你挑选日常好物" }),
    ).toBeVisible();
    await expect(page.locator(".section[aria-busy='true']")).toBeVisible();
  } finally {
    releaseProducts();
  }

  await expect(
    page.getByRole("heading", { name: "好物正在准备中" }),
  ).toBeVisible();
  await expect(page.locator(".product-card")).toHaveCount(0);
  await expect(page.locator(".section[aria-busy='false']")).toBeVisible();
  await page.getByRole("link", { name: "认识素见" }).click();
  await expect(page).toHaveURL(/\/legal\/about$/);
  await expect(
    page.getByRole("heading", { name: "素见，生活本来的样子。" }),
  ).toBeVisible();
});

test("homepage retries a failed product request", async ({ page }) => {
  let attempts = 0;
  await page.route("**/api/v1/products", async (route) => {
    attempts += 1;
    if (attempts === 1) {
      await route.fulfill({ status: 503, json: { code: "UNAVAILABLE" } });
      return;
    }
    await route.continue();
  });

  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "暂时无法加载好物" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "重新加载" }).click();
  await expect(page.locator(".product-card")).toHaveCount(3);
  await expect(page.locator(".product-card").nth(2)).toBeVisible();
  expect(attempts).toBe(2);
});
