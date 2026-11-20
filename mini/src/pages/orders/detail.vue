<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad, onPullDownRefresh, onShow } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import OrderItems from "../../components/OrderItems.vue";
import RefundDialog from "../../components/RefundDialog.vue";
import PageLoadState from "../../components/PageLoadState.vue";
import { tradeApi } from "../../api/trade";
import { useSessionStore } from "../../store/session";
import { useAppStore } from "../../store/app";
import { useTask, confirm } from "../../utils/useTask";
import {
  payOrder,
  isBrowserPayment,
  browserOrderLink,
} from "../../utils/orderPayment";
import { navigate, requireSession } from "../../utils/router";
import { money, label, time } from "../../utils/format";
import type { Order, Track } from "../../types/models";
const order = ref<Order | null>(null),
  tracks = ref<Track[]>([]),
  refundOpen = ref(false),
  loadError = ref(false);
const app = useAppStore(),
  session = useSessionStore();
const { busy, run } = useTask();
const browserPayment = computed(() =>
  isBrowserPayment(order.value?.payment_channel),
);
const closing = computed(() => order.value?.payment_state === "CLOSING");
let orderId = "";
async function refresh() {
  order.value = await tradeApi.order(orderId);
  await session.refresh();
}
async function reload() {
  if (!orderId || !requireSession("order", { id: orderId })) return;
  await run(async () => {
    loadError.value = false;
    try {
      await app.load(true);
      await refresh();
    } catch (error) {
      loadError.value = true;
      throw error;
    }
  });
}
onLoad((options) => {
  orderId = options?.id || "";
  if (!orderId) {
    loadError.value = true;
    uni.showToast({ title: "订单链接无效", icon: "none" });
    return;
  }
});
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
async function pay() {
  await run(async () => {
    await payOrder(
      orderId,
      !!app.config?.devMode,
      order.value?.payment_channel,
      order.value?.payment_state,
    );
    await refresh();
  });
}
async function copyPaymentLink() {
  await run(async () => {
    const data = browserOrderLink(app.config?.shareBase || "", orderId);
    await new Promise<void>((resolve, reject) =>
      uni.setClipboardData({
        data,
        success: () => resolve(),
        fail: reject,
      }),
    );
  });
}
async function close() {
  if (
    !(await confirm("渠道确认未支付后，释放库存和积分预占。", "确认取消订单？"))
  )
    return;
  await run(async () => {
    await tradeApi.cancel(orderId);
    await refresh();
  });
}
async function receive() {
  if (
    !(await confirm(
      "确认后开始售后观察期，法定售后权利不受影响。",
      "确认收到商品？",
    ))
  )
    return;
  await run(async () => {
    await tradeApi.receive(orderId);
    await refresh();
  });
}
async function status() {
  await run(async () => {
    order.value = await tradeApi.status(orderId);
    await session.refresh();
  });
}
async function logistics() {
  await run(async () => {
    tracks.value = await tradeApi.logistics(orderId);
    if (!tracks.value.length)
      uni.showToast({ title: "暂无物流轨迹，请稍后查询", icon: "none" });
  });
}
</script>
<template>
  <PageShell
    ><PageLoadState
      :loading="busy && !order"
      :failed="loadError"
      :retryable="!!orderId"
      @retry="reload"
    />
    <view v-if="!order && loadError" class="box">
      <button class="secondary" @tap="navigate('orders')">返回全部订单</button>
    </view>
    <template v-if="order"
      ><PageHeading :title="closing ? '取消核对中' : label(order.state)"
        ><text class="caption">{{ order.id }}</text></PageHeading
      ><view class="box"
        ><text>{{ order.address.name }} · {{ order.address.phone }}</text
        ><view class="sub"
          >{{ order.address.region }} {{ order.address.detail }}</view
        ><view v-if="order.carrier" class="row"
          ><text>物流 {{ order.carrier }}</text
          ><text selectable>{{ order.tracking_no }}</text></view
        ><text
          v-if="order.carrier && app.config?.logisticsEnabled"
          class="link"
          @tap="logistics"
          >查询物流轨迹</text
        ><view
          v-for="track in tracks"
          :key="track.time + track.context"
          class="history"
          ><text class="caption">{{ track.time }}</text
          ><text class="sub">{{ track.context }}</text></view
        ></view
      ><view class="box"
        ><OrderItems :items="order.items" show-price /><view class="row"
          ><text>运费</text><text>¥{{ money(order.freight) }}</text></view
        ><view class="row"
          ><text>积分抵扣</text><text>{{ order.points_used }}积分</text></view
        ><view class="row"
          ><text>实付现金</text
          ><text class="big">¥{{ money(order.cash_due) }}</text></view
        ><view v-if="order.cash_refunded" class="row"
          ><text>已退现金</text
          ><text>¥{{ money(order.cash_refunded) }}</text></view
        ><template v-if="order.state === 'CREATED'"
          ><text class="caption"
            >请于{{ time(order.expires_at) }}前完成支付</text
          ><text v-if="closing" class="sub"
            >正在向支付渠道核对关单，确认未付款后才会释放库存与积分。可查询结果或稍后重试取消，请勿再次付款。</text
          ><template v-if="browserPayment">
            <text class="sub"
              >此订单已选择网页{{
                order.payment_channel === "NATIVE" ? "扫码" : "H5"
              }}支付。可打开原网页订单链接继续付款，也可取消后在小程序重新下单。</text
            >
            <button
              class="primary"
              :disabled="busy || closing"
              @tap="copyPaymentLink"
            >
              复制网页订单链接
            </button> </template
          ><button
            v-else
            class="primary"
            :disabled="busy || closing"
            @tap="pay"
          >
            前往支付</button
          ><button class="secondary" :disabled="busy" @tap="status">
            查询支付结果</button
          ><button class="text-button" :disabled="busy" @tap="close">
            {{ closing ? "继续核对取消" : "取消订单" }}
          </button></template
        ><button
          v-if="order.state === 'SHIPPED'"
          class="primary"
          :disabled="busy"
          @tap="receive"
        >
          确认收货</button
        ><button
          v-if="['PAID', 'SHIPPED', 'COMPLETED'].includes(order.state)"
          class="secondary"
          :disabled="busy"
          @tap="refundOpen = true"
        >
          申请售后
        </button></view
      ><view v-if="order.refunds.length" class="box"
        ><view class="field-title">售后记录</view
        ><view v-for="refund in order.refunds" :key="refund.id" class="history"
          ><view class="row"
            ><text>{{ label(refund.state) }}</text
            ><text>¥{{ money(refund.amount) }}</text></view
          ><text class="sub">{{ refund.reason }}</text></view
        ></view
      ><button class="text-button" @tap="navigate('orders')">
        返回全部订单</button
      ><RefundDialog
        :open="refundOpen"
        :order="order"
        @close="refundOpen = false"
        @submitted="reload" /></template
  ></PageShell>
</template>
