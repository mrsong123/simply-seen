import { beforeEach, describe, it, expect } from "vitest";
import { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { installRequestHooks } from "../../src/bootstrap/http";
import { pinia } from "../../src/store";
import { useAuthStore } from "../../src/store/auth";
import { useUiStore } from "../../src/store/ui";
import request, { ApiError } from "../../src/utils/request";
const memory = new Map<string, string>();
Object.defineProperty(globalThis, "localStorage", {
  value: {
    getItem: (key: string) => memory.get(key) ?? null,
    setItem: (key: string, value: string) => memory.set(key, value),
    removeItem: (key: string) => memory.delete(key),
  },
  configurable: true,
});
const response = (
  config: InternalAxiosRequestConfig,
  data: unknown,
  status = 200,
) => ({ config, data, status, statusText: "test", headers: {} });
beforeEach(() => {
  memory.clear();
  useAuthStore(pinia).$reset();
  useUiStore(pinia).$reset();
  installRequestHooks();
});
describe("Axios request wrapper", () => {
  it("post serializes payload, picks the correct token and forwards idempotency headers", async () => {
    const auth = useAuthStore(pinia);
    auth.saveSession("member", {
      token: "member-test-token",
      kind: "MEMBER",
      profile: {},
    });
    auth.saveSession("admin", {
      token: "admin-test-token",
      kind: "ADMIN",
      profile: {},
    });
    const data = await request.post(
      "/orders",
      { quoteId: "Q1" },
      {
        sessionKind: "admin",
        idempotencyKey: "request-key-001",
        adapter: async (config) => {
          expect(config.headers.get("Authorization")).toBe(
            "Bearer admin-test-token",
          );
          expect(config.headers.get("Idempotency-Key")).toBe("request-key-001");
          expect(JSON.parse(config.data)).toEqual({ quoteId: "Q1" });
          return response(config, { code: "OK", data: { id: "SJ1" } });
        },
      },
    );
    expect(data).toEqual({ id: "SJ1" });
    expect(useUiStore(pinia).pending).toBe(0);
  });
  it("public requests do not leak either identity token", async () => {
    useAuthStore(pinia).saveSession("member", {
      token: "member-test-token",
      kind: "MEMBER",
      profile: {},
    });
    await request.get("/products", {
      sessionKind: "none",
      adapter: async (config) => {
        expect(config.headers.has("Authorization")).toBe(false);
        return response(config, { code: "OK", data: [] });
      },
    });
  });
  it("401 invalidates only the session that issued the request and preserves requestId", async () => {
    const auth = useAuthStore(pinia);
    auth.saveSession("member", {
      token: "member-test-token",
      kind: "MEMBER",
      profile: {},
    });
    auth.saveSession("admin", {
      token: "admin-test-token",
      kind: "ADMIN",
      profile: {},
    });
    await expect(
      request.get("/admin/me", {
        sessionKind: "admin",
        adapter: async (config) => {
          throw new AxiosError(
            "Unauthorized",
            "ERR_BAD_REQUEST",
            config,
            {},
            response(
              config,
              {
                code: "AUTH_REQUIRED",
                message: "登录失效",
                requestId: "req-123",
              },
              401,
            ),
          );
        },
      }),
    ).rejects.toMatchObject({
      name: "ApiError",
      code: "AUTH_REQUIRED",
      status: 401,
      requestId: "req-123",
    });
    expect(auth.admin).toBeNull();
    expect(auth.member?.token).toBe("member-test-token");
    expect(useUiStore(pinia).error).toBe("登录失效");
    expect(useUiStore(pinia).pending).toBe(0);
  });
  it("does not clear a new member session when the previous session returns a late 401", async () => {
    const auth = useAuthStore(pinia);
    auth.saveSession("member", {
      token: "old-test-session",
      kind: "MEMBER",
      profile: { id: 1 },
    });
    await expect(
      request.get("/me", {
        adapter: async (config) => {
          expect(config.headers.get("Authorization")).toBe(
            "Bearer old-test-session",
          );
          auth.saveSession("member", {
            token: "new-test-session",
            kind: "MEMBER",
            profile: { id: 2 },
          });
          throw new AxiosError(
            "Unauthorized",
            "ERR_BAD_REQUEST",
            config,
            {},
            response(
              config,
              { code: "AUTH_REQUIRED", message: "原会话已失效" },
              401,
            ),
          );
        },
      }),
    ).rejects.toMatchObject({ status: 401 });
    expect(auth.member?.token).toBe("new-test-session");
    expect(auth.memberProfile?.id).toBe(2);
    expect(useUiStore(pinia).error).toBe("");
    expect(useUiStore(pinia).pending).toBe(0);
  });
  it("does not treat a 200 business failure as successful data", async () => {
    await expect(
      request.post(
        "/orders",
        {},
        {
          adapter: async (config) =>
            response(config, {
              code: "STOCK_NOT_ENOUGH",
              message: "库存不足",
              requestId: "req-stock",
            }),
        },
      ),
    ).rejects.toBeInstanceOf(ApiError);
    expect(useUiStore(pinia).error).toBe("库存不足");
    expect(useUiStore(pinia).pending).toBe(0);
  });
  it("reports a timeout and restores the loading counter", async () => {
    await expect(
      request.get("/products", {
        adapter: async (config) => {
          throw new AxiosError("Timeout", "ECONNABORTED", config);
        },
      }),
    ).rejects.toMatchObject({ message: "请求超时，请稍后重试" });
    expect(useUiStore(pinia).pending).toBe(0);
  });
});
