import type { CartItem } from "@/types/models";
import request from "@/utils/request";
export const cartApi = {
  getItems: (silent = false) => request.get<CartItem[]>("/cart", { silent }),
  updateItem: (sku: number, quantity: number) =>
    request.put(`/cart/${sku}`, { quantity }),
};
