import { afterEach, beforeEach, describe, it, expect } from "vitest";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import viteConfig from "../../vite.config";
const configure = viteConfig as (context: {
  mode: string;
  command: "serve" | "build";
}) => any;
const names = ["VITE_API_BASE_URL", "API_PROXY_TARGET", "WEB_PORT"];
let directory = "",
  previousDirectory = "";
let previous: Record<string, string | undefined> = {};
beforeEach(() => {
  previousDirectory = process.cwd();
  directory = mkdtempSync(join(tmpdir(), "sujian-vite-test-"));
  previous = Object.fromEntries(names.map((name) => [name, process.env[name]]));
  for (const name of names) delete process.env[name];
  process.chdir(directory);
  writeFileSync(
    ".env.development",
    "VITE_API_BASE_URL=/api/v1\nAPI_PROXY_TARGET=http://127.0.0.1:8080\nWEB_PORT=5173\n",
  );
  writeFileSync(".env.production", "VITE_API_BASE_URL=/api/v1\n");
});
afterEach(() => {
  process.chdir(previousDirectory);
  for (const name of names) {
    if (previous[name] === undefined) delete process.env[name];
    else process.env[name] = previous[name];
  }
  rmSync(directory, { recursive: true, force: true });
});
describe("frontend startup environment", () => {
  it("starts from tracked development defaults without requiring a private env file", () => {
    const result = configure({ mode: "development", command: "serve" });
    expect(result.server.proxy["/api"].target).toBe("http://127.0.0.1:8080");
    expect(result.server.port).toBe(5173);
    expect(result.define["import.meta.env.VITE_API_BASE_URL"]).toBe(
      '"/api/v1"',
    );
  });
  it("preserves local overrides and gives explicitly injected environment variables highest priority", () => {
    writeFileSync(
      ".env.local",
      "VITE_API_BASE_URL=/custom/api\nAPI_PROXY_TARGET=http://127.0.0.1:9000\nWEB_PORT=5182\n",
    );
    let result = configure({ mode: "development", command: "serve" });
    expect(result.server.proxy["/api"].target).toBe("http://127.0.0.1:9000");
    expect(result.server.port).toBe(5182);
    process.env.API_PROXY_TARGET = "http://127.0.0.1:9001";
    result = configure({ mode: "development", command: "serve" });
    expect(result.server.proxy["/api"].target).toBe("http://127.0.0.1:9001");
  });
  it("allows production same-origin requests but rejects an insecure external API base", () => {
    expect(
      configure({ mode: "production", command: "build" }).define[
        "import.meta.env.VITE_API_BASE_URL"
      ],
    ).toBe('"/api/v1"');
    process.env.VITE_API_BASE_URL = "http://insecure.example/api";
    expect(() => configure({ mode: "production", command: "build" })).toThrow(
      "Production API base must use HTTPS or a same-origin path",
    );
  });
});
