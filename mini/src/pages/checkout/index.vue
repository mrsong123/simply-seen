<script setup lang="ts">
import { ref, watch } from "vue";
import { onShow } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import { useCartStore } from "../../store/cart";
import { useSessionStore } from "../../store/session";
import { useTask } from "../../utils/useTask";
import { navigate, requireSession } from "../../utils/router";
import { addressApi } from "../../api/address";
import { tradeApi } from "../../api/trade";
import { money, image } from "../../utils/format";
import { requestKey } from "../../utils/idempotency";
import { openCreatedOrder } from "../../utils/orderCompletion";
import type { Address, Quote } from "../../types/models";
const cart = useCartStore(),
  session = useSessionStore();
const { busy, run } = useTask();
const addresses = ref<Address[]>([]),
  addressId = ref(0),
  points = ref(0),
  remark = ref(""),
  accepted = ref(false),
  quote = ref<Quote | null>(null);
let idempotency = requestKey(),
  sequence = 0;
async function updateQuote() {
  const attempt = ++sequence;
  quote.value = null;
  if (!addressId.value || !cart.items.length) return;
  const result = await tradeApi.quote({
    addressId: addressId.value,
    pointsRequested: Number(points.value),
    items: cart.items.map((item) => ({
      skuId: item.sku_id,
      quantity: item.quantity,
    })),
  });
  if (attempt === sequence) quote.value = result;
}
async function reload() {
  if (!requireSession("checkout")) return;
  await run(async () => {
    await Promise.all([cart.refresh(), session.refresh()]);
    addresses.value = await addressApi.list();
    if (!addresses.value.some((item) => item.id === addressId.value))
      addressId.value =
        addresses.value.find((item) => item.is_default)?.id ||
        addresses.value[0]?.id ||
        0;
    await updateQuote();
  });
}
function choose(id: number) {
  addressId.value = id;
  void run(updateQuote);
}
watch(points, () => {
  sequence++;
  quote.value = null;
});
async function submit() {
  await run(async () => {
    if (!accepted.value || !quote.value)
      throw new Error("请确认报价并同意交易规则");
    const order = await tradeApi.create(
      {
        quoteId: quote.value.quoteId,
        addressId: addressId.value,
        buyerRemark: remark.value,
      },
      idempotency,
    );
    idempotency = requestKey();
    openCreatedOrder(order.id, cart.refresh);
  });
}
onShow(reload);
</script>
<template>
  <PageShell
    ><PageHeading title="确认这份美好" /><view class="box"
      ><view class="row"
        ><text class="field-title">收货地址</text
        ><text class="link" @tap="navigate('addressEdit')">新增地址</text></view
      ><view
        v-for="address in addresses"
        :key="address.id"
        class="address-option"
        :class="{ selected: addressId === address.id }"
        @tap="choose(address.id || 0)"
        ><view
          >{{ address.name }} · {{ address.phone }}
          <text v-if="addressId === address.id">✓</text></view
        ><text class="sub"
          >{{ address.region }} {{ address.detail }}</text
        ></view
      ><text v-if="!addresses.length" class="sub">请先添加收货地址</text></view
    ><view class="box"
      ><view v-for="item in cart.items" :key="item.sku_id" class="small-item"
        ><image :src="image(item.image)" mode="aspectFit" /><view
          ><text>{{ item.name }}</text
          ><text class="sub"
            >{{ item.sku_name }} × {{ item.quantity }}</text
          ></view
        ><text>¥{{ money(item.price * item.quantity) }}</text></view
      ><view class="field"
        ><text>使用积分（可用{{ session.profile?.points }}）</text
        ><input
          v-model.number="points"
          type="number"
          @blur="run(updateQuote)" /></view
      ><view class="field"
        ><text>订单留言</text
        ><input
          v-model="remark"
          maxlength="240"
          placeholder="如有收货要求，请告诉我们" /></view></view
    ><view class="box"
      ><template v-if="quote"
        ><view class="row"
          ><text>商品金额</text
          ><text>¥{{ money(quote.itemAmountFen) }}</text></view
        ><view class="row"
          ><text>运费</text><text>¥{{ money(quote.freightFen) }}</text></view
        ><view class="row"
          ><text>积分抵扣</text
          ><text>− ¥{{ money(quote.pointsUsed * 100) }}</text></view
        ><view class="row"
          ><text>应付金额</text
          ><text class="big">¥{{ money(quote.cashDueFen) }}</text></view
        ></template
      ><view class="checkbox" @tap="accepted = !accepted"
        ><text>{{ accepted ? "☑" : "□" }} 我已阅读并同意</text
        ><text class="link" @tap.stop="navigate('legal', { type: 'terms' })"
          >交易与售后规则</text
        ></view
      ><button
        class="primary"
        :disabled="busy || !quote || !accepted"
        @tap="submit"
      >
        提交订单
      </button></view
    ></PageShell
  >
</template>
