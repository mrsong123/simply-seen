import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosRequestConfig,
  type AxiosResponse,
} from "axios";
import { authStorage } from "./storage";
interface Envelope<T> {
  code: string;
  message: string;
  data: T;
  requestId?: string;
}
export interface RequestOptions extends AxiosRequestConfig {
  idempotencyKey?: string;
}
export class RequestError extends Error {
  readonly notified = true;
  constructor(
    message: string,
    public code: string,
    public requestId?: string,
  ) {
    super(message);
    this.name = "RequestError";
  }
}
const adapter: AxiosAdapter = (config) =>
  new Promise((resolve, reject) => {
    const headers = AxiosHeaders.from(config.headers).toJSON() as Record<
      string,
      string
    >;
    uni.request({
      url: axios.getUri(config),
      method: (
        config.method || "get"
      ).toUpperCase() as UniApp.RequestOptions["method"],
      data: config.data,
      header: headers,
      timeout: config.timeout || 15000,
      success(result) {
        const response: AxiosResponse = {
          data: result.data,
          status: result.statusCode,
          statusText: String(result.statusCode),
          headers: result.header,
          config,
        };
        if (!config.validateStatus || config.validateStatus(result.statusCode))
          resolve(response);
        else
          reject(
            new AxiosError(
              "请求失败",
              AxiosError.ERR_BAD_RESPONSE,
              config,
              undefined,
              response,
            ),
          );
      },
      fail(error) {
        reject(
          new AxiosError(
            error.errMsg || "网络连接失败",
            AxiosError.ERR_NETWORK,
            config,
          ),
        );
      },
    });
  });
const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
  adapter,
  headers: { "Content-Type": "application/json" },
});
client.interceptors.request.use((config) => {
  const token = authStorage.token();
  if (token) config.headers.set("Authorization", "Bearer " + token);
  return config;
});
function notify(error: RequestError): never {
  uni.showToast({ title: error.message, icon: "none" });
  throw error;
}
client.interceptors.response.use(
  (response) => {
    const body = response.data as Envelope<unknown>;
    if (!body || typeof body !== "object" || typeof body.code !== "string")
      return notify(
        new RequestError("接口响应无效，请检查API地址配置", "INVALID_RESPONSE"),
      );
    if (body.code !== "OK")
      return notify(
        new RequestError(body.message || "操作失败", body.code, body.requestId),
      );
    return response;
  },
  (error: AxiosError<Envelope<unknown>>) => {
    if (error.response?.status === 401) {
      authStorage.clear();
      uni.$emit("session-expired");
    }
    const body = error.response?.data;
    return notify(
      new RequestError(
        body?.message || "网络连接失败，请稍后重试",
        body?.code || error.code || "NETWORK",
        body?.requestId,
      ),
    );
  },
);
function options(config: RequestOptions = {}): AxiosRequestConfig {
  const { idempotencyKey, ...rest } = config;
  return {
    ...rest,
    headers: {
      ...rest.headers,
      ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}),
    },
  };
}
export const request = {
  async get<T>(url: string, config?: RequestOptions): Promise<T> {
    return (await client.get<Envelope<T>>(url, options(config))).data.data;
  },
  async post<T = void>(
    url: string,
    data?: unknown,
    config?: RequestOptions,
  ): Promise<T> {
    return (await client.post<Envelope<T>>(url, data, options(config))).data
      .data;
  },
  async put<T = void>(
    url: string,
    data?: unknown,
    config?: RequestOptions,
  ): Promise<T> {
    return (await client.put<Envelope<T>>(url, data, options(config))).data
      .data;
  },
  async delete<T = void>(url: string, config?: RequestOptions): Promise<T> {
    return (await client.delete<Envelope<T>>(url, options(config))).data.data;
  },
};
