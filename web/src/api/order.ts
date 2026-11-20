import type { Order, OrderDetail, Quote, ShipmentTrack } from "@/types/models";
import type { PaymentChannel, PaymentResult } from "@/types/payment";
import request from "@/utils/request";
export const orderApi = {
  getOrders: () => request.get<Order[]>("/orders"),
  getOrder: (id: string) =>
    request.get<OrderDetail>(`/orders/${encodeURIComponent(id)}`),
  getQuote: (body: Record<string, any>) =>
    request.post<Quote>("/checkout/quote", body),
  createOrder: (body: Record<string, any>, idempotencyKey: string) =>
    request.post<OrderDetail>("/orders", body, { idempotencyKey }),
  prepay: (id: string, channel: PaymentChannel = "MINI_PROGRAM") =>
    request.post<PaymentResult>(`/orders/${encodeURIComponent(id)}/prepay`, { channel }),
  confirmDevPayment: (id: string) =>
    request.post(`/dev/orders/${encodeURIComponent(id)}/pay`),
  cancel: (id: string) =>
    request.post(`/orders/${encodeURIComponent(id)}/cancel`),
  receive: (id: string) =>
    request.post(`/orders/${encodeURIComponent(id)}/receive`),
  queryPayment: (id: string, silent = false) =>
    request.get<OrderDetail>(`/orders/${encodeURIComponent(id)}/payment-status`, { silent }),
  getLogistics: (id: string) =>
    request.get<ShipmentTrack[]>(`/orders/${encodeURIComponent(id)}/logistics`),
  createRefund: (body: Record<string, any>, idempotencyKey: string) =>
    request.post("/refunds", body, { idempotencyKey }),
};
