import type { PageResult, PageQuery } from "@/types/pagination";
import type {
  Category,
  ProductCard,
  ProductDetail,
  PublicConfig,
  PublicRules,
} from "@/types/models";
import request from "@/utils/request";
export const catalogApi = {
  getProductsPage: (params: PageQuery & { categoryId?: string; points?: boolean; sort?: string }) => request.get<PageResult>("/products/page", { params, sessionKind: "none" }),
  getConfig: () =>
    request.get<PublicConfig>("/config", { sessionKind: "none" }),
  getCategories: () =>
    request.get<Category[]>("/categories", { sessionKind: "none" }),
  getRules: () =>
    request.get<PublicRules>("/rules/public", { sessionKind: "none" }),
  getProducts: (params: { category?: string; search?: string } = {}) =>
    request.get<ProductCard[]>("/products", { params, sessionKind: "none" }),
  getProduct: (id: string | number) =>
    request.get<ProductDetail>(`/products/${encodeURIComponent(id)}`, {
      sessionKind: "none",
    }),
};
