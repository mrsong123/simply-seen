import { describe, it, expect, afterEach } from "vitest";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { readAdminCredentials } from "../support/admin-credentials";
const folders: string[] = [];
function fixture() {
  const dir = mkdtempSync(join(tmpdir(), "sujian-e2e-credentials-"));
  folders.push(dir);
  return dir;
}
afterEach(() => {
  for (const dir of folders.splice(0))
    rmSync(dir, { recursive: true, force: true });
});
describe("E2E credential discovery", () => {
  it("uses explicit environment credentials before local files", () => {
    const root = fixture();
    writeFileSync(
      join(root, ".env"),
      "ADMIN_BOOTSTRAP_PASSWORD=legacy-test-secret",
    );
    expect(
      readAdminCredentials(root, {
        E2E_ADMIN_USER: "operator",
        E2E_ADMIN_PASSWORD: "explicit-test-secret",
      }),
    ).toEqual({ username: "operator", password: "explicit-test-secret" });
  });
  it("uses the bootstrapped local credentials before a legacy env file", () => {
    const root = fixture();
    mkdirSync(join(root, "server/data"), { recursive: true });
    writeFileSync(
      join(root, "server/data/bootstrap-admin.txt"),
      "username=local-admin\npassword=local-test-secret\n",
    );
    writeFileSync(
      join(root, ".env"),
      "ADMIN_BOOTSTRAP_PASSWORD=legacy-test-secret",
    );
    expect(readAdminCredentials(root, {})).toEqual({
      username: "local-admin",
      password: "local-test-secret",
    });
  });
  it("supports a configured credentials path and never includes secret values in errors", () => {
    const root = fixture();
    writeFileSync(
      join(root, "credentials.txt"),
      "username=custom\npassword=custom-test-secret",
    );
    expect(
      readAdminCredentials(root, {
        E2E_ADMIN_CREDENTIALS_PATH: "credentials.txt",
      }),
    ).toEqual({ username: "custom", password: "custom-test-secret" });
    expect(() =>
      readAdminCredentials(root, {
        E2E_ADMIN_PASSWORD: "do-not-print-this-secret",
      }),
    ).toThrow("Set both E2E_ADMIN_USER and E2E_ADMIN_PASSWORD");
  });
});
