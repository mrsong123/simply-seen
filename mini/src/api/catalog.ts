import { request } from "../utils/request";
import type { Category, Product, AppConfig, PublicRule } from "../types/models";
import type { RecordPage } from "../types/pagination";
export const catalogApi = {
  config: () => request.get<AppConfig>("/config", { anonymous: true }),
  rules: () => request.get<PublicRule>("/rules/public", { anonymous: true }),
  categories: () => request.get<Category[]>("/categories", { anonymous: true }),
  products: (category = "", search = "") =>
    request.get<Product[]>("/products", {
      anonymous: true,
      params: { category, search },
    }),
  detail: (id: number) =>
    request.get<Product>("/products/" + id, { anonymous: true }),
  page: (
    page: number,
    size: number,
    categoryId = "",
    search = "",
    points = false,
  ) =>
    request.get<RecordPage<Product>>("/products/page", {
      anonymous: true,
      params: { page, size, categoryId, search, points },
    }),
};
