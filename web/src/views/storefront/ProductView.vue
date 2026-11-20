<script setup lang="ts">
import ResourceState from "@/components/common/ResourceState.vue";
import { useViewResource } from "@/composables/useViewResource";
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, computed, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { catalogApi } from "@/api/catalog";
import { useAuthStore } from "@/store/auth";
import { useCartStore } from "@/store/cart";
import { useConfigStore } from "@/store/config";
import { useUiStore } from "@/store/ui";
import { useAction } from "@/composables/useAction";
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore(),
  cart = useCartStore(),
  config = useConfigStore(),
  ui = useUiStore();
const { data: product, loading, error, load: loadProduct } = useViewResource<any>(
  () => catalogApi.getProduct(String(route.params.id)), null, false,
);
const sku = ref(0),
  qty = ref(1);
const selectedSku = computed(() =>
  product.value?.skus.find((s: any) => s.id === sku.value),
);
const rules = computed(() => config.rules);
const { busy, run } = useAction();
async function load() {
  product.value = null;
  if (await loadProduct()) {
    sku.value = product.value.skus.find((item: any) => item.stock > 0)?.id || product.value.skus[0]?.id || 0;
    qty.value = 1;
  }
}
watch(() => route.params.id, load, { immediate: true });
async function addCart(buy = false) {
  if (!auth.member) {
    ui.openLogin();
    return;
  }
  await run(async () => {
    await cart.add(sku.value, qty.value);
    ui.message("已加入购物袋");
    if (buy) await router.push("/checkout");
  });
}
</script>
<template>
  <ResourceState :loading="loading" :error="error" @retry="load" />
  <template v-if="product && !error"
    ><section class="detail-layout">
      <div class="detail-art" :style="{ background: product.color }">
        <img :src="product.image" :alt="product.name" />
      </div>
      <div class="detail-copy">
        <RouterLink to="/catalog" class="back-link"
          ><Icon name="ArrowLeft" :size="16" /> 返回全部好物</RouterLink
        >
        <div class="eyebrow">ESSENTIALS FOR EVERYDAY</div>
        <h1>{{ product.name }}</h1>
        <p class="subtitle">{{ product.subtitle }}</p>
        <div class="detail-price">
          ¥{{ money(selectedSku?.price)
          }}<span>最高可抵 {{ product.points_percent }}% 积分</span>
        </div>
        <div class="divider"></div>
        <label class="field-label">选择规格</label>
        <div class="sku-options">
          <button
            v-for="s in product.skus"
            :key="s.id"
            :class="{ active: sku === s.id }"
            :disabled="s.stock < 1"
            @click="sku = s.id"
          >
            {{ s.name }}
          </button>
        </div>
        <div class="quantity-line">
          <span
            >数量 <small>库存 {{ selectedSku?.stock }} 件</small></span
          >
          <div class="stepper">
            <button aria-label="减少数量" :disabled="qty <= 1" @click="qty--">
              <Icon name="Minus" :size="16" /></button
            ><span>{{ qty }}</span
            ><button
              aria-label="增加数量"
              :disabled="qty >= Math.min(99, selectedSku?.stock || 0)"
              @click="qty++"
            >
              <Icon name="Plus" :size="16" />
            </button>
          </div>
        </div>
        <button
          class="button dark full"
          :disabled="busy || !selectedSku?.stock"
          @click="addCart()"
        >
          加入购物袋 <Icon name="ShoppingBag" :size="18" /></button
        ><button class="button full" :disabled="busy || !selectedSku?.stock" @click="addCart(true)">立即选购</button>
        <div class="detail-promises">
          <span><Icon name="Shield" :size="15" /> 售后保障</span
          ><span
            ><Icon name="Truck" :size="15" /> 满 ¥{{
              money(rules.freeShippingFen)
            }}
            包邮</span
          >
        </div>
        <details open>
          <summary>关于这件好物</summary>
          <p class="preline">{{ product.description }}</p>
        </details>
        <details>
          <summary>配送与售后</summary>
          <p>
            运费以结算页实时报价为准。物流发货后可在订单详情查询运单。符合条件的商品支持七日无理由退货，质量问题请通过订单申请售后。
          </p>
        </details>
      </div>
    </section></template
  >
</template>
