<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, onMounted, watch, onBeforeUnmount, computed } from "vue";
import { storeToRefs } from "pinia";
import { useRouter } from "vue-router";
import { memberApi } from "@/api/member";
import { orderApi } from "@/api/order";
import { useAuthStore } from "@/store/auth";
import { useCartStore } from "@/store/cart";
import { useUiStore } from "@/store/ui";
import { useCheckoutSubmission } from "@/composables/storefront/useCheckoutSubmission";
import { useAction } from "@/composables/useAction";
import { createRequestKey } from "@/utils/idempotency";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
import AddressDialog from "@/components/storefront/AddressDialog.vue";
const auth = useAuthStore(),
  cartStore = useCartStore(),
  ui = useUiStore(),
  router = useRouter();
const { items: cart } = storeToRefs(cartStore);
const profile = computed(() => auth.memberProfile);
const addresses = ref<any[]>([]),
  addressId = ref(0),
  points = ref(0),
  remark = ref(""),
  accepted = ref(false),
  quote = ref<any>(null),
  addressVisible = ref(false);
const { busy, run } = useAction();
let requestKey = createRequestKey(),
  sequence = 0,
  timer: ReturnType<typeof setTimeout>;
async function loadAddresses() {
  addresses.value = await memberApi.getAddresses();
  addressId.value =
    addresses.value.find((a) => a.is_default)?.id ||
    addresses.value[0]?.id ||
    0;
  await getQuote();
}
async function getQuote() {
  const generation = ++sequence;
  quote.value = null;
  if (!addressId.value || !cart.value.length) return;
  try {
    const result = await orderApi.getQuote({
      items: cart.value.map((i) => ({ skuId: i.sku_id, quantity: i.quantity })),
      addressId: addressId.value,
      pointsRequested: points.value,
    });
    if (generation === sequence) quote.value = result;
  } catch {}
}
watch([addressId, points], () => {
  sequence++;
  quote.value = null;
  clearTimeout(timer);
  timer = setTimeout(getQuote, 250);
});
onBeforeUnmount(() => {
  sequence++;
  clearTimeout(timer);
});
onMounted(() =>
  run(async () => {
    await Promise.all([cartStore.load(), auth.fetchMemberProfile()]);
    await loadAddresses();
  }),
);
function openAddress() {
  addressVisible.value = true;
}
const { createdOrder, submit } = useCheckoutSubmission({
  create: () =>
    orderApi.createOrder(
      {
        quoteId: quote.value.quoteId,
        addressId: addressId.value,
        buyerRemark: remark.value,
      },
      requestKey,
    ),
  onCreated: () => {
    requestKey = createRequestKey();
  },
  refreshCart: () => cartStore.load(true),
  navigate: (orderId) => router.push("/orders/" + orderId),
});
async function checkout() {
  await run(async () => {
    if (!createdOrder.value && (!quote.value || !accepted.value)) {
      ui.message("请确认报价并阅读交易规则", true);
      return;
    }
    await submit();
  });
}
</script>
<template>
  <AccountHeader title="确认这份美好" />
  <section class="section bag-layout">
    <div>
      <div class="panel">
        <div class="panel-title">
          <h3><Icon name="MapPin" /> 送到这里</h3>
          <button class="text-link" @click="openAddress()">
            新增地址 <Icon name="Plus" :size="16" />
          </button>
        </div>
        <label
          v-for="a in addresses"
          :key="a.id"
          class="address-option"
          :class="{ active: addressId === a.id }"
          ><input type="radio" v-model="addressId" :value="a.id" />
          <div>
            <strong
              >{{ a.name }} <span>{{ a.phone }}</span></strong
            >
            <p>{{ a.region }} {{ a.detail }}</p>
          </div></label
        >
        <p v-if="!addresses.length" class="muted">
          先添加一个收货地址，好物才能如约而至。
        </p>
      </div>
      <div class="panel">
        <h3>已选好物</h3>
        <article v-for="i in cart" :key="i.sku_id" class="checkout-item">
          <img :src="i.image" :alt="i.name" />
          <div>
            <strong>{{ i.name }}</strong>
            <p>{{ i.sku_name }} × {{ i.quantity }}</p>
          </div>
          <span>¥{{ money(i.price * i.quantity) }}</span>
        </article>
      </div>
      <div class="panel">
        <h3>积分礼遇</h3>
        <label class="input-label"
          >使用积分
          <span class="muted"
            >可用 {{ profile?.points }} 点，每点抵1元，受商品上限限制</span
          ><input
            v-model.number="points"
            type="number"
            min="0"
            :max="profile?.points" /></label
        ><label class="input-label"
          >给我们的留言<textarea
            v-model="remark"
            maxlength="240"
            placeholder="如有特别的收货要求，请告诉我们"
          ></textarea>
        </label>
      </div>
    </div>
    <aside class="summary-card">
      <h3>订单明细</h3>
      <template v-if="quote"
        ><div class="summary-line">
          <span>商品金额</span><span>¥{{ money(quote.itemAmountFen) }}</span>
        </div>
        <div class="summary-line">
          <span>运费</span><span>¥{{ money(quote.freightFen) }}</span>
        </div>
        <div class="summary-line green">
          <span>积分抵扣</span
          ><span>− ¥{{ money(quote.pointsUsed * 100) }}</span>
        </div>
        <div class="divider"></div>
        <div class="summary-line">
          <span>应付金额</span
          ><strong class="large">¥{{ money(quote.cashDueFen) }}</strong>
        </div></template
      >
      <p v-else class="muted">请完善地址和积分，获取实时报价。</p>
      <label class="checkbox-label"
        ><input type="checkbox" v-model="accepted" /><span
          >我已阅读并同意<RouterLink to="/legal/terms"
            >《交易与售后规则》</RouterLink
          ></span
        ></label
      ><button
        class="button dark full"
        :disabled="
          busy || (!createdOrder && (!quote || !accepted || !cart.length))
        "
        @click="checkout"
      >
        {{ createdOrder ? "查看已创建订单" : "提交订单" }}
        <Icon name="ArrowRight" :size="17" />
      </button>
      <p class="caption">价格和库存以下单时的服务端校验为准</p>
    </aside>
  </section>
  <AddressDialog v-model="addressVisible" @saved="loadAddresses" />
</template>
