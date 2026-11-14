<script setup lang="ts">
import type { OrderItem } from "../types/models";
import { image, money } from "../utils/format";
defineProps<{ items: OrderItem[]; showPrice?: boolean }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
</script>
<template>
  <view
    ><view v-for="item in items" :key="item.id" class="small-item"
      ><image :src="image(item.image)" mode="aspectFit" /><view
        ><text>{{ item.name }}</text
        ><text class="sub"
          >数量{{ item.quantity
          }}<text v-if="item.refunded_quantity">
            / 已退{{ item.refunded_quantity }}</text
          ></text
        ></view
      ><text v-if="showPrice">¥{{ money(item.cash_amount) }}</text></view
    ></view
  >
</template>
