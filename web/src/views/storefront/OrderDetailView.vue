<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, watch } from "vue";
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
const route = useRoute(),
  auth = useAuthStore(),
  config = useConfigStore(),
  ui = useUiStore();
const order = ref<any>(null),
  logistics = ref<any[]>([]),
  modal = ref(""),
  refundVisible = ref(false);
const { busy, run } = useAction();
async function load() {
  order.value = await orderApi.getOrder(String(route.params.id));
}
watch(
  () => route.params.id,
  () => {
    logistics.value = [];
    load().catch(() => {});
  },
  { immediate: true },
);
function openRefund() {
  refundVisible.value = true;
}
async function pay() {
  await run(async () => {
    if (!config.config.devMode) {
      await auth.fetchMemberProfile();
      if (!auth.memberProfile?.wechatBound) {
        ui.message("请在小程序使用相同手机号登录并绑定微信后支付", true);
        return;
      }
    }
    const result = await orderApi.prepay(order.value.id);
    if (result.mode === "DEVELOPMENT") modal.value = "testPayment";
    else ui.message("请在微信小程序内完成支付；本页面可查询支付结果");
  });
}
async function testPay() {
  await run(async () => {
    await orderApi.confirmDevPayment(order.value.id);
    modal.value = "";
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
    await load();
    await auth.fetchMemberProfile();
    ui.message("订单状态已更新");
  });
}
</script>
<template>
  <AccountHeader title="我的订单" />
  <section v-if="order" class="section narrow">
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
    <div class="panel">
      <h3>收货信息</h3>
      <p>{{ order.address.name }} {{ order.address.phone }}</p>
      <p>{{ order.address.region }} {{ order.address.detail }}</p>
    </div>
    <div class="panel">
      <div v-for="i in order.items" :key="i.id" class="checkout-item">
        <img :src="i.image" :alt="i.name" />
        <div>
          <strong>{{ i.name }}</strong>
          <p>
            数量 {{ i.quantity }}
            <span v-if="i.refunded_quantity">
              / 已退 {{ i.refunded_quantity }}</span
            >
          </p>
        </div>
        <span>¥{{ money(i.cash_amount) }} + {{ i.points_amount }}积分</span>
      </div>
      <div class="divider"></div>
      <div class="summary-line">
        <span>运费</span><span>¥{{ money(order.freight) }}</span>
      </div>
      <div class="summary-line">
        <span>实付现金</span><strong>¥{{ money(order.cash_due) }}</strong>
      </div>
      <div v-if="order.cash_refunded" class="summary-line">
        <span>已退款</span><strong>¥{{ money(order.cash_refunded) }}</strong>
      </div>
      <p
        v-if="order.state === 'CREATED' && !config.config.devMode"
        class="notice"
      >
        微信支付请在小程序完成。手机号会员需先在小程序使用同一手机号登录并绑定微信，再查看这笔订单。
        <RouterLink to="/account/security" class="text-link"
          >查看账户绑定</RouterLink
        >
      </p>
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
          ><button class="button dark" :disabled="busy" @click="pay">
            前往支付
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
    </div>
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
