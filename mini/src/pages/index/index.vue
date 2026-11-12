<script setup lang="ts">
import { ref, computed } from "vue";
import {
  onLoad,
  onPullDownRefresh,
  onShareAppMessage,
} from "@dcloudio/uni-app";
import { api, money, time, key, image, labels } from "../../api";
const privacyApi = wx as typeof wx & {
  getPrivacySetting(options: {
    success: (result: { needAuthorization: boolean }) => void;
    fail: (error: any) => void;
  }): void;
  requirePrivacyAuthorize(options: {
    success: () => void;
    fail: (error: any) => void;
  }): void;
};
const logistics = ref<any[]>([]);
const page = ref("home"),
  config = ref<any>({}),
  rules = ref<any>({}),
  products = ref<any[]>([]),
  categories = ref<any[]>([]),
  product = ref<any>(null),
  sku = ref(0),
  quantity = ref(1),
  search = ref(""),
  category = ref(""),
  profile = ref<any>(null),
  cart = ref<any[]>([]),
  addresses = ref<any[]>([]),
  addressId = ref(0),
  quote = ref<any>(null),
  points = ref(0),
  remark = ref(""),
  orders = ref<any[]>([]),
  order = ref<any>(null),
  ledger = ref<any[]>([]),
  withdrawals = ref<any[]>([]),
  team = ref<any>({ level1: [], level2: [], commissions: [] }),
  amount = ref(""),
  accepted = ref(false),
  invite = ref(""),
  busy = ref(false),
  dialog = ref(""),
  address = ref<any>({}),
  refund = ref<any>({ reason: "", lines: [] }),
  name = ref(""),
  quoteKey = ref(key()),
  withdrawKey = ref(key()),
  refundKey = ref(key());
