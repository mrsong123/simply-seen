export type PaymentChannel = "MINI_PROGRAM" | "NATIVE" | "H5";
export interface DevelopmentPayment {
  mode: "DEVELOPMENT";
  orderNo: string;
}
export interface NativePayment {
  mode: "NATIVE";
  codeUrl: string;
  expiresAt: number;
  orderNo: string;
}
export interface H5Payment {
  mode: "H5";
  h5Url: string;
  expiresAt: number;
  orderNo: string;
}
export type WebPayment = NativePayment | H5Payment;
export type PaymentResult = DevelopmentPayment | WebPayment;
export type PaymentProgress = "IDLE" | "PREPARING" | "WAITING" | "PAUSED" | "EXPIRED";
