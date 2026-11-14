<script setup lang="ts">
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import LoginRequired from "../../components/LoginRequired.vue";
import QuantityStepper from "../../components/QuantityStepper.vue";
import { useCartStore } from "../../store/cart";
import { useSessionStore } from "../../store/session";
import { useTask } from "../../utils/useTask";
import { navigate } from "../../utils/router";
import { money, image } from "../../utils/format";
const cart = useCartStore(),
  session = useSessionStore();
const { busy, run } = useTask();
async function reload() {
  await run(async () => {
    await session.refresh();
    await cart.refresh();
  });
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
function quantity(id: number, value: number) {
  void run(() => cart.setQuantity(id, value));
}
</script>
<template>
  <PageShell tab="cart"
    ><PageHeading title="我的购物袋" subtitle="YOUR EVERYDAY" /><LoginRequired
      v-if="!session.profile"
      message="登录后查看购物袋"
    /><template v-else
      ><view v-for="item in cart.items" :key="item.sku_id" class="cart-item"
        ><image
          :src="image(item.image)"
          mode="aspectFit"
          :style="{ background: item.color }"
          @tap="navigate('detail', { id: item.product_id })"
        /><view class="cart-info"
          ><view>{{ item.name }}</view
          ><text class="sub">{{ item.sku_name }}</text
          ><view class="row"
            ><text>¥{{ money(item.price) }}</text
            ><QuantityStepper
              :model-value="item.quantity"
              :disabled="busy"
              @update:model-value="
                quantity(item.sku_id, $event)
              " /></view></view
        ><text @tap="quantity(item.sku_id, 0)">×</text></view
      ><view v-if="cart.items.length" class="box"
        ><view class="row"
          ><text>商品合计</text
          ><text class="big">¥{{ money(cart.total) }}</text></view
        ><text class="caption">运费与积分抵扣将在结算时计算</text
        ><button class="primary" @tap="navigate('checkout')">
          前往结算 →
        </button></view
      ><view v-else class="empty"
        ><text>购物袋还很轻盈</text
        ><button class="secondary" @tap="navigate('home')">
          去发现好物
        </button></view
      ></template
    ></PageShell
  >
</template>
