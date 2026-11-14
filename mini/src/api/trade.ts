import { request } from "../utils/request";
import type {
  CartItem,
  Quote,
  QuoteRequest,
  Order,
  OrderRequest,
  RefundRequest,
  Prepay,
  Track,
} from "../types/models";
export const cartApi = {
  list: () => request.get<CartItem[]>("/cart"),
  quantity: (skuId: number, quantity: number) =>
    request.put("/cart/" + skuId, { quantity }),
};
export const tradeApi = {
  quote: (data: QuoteRequest) => request.post<Quote>("/checkout/quote", data),
  create: (data: OrderRequest, key: string) =>
    request.post<Order>("/orders", data, { idempotencyKey: key }),
  orders: () => request.get<Order[]>("/orders"),
  order: (id: string) => request.get<Order>("/orders/" + id),
  prepay: (id: string) => request.post<Prepay>("/orders/" + id + "/prepay"),
  status: (id: string) =>
    request.get<Order>("/orders/" + id + "/payment-status"),
  cancel: (id: string) => request.post("/orders/" + id + "/cancel"),
  receive: (id: string) => request.post("/orders/" + id + "/receive"),
  devPay: (id: string) => request.post("/dev/orders/" + id + "/pay"),
  refund: (data: RefundRequest, key: string) =>
    request.post("/refunds", data, { idempotencyKey: key }),
  logistics: (id: string) =>
    request.get<Track[]>("/orders/" + id + "/logistics"),
};
