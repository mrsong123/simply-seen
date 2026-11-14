<script setup lang="ts">
import type { Product } from "../types/models";
import { image, money } from "../utils/format";
import { navigate } from "../utils/router";
defineProps<{ product: Product }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
</script>
<template>
  <view class="product" @tap="navigate('detail', { id: product.id })"
    ><view class="art" :style="{ background: product.color }"
      ><image :src="image(product.image)" mode="aspectFit" /><text
        v-if="product.featured"
        class="tag"
        >素见精选</text
      ></view
    ><view class="product-name">{{ product.name }}</view
    ><text class="sub">{{ product.subtitle }}</text
    ><view class="price"
      >¥{{ money(product.price)
      }}<text>{{ product.points_percent }}% 积分抵扣</text></view
    ></view
  >
</template>
