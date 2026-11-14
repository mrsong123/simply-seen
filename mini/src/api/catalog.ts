import { request } from "../utils/request";
import type { Category, Product, AppConfig, PublicRule } from "../types/models";
export const catalogApi = {
  config: () => request.get<AppConfig>("/config"),
  rules: () => request.get<PublicRule>("/rules/public"),
  categories: () => request.get<Category[]>("/categories"),
  products: (category = "", search = "") =>
    request.get<Product[]>("/products", { params: { category, search } }),
  detail: (id: number) => request.get<Product>("/products/" + id),
};
