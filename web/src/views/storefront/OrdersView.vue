<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, computed, onMounted } from "vue";
import { orderApi } from "@/api/order";
import { useMemberRecords } from "@/composables/useMemberRecords";
import PageNavigation from "@/components/common/PageNavigation.vue";
import ResourceState from "@/components/common/ResourceState.vue";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
const records = useMemberRecords<any>("orders");
const { items: displayedOrders, loading, error, loaded, load } = records;
const orderTab = computed(() => records.state.value || "ALL");
function selectTab(value: string) { records.setState(value === "ALL" ? "" : value); }
</script>
<template>
  <AccountHeader title="我的订单" />
  <section class="section narrow">
    <label class="input-label">查找订单<input :value="records.search.value" aria-label="搜索我的订单" @input="records.setSearch(($event.target as HTMLInputElement).value)" /></label>
    <ResourceState :loading="loading" :error="error" @retry="load" />
    <div v-if="!error" class="category-pills order-tabs">
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
        @click="selectTab(t)"
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
    <PageNavigation :records="records" label="订单" />
    <div v-if="loaded && !error && !loading && !displayedOrders.length" class="empty-state">
      <Icon name="Package" :size="40" />
      <h3>这里还没有订单</h3>
      <RouterLink to="/catalog" class="text-link"
        >去发现好物 <Icon name="ArrowRight"
      /></RouterLink>
    </div>
  </section>
</template>
