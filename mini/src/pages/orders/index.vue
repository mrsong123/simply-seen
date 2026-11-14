<script setup lang="ts">
import { ref } from "vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import OrderItems from "../../components/OrderItems.vue";
import { tradeApi } from "../../api/trade";
import { navigate, requireSession } from "../../utils/router";
import { useTask } from "../../utils/useTask";
import { money, time, label } from "../../utils/format";
import type { Order } from "../../types/models";
const orders = ref<Order[]>([]);
const { run } = useTask();
async function reload() {
  if (!requireSession("orders")) return;
  await run(async () => (orders.value = await tradeApi.orders()));
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
</script>
<template>
  <PageShell
    ><PageHeading title="我的订单" /><view
      v-for="order in orders"
      :key="order.id"
      class="box"
      @tap="navigate('order', { id: order.id })"
      ><view class="row"
        ><text class="caption">{{ time(order.created_at) }}</text
        ><text class="badge">{{ label(order.state) }}</text></view
      ><OrderItems :items="order.items" /><view class="row"
        ><text>实付 ¥{{ money(order.cash_due) }}</text
        ><text class="link">查看详情 →</text></view
      ></view
    ><view v-if="!orders.length" class="empty"
      >还没有订单，去挑选一件好物吧。</view
    ></PageShell
  >
</template>
