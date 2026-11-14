import axios, {
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";
import { readSession, persistSession, type SessionKind } from "@/utils/session";
export interface RequestConfig extends AxiosRequestConfig {
  sessionKind?: SessionKind | "none";
  idempotencyKey?: string;
  silent?: boolean;
}
interface InternalConfig extends InternalAxiosRequestConfig {
  sessionKind?: SessionKind | "none";
  idempotencyKey?: string;
  silent?: boolean;
}
export class ApiError extends Error {
  constructor(
    message: string,
    public code: string,
    public status: number,
    public requestId?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}
export interface RequestHooks {
  onPendingChange?: (pending: number) => void;
  onError?: (error: ApiError) => void;
  onUnauthorized?: (kind: SessionKind) => void;
}
let hooks: RequestHooks = {},
  pending = 0;
export function configureRequestHooks(value: RequestHooks) {
  hooks = value;
  hooks.onPendingChange?.(pending);
}
function changePending(delta: number) {
  pending = Math.max(0, pending + delta);
  hooks.onPendingChange?.(pending);
}
const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  headers: { "Content-Type": "application/json" },
});
instance.interceptors.request.use((raw) => {
  const config = raw as InternalConfig;
  changePending(1);
  const kind = config.sessionKind ?? "member";
  const session = kind === "none" ? null : readSession(kind);
  if (session?.token)
    config.headers.set("Authorization", `Bearer ${session.token}`);
  if (config.idempotencyKey)
    config.headers.set("Idempotency-Key", config.idempotencyKey);
  return config;
});
function fail(error: any, config?: InternalConfig): never {
  const status = error.response?.status ?? 0;
  const payload = error.response?.data;
  const message =
    payload?.message ||
    (error.code === "ECONNABORTED"
      ? "请求超时，请稍后重试"
      : "无法连接服务，请检查后端与接口配置");
  if (status === 401 && config?.sessionKind !== "none") {
    const kind = config?.sessionKind ?? "member";
    persistSession(kind, null);
    hooks.onUnauthorized?.(kind);
  }
  const failure =
    error instanceof ApiError
      ? error
      : new ApiError(
          message,
          payload?.code || "NETWORK_ERROR",
          status,
          payload?.requestId,
        );
  if (!config?.silent) hooks.onError?.(failure);
  throw failure;
}
instance.interceptors.response.use(
  (response) => {
    changePending(-1);
    if (response.data?.code !== "OK")
      return fail(
        new ApiError(
          response.data?.message || "服务响应异常",
          response.data?.code || "INVALID_RESPONSE",
          response.status,
          response.data?.requestId,
        ),
        response.config as InternalConfig,
      );
    return response.data.data;
  },
  (error) => {
    changePending(-1);
    return fail(error, error.config);
  },
);
const request = {
  get<T = any>(url: string, config?: RequestConfig): Promise<T> {
    return instance.get(url, config) as unknown as Promise<T>;
  },
  post<T = any>(
    url: string,
    data?: unknown,
    config?: RequestConfig,
  ): Promise<T> {
    return instance.post(url, data, config) as unknown as Promise<T>;
  },
  put<T = any>(
    url: string,
    data?: unknown,
    config?: RequestConfig,
  ): Promise<T> {
    return instance.put(url, data, config) as unknown as Promise<T>;
  },
  delete<T = any>(url: string, config?: RequestConfig): Promise<T> {
    return instance.delete(url, config) as unknown as Promise<T>;
  },
};
export default request;
