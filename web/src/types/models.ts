export interface Category {
  id: number;
  name: string;
  sort_no: number;
}
export interface Product {
  id: number;
  category_id: number;
  name: string;
  subtitle: string;
  description: string;
  image: string;
  color: string;
  status: "ON_SALE" | "OFF_SALE";
  featured: boolean;
  points_percent: number;
  commission_percent: number;
}
export interface ProductCard extends Product {
  price: number;
  stock: number;
}
export interface ProductSku {
  id: number;
  name: string;
  price: number;
  stock: number;
}
export interface ProductDetail extends Product {
  skus: ProductSku[];
}
export interface CartItem {
  sku_id: number;
  product_id: number;
  quantity: number;
  price: number;
  stock: number;
  sku_name: string;
  name: string;
  image: string;
  color: string;
  status: string;
}
export interface Address {
  id: number;
  member_id: number;
  name: string;
  phone: string;
  region: string;
  detail: string;
  is_default: boolean;
}
export interface MemberProfile {
  phoneBound: boolean;
  phoneMasked: string;
  wechatBound: boolean;
  identityStatus: string;
  identityDevelopment: boolean;
  id: number;
  name: string;
  invite_code: string;
  inviter_id: number | null;
  points: number;
  points_held: number;
  available: number;
  reserved: number;
  debt: number;
  promoter: boolean;
  created_at: number;
}
export interface PublicConfig {
  phoneLoginEnabled: boolean;
  identityVerificationEnabled: boolean;
  identityRequiredForWithdrawal: boolean;
  brand: string;
  appId: string;
  mchId: string;
  support: string;
  privacy: string;
  shareBase: string;
  devMode: boolean;
  distributionEnabled: boolean;
  transferEnabled: boolean;
  monthlyEnabled: boolean;
  logisticsEnabled: boolean;
}
export interface PublicRules {
  version: number;
  pointsPer100Yuan: number;
  freightFen: number;
  freeShippingFen: number;
  settlementDays: number;
  level1Bps: number;
  level2Bps: number;
  distributionEnabled: boolean;
}
export interface QuoteRequest {
  items: { skuId: number; quantity: number }[];
  addressId: number;
  pointsRequested: number;
}
export interface Quote {
  quoteId: string;
  itemAmountFen: number;
  freightFen: number;
  pointsUsed: number;
  pointsAllowed: number;
  cashDueFen: number;
  expiresAt: number;
  address: Address;
  rule: Record<string, unknown>;
  items: Record<string, unknown>[];
}
export interface OrderItem {
  id: number;
  order_id: string;
  sku_id: number;
  product_id: number;
  name: string;
  image: string;
  quantity: number;
  unit_price: number;
  cash_amount: number;
  points_amount: number;
  refunded_quantity: number;
  refunded_cash: number;
  refunded_points: number;
}
export interface Refund {
  id: string;
  order_id: string;
  member_id: number;
  state: string;
  amount: number;
  points: number;
  reason: string;
  created_at: number;
}
export interface Order {
  id: string;
  member_id: number;
  state: string;
  payment_state: string;
  item_amount: number;
  freight: number;
  points_used: number;
  cash_due: number;
  cash_refunded: number;
  points_refunded: number;
  created_at: number;
  expires_at: number;
  carrier: string | null;
  tracking_no: string | null;
  items: OrderItem[];
}
export interface OrderDetail extends Order {
  address: Address;
  refunds: Refund[];
}
export interface LedgerEntry {
  id: number;
  member_id: number;
  asset: string;
  amount: number;
  balance: number;
  event_key: string;
  order_id: string | null;
  note: string;
  created_at: number;
}
export interface Withdrawal {
  id: string;
  member_id: number;
  gross: number;
  tax: number;
  net: number;
  state: string;
  reason: string | null;
  created_at: number;
}
export interface ShipmentTrack {
  time: string;
  context: string;
}
