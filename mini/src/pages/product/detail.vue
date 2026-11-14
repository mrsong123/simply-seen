<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad, onShareAppMessage } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import QuantityStepper from "../../components/QuantityStepper.vue";
import { catalogApi } from "../../api/catalog";
import { useCartStore } from "../../store/cart";
import { useSessionStore } from "../../store/session";
import { useTask } from "../../utils/useTask";
import { navigate, requireSession } from "../../utils/router";
import { image, money } from "../../utils/format";
import { authStorage } from "../../utils/storage";
import type { Product } from "../../types/models";
const product = ref<Product | null>(null),
  sku = ref(0),
  quantity = ref(1);
const selected = computed(() =>
  product.value?.skus.find((item) => item.id === sku.value),
);
const cart = useCartStore(),
  session = useSessionStore();
const { busy, run } = useTask();
onLoad((options) =>
  run(async () => {
    authStorage.saveInvite(options?.invite || "");
    void session.refresh().catch(() => undefined);
    const id = Number(options?.id);
    if (!id) throw new Error("商品链接无效");
    product.value = await catalogApi.detail(id);
    sku.value =
      product.value.skus.find((item) => item.stock > 0)?.id ||
      product.value.skus[0]?.id ||
      0;
  }),
);
async function add(buy = false) {
  if (!product.value || !requireSession("detail", { id: product.value.id }))
    return;
  await run(async () => {
    await cart.add(sku.value, quantity.value);
    if (buy) navigate("checkout");
    else uni.showToast({ title: "已加入购物袋", icon: "success" });
  });
}
onShareAppMessage(() => ({
  title: product.value?.name || "素见好物",
  path:
    "/pages/product/detail?id=" +
    product.value?.id +
    "&invite=" +
    (session.profile?.promoter ? session.profile.invite_code : ""),
  imageUrl: image(product.value?.image),
}));
</script>
<template>
  <PageShell
    ><template v-if="product"
      ><view class="detail-art" :style="{ background: product.color }"
        ><image :src="image(product.image)" mode="aspectFit" /></view
      ><view class="box"
        ><view class="eyebrow">ESSENTIALS FOR EVERYDAY</view
        ><view class="title">{{ product.name }}</view
        ><text class="sub">{{ product.subtitle }}</text
        ><view class="detail-price">¥{{ money(selected?.price) }}</view
        ><view class="field-title">规格选择</view
        ><view class="options"
          ><text
            v-for="item in product.skus"
            :key="item.id"
            :class="{ active: sku === item.id }"
            @tap="sku = item.id"
            >{{ item.name }}<text v-if="!item.stock">（售罄）</text></text
          ></view
        ><view class="row"
          ><text>数量 · 库存{{ selected?.stock }}</text
          ><QuantityStepper
            v-model="quantity"
            :min="1"
            :max="Math.min(99, selected?.stock || 1)"
            :disabled="busy" /></view
        ><button
          class="primary"
          :disabled="busy || !selected?.stock"
          @tap="add()"
        >
          加入购物袋</button
        ><button
          class="secondary"
          :disabled="busy || !selected?.stock"
          @tap="add(true)"
        >
          立即选购</button
        ><button class="text-button" open-type="share">分享好物</button
        ><view class="divider"></view
        ><view class="field-title">关于这件好物</view
        ><text class="description">{{ product.description }}</text></view
      ></template
    ><view v-else class="empty">正在读取商品，请稍候</view></PageShell
  >
</template>
