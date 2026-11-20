<script setup lang="ts">
import { useViewResource } from "@/composables/useViewResource";
import ResourceState from "@/components/common/ResourceState.vue";
import { adminApi } from "@/api/admin";
import AdminModal from "./AdminModal.vue";
import { money, label } from "@/utils/format";
const props = defineProps<{ orderNo: string }>();
defineEmits<{ close: [] }>();
const { data: order, loading, error, load } = useViewResource<any>(() => adminApi.getOrder(props.orderNo), null);
</script>
<template>
  <AdminModal title="订单详情" wide @close="$emit('close')"
    ><ResourceState :loading="loading" :error="error" @retry="load" /><template v-if="order && !error"
      ><p class="mono">
        {{ order.id }} <span class="badge">{{ label(order.state) }}</span>
      </p>
      <div class="panel">
        <h3>收货信息</h3>
        <p>{{ order.address.name }} · {{ order.address.phone }}</p>
        <p>{{ order.address.region }} {{ order.address.detail }}</p>
        <p v-if="order.carrier">
          {{ order.carrier }} · {{ order.tracking_no }}
        </p>
      </div>
      <div v-for="item in order.items" :key="item.id" class="checkout-item">
        <img :src="item.image" :alt="item.name" />
        <div>
          <strong>{{ item.name }}</strong>
          <p>数量 {{ item.quantity }} · 已退 {{ item.refunded_quantity }}</p>
        </div>
        <span>¥{{ money(item.cash_amount) }}</span>
      </div>
      <div class="history-row">
        <span>现金实付 / 已退</span
        ><strong
          >¥{{ money(order.cash_due) }} / ¥{{
            money(order.cash_refunded)
          }}</strong
        >
      </div>
      <div class="history-row">
        <span>积分使用 / 已退</span
        ><strong>{{ order.points_used }} / {{ order.points_refunded }}</strong>
      </div>
      <div v-for="refund in order.refunds" :key="refund.id" class="history-row">
        <span>{{ refund.id }} · {{ label(refund.state) }}</span
        ><span>¥{{ money(refund.amount) }}</span>
      </div></template
    >
</AdminModal
  >
</template>
