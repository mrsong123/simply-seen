<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money } from "@/utils/format";
defineProps<{ product: any; tag?: string }>();
</script>
<template>
  <RouterLink :to="'/product/' + product.id" class="product-card"
    ><div class="product-art" :style="{ background: product.color }">
      <span v-if="tag || product.featured" class="product-tag">{{
        tag || "素见精选"
      }}</span
      ><img :src="product.image" :alt="product.name" loading="lazy" /><span
        class="quick-add"
        ><Icon name="Plus"
      /></span>
    </div>
    <div class="product-info">
      <div>
        <h3>{{ product.name }}</h3>
        <p>{{ product.subtitle }}</p>
      </div>
      <strong>¥{{ money(product.price) }}</strong>
    </div>
    <div class="product-meta">
      最高 {{ product.points_percent }}% 积分抵扣
    </div></RouterLink
  >
</template>
