<script setup lang="ts">
import { ref } from "vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import OrderItems from "../../components/OrderItems.vue";
import RecordPager from "../../components/RecordPager.vue";
import { memberRecords } from "../../api/records";
import { usePagedRecords } from "../../composables/usePagedRecords";
import { navigate, requireSession } from "../../utils/router";
import { useTask } from "../../utils/useTask";
import { money, time, label } from "../../utils/format";
import type { Order } from "../../types/models";
const state = ref("");
const filters = [
  "",
  "CREATED",
  "PAID",
  "SHIPPED",
  "COMPLETED",
  "REFUNDED",
  "CLOSED",
];
const filterLabels = filters.map((value) =>
  value ? label(value) : "全部订单",
);
const {
  items: orders,
  page,
  total,
  totalPages,
  loaded,
  loading,
  failed,
  reload: readOrders,
  next,
  retry,
} = usePagedRecords<Order>(
  (page, size) => memberRecords("orders", page, size, { state: state.value }),
  true,
);
const { run } = useTask();
async function reload() {
  if (requireSession("orders")) await run(readOrders);
}
function filter(event: { detail: { value: string } }) {
  state.value = filters[Number(event.detail.value)] || "";
  void reload();
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
</script>
<template>
  <PageShell>
    <PageHeading title="我的订单" />
    <view class="box"
      ><picker
        :range="filterLabels"
        :value="filters.indexOf(state)"
        :disabled="loading"
        @change="filter"
        ><view class="row"
          ><text>订单状态</text
          ><text class="link"
            >{{ filterLabels[filters.indexOf(state)] }} ▾</text
          ></view
        ></picker
      ></view
    >
    <view
      v-for="order in orders"
      :key="order.id"
      class="box"
      @tap="navigate('order', { id: order.id })"
    >
      <view class="row"
        ><text class="caption">{{ time(order.created_at) }}</text
        ><text class="badge">{{
          order.payment_state === "CLOSING" ? "取消核对中" : label(order.state)
        }}</text></view
      >
      <OrderItems :items="order.items" />
      <view class="row"
        ><text>实付 ¥{{ money(order.cash_due) }}</text
        ><text class="link">查看详情 →</text></view
      >
    </view>
    <view v-if="loaded && !orders.length && !failed" class="empty"
      >当前条件下暂无订单。</view
    >
    <view class="box"
      ><RecordPager
        :page="page"
        :total="total"
        :total-pages="totalPages"
        :loading="loading"
        :failed="failed"
        @more="run(next)"
        @retry="run(retry)"
    /></view>
  </PageShell>
</template>
