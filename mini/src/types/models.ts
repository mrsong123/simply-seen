export interface Category {
  id: number;
  name: string;
  sort_no: number;
}
export interface Product {
  id: number;
  name: string;
  subtitle: string;
  description: string;
  image: string;
  color: string;
  price: number;
  stock: number;
  featured: boolean;
  points_percent: number;
  skus: Sku[];
}
export interface Sku {
  id: number;
  name: string;
  stock: number;
  price: number;
}
export interface Member {
  id: number;
  name: string;
  invite_code: string;
  promoter: boolean;
  points: number;
  points_held: number;
  available: number;
  reserved: number;
  debt: number;
}
export interface AppConfig {
  devMode: boolean;
  distributionEnabled: boolean;
  transferEnabled: boolean;
  logisticsEnabled: boolean;
  appId: string;
  mchId: string;
  support: string;
  privacy: string;
  shareBase: string;
}
export interface PublicRule {
  level1Bps: number;
  level2Bps: number;
  settlementDays: number;
  pointsPer100Yuan: number;
  freightFen: number;
  freeShippingFen: number;
  distributionEnabled: boolean;
}
export interface Session {
  token: string;
  profile: Member;
  kind: string;
}
export interface CartItem {
  sku_id: number;
  product_id: number;
  name: string;
  sku_name: string;
  image: string;
  color: string;
  price: number;
  quantity: number;
  stock: number;
  status: string;
}
export interface Address {
  id?: number;
  member_id?: number;
  name: string;
  phone: string;
  region: string;
  detail: string;
  is_default: boolean;
}
export interface Quote {
  quoteId: string;
  itemAmountFen: number;
  freightFen: number;
  pointsUsed: number;
  pointsAllowed: number;
  cashDueFen: number;
  expiresAt: number;
}
export interface OrderItem {
  id: number;
  sku_id: number;
  name: string;
  image: string;
  quantity: number;
  refunded_quantity: number;
  cash_amount: number;
  points_amount: number;
}
export interface Refund {
  id: string;
  amount: number;
  state: string;
  reason: string;
  created_at: number;
}
export interface Order {
  id: string;
  state: string;
  payment_state: string;
  cash_due: number;
  cash_refunded: number;
  points_used: number;
  freight: number;
  created_at: number;
  expires_at: number;
  carrier?: string;
  tracking_no?: string;
  address: Address;
  items: OrderItem[];
  refunds: Refund[];
}
export interface Ledger {
  id: number;
  asset: string;
  amount: number;
  balance: number;
  note: string;
  created_at: number;
}
export interface Commission {
  id: number;
  order_id: string;
  level_no: number;
  state: string;
  amount: number;
  created_at: number;
}
export interface Invitee {
  id: number;
  name: string;
  created_at: number;
}
export interface Team {
  level1: Invitee[];
  level2: Invitee[];
  commissions: Commission[];
}
export interface Withdrawal {
  id: string;
  gross: number;
  tax: number;
  net: number;
  state: string;
  package_info: string;
  reason: string;
  created_at: number;
}
export interface Track {
  time: string;
  context: string;
}
export interface OrderRequest {
  quoteId: string;
  addressId: number;
  buyerRemark: string;
}
export interface QuoteRequest {
  addressId: number;
  pointsRequested: number;
  items: { skuId: number; quantity: number }[];
}
export interface RefundRequest {
  orderNo: string;
  reason: string;
  lines: { orderItemId: number; quantity: number }[];
}
export interface WechatPrepay {
  timeStamp: string;
  nonceStr: string;
  package: string;
  signType: "RSA";
  paySign: string;
}
export interface DevelopmentPrepay {
  mode: "DEVELOPMENT";
  orderNo: string;
}
export type Prepay = WechatPrepay | DevelopmentPrepay;
