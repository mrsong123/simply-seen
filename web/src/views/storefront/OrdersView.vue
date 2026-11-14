<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, computed, onMounted } from "vue";
import { orderApi } from "@/api/order";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
const orders = ref<any[]>([]),
  orderTab = ref("ALL"),
  loaded = ref(false);
const displayedOrders = computed(() =>
  orderTab.value === "ALL"
    ? orders.value
    : orders.value.filter((o) => o.state === orderTab.value),
);
onMounted(async () => {
  try {
    orders.value = await orderApi.getOrders();
  } catch {
    orders.value = [];
  } finally {
    loaded.value = true;
  }
});
</script>
<template>
  <AccountHeader title="我的订单" />
  <section class="section narrow">
    <div class="category-pills order-tabs">
      <button
        v-for="t in [
          'ALL',
          'CREATED',
          'PAID',
          'SHIPPED',
          'COMPLETED',
          'REFUNDED',
        ]"
        :key="t"
        :class="{ active: orderTab === t }"
        @click="orderTab = t"
      >
        {{ t === "ALL" ? "全部订单" : label(t) }}
      </button>
    </div>
    <RouterLink
      v-for="o in displayedOrders"
      :key="o.id"
      :to="'/orders/' + o.id"
      class="order-card"
      ><div class="order-card-head">
        <span
          >{{ date(o.created_at) }} <small>{{ o.id }}</small></span
        ><span class="badge">{{ label(o.state) }}</span>
      </div>
      <div v-for="i in o.items" :key="i.id" class="checkout-item">
        <img :src="i.image" :alt="i.name" />
        <div>
          <strong>{{ i.name }}</strong>
          <p>× {{ i.quantity }}</p>
        </div>
        <span>¥{{ money(i.cash_amount) }}</span>
      </div>
      <div class="order-card-foot">
        <span
          >实付 <strong>¥{{ money(o.cash_due) }}</strong>
          <small v-if="o.cash_refunded"
            >已退 ¥{{ money(o.cash_refunded) }}</small
          ></span
        ><span class="text-link"
          >查看详情 <Icon name="ArrowRight" :size="16"
        /></span></div
    ></RouterLink>
    <div v-if="loaded && !displayedOrders.length" class="empty-state">
      <Icon name="Package" :size="40" />
      <h3>这里还没有订单</h3>
      <RouterLink to="/catalog" class="text-link"
        >去发现好物 <Icon name="ArrowRight"
      /></RouterLink>
    </div>
  </section>
</template>