const selectedSku = computed(() =>
  product.value?.skus.find((s: any) => s.id === sku.value),
);
const cartTotal = computed(() =>
  cart.value.reduce((s, i) => s + i.price * i.quantity, 0),
);
const tabs = [
  ["home", "好物", "◈"],
  ["points", "积分", "◇"],
  ["share", "分享", "↗"],
  ["cart", "购物袋", "▢"],
  ["account", "我的", "○"],
];
const visible = computed(() =>
  page.value === "points"
    ? products.value.filter((x) => x.points_percent > 0)
    : products.value,
);
async function act(fn: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  uni.showLoading({ title: "请稍候" });
  try {
    await fn();
  } catch (error: any) {
    if (error?.errMsg)
      uni.showToast({
        title: "操作未完成，请检查微信授权或稍后重试",
        icon: "none",
      });
    else if (error?.message)
      uni.showToast({ title: error.message, icon: "none" });
  } finally {
    busy.value = false;
    uni.hideLoading();
  }
}
async function refresh() {
  if (uni.getStorageSync("sj-token")) {
    profile.value = await api("/me");
    cart.value = await api("/cart");
  } else profile.value = null;
}
async function navigate(p: string) {
  page.value = p;
  dialog.value = "";
  uni.pageScrollTo({ scrollTop: 0, duration: 0 });
  await act(async () => {
    await refresh();
    if (["home", "points"].includes(p)) await filter();
    if (!profile.value) {
      if (["addresses", "checkout", "orders", "withdraw"].includes(p))
        page.value = "account";
      return;
    }
    if (["addresses", "checkout"].includes(p)) {
      addresses.value = await api("/addresses");
      addressId.value =
        addresses.value.find((a) => a.is_default)?.id ||
        addresses.value[0]?.id ||
        0;
      if (p === "checkout") await getQuote();
    }
    if (p === "orders") {
      order.value = null;
      orders.value = await api("/orders");
    }
    if (p === "account") ledger.value = await api("/me/ledger");
    if (p === "share") team.value = await api("/me/team");
    if (p === "withdraw") withdrawals.value = await api("/me/withdrawals");
  });
}
async function filter() {
  products.value = await api(
    "/products?category=" +
      category.value +
      "&search=" +
      encodeURIComponent(search.value),
  );
}
async function login() {
  await act(async () => {
    await new Promise<void>((resolve, reject) =>
      privacyApi.getPrivacySetting({
        success(r) {
          if (!r.needAuthorization) {
            resolve();
            return;
          }
          privacyApi.requirePrivacyAuthorize({
            success: () => resolve(),
            fail: reject,
          });
        },
        fail: reject,
      }),
    );
    const code = await new Promise<string>((resolve, reject) =>
      uni.login({
        provider: "weixin",
        success: (r) => resolve(r.code),
        fail: reject,
      }),
    );
    const result = await api("/auth/wechat", "POST", {
      code,
      inviteCode: invite.value,
    });
    uni.setStorageSync("sj-token", result.token);
    await refresh();
  });
}
function needLogin() {
  if (profile.value) return true;
  page.value = "account";
  uni.showToast({ title: "请先微信登录", icon: "none" });
  return false;
}
async function detail(p: any) {
  await act(async () => {
    product.value = await api("/products/" + p.id);
    sku.value = product.value.skus[0]?.id;
    quantity.value = 1;
    page.value = "detail";
    uni.pageScrollTo({ scrollTop: 0 });
  });
}
async function add(buy = false) {
  if (!needLogin()) return;
  let added = false;
  await act(async () => {
    await api("/cart/" + sku.value, "PUT", {
      quantity: Math.min(
        99,
        quantity.value +
          (cart.value.find((i) => i.sku_id === sku.value)?.quantity || 0),
      ),
    });
    await refresh();
    added = true;
    uni.showToast({ title: "已加入购物袋", icon: "success" });
  });
  if (buy && added) await navigate("checkout");
}
async function cartChange(i: any, delta: number) {
  await act(async () => {
    await api("/cart/" + i.sku_id, "PUT", {
      quantity: Math.max(0, i.quantity + delta),
    });
    await refresh();
  });
}
async function getQuote() {
  quote.value = null;
  if (!addressId.value || !cart.value.length) return;
  quote.value = await api("/checkout/quote", "POST", {
    addressId: addressId.value,
    items: cart.value.map((i) => ({ skuId: i.sku_id, quantity: i.quantity })),
    pointsRequested: Number(points.value),
  });
}
async function checkout() {
  await act(async () => {
    if (!accepted.value || !quote.value)
      throw new Error("请确认报价和交易规则");
    const result = await api(
      "/orders",
      "POST",
      {
        quoteId: quote.value.quoteId,
        addressId: addressId.value,
        buyerRemark: remark.value,
      },
      quoteKey.value,
    );
    quoteKey.value = key();
    order.value = await api("/orders/" + result.id);
    await refresh();
    page.value = "order";
  });
}
async function openOrder(o: any) {
  logistics.value = [];
  await act(async () => {
    order.value = await api("/orders/" + o.id);
    page.value = "order";
  });
}
async function orderAction(op: string) {
  await act(async () => {
    await api(
      "/orders/" + order.value.id + "/" + op,
      op === "payment-status" ? "GET" : "POST",
    );
    order.value = await api("/orders/" + order.value.id);
    await refresh();
    dialog.value = "";
  });
}
async function orderActionDev() {
  await act(async () => {
    await api("/dev/orders/" + order.value.id + "/pay", "POST");
    order.value = await api("/orders/" + order.value.id);
    await refresh();
    dialog.value = "";
  });
}
async function pay() {
  await act(async () => {
    const params = await api("/orders/" + order.value.id + "/prepay", "POST");
    if (params.mode === "DEVELOPMENT") {
      dialog.value = "devpay";
      return;
    }
    try {
      await new Promise<void>((resolve, reject) =>
        uni.requestPayment({
          provider: "wxpay",
          ...params,
          success: () => resolve(),
          fail: reject,
        }),
      );
    } catch {
      uni.showToast({ title: "支付未完成，请查询订单状态", icon: "none" });
    } finally {
      order.value = await api("/orders/" + order.value.id + "/payment-status");
      await refresh();
    }
  });
}
async function saveAddress() {
  await act(async () => {
    await api("/addresses", "POST", address.value);
    dialog.value = "";
    addresses.value = await api("/addresses");
    addressId.value =
      addresses.value.find((a) => a.is_default)?.id ||
      addresses.value[0]?.id ||
      0;
    if (page.value === "checkout") await getQuote();
  });
}
function editAddress(a?: any) {
  address.value = a
    ? { ...a }
    : { name: "", phone: "", region: "", detail: "", is_default: true };
  dialog.value = "address";
}
async function chooseWxAddress() {
  uni.chooseAddress({
    success(r) {
      address.value = {
        name: r.userName,
        phone: r.telNumber,
        region: r.provinceName + " " + r.cityName + " " + r.countyName,
        detail: r.detailInfo,
        is_default: true,
      };
      dialog.value = "address";
    },
    fail() {
      uni.showToast({ title: "可选择手动添加地址", icon: "none" });
    },
  });
}
function startRefund() {
  refund.value = {
    reason: "",
    lines: order.value.items
      .filter((i: any) => i.quantity > i.refunded_quantity)
      .map((i: any) => ({
        id: i.id,
        name: i.name,
        quantity: i.quantity - i.refunded_quantity,
        max: i.quantity - i.refunded_quantity,
        selected: true,
      })),
  };
  refundKey.value = key();
  dialog.value = "refund";
}
async function applyRefund() {
  await act(async () => {
    await api(
      "/refunds",
      "POST",
      {
        orderNo: order.value.id,
        reason: refund.value.reason,
        lines: refund.value.lines
          .filter((i: any) => i.selected)
          .map((i: any) => ({
            orderItemId: i.id,
            quantity: Number(i.quantity),
          })),
      },
      refundKey.value,
    );
    order.value = await api("/orders/" + order.value.id);
    dialog.value = "";
    uni.showToast({ title: "申请已提交", icon: "success" });
  });
}
async function promote() {
  await act(async () => {
    await api("/me/promoter", "POST", { accepted: accepted.value });
    await refresh();
  });
}
async function withdraw() {
  await act(async () => {
    if (!/^\d+(\.\d{1,2})?$/.test(amount.value)) {
      uni.showToast({ title: "请输入有效金额", icon: "none" });
      return;
    }
    await api(
      "/me/withdrawals",
      "POST",
      { amountFen: Math.round(Number(amount.value) * 100) },
      withdrawKey.value,
    );
    withdrawKey.value = key();
    amount.value = "";
    await refresh();
    withdrawals.value = await api("/me/withdrawals");
  });
}
async function queryLogistics() {
  await act(async () => {
    logistics.value = await api("/orders/" + order.value.id + "/logistics");
    if (!logistics.value.length)
      uni.showToast({ title: "暂无轨迹，请稍后查询", icon: "none" });
  });
}
async function receiveTransfer(r: any) {
  await act(async () => {
    if (!r.package_info) {
      uni.showToast({ title: "请稍后刷新收款参数", icon: "none" });
      return;
    }
    const wxApi: any = wx;
    if (!wxApi.requestMerchantTransfer) {
      uni.showToast({ title: "请更新微信后确认收款", icon: "none" });
      return;
    }
    await new Promise<void>((resolve, reject) =>
      wxApi.requestMerchantTransfer({
        mchId: config.value.mchId,
        appId: config.value.appId,
        package: r.package_info,
        success: () => resolve(),
        fail: reject,
      }),
    );
    withdrawals.value = await api("/me/withdrawals");
  });
}
function copySupport() {
  uni.setClipboardData({ data: config.value.support });
}
async function signOut() {
  await act(async () => {
    await api("/auth/logout", "POST");
    uni.removeStorageSync("sj-token");
    profile.value = null;
  });
}
function copyInvite() {
  uni.setClipboardData({ data: profile.value.invite_code });
}
onLoad(async (options: any) => {
  invite.value = options?.invite || "";
  await act(async () => {
    config.value = await api("/config");
    rules.value = await api("/rules/public");
    categories.value = await api("/categories");
    await filter();
    await refresh();
  });
});
onPullDownRefresh(async () => {
  await navigate(page.value);
  uni.stopPullDownRefresh();
});
onShareAppMessage(() => ({
  title: "素见好物 · 少一些，更好一些",
  path:
    "/pages/index/index?invite=" +
    (profile.value?.promoter ? profile.value.invite_code : ""),
  imageUrl: product.value ? image(product.value.image) : undefined,
}));
</script>
<template>
  <view class="app"
    ><view class="topbar"
      ><view class="brand"
        ><text class="mark">素</text
        ><view
          ><text>素见好物</text><text class="english">SIMPLY SEEN</text></view
        ></view
      ><text class="toplink" @tap="navigate('orders')">我的订单</text></view
    >
    <template v-if="page === 'home' || page === 'points'"
      ><view v-if="page === 'home'" class="hero"
        ><view class="eyebrow">THE ART OF EVERYDAY</view
        ><view class="hero-title">少一些，<br />更好一些。</view
        ><text class="hero-note">从一件心仪的好物，开始认真生活。</text
        ><image
          class="hero-image"
          src="../../static/cup.png"
          mode="aspectFit"
        /><text
          class="hero-link"
          @tap="
            category = '';
            filter();
          "
          >发现日常好物 ↗</text
        ></view
      ><view v-else class="points-hero"
        ><text class="eyebrow">A LITTLE THANK YOU</text
        ><view class="title">心意，值得被回馈。</view
        ><view v-if="profile" class="point-balance"
          >{{ profile.points }} <text>可用积分</text></view
        ><text class="caption"
          >商品级限额内1积分抵1元，不可提现或转赠</text
        ></view
      ><view class="search"
        ><input
          v-model="search"
          placeholder="寻找一件好物"
          confirm-type="search"
          @confirm="act(filter)"
        /><text @tap="act(filter)">搜索</text></view
      ><scroll-view scroll-x class="categories"
        ><text
          :class="{ active: category === '' }"
          @tap="
            category = '';
            act(filter);
          "
          >全部好物</text
        ><text
          v-for="c in categories"
          :key="c.id"
          :class="{ active: category === String(c.id) }"
          @tap="
            category = String(c.id);
            act(filter);
          "
          >{{ c.name }}</text
        ></scroll-view
      ><view class="section-title"
        ><view>日常里的，心动之选</view
        ><text>{{ visible.length }}件好物</text></view
      ><view class="products"
        ><view v-for="p in visible" :key="p.id" class="product" @tap="detail(p)"
          ><view class="art" :style="{ background: p.color }"
            ><image :src="image(p.image)" mode="aspectFit" /><text
              v-if="p.featured"
              class="tag"
              >素见精选</text
            ></view
          ><view class="product-name">{{ p.name }}</view
          ><text class="sub">{{ p.subtitle }}</text
          ><view class="price"
            >¥{{ money(p.price)
            }}<text>{{ p.points_percent }}% 积分抵扣</text></view
          ></view
        ></view
      ><view v-if="!visible.length" class="empty">暂无符合条件的商品</view
      ><view class="bottom-note"
        >LESS, BUT BETTER.<text>让好物回归日常，让生活简单而丰盈。</text></view
      ></template
    >
    <template v-else-if="page === 'detail' && product"
      ><view class="detail-art" :style="{ background: product.color }"
        ><image :src="image(product.image)" mode="aspectFit" /></view
      ><view class="box"
        ><view class="eyebrow">ESSENTIALS FOR EVERYDAY</view
        ><view class="title">{{ product.name }}</view
        ><text class="sub">{{ product.subtitle }}</text
        ><view class="detail-price">¥{{ money(selectedSku?.price) }}</view
        ><view class="field-title">规格选择</view
        ><view class="options"
          ><text
            v-for="s in product.skus"
            :key="s.id"
            :class="{ active: sku === s.id }"
            @tap="sku = s.id"
            >{{ s.name }}<text v-if="!s.stock">（售罄）</text></text
          ></view
        ><view class="row"
          ><text>数量 · 库存{{ selectedSku?.stock }}</text
          ><view class="stepper"
            ><text @tap="quantity = Math.max(1, quantity - 1)">−</text
            ><text>{{ quantity }}</text
            ><text
              @tap="quantity = Math.min(99, selectedSku?.stock, quantity + 1)"
              >＋</text
            ></view
          ></view
        ><button
          class="primary"
          :disabled="busy || !selectedSku?.stock"
          @tap="add()"
        >
          加入购物袋</button
        ><button
          class="secondary"
          :disabled="busy || !selectedSku?.stock"
          @tap="add(true)"
        >
          立即选购</button
        ><view class="divider"></view
        ><view class="field-title">关于这件好物</view
        ><text class="description">{{ product.description }}</text></view
      ></template
    >
    <template v-else-if="page === 'cart'"
      ><view class="page-heading"
        ><text class="eyebrow">YOUR EVERYDAY</text
        ><view class="title">我的购物袋</view></view
      ><view v-if="!profile" class="empty"
        ><text>登录后查看购物袋</text
        ><button class="primary" @tap="navigate('account')">
          前往登录
        </button></view
      ><template v-else
        ><view v-for="i in cart" :key="i.sku_id" class="cart-item"
          ><image
            :src="image(i.image)"
            mode="aspectFit"
            :style="{ background: i.color }"
          /><view class="cart-info"
            ><view>{{ i.name }}</view
            ><text class="sub">{{ i.sku_name }}</text
            ><view class="row"
              ><text>¥{{ money(i.price) }}</text
              ><view class="stepper"
                ><text @tap="cartChange(i, -1)">−</text
                ><text>{{ i.quantity }}</text
                ><text @tap="cartChange(i, 1)">＋</text></view
              ></view
            ></view
          ><text @tap="cartChange(i, -i.quantity)">×</text></view
        ><view v-if="cart.length" class="box"
          ><view class="row"
            ><text>商品合计</text
            ><text class="big">¥{{ money(cartTotal) }}</text></view
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
      ></template
    >
    <template v-else-if="page === 'checkout'"
      ><view class="page-heading"><view class="title">确认这份美好</view></view
      ><view class="box"
        ><view class="row"
          ><text class="field-title">收货地址</text
          ><text class="link" @tap="editAddress()">新增地址</text></view
        ><view
          v-for="a in addresses"
          :key="a.id"
          class="address-option"
          :class="{ selected: addressId === a.id }"
          @tap="
            addressId = a.id;
            act(getQuote);
          "
          ><view
            >{{ a.name }} · {{ a.phone }}
            <text v-if="addressId === a.id">✓</text></view
          ><text class="sub">{{ a.region }} {{ a.detail }}</text></view
        ><text v-if="!addresses.length" class="sub"
          >请先添加收货地址</text
        ></view
      ><view class="box"
        ><view v-for="i in cart" :key="i.sku_id" class="small-item"
          ><image :src="image(i.image)" mode="aspectFit" /><view
            ><text>{{ i.name }}</text
            ><text class="sub">{{ i.sku_name }} × {{ i.quantity }}</text></view
          ><text>¥{{ money(i.price * i.quantity) }}</text></view
        ><view class="field"
          ><text>使用积分（可用{{ profile?.points }}）</text
          ><input
            v-model.number="points"
            type="number"
            @blur="act(getQuote)" /></view
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
          ><text class="link" @tap.stop="dialog = 'terms'"
            >交易与售后规则</text
          ></view
        ><button
          class="primary"
          :disabled="!quote || !accepted || busy"
          @tap="checkout"
        >
          提交订单
        </button></view
      ></template
    >
    <template v-else-if="page === 'orders'"
      ><view class="page-heading"><view class="title">我的订单</view></view
      ><view v-for="o in orders" :key="o.id" class="box" @tap="openOrder(o)"
        ><view class="row"
          ><text class="caption">{{ time(o.created_at) }}</text
          ><text class="badge">{{ labels[o.state] || o.state }}</text></view
        ><view v-for="i in o.items" :key="i.id" class="small-item"
          ><image :src="image(i.image)" mode="aspectFit" /><view
            ><text>{{ i.name }}</text
            ><text class="sub">× {{ i.quantity }}</text></view
          ></view
        ><view class="row"
          ><text>实付 ¥{{ money(o.cash_due) }}</text
          ><text class="link">查看详情 →</text></view
        ></view
      ><view v-if="!orders.length" class="empty"
        >还没有订单，去挑选一件好物吧。</view
      ></template
    >
    <template v-else-if="page === 'order' && order"
      ><view class="page-heading"
        ><view class="title">{{ labels[order.state] }}</view
        ><text class="caption">{{ order.id }}</text></view
      ><view class="box"
        ><text>{{ order.address.name }} · {{ order.address.phone }}</text
        ><view class="sub"
          >{{ order.address.region }} {{ order.address.detail }}</view
        ><view v-if="order.carrier" class="row"
          ><text>物流 {{ order.carrier }}</text
          ><text selectable
            >{{ order.tracking_no
            }}<text
              v-if="config.logisticsEnabled"
              class="link"
              @tap="queryLogistics"
              >查询轨迹</text
            ></text
          ></view
        ></view
      ><view class="box"
        ><view v-for="i in order.items" :key="i.id" class="small-item"
          ><image :src="image(i.image)" mode="aspectFit" /><view
            ><text>{{ i.name }}</text
            ><text class="sub"
              >数量{{ i.quantity }} / 已退{{ i.refunded_quantity }}</text
            ></view
          ><text>¥{{ money(i.cash_amount) }}</text></view
        ><view class="row"
          ><text>运费</text><text>¥{{ money(order.freight) }}</text></view
        ><view class="row"
          ><text>积分抵扣</text><text>{{ order.points_used }}积分</text></view
        ><view class="row"
          ><text>实付现金</text
          ><text class="big">¥{{ money(order.cash_due) }}</text></view
        ><view v-if="order.cash_refunded" class="row"
          ><text>已退现金</text
          ><text>¥{{ money(order.cash_refunded) }}</text></view
        ><template v-if="order.state === 'CREATED'"
          ><button class="primary" :disabled="busy" @tap="pay">前往支付</button
          ><button
            class="secondary"
            :disabled="busy"
            @tap="orderAction('payment-status')"
          >
            查询支付结果</button
          ><button class="text-button" @tap="dialog = 'cancel'">
            取消订单
          </button></template
        ><button
          v-if="order.state === 'SHIPPED'"
          class="primary"
          @tap="dialog = 'receive'"
        >
          确认收货</button
        ><button
          v-if="['PAID', 'SHIPPED', 'COMPLETED'].includes(order.state)"
          class="secondary"
          @tap="startRefund"
        >
          申请售后
        </button></view
      ><view v-if="order.refunds.length" class="box"
        ><view class="field-title">售后记录</view
        ><view v-for="r in order.refunds" :key="r.id" class="history"
          ><view class="row"
            ><text>{{ labels[r.state] || r.state }}</text
            ><text>¥{{ money(r.amount) }}</text></view
          ><text class="sub">{{ r.reason }}</text></view
        ></view
      ><button class="text-button" @tap="navigate('orders')">
        返回全部订单
      </button></template
    >
    <template v-else-if="page === 'account'"
      ><view v-if="!profile" class="login-card"
        ><text class="mark">素</text><view class="title">欢迎来到素见</view
        ><text class="sub">登录，开启你的质感日常。</text
        ><button class="primary" :disabled="busy" @tap="login">
          微信授权登录</button
        ><view class="checkbox"
          ><text class="link" @tap="dialog = 'privacy'">隐私政策</text
          ><text> · </text
          ><text class="link" @tap="dialog = 'terms'"
            >交易与售后规则</text
          ></view
        ></view
      ><template v-else
        ><view class="member-card"
          ><view class="avatar">{{ profile.name.slice(0, 1) }}</view
          ><view
            ><text class="eyebrow">WELCOME BACK</text
            ><view class="title">{{ profile.name }}</view></view
          ><text
            class="link"
            @tap="
              name = profile.name;
              dialog = 'name';
            "
            >编辑</text
          ></view
        ><view class="asset-grid"
          ><view @tap="navigate('points')"
            ><text>可用积分</text><view>{{ profile.points }}</view></view
          ><view @tap="navigate('withdraw')"
            ><text>可用佣金</text
            ><view>{{ money(profile.available) }}</view></view
          ><view @tap="navigate('withdraw')"
            ><text>提现冻结</text
            ><view>{{ money(profile.reserved) }}</view></view
          ></view
        ><view class="box"
          ><view
            v-for="n in [
              ['orders', '我的订单'],
              ['addresses', '地址管理'],
              ['share', '分享与佣金'],
              ['withdraw', '佣金提现'],
            ]"
            :key="n[0]"
            class="menu-row"
            @tap="navigate(n[0])"
            ><text>{{ n[1] }}</text
            ><text>→</text></view
          ><view class="menu-row" @tap="dialog = 'privacy'"
            ><text>隐私与信息保护</text><text>→</text></view
          ><view class="menu-row" @tap="dialog = 'support'"
            ><text>联系素见</text><text>→</text></view
          ></view
        ><view class="box"
          ><view class="field-title">资产明细</view
          ><view v-for="l in ledger.slice(0, 30)" :key="l.id" class="history"
            ><view class="row"
              ><text>{{ l.note }}</text
              ><text
                >{{ l.amount > 0 ? "+" : ""
                }}{{
                  l.asset.startsWith("points") ? l.amount : money(l.amount)
                }}</text
              ></view
            ><text class="sub"
              >{{ labels[l.asset] || l.asset }} · {{ time(l.created_at) }}</text
            ></view
          ><text v-if="!ledger.length" class="sub">暂无资产变动</text></view
        ><button class="text-button" @tap="signOut">退出登录</button></template
      ></template
    >
    <template v-else-if="page === 'addresses'"
      ><view class="page-heading"><view class="title">收货地址</view></view
      ><view v-for="a in addresses" :key="a.id" class="box"
        ><view class="row"
          ><text>{{ a.name }} · {{ a.phone }}</text
          ><text v-if="a.is_default" class="badge">默认</text></view
        ><text class="sub">{{ a.region }} {{ a.detail }}</text
        ><view class="row"
          ><text class="link" @tap="editAddress(a)">编辑</text
          ><text
            class="link"
            @tap="
              address = a;
              dialog = 'deleteAddress';
            "
            >删除</text
          ></view
        ></view
      ><view class="box"
        ><button class="primary" @tap="editAddress()">手动添加地址</button
        ><button class="secondary" @tap="chooseWxAddress">
          导入微信收货地址
        </button></view
      ></template
    >
    <template v-else-if="page === 'share'"
      ><view v-if="!profile" class="empty"
        ><text>登录后分享美好</text
        ><button class="primary" @tap="navigate('account')">
          前往登录
        </button></view
      ><template v-else
        ><view class="share-hero"
          ><text class="eyebrow">BETTER TOGETHER</text
          ><view class="title">好物，值得分享。</view
          ><text class="sub"
            >无入门费，无注册现金奖励。<br />仅按两级真实成交及可分配毛利计佣。</text
          ><template v-if="profile.promoter"
            ><view class="code" @tap="copyInvite">{{
              profile.invite_code
            }}</view
            ><button class="primary" open-type="share">分享给朋友</button
            ><text class="link" @tap="copyInvite">复制邀请码</text></template
          ><template v-else
            ><view class="checkbox" @tap="accepted = !accepted"
              ><text>{{ accepted ? "☑" : "□" }} 我已阅读并同意</text
              ><text class="link" @tap.stop="dialog = 'distribution'"
                >推广规则</text
              ></view
            ><button class="primary" :disabled="!accepted" @tap="promote">
              成为好物分享者
            </button></template
          ></view
        ><view class="asset-grid"
          ><view
            ><text>直接邀请</text><view>{{ team.level1.length }}</view></view
          ><view
            ><text>二级归因</text><view>{{ team.level2.length }}</view></view
          ><view
            ><text>可用佣金</text
            ><view>{{ money(profile.available) }}</view></view
          ></view
        ><view class="box"
          ><view class="field-title">成交佣金明细</view
          ><view v-for="c in team.commissions" :key="c.id" class="history"
            ><view class="row"
              ><text>{{ c.level_no }}级佣金 · {{ labels[c.state] }}</text
              ><text>¥{{ money(c.amount) }}</text></view
            ><text class="sub">{{ c.order_id }}</text></view
          ><text v-if="!team.commissions.length" class="sub"
            >暂无有效成交佣金</text
          ></view
        ><view class="box"
          ><view class="field-title">我的直接邀请</view
          ><view v-for="m in team.level1" :key="m.id" class="row"
            ><text>{{ m.name }}</text
            ><text class="caption">{{ time(m.created_at) }}</text></view
          ></view
        ></template
      ></template
    >
    <template v-else-if="page === 'withdraw'"
      ><view class="page-heading"><view class="title">佣金提现</view></view
      ><view class="box"
        ><text class="sub">可申请提现佣金</text
        ><view class="balance">¥{{ money(profile?.available) }}</view
        ><text class="caption"
          >冻结 ¥{{ money(profile?.reserved) }} · 待追偿 ¥{{
            money(profile?.debt)
          }}</text
        ><view class="field"
          ><text>申请金额（元）</text
          ><input
            v-model="amount"
            type="digit"
            placeholder="请输入提现金额" /></view
        ><text class="caption"
          >税额经财务根据实际适用依据确认。渠道确认前不表示已到账。</text
        ><button class="primary" :disabled="busy || !amount" @tap="withdraw">
          提交申请
        </button></view
      ><view class="box"
        ><view class="field-title">提现记录</view
        ><view v-for="r in withdrawals" :key="r.id" class="history"
          ><view class="row"
            ><text>¥{{ money(r.gross) }}</text
            ><text class="badge">{{ labels[r.state] }}</text></view
          ><text class="sub"
            >税款 ¥{{ money(r.tax) }} · 税后 ¥{{ money(r.net) }}</text
          ><text class="caption"
            >{{ time(r.created_at) }} {{ r.reason || "" }}</text
          ><button
            v-if="r.state === 'CONFIRMING'"
            class="primary"
            @tap="receiveTransfer(r)"
          >
            确认收款
          </button></view
        ><text v-if="!withdrawals.length" class="sub">暂无提现记录</text></view
      ></template
    >
    <view class="nav"
      ><view
        v-for="t in tabs"
        :key="t[0]"
        :class="{ active: page === t[0] }"
        @tap="navigate(t[0])"
        ><text class="nav-icon">{{ t[2] }}</text
        ><text>{{ t[1] }}</text></view
      ></view
    >
    <view v-if="dialog" class="mask" @tap.self="dialog = ''"
      ><scroll-view scroll-y class="dialog"
        ><text class="close" @tap="dialog = ''">×</text
        ><template v-if="dialog === 'address'"
          ><view class="title">收货地址</view
          ><view class="field"
            ><text>收货人</text
            ><input v-model="address.name" maxlength="60" /></view
          ><view class="field"
            ><text>手机号</text
            ><input
              v-model="address.phone"
              type="number"
              maxlength="11" /></view
          ><view class="field"
            ><text>省市区</text
            ><input v-model="address.region" maxlength="160" /></view
          ><view class="field"
            ><text>详细地址</text
            ><textarea v-model="address.detail" maxlength="240" /></view
          ><view
            class="checkbox"
            @tap="address.is_default = !address.is_default"
            >{{ address.is_default ? "☑" : "□" }} 设为默认地址</view
          ><button class="primary" :disabled="busy" @tap="saveAddress">
            保存地址
          </button></template
        >
        <template v-else-if="dialog === 'name'"
          ><view class="title">怎么称呼你？</view
          ><view class="field"><input v-model="name" maxlength="40" /></view
          ><button
            class="primary"
            @tap="
              act(async () => {
                await api('/me', 'PUT', { name });
                await refresh();
                dialog = '';
              })
            "
          >
            保存昵称
          </button></template
        >
        <template v-else-if="dialog === 'refund'"
          ><view class="title">申请售后退款</view
          ><view v-for="i in refund.lines" :key="i.id" class="refund-line"
            ><text @tap="i.selected = !i.selected"
              >{{ i.selected ? "☑" : "□" }} {{ i.name }}</text
            ><input v-model.number="i.quantity" type="number" /></view
          ><view class="field"
            ><text>退款原因</text
            ><textarea v-model="refund.reason" maxlength="240" /></view
          ><text class="caption">按原始订单分摊计算，提交不代表退款成功。</text
          ><button class="primary" :disabled="busy" @tap="applyRefund">
            提交申请
          </button></template
        >
        <template v-else-if="dialog === 'deleteAddress'"
          ><view class="title">删除这个地址？</view
          ><text class="sub">已创建订单的地址快照会保留。</text
          ><button
            class="primary"
            @tap="
              act(async () => {
                await api('/addresses/' + address.id, 'DELETE');
                addresses = await api('/addresses');
                dialog = '';
              })
            "
          >
            确认删除
          </button></template
        >
        <template v-else-if="dialog === 'cancel' || dialog === 'receive'"
          ><view class="title">{{
            dialog === "cancel" ? "确认取消订单？" : "确认收到商品？"
          }}</view
          ><text class="sub">{{
            dialog === "cancel"
              ? "渠道确认未支付后，将解除积分及库存预占。"
              : "确认收货后开始售后观察期，法定售后权利不受影响。"
          }}</text
          ><button
            class="primary"
            @tap="orderAction(dialog === 'cancel' ? 'cancel' : 'receive')"
          >
            确认操作
          </button></template
        >
        <template v-else-if="dialog === 'devpay'"
          ><view class="title">开发测试付款</view
          ><text class="sub">不涉及真实资金，仅供开发环境验证。</text
          ><button class="primary" @tap="orderActionDev">
            确认测试支付
          </button></template
        >
        <template v-else-if="dialog === 'support'"
          ><view class="title">联系素见</view
          ><text class="description"
            >{{
              config.support
            }}。订单售后请通过订单详情提交申请。个人信息查询、复制及注销请联系
            {{ config.privacy }}。</text
          ><button class="secondary" @tap="copySupport">
            复制联系方式
          </button></template
        >
        <template v-else-if="dialog === 'privacy'"
          ><view class="title">隐私与信息保护</view
          ><text class="description"
            >我们仅处理服务必需的信息：微信身份标识用于登录，收货人、手机与地址用于订单履约，交易和账本用于售后及法定义务。收货信息仅在履约需要时提供给物流方，支付通过微信商户服务处理。推广者不能查看被邀请会员的地址、手机及微信标识。您可以在地址页修改或删除地址。信息查询、复制、撤回授权及注销请联系
            {{
              config.privacy
            }}。未结交易与依法需留存的财务记录按法律义务处理。</text
          ></template
        >
        <template v-else-if="dialog === 'distribution'"
          ><view class="title">推广服务规则</view
          ><text class="description"
            >DIST-1.0：首次注册固定邀请归属，最多两级，不收取入门费，不强制消费或囤货，无拉人头奖励，自邀及虚假成交不计佣。一级基准{{
              rules.level1Bps / 100
            }}%，二级{{
              rules.level2Bps / 100
            }}%，实际受商品佣金比例、现金实收、可分配毛利和预算约束。运费与积分抵扣不计佣。确认收货后{{
              rules.settlementDays
            }}天且无进行中的售后，才可结算；退款依原订单比例冲回，已出款不足冲回则生成追偿，新佣金优先抵偿。提现须双人复核并以渠道成功为准。不承诺固定收益，不得宣传消费解锁或保本回报。</text
          ></template
        >
        <template v-else
          ><view class="title">交易与售后规则</view
          ><text class="description"
            >价格、运费、积分限额由服务端报价，下单时再次校验。未付订单超时后经查单及关单确认，再释放库存和积分。支付以渠道结果为准。每满100元合格商品现金消费赠{{
              rules.pointsPer100Yuan
            }}积分，按单取整，运费及积分抵扣部分不再计赠。积分不可提现或转赠，按商品限额每点抵1元。订单支持按商品数量申请部分及全额退款，审核后原路退款，最终以渠道成功为准。依法适用七日无理由退货及商品质量售后权利。任何争议请联系
            {{ config.support }}。</text
          ></template
        >
      </scroll-view></view
    ></view
  >
