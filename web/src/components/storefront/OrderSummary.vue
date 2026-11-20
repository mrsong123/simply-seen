<script setup lang="ts">
import type { OrderDetail } from "@/types/models";
import { money } from "@/utils/format";
defineProps<{ order: OrderDetail }>();
</script>
<template>
  <div class="panel">
    <h3>收货信息</h3>
    <p>{{ order.address.name }} {{ order.address.phone }}</p>
    <p>{{ order.address.region }} {{ order.address.detail }}</p>
  </div>
  <div class="panel">
    <div v-for="item in order.items" :key="item.id" class="checkout-item">
      <img :src="item.image" :alt="item.name" />
      <div><strong>{{ item.name }}</strong><p>数量 {{ item.quantity }} <span v-if="item.refunded_quantity">/ 已退 {{ item.refunded_quantity }}</span></p></div>
      <span>¥{{ money(item.cash_amount) }} + {{ item.points_amount }}积分</span>
    </div>
    <div class="divider"></div>
    <div class="summary-line"><span>运费</span><span>¥{{ money(order.freight) }}</span></div>
    <div class="summary-line"><span>实付现金</span><strong>¥{{ money(order.cash_due) }}</strong></div>
    <div v-if="order.cash_refunded" class="summary-line"><span>已退款</span><strong>¥{{ money(order.cash_refunded) }}</strong></div>
    <slot />
  </div>
</template>
