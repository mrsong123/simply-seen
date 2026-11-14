import { defineConfig } from "@playwright/test";
if (!process.env.E2E_BASE_URL)
  throw new Error("Set E2E_BASE_URL to the running web server");
export default defineConfig({
  testDir: "./tests",
  testMatch: "**/*.spec.ts",
  testIgnore: ["**/unit/**", "**/support/**"],
  timeout: 60000,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL,
    headless: true,
    viewport: { width: 1440, height: 1000 },
    launchOptions: {
      executablePath: process.env.CHROMIUM_PATH,
      args: ["--no-sandbox"],
    },
    screenshot: "only-on-failure",
  },
});