</template>
<style scoped>
.app {
  padding-bottom: 150rpx;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 25rpx 35rpx;
  background: #f8f9f4;
}
.brand {
  display: flex;
  gap: 17rpx;
  align-items: center;
  letter-spacing: 4rpx;
  font-size: 31rpx;
}
.mark {
  background: #344e3c;
  color: #fff;
  border-radius: 5rpx 18rpx 5rpx 5rpx;
  font-family: serif;
  font-size: 42rpx;
  width: 61rpx;
  height: 68rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.english {
  display: block;
  font-size: 13rpx;
  letter-spacing: 3rpx;
  margin-top: 6rpx;
  color: #93a086;
}
.toplink {
  font-size: 21rpx;
  color: #89977c;
}
.hero {
  margin: 0;
  background: #e9ecdf;
  position: relative;
  overflow: hidden;
  height: 490rpx;
  padding: 48rpx 40rpx;
}
.eyebrow {
  display: block;
  font-size: 15rpx;
  letter-spacing: 3rpx;
  color: #8c9c7c;
  margin-bottom: 22rpx;
}
.hero-title {
  font-family: serif;
  font-size: 66rpx;
  line-height: 1.5;
  letter-spacing: 4rpx;
  position: relative;
  z-index: 1;
}
.hero-note {
  display: block;
  font-size: 21rpx;
  color: #8e9a80;
  margin-top: 23rpx;
  max-width: 330rpx;
  line-height: 1.9;
}
.hero-image {
  position: absolute;
  width: 450rpx;
  height: 480rpx;
  right: -70rpx;
  top: 25rpx;
  transform: rotate(-9deg);
}
.hero-link {
  display: block;
  font-size: 21rpx;
  margin-top: 24rpx;
  color: #537046;
  position: relative;
  z-index: 1;
}
.search {
  display: flex;
  gap: 15rpx;
  margin: 30rpx 30rpx 15rpx;
  background: #fff;
  border: 1rpx solid #e3e9da;
  border-radius: 12rpx;
  padding: 19rpx 24rpx;
  font-size: 23rpx;
  color: #8c9b7a;
}
.search input {
  flex: 1;
  font-size: 23rpx;
}
.categories {
  white-space: nowrap;
  padding: 15rpx 25rpx;
  width: 100%;
  box-sizing: border-box;
}
.categories > text {
  display: inline-block;
  font-size: 22rpx;
  color: #929f81;
  padding: 13rpx 21rpx;
  margin-right: 10rpx;
  border-radius: 9rpx;
}
.categories > text.active {
  background: #e6eddb;
  color: #4d6c3a;
}
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 26rpx 33rpx;
  font-size: 29rpx;
  font-family: serif;
}
.section-title > text {
  font-family: sans-serif;
  font-size: 19rpx;
  color: #9da88f;
}
.products {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 35rpx 22rpx;
  padding: 0 30rpx;
}
.art {
  height: 330rpx;
  position: relative;
  border-radius: 9rpx;
  overflow: hidden;
}
.art image {
  width: 100%;
  height: 100%;
}
.tag {
  position: absolute;
  left: 15rpx;
  top: 16rpx;
  background: #ffffff88;
  padding: 8rpx 12rpx;
  border-radius: 5rpx;
  font-size: 17rpx;
  color: #849274;
}
.product-name {
  font-size: 26rpx;
  margin: 22rpx 0 10rpx;
}
.sub {
  font-size: 21rpx;
  color: #9aa58e;
  line-height: 1.9;
  display: block;
}
.price {
  font-size: 25rpx;
  margin: 15rpx 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.price text {
  font-size: 16rpx;
  color: #8b9b79;
}
.bottom-note {
  text-align: center;
  letter-spacing: 4rpx;
  font-size: 17rpx;
  color: #9fab8f;
  padding: 65rpx 20rpx 25rpx;
}
.bottom-note text {
  display: block;
  letter-spacing: 1rpx;
  margin-top: 15rpx;
  font-size: 18rpx;
}
.nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-around;
  background: #fcfdf8f5;
  border-top: 1rpx solid #e8eddf;
  padding: 17rpx 0 calc(17rpx + env(safe-area-inset-bottom));
  z-index: 20;
}
.nav > view {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 18rpx;
  color: #a5ae98;
  gap: 8rpx;
}
.nav > view.active {
  color: #3f6231;
}
.nav-icon {
  font-size: 37rpx;
  line-height: 1.1;
}
.detail-art {
  height: 660rpx;
  margin: 0 20rpx;
  border-radius: 12rpx;
}
.detail-art image {
  width: 100%;
  height: 100%;
}
.box {
  background: #fff;
  border: 1rpx solid #e7ecdd;
  margin: 25rpx 28rpx;
  padding: 30rpx;
  border-radius: 14rpx;
}
.title {
  font-family: serif;
  font-size: 42rpx;
  line-height: 1.5;
  letter-spacing: 2rpx;
  margin: 10rpx 0 20rpx;
}
.detail-price {
  font-size: 48rpx;
  margin: 30rpx 0;
}
.field-title {
  font-size: 27rpx;
  font-weight: 500;
  margin: 15rpx 0 23rpx;
}
.options {
  display: flex;
  gap: 15rpx;
  flex-wrap: wrap;
}
.options > text {
  border: 1rpx solid #dde5d4;
  padding: 17rpx 22rpx;
  border-radius: 9rpx;
  font-size: 23rpx;
  color: #879b73;
}
.options > text.active {
  background: #eaf0e1;
  border-color: #759263;
  color: #466936;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin: 25rpx 0;
  font-size: 24rpx;
  line-height: 1.7;
}
.stepper {
  display: flex;
  border: 1rpx solid #dfe7d5;
  border-radius: 8rpx;
  align-items: center;
}
.stepper text {
  padding: 9rpx 19rpx;
  font-size: 25rpx;
  min-width: 45rpx;
  text-align: center;
}
.primary,
.secondary {
  display: block;
  width: 100%;
  padding: 8rpx 0;
  margin: 25rpx 0 0;
  border-radius: 10rpx;
  font-size: 25rpx;
  font-weight: 400;
  background: #344e3c;
  color: #fff;
  line-height: 2.6;
  letter-spacing: 1rpx;
}
.secondary {
  background: #f0f4e8;
  color: #597946;
  border: 1rpx solid #dce5cf;
}
.primary[disabled] {
  background: #c2ccba;
  color: #fff;
}
.text-button {
  background: transparent;
  font-size: 22rpx;
  color: #8da078;
  margin: 25rpx auto;
}
.description {
  white-space: pre-line;
  font-size: 23rpx;
  line-height: 2.2;
  color: #8d9d7b;
  display: block;
}
.divider {
  height: 1rpx;
  background: #e6ecdc;
  margin: 35rpx 0;
}
.page-heading {
  padding: 35rpx 35rpx 20rpx;
  background: #edf1e4;
}
.page-heading .title {
  font-size: 39rpx;
}
.empty {
  text-align: center;
  padding: 90rpx 40rpx;
  font-size: 26rpx;
  color: #8e9e7c;
  line-height: 2;
}
.empty button {
  max-width: 350rpx;
  margin: 35rpx auto;
}
.cart-item {
  display: flex;
  gap: 22rpx;
  padding: 32rpx 28rpx;
  border-bottom: 1rpx solid #e6ecdc;
  align-items: start;
}
.cart-item > image {
  width: 160rpx;
  height: 190rpx;
  border-radius: 9rpx;
  flex-shrink: 0;
}
.cart-info {
  flex: 1;
  font-size: 25rpx;
  padding-top: 9rpx;
}
.cart-info .sub {
  font-size: 19rpx;
  margin-top: 10rpx;
}
.cart-info .row {
  margin: 23rpx 0 0;
}
.cart-item > .cart-info .stepper text {
  padding: 5rpx 14rpx;
  font-size: 22rpx;
}
.big {
  font-size: 36rpx;
}
.caption {
  display: block;
  font-size: 19rpx;
  line-height: 1.9;
  color: #9ba98b;
}
.field {
  margin: 27rpx 0;
}
.field > text {
  font-size: 23rpx;
  color: #849870;
}
.field input,
.field textarea {
  display: block;
  width: 100%;
  background: #fbfcf7;
  border: 1rpx solid #dfe8d3;
  padding: 19rpx;
  border-radius: 10rpx;
  font-size: 25rpx;
  margin-top: 14rpx;
  height: 78rpx;
}
.field textarea {
  height: 130rpx;
}
.address-option {
  border: 1rpx solid #e1e8d8;
  border-radius: 10rpx;
  padding: 22rpx;
  margin: 19rpx 0;
  font-size: 24rpx;
  line-height: 1.8;
}
.address-option.selected {
  background: #f1f6e9;
  border-color: #8fa67b;
}
.link {
  font-size: 22rpx;
  color: #64854f;
}
.small-item {
  display: flex;
  gap: 20rpx;
  align-items: center;
  border-bottom: 1rpx solid #edf1e5;
  padding: 23rpx 0;
  font-size: 23rpx;
}
.small-item image {
  width: 100rpx;
  height: 115rpx;
  background: #eeeee5;
  border-radius: 7rpx;
  flex-shrink: 0;
}
.small-item > view {
  flex: 1;
}
.small-item .sub {
  font-size: 18rpx;
  margin-top: 9rpx;
}
.small-item > text {
  font-size: 21rpx;
}
.checkbox {
  font-size: 21rpx;
  color: #8a9b78;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8rpx;
  margin: 30rpx 0;
  line-height: 1.8;
}
.badge {
  background: #e9f0de;
  border-radius: 7rpx;
  padding: 5rpx 12rpx;
  color: #6e8d55;
  font-size: 19rpx;
}
.history {
  border-bottom: 1rpx solid #e9efdf;
  padding: 10rpx 0 25rpx;
}
.history .row {
  font-size: 22rpx;
  margin: 19rpx 0 10rpx;
}
.history .sub {
  font-size: 18rpx;
}
.login-card {
  margin: 70rpx 35rpx;
  background: #fff;
  border-radius: 18rpx;
  padding: 65rpx 40rpx;
  text-align: center;
}
.login-card .mark {
  margin-bottom: 35rpx;
}
.login-card .checkbox {
  justify-content: center;
}
.member-card {
  display: flex;
  gap: 25rpx;
  align-items: center;
  padding: 50rpx 35rpx;
  background: #eaf0df;
}
.member-card > view:nth-child(2) {
  flex: 1;
}
.member-card .title {
  font-size: 36rpx;
  margin: 8rpx 0;
}
.member-card .eyebrow {
  font-size: 13rpx;
  letter-spacing: 3rpx;
  margin: 0;
}
.avatar {
  width: 95rpx;
  height: 95rpx;
  border-radius: 50%;
  background: #d6e3c8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 37rpx;
  color: #7c9668;
}
.asset-grid {
  display: flex;
  gap: 18rpx;
  margin: 25rpx 28rpx;
}
.asset-grid > view {
  flex: 1;
  background: #fff;
  border: 1rpx solid #e2ead6;
  border-radius: 12rpx;
  padding: 24rpx 12rpx;
  text-align: center;
}
.asset-grid > view > text {
  font-size: 20rpx;
  color: #8da17a;
}
.asset-grid > view > view {
  font-size: 33rpx;
  margin-top: 20rpx;
  color: #547340;
}
.menu-row {
  display: flex;
  justify-content: space-between;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #ecf1e2;
  font-size: 25rpx;
  color: #768e62;
}
.menu-row:last-child {
  border: 0;
}
.points-hero {
  padding: 45rpx 35rpx;
  background: #e9f0df;
}
.point-balance {
  font-size: 53rpx;
  margin: 25rpx 0;
  color: #597b43;
}
.point-balance text {
  font-size: 21rpx;
}
.share-hero {
  margin: 25rpx 28rpx;
  padding: 45rpx 30rpx;
  background: #e7efdb;
  border-radius: 14rpx;
  text-align: center;
}
.share-hero .title {
  font-size: 40rpx;
}
.share-hero .sub {
  font-size: 22rpx;
  line-height: 2;
}
.share-hero .checkbox {
  justify-content: center;
}
.code {
  font-family: monospace;
  font-size: 45rpx;
  letter-spacing: 5rpx;
  margin: 35rpx 0;
  color: #5e7c47;
}
.share-hero > .link {
  display: block;
  margin-top: 27rpx;
}
.balance {
  font-size: 62rpx;
  margin: 25rpx 0;
  color: #5e7b4a;
}
.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: #1d301c66;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 35rpx;
}
.dialog {
  max-height: 85vh;
  width: 100%;
  background: #fcfef8;
  border-radius: 20rpx;
  padding: 45rpx 32rpx;
  box-sizing: border-box;
  position: relative;
}
.close {
  position: absolute;
  right: 25rpx;
  top: 15rpx;
  font-size: 42rpx;
  color: #9cae8d;
}
.dialog .title {
  font-size: 36rpx;
  padding-right: 30rpx;
}
.dialog .description {
  font-size: 24rpx;
  line-height: 2.3;
}
.refund-line {
  display: flex;
  gap: 15rpx;
  justify-content: space-between;
  font-size: 23rpx;
  margin: 20rpx 0;
  align-items: center;
}
.refund-line input {
  width: 80rpx;
  border: 1rpx solid #d5e0c9;
  padding: 10rpx;
  border-radius: 7rpx;
  height: 60rpx;
}
</style>
