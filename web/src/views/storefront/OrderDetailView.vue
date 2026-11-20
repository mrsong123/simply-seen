<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { orderApi } from "@/api/order";
import { useAuthStore } from "@/store/auth";
import { useConfigStore } from "@/store/config";
import { useUiStore } from "@/store/ui";
import { useAction } from "@/composables/useAction";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
import BaseModal from "@/components/common/BaseModal.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import RefundDialog from "@/components/storefront/RefundDialog.vue";
import OrderSummary from "@/components/storefront/OrderSummary.vue";
import WebPaymentDialog from "@/components/storefront/WebPaymentDialog.vue";
import { useWebPayment } from "@/composables/storefront/useWebPayment";
import { webPaymentChannel } from "@/utils/payment";
const route = useRoute(),
  auth = useAuthStore(),
  config = useConfigStore(),
  ui = useUiStore();
const order = ref<any>(null),
  logistics = ref<any[]>([]),
  modal = ref(""),
  refundVisible = ref(false);
const { busy, run } = useAction();
const loading = ref(false), loadError = ref(false);
let loadGeneration = 0, disposed = false;
const now = ref(Date.now());
const clock = setInterval(() => (now.value = Date.now()), 1000);
const expired = computed(() => order.value?.state === "CREATED" && Number(order.value.expires_at) <= now.value);
onBeforeUnmount(() => { disposed = true; loadGeneration++; clearInterval(clock); });
async function load() {
  const generation = ++loadGeneration, id = String(route.params.id);
  loading.value = true;
  loadError.value = false;
  try {
    const result = await orderApi.getOrder(id);
    if (generation === loadGeneration && id === String(route.params.id)) order.value = result;
  } catch {
    if (generation === loadGeneration) loadError.value = true;
  } finally {
    if (generation === loadGeneration) loading.value = false;
  }
}
const payment = useWebPayment({
  orderNo: () => String(route.params.id),
  prepay: (channel) => orderApi.prepay(String(route.params.id), channel),
  query: () => orderApi.queryPayment(String(route.params.id), true),
  onOrder: (value) => { order.value = value; },
  onDevelopment: () => { modal.value = "testPayment"; },
  onConfirmed: (state) => {
    ui.message(["PAID", "SHIPPED", "COMPLETED"].includes(state) ? "支付已确认，订单状态已更新" : "订单状态已更新");
    void auth.fetchMemberProfile().catch(() => {});
  },
});
watch(
  () => route.params.id,
  () => {
    const id = String(route.params.id);
    logistics.value = [];
    order.value = null;
    payment.close();
    void load().then(() => {
      if (disposed || id !== String(route.params.id)) return;
      if (order.value?.state === "CREATED" && order.value.payment_channel === "H5")
        void payment.begin("H5").then(() => payment.check());
    });
  },
  { immediate: true },
);
function openRefund() {
  refundVisible.value = true;
}
async function pay() {
  if (!config.loaded) { try { await config.load(); } catch { return; } }
  if (expired.value) { ui.message("订单已过期，请先查询结果；仍未付款时可取消后重新下单", true); return; }
  if (order.value.payment_state === "CLOSING") { ui.message("订单正在取消，请查询结果或继续取消", true); return; }
  if (order.value.payment_channel === "MINI_PROGRAM" && !config.config.devMode) {
    ui.message("这笔订单已在小程序发起支付，请通过原入口完成，或取消后重新下单", true);
    return;
  }
  const mobile = window.matchMedia("(max-width: 760px)").matches;
  await payment.begin(webPaymentChannel(config.config.webPaymentMode, mobile, order.value.payment_channel));
}
async function testPay() {
  await run(async () => {
    await orderApi.confirmDevPayment(order.value.id);
    modal.value = "";
    payment.close();
    await load();
    await auth.fetchMemberProfile();
    ui.message("开发测试付款已记录，不涉及真实资金");
  });
}
async function queryLogistics() {
  await run(async () => {
    logistics.value = await orderApi.getLogistics(order.value.id);
    if (!logistics.value.length) ui.message("暂无物流轨迹，请稍后查询");
  });
}
async function orderAction(op: string) {
  await run(async () => {
    if (op === "cancel") await orderApi.cancel(order.value.id);
    else if (op === "receive") await orderApi.receive(order.value.id);
    else await orderApi.queryPayment(order.value.id);
    modal.value = "";
    payment.close();
    await load();
    await auth.fetchMemberProfile();
    ui.message("订单状态已更新");
  });
}
</script>
<template>
  <AccountHeader title="我的订单" />
  <section v-if="loading && !order" class="section narrow empty-state" role="status">
    <Icon name="Clock" /><h3>正在加载订单</h3>
  </section>
  <section v-else-if="loadError" class="section narrow empty-state" role="alert">
    <Icon name="RefreshCw" /><h3>暂时无法加载订单</h3>
    <button class="button" @click="load">重新加载</button>
    <RouterLink to="/orders" class="text-link">返回订单列表</RouterLink>
  </section>
  <section v-else-if="order" class="section narrow">
    <RouterLink to="/orders" class="back-link"
      ><Icon name="ArrowLeft" :size="16" /> 返回订单列表</RouterLink
    >
    <div class="order-status panel">
      <Icon
        :name="order.state === 'COMPLETED' ? 'CheckCircle2' : 'Package'"
        :size="36"
      />
      <div>
        <h2>{{ label(order.state) }}</h2>
        <p>订单号 {{ order.id }}</p>
        <p v-if="order.state === 'CREATED'">
          请于 {{ date(order.expires_at) }} 前完成支付
        </p>
      </div>
    </div>
    <div class="panel" v-if="order.carrier">
      <h3>物流信息</h3>
      <p>{{ order.carrier }} · {{ order.tracking_no }}</p>
      <button
        v-if="config.config.logisticsEnabled"
        class="text-link"
        @click="queryLogistics"
      >
        查询物流轨迹
      </button>
      <div
        v-for="track in logistics"
        :key="track.time + track.context"
        class="history-row"
      >
        <small>{{ track.time }}</small
        ><span>{{ track.context }}</span>
      </div>
    </div>
    <OrderSummary :order="order">
      <p
        v-if="order.state === 'CREATED' && !config.config.devMode"
        class="notice"
      >
        {{ order.payment_channel === 'MINI_PROGRAM'
          ? '这笔订单已在小程序发起付款，请通过原入口完成。网页可查询结果，或取消后重新下单。'
          : '支持微信扫码付款；启用手机网页支付时可跳转微信完成。付款结果以渠道确认及订单状态为准。' }}
      </p>
      <p v-if="order.payment_state === 'CLOSING'" class="notice" role="status">订单正在取消，渠道确认关单后会释放库存和积分。你可以查询结果或继续取消。</p>
      <p v-else-if="expired" class="notice" role="status">订单已过期，请先查询付款结果；如仍未付款，可取消订单后重新下单。</p>
      <p v-if="payment.error.value && !payment.payment.value" class="notice" role="alert">{{ payment.error.value }}</p>
      <div class="button-row">
        <template v-if="order.state === 'CREATED'"
          ><button
            class="button"
            :disabled="busy"
            @click="modal = 'cancelOrder'"
          >
            取消订单</button
          ><button
            class="button"
            :disabled="busy"
            @click="orderAction('payment-status')"
          >
            查询支付结果</button
          ><button class="button dark" :disabled="busy || expired || order.payment_state === 'CLOSING' || payment.progress.value === 'PREPARING'" @click="pay">
            {{ expired ? '订单已过期' : '前往支付' }}
          </button></template
        ><button
          v-if="order.state === 'SHIPPED'"
          class="button dark"
          @click="modal = 'receiveOrder'"
        >
          确认收货</button
        ><button
          v-if="['PAID', 'SHIPPED', 'COMPLETED'].includes(order.state)"
          class="button"
          @click="openRefund"
        >
          申请售后
        </button>
      </div>
    </OrderSummary>
    <div v-if="order.refunds.length" class="panel">
      <h3>售后记录</h3>
      <div v-for="r in order.refunds" :key="r.id" class="history-row">
        <div>
          <strong>{{ label(r.state) }}</strong>
          <p>{{ r.reason }} · {{ date(r.created_at) }}</p>
        </div>
        <span>¥{{ money(r.amount) }}</span>
      </div>
    </div>
  </section>
  <ConfirmDialog
    :model-value="modal === 'cancelOrder' || modal === 'receiveOrder'"
    :title="modal === 'cancelOrder' ? '取消这笔订单？' : '确认已收到商品？'"
    :description="
      modal === 'cancelOrder'
        ? '渠道确认未支付后，库存及积分会解除预占。'
        : '确认后开始计算售后观察期，您的法定售后权利不受影响。'
    "
    :busy="busy"
    @update:model-value="modal = ''"
    @confirm="orderAction(modal === 'cancelOrder' ? 'cancel' : 'receive')"
  /><WebPaymentDialog
    v-if="payment.payment.value && order"
    :payment="payment.payment.value"
    :amount="order.cash_due"
    :progress="payment.progress.value"
    :error="payment.error.value"
    :checking="payment.checking.value"
    @query="payment.check(false)"
    @close="payment.close"
  /><BaseModal
    :model-value="modal === 'testPayment'"
    @update:model-value="modal = ''"
    ><h2>开发测试付款</h2>
    <p class="notice">此操作仅在开发环境可用，不会产生真实微信扣款。</p>
    <div class="balance">¥{{ money(order.cash_due) }}</div>
    <button class="button dark full" :disabled="busy" @click="testPay">
      确认测试支付
    </button></BaseModal
  ><RefundDialog
    v-if="order"
    v-model="refundVisible"
    :order="order"
    @saved="load"
  />
</template>
