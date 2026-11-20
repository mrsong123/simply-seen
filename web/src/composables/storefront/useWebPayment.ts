import { getCurrentInstance, onBeforeUnmount, ref, shallowRef } from "vue";
import type { OrderDetail } from "@/types/models";
import type { PaymentChannel, PaymentProgress, PaymentResult, WebPayment } from "@/types/payment";
import { validateWebPayment } from "@/utils/payment";

interface Dependencies {
  orderNo: () => string;
  prepay: (channel: PaymentChannel) => Promise<PaymentResult>;
  query: () => Promise<OrderDetail>;
  onOrder: (order: OrderDetail) => void;
  onDevelopment: () => void;
  onConfirmed: (state: string) => void;
  now?: () => number;
  visible?: () => boolean;
  maxPolls?: number;
}

export function useWebPayment(dependencies: Dependencies) {
  const payment = shallowRef<WebPayment | null>(null);
  const progress = ref<PaymentProgress>("IDLE");
  const error = ref("");
  const checking = ref(false);
  let timer: ReturnType<typeof setTimeout> | undefined;
  let generation = 0, polls = 0;
  const now = dependencies.now ?? Date.now;
  const visible = dependencies.visible ?? (() => document.visibilityState !== "hidden");
  function stop() {
    if (timer) clearTimeout(timer);
    timer = undefined;
  }
  function close() {
    generation++;
    stop();
    payment.value = null;
    progress.value = "IDLE";
    error.value = "";
  }
  function schedule() {
    stop();
    if (progress.value !== "WAITING") return;
    timer = setTimeout(() => void check(true), 5000);
  }
  async function check(automatic = false) {
    if (!payment.value) return;
    if (checking.value) { if (automatic) schedule(); return; }
    if (automatic && !visible()) { schedule(); return; }
    if (automatic && polls >= (dependencies.maxPolls ?? 60)) {
      progress.value = "PAUSED";
      return;
    }
    if (automatic) polls++;
    const epoch = generation, orderNo = dependencies.orderNo();
    checking.value = true;
    try {
      const order = await dependencies.query();
      if (epoch !== generation || dependencies.orderNo() !== orderNo) return;
      if (order.id !== orderNo) throw new Error("查询结果与当前订单不一致，请重新加载订单");
      dependencies.onOrder(order);
      error.value = "";
      if (order.state !== "CREATED") {
        close();
        dependencies.onConfirmed(order.state);
        return;
      }
      if (now() >= payment.value!.expiresAt) {
        progress.value = "EXPIRED";
        stop();
      }
    } catch (failure) {
      if (epoch !== generation) return;
      error.value = failure instanceof Error ? failure.message : "暂时无法查询支付结果，请稍后重试";
      if (payment.value && now() >= payment.value.expiresAt) progress.value = "EXPIRED";
    } finally {
      checking.value = false;
      if (epoch === generation) schedule();
    }
  }
  async function begin(channel: PaymentChannel) {
    if (progress.value === "PREPARING") return;
    const epoch = ++generation, orderNo = dependencies.orderNo();
    stop();
    payment.value = null;
    progress.value = "PREPARING";
    error.value = "";
    try {
      const result = await dependencies.prepay(channel);
      if (epoch !== generation || orderNo !== dependencies.orderNo()) return;
      const payload = validateWebPayment(result, orderNo);
      if (!payload) {
        progress.value = "IDLE";
        dependencies.onDevelopment();
        return;
      }
      payment.value = payload;
      progress.value = "WAITING";
      polls = 0;
      schedule();
    } catch (failure) {
      if (epoch !== generation) return;
      progress.value = "IDLE";
      error.value = failure instanceof Error ? failure.message : "支付暂不可用，请稍后重试";
    }
  }
  if (getCurrentInstance()) onBeforeUnmount(close);
  return { payment, progress, error, checking, begin, check, close };
}
