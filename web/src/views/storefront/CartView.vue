<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { onMounted } from "vue";
import { storeToRefs } from "pinia";
import { useCartStore } from "@/store/cart";
import { useAction } from "@/composables/useAction";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
const store = useCartStore();
const { items: cart, total: cartTotal } = storeToRefs(store);
const { busy, run } = useAction();
onMounted(() => store.load().catch(() => {}));
async function changeCart(item: any, delta: number) {
  await run(() => store.change(item.sku_id, item.quantity + delta));
}
</script>
<template>
  <AccountHeader title="我的购物袋" />
  <section class="section bag-layout">
    <div>
      <div v-if="!cart.length" class="empty-state">
        <Icon name="ShoppingBag" :size="44" />
        <h3>购物袋还很轻盈</h3>
        <p>去挑选一件能陪伴日常的好物吧。</p>
        <RouterLink to="/catalog" class="button dark"
          >发现好物 <Icon name="ArrowRight" :size="17"
        /></RouterLink>
      </div>
      <article v-for="item in cart" :key="item.sku_id" class="cart-row">
        <RouterLink
          :to="'/product/' + item.product_id"
          class="cart-image"
          :style="{ background: item.color }"
          ><img :src="item.image" :alt="item.name"
        /></RouterLink>
        <div class="cart-desc">
          <RouterLink :to="'/product/' + item.product_id"
            ><h3>{{ item.name }}</h3></RouterLink
          >
          <p>{{ item.sku_name }}</p>
          <strong>¥{{ money(item.price) }}</strong>
          <div class="stepper">
            <button
              aria-label="减少数量"
              :disabled="busy"
              @click="changeCart(item, -1)"
            >
              <Icon name="Minus" :size="14" /></button
            ><span>{{ item.quantity }}</span
            ><button
              aria-label="增加数量"
              :disabled="busy || item.quantity >= 99"
              @click="changeCart(item, 1)"
            >
              <Icon name="Plus" :size="14" />
            </button>
          </div>
        </div>
        <button
          class="icon-button"
          aria-label="移除商品"
          @click="changeCart(item, -item.quantity)"
        >
          <Icon name="X" :size="18" />
        </button>
      </article>
    </div>
    <aside v-if="cart.length" class="summary-card">
      <h3>这份美好</h3>
      <div class="summary-line">
        <span>商品金额</span><strong>¥{{ money(cartTotal) }}</strong>
      </div>
      <div class="summary-line muted">
        <span>运费与积分</span><span>结算时计算</span>
      </div>
      <div class="divider"></div>
      <div class="summary-line">
        <span>商品合计</span
        ><strong class="large">¥{{ money(cartTotal) }}</strong>
      </div>
      <RouterLink to="/checkout" class="button dark full"
        >前往结算 <Icon name="ArrowRight" :size="17"
      /></RouterLink>
      <p class="caption">服务端实时报价 · 安全结算</p>
    </aside>
  </section>
</template>
