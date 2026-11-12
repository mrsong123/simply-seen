<script setup lang="ts">
import { ref, computed, watch, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import Icon from "./Icon.vue";
import {
  api,
  state,
  session,
  logout,
  money,
  date,
  label,
  key,
  message,
} from "./api";
const logistics = ref<any[]>([]);
const route = useRoute(),
  router = useRouter();
const page = computed(() => route.path.split("/")[1] || "home");
const products = ref<any[]>([]),
  categories = ref<any[]>([]),
  cart = ref<any[]>([]),
  orders = ref<any[]>([]),
  addresses = ref<any[]>([]),
  ledger = ref<any[]>([]),
  withdrawals = ref<any[]>([]);
const product = ref<any>(null),
  order = ref<any>(null),
  profile = ref<any>(null),
  team = ref<any>({ level1: [], level2: [], commissions: [] }),
  rules = ref<any>({}),
  quote = ref<any>(null);
const search = ref(""),
  category = ref(""),
  sort = ref("curated"),
  sku = ref(0),
  qty = ref(1),
  points = ref(0),
  addressId = ref(0),
  remark = ref(""),
  busy = ref(false),
  loaded = ref(false),
  modal = ref(""),
  loginName = ref(""),
  inviteCode = ref(localStorage.getItem("sj-invite") || ""),
  nickname = ref(""),
  withdrawAmount = ref(""),
  accepted = ref(false),
  orderTab = ref("ALL"),
  mobileNav = ref(false),
  addressForm = ref<any>({
    name: "",
    phone: "",
    region: "",
    detail: "",
    is_default: true,
  }),
  refundForm = ref<any>({ reason: "", lines: [] });
let quoteTimer: any;
let quoteSequence = 0;
let checkoutKey = key();
let refundKey = key();
let withdrawKey = key();
const visibleProducts = computed(() => {
  let p = [...products.value];
  if (page.value === "points") p = p.filter((x) => x.points_percent > 0);
  if (sort.value === "priceUp") p.sort((a, b) => a.price - b.price);
  if (sort.value === "priceDown") p.sort((a, b) => b.price - a.price);
  return p;
});
const selectedSku = computed(() =>
  product.value?.skus.find((s: any) => s.id === sku.value),
);
const cartTotal = computed(() =>
  cart.value.reduce((s, i) => s + i.price * i.quantity, 0),
);
const displayedOrders = computed(() =>
  orderTab.value === "ALL"
    ? orders.value
    : orders.value.filter((x) => x.state === orderTab.value),
);
const cartCount = computed(() =>
  cart.value.reduce((s, i) => s + i.quantity, 0),
);
const pendingCommission = computed(() =>
  team.value.commissions
    .filter((x: any) => x.state === "PENDING")
    .reduce((s: number, i: any) => s + i.amount, 0),
);
async function action(fn: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  try {
    await fn();
  } catch {
  } finally {
    busy.value = false;
  }
}
function needLogin() {
  if (state.member) return true;
  modal.value = "login";
  return false;
}
async function refreshAccount() {
  if (!state.member) return;
  profile.value = await api("/me");
  nickname.value = profile.value.name;
  cart.value = await api("/cart");
}
async function load() {
  logistics.value = [];
  loaded.value = false;
  quote.value = null;
  try {
    categories.value = await api("/categories");
    rules.value = await api("/rules/public");
    if (["home", "catalog", "points"].includes(page.value))
      products.value = await api(
        "/products?category=" +
          category.value +
          "&search=" +
          encodeURIComponent(search.value),
      );
    if (page.value === "product") {
      product.value = await api("/products/" + route.path.split("/")[2]);
      sku.value = product.value.skus[0]?.id || 0;
      qty.value = 1;
    }
    if (state.member) {
      await refreshAccount();
      if (["checkout", "addresses"].includes(page.value)) {
        addresses.value = await api("/addresses");
        addressId.value =
          addresses.value.find((a) => a.is_default)?.id ||
          addresses.value[0]?.id ||
          0;
      }
      if (page.value === "orders") {
        if (route.path.split("/")[2])
          order.value = await api("/orders/" + route.path.split("/")[2]);
        else {
          order.value = null;
          orders.value = await api("/orders");
        }
      }
      if (["account", "points"].includes(page.value))
        ledger.value = await api("/me/ledger");
      if (page.value === "share") team.value = await api("/me/team");
      if (page.value === "withdraw")
        withdrawals.value = await api("/me/withdrawals");
      if (page.value === "checkout" && cart.value.length && addressId.value)
        await getQuote();
    }
  } catch {
  } finally {
    loaded.value = true;
  }
}
// Route params use a catch-all because store and admin are separately mounted shells.
watch(
  () => route.fullPath,
  () => {
    mobileNav.value = false;
    load();
  },
);
onMounted(() => {
  const code = route.query.invite?.toString();
  if (code) {
    inviteCode.value = code;
    localStorage.setItem("sj-invite", code);
  }
  load();
});
async function filter() {
  try {
    products.value = await api(
      "/products?category=" +
        category.value +
        "&search=" +
        encodeURIComponent(search.value),
    );
  } catch {}
}
async function login() {
  await action(async () => {
    const result = await api("/auth/dev", {
      method: "POST",
      body: { name: loginName.value, inviteCode: inviteCode.value },
    });
    session(result);
    modal.value = "";
    await load();
    message("欢迎来到素见");
  });
}
async function addCart(buy = false) {
  if (!needLogin()) return;
  await action(async () => {
    const current = cart.value.find((i) => i.sku_id === sku.value);
    await api("/cart/" + sku.value, {
      method: "PUT",
      body: { quantity: Math.min(99, qty.value + (current?.quantity || 0)) },
    });
    await refreshAccount();
    message("已加入购物袋");
    if (buy) router.push("/checkout");
  });
}
async function changeCart(item: any, delta: number) {
  await action(async () => {
    await api("/cart/" + item.sku_id, {
      method: "PUT",
      body: { quantity: Math.max(0, item.quantity + delta) },
    });
    await refreshAccount();
  });
}
async function getQuote() {
  const sequence = ++quoteSequence;
  quote.value = null;
  if (!addressId.value || !cart.value.length) return;
  try {
    const result = await api("/checkout/quote", {
      method: "POST",
      body: {
        items: cart.value.map((i) => ({
          skuId: i.sku_id,
          quantity: i.quantity,
        })),
        addressId: addressId.value,
        pointsRequested: points.value,
      },
    });
    if (sequence === quoteSequence) quote.value = result;
  } catch {}
}
watch([addressId, points], () => {
  if (page.value === "checkout") {
    quoteSequence++;
    quote.value = null;
    clearTimeout(quoteTimer);
    quoteTimer = setTimeout(getQuote, 250);
  }
});
async function checkout() {
  await action(async () => {
    if (!quote.value || !accepted.value) {
      message("请确认报价并阅读交易规则", true);
      return;
    }
    const result = await api("/orders", {
      method: "POST",
      key: checkoutKey,
      body: {
        quoteId: quote.value.quoteId,
        addressId: addressId.value,
        buyerRemark: remark.value,
      },
    });
    checkoutKey = key();
    await refreshAccount();
    router.push("/orders/" + result.id);
  });
}
async function pay() {
  await action(async () => {
    const prepay = await api("/orders/" + order.value.id + "/prepay", {
      method: "POST",
    });
    if (prepay.mode === "DEVELOPMENT") {
      modal.value = "testPayment";
      return;
    }
    message("请在微信小程序内完成支付；本页面可查询支付结果");
  });
}
async function testPay() {
  await action(async () => {
    await api("/dev/orders/" + order.value.id + "/pay", { method: "POST" });
    modal.value = "";
    await load();
    message("开发测试付款已记录，不涉及真实资金");
  });
}
async function queryLogistics() {
  await action(async () => {
    logistics.value = await api("/orders/" + order.value.id + "/logistics");
    if (!logistics.value.length) message("暂无物流轨迹，请稍后查询");
  });
}
async function orderAction(op: string) {
  await action(async () => {
    await api("/orders/" + order.value.id + "/" + op, {
      method: op === "payment-status" ? "GET" : "POST",
    });
    modal.value = "";
    await load();
    message("订单状态已更新");
  });
}
function openAddress(a?: any) {
  addressForm.value = a
    ? { ...a }
    : { name: "", phone: "", region: "", detail: "", is_default: true };
  modal.value = "address";
}
async function saveAddress() {
  await action(async () => {
    await api("/addresses", { method: "POST", body: addressForm.value });
    modal.value = "";
    await load();
  });
}
async function deleteAddress(id: number) {
  await action(async () => {
    await api("/addresses/" + id, { method: "DELETE" });
    await load();
  });
}
function openRefund() {
  refundForm.value = {
    reason: "",
    lines: order.value.items
      .filter((i: any) => i.quantity > i.refunded_quantity)
      .map((i: any) => ({
        orderItemId: i.id,
        name: i.name,
        quantity: i.quantity - i.refunded_quantity,
        max: i.quantity - i.refunded_quantity,
        selected: true,
      })),
  };
  refundKey = key();
  modal.value = "refund";
}
async function refund() {
  await action(async () => {
    await api("/refunds", {
      method: "POST",
      key: refundKey,
      body: {
        orderNo: order.value.id,
        reason: refundForm.value.reason,
        lines: refundForm.value.lines
          .filter((i: any) => i.selected)
          .map((i: any) => ({
            orderItemId: i.orderItemId,
            quantity: Number(i.quantity),
          })),
      },
    });
    modal.value = "";
    await load();
    message("售后申请已提交");
  });
}
async function promote() {
  await action(async () => {
    await api("/me/promoter", {
      method: "POST",
      body: { accepted: accepted.value },
    });
    await load();
    message("推广规则已确认");
  });
}
async function copyInvite() {
  try {
    await navigator.clipboard.writeText(
      (state.config.shareBase || location.origin) +
        "/?invite=" +
        profile.value.invite_code,
    );
    message("邀请链接已复制");
  } catch {
    message("邀请码：" + profile.value.invite_code);
  }
}
async function withdraw() {
  await action(async () => {
    const value = Number(withdrawAmount.value);
    if (
      !Number.isFinite(value) ||
      value <= 0 ||
      !/^\d+(\.\d{1,2})?$/.test(withdrawAmount.value)
    ) {
      message("请输入最多两位小数的金额", true);
      return;
    }
    await api("/me/withdrawals", {
      method: "POST",
      key: withdrawKey,
      body: { amountFen: Math.round(value * 100) },
    });
    withdrawKey = key();
    withdrawAmount.value = "";
    await load();
    message("提现申请已提交，等待财务复核");
  });
}
async function saveName() {
  await action(async () => {
    await api("/me", { method: "PUT", body: { name: nickname.value } });
    modal.value = "";
    await load();
    message("昵称已更新");
  });
}
const assets = [
  ["points", "可用积分", "Gift"],
  ["available", "可用佣金", "Wallet"],
  ["reserved", "提现冻结", "Clock"],
];
</script>
<template>
  <div class="store-shell">
    <div class="announcement">
      为生活做减法，为美好留余地。<span>精选好物 · 用心相伴</span>
    </div>
    <header class="store-header">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark">素</span
        ><span>素见好物<small>SIMPLY SEEN</small></span></RouterLink
      >
      <nav :class="{ open: mobileNav }">
        <RouterLink to="/catalog">全部好物</RouterLink
        ><RouterLink to="/points">积分礼遇</RouterLink
        ><RouterLink to="/share">分享美好</RouterLink
        ><RouterLink to="/legal/about">关于素见</RouterLink>
      </nav>
      <div class="header-actions">
        <button
          class="icon-button"
          aria-label="搜索商品"
          @click="router.push('/catalog')"
        >
          <Icon name="Search" /></button
        ><button
          class="icon-button"
          aria-label="我的账户"
          @click="state.member ? router.push('/account') : (modal = 'login')"
        >
          <Icon name="User" /></button
        ><RouterLink class="icon-button bag-link" to="/cart" aria-label="购物袋"
          ><Icon name="ShoppingBag" /><i v-if="cartCount">{{
            cartCount
          }}</i></RouterLink
        ><button
          class="icon-button mobile-menu"
          aria-label="打开导航"
          @click="mobileNav = !mobileNav"
        >
          <Icon name="Menu" />
        </button>
      </div>
    </header>
    <main>
      <template v-if="page === 'home'">
        <section class="hero">
          <div class="hero-copy">
            <div class="eyebrow"><span></span> THE ART OF EVERYDAY</div>
            <h1>少一些，<br />更好一些<span>。</span></h1>
            <p>让日常回归简单。<br />从一件心仪的好物，开始认真生活。</p>
            <RouterLink to="/catalog" class="button dark"
              >探索日常好物 <Icon name="ArrowRight" :size="18"
            /></RouterLink>
            <div class="hero-footnote">
              <span>01 — 06</span><span>精挑细选 · 恰到好处</span>
            </div>
          </div>
          <RouterLink to="/product/1" class="hero-art"
            ><div class="sun-circle"></div>
            <img src="/art/cup.svg" alt="雾白云朵随行杯" />
            <div class="hero-art-caption">
              <div>
                <small>给生活，一点温度</small><strong>云朵随行杯</strong>
              </div>
              <span class="circle-arrow"><Icon name="ArrowRight" /></span>
            </div>
            <span class="vertical-note"
              >MADE FOR A SLOWER LIFE</span
            ></RouterLink
          >
        </section>
        <section class="promise-strip">
          <div>
            <Icon name="Leaf" /><span
              >甄选材质<small>感受自然的质地</small></span
            >
          </div>
          <div>
            <Icon name="Package" /><span
              >用心履约<small>每件好物，妥帖送达</small></span
            >
          </div>
          <div>
            <Icon name="Shield" /><span
              >安心售后<small>让每一次选择更放心</small></span
            >
          </div>
          <div>
            <Icon name="Gift" /><span
              >积分礼遇<small>让喜欢，多一份惊喜</small></span
            >
          </div>
        </section>
        <section class="section">
          <div class="section-head">
            <div>
              <div class="eyebrow">CURATED FOR YOU</div>
              <h2>日常里的，心动之选</h2>
              <p>实用与美感之间，我们为你找到平衡。</p>
            </div>
            <RouterLink to="/catalog" class="text-link"
              >浏览全部 <Icon name="ArrowRight" :size="17"
            /></RouterLink>
          </div>
          <div class="product-grid">
            <RouterLink
              v-for="(p, i) in products.slice(0, 3)"
              :key="p.id"
              :to="'/product/' + p.id"
              class="product-card"
              ><div class="product-art" :style="{ background: p.color }">
                <span class="product-tag">{{
                  i === 0 ? "日常精选" : i === 1 ? "慢生活提案" : "质感之选"
                }}</span
                ><img :src="p.image" :alt="p.name" loading="lazy" /><span
                  class="quick-add"
                  ><Icon name="Plus"
                /></span>
              </div>
              <div class="product-info">
                <div>
                  <h3>{{ p.name }}</h3>
                  <p>{{ p.subtitle }}</p>
                </div>
                <strong>¥{{ money(p.price) }}</strong>
              </div>
              <div class="product-meta">
                最高 {{ p.points_percent }}% 积分抵扣
              </div></RouterLink
            >
          </div>
          <div v-if="loaded && !products.length" class="empty-state">
            <Icon name="Package" />
            <h3>好物正在准备中</h3>
            <p>商品上架后将在这里展示。</p>
          </div>
        </section>
        <section class="story-banner">
          <div class="story-illustration">
            <img src="/art/bowl.svg" alt="日光陶瓷碗" />
          </div>
          <div class="story-copy">
            <div class="eyebrow">LESS, BUT BETTER</div>
            <h2>好好生活，<br />从细微处开始。</h2>
            <p>
              我们相信，真正的好物无需喧哗。<br />它藏在清晨的一杯茶里，也在归家时的一盏暖意中。<br />素见，陪你发现平凡日常中的不凡。
            </p>
            <RouterLink to="/legal/about" class="text-link"
              >认识素见 <Icon name="ArrowRight" :size="18"
            /></RouterLink>
          </div>
        </section>
      </template>
      <template v-else-if="page === 'catalog' || page === 'points'">
        <section class="catalog-heading">
          <div class="eyebrow">
            {{
              page === "points" ? "A LITTLE THANK YOU" : "THOUGHTFULLY SELECTED"
            }}
          </div>
          <h1>
            {{
              page === "points" ? "心意，值得被回馈。" : "让喜欢，融入日常。"
            }}
          </h1>
          <p>
            {{
              page === "points"
                ? "积分是非现金消费权益，可在商品限额内抵扣，不能提现或转赠。"
                : "每一件好物，都经过认真选择。"
            }}
          </p>
        </section>
        <div v-if="page === 'points' && profile" class="points-banner">
          <div>
            <small>我的可用积分</small
            ><strong>{{ profile.points }}<span>积分</span></strong>
          </div>
          <button class="text-link" @click="modal = 'pointsRules'">
            积分规则 <Icon name="CircleHelp" :size="16" />
          </button>
        </div>
        <section class="section catalog-section">
          <div class="catalog-toolbar">
            <div class="category-pills">
              <button
                :class="{ active: category === '' }"
                @click="
                  category = '';
                  filter();
                "
              >
                全部</button
              ><button
                v-for="cat in categories"
                :key="cat.id"
                :class="{ active: category === String(cat.id) }"
                @click="
                  category = String(cat.id);
                  filter();
                "
              >
                {{ cat.name }}
              </button>
            </div>
            <div class="search-sort">
              <label class="search-field"
                ><Icon name="Search" :size="17" /><input
                  v-model="search"
                  aria-label="搜索好物"
                  placeholder="寻找一件好物"
                  @keyup.enter="filter()" /><button
                  v-if="search"
                  class="icon-button"
                  @click="
                    search = '';
                    filter();
                  "
                >
                  <Icon name="X" :size="15" /></button></label
              ><select v-model="sort" aria-label="商品排序">
                <option value="curated">推荐排序</option>
                <option value="priceUp">价格从低到高</option>
                <option value="priceDown">价格从高到低</option>
              </select>
            </div>
          </div>
          <div class="result-count">{{ visibleProducts.length }} 件好物</div>
          <div class="product-grid">
            <RouterLink
              v-for="p in visibleProducts"
              :key="p.id"
              :to="'/product/' + p.id"
              class="product-card"
              ><div class="product-art" :style="{ background: p.color }">
                <span v-if="p.featured" class="product-tag">素见精选</span
                ><img :src="p.image" :alt="p.name" loading="lazy" /><span
                  class="quick-add"
                  ><Icon name="Plus"
                /></span>
              </div>
              <div class="product-info">
                <div>
                  <h3>{{ p.name }}</h3>
                  <p>{{ p.subtitle }}</p>
                </div>
                <strong>¥{{ money(p.price) }}</strong>
              </div>
              <div class="product-meta">
                最高 {{ p.points_percent }}% 积分抵扣
              </div></RouterLink
            >
          </div>
          <div v-if="loaded && !visibleProducts.length" class="empty-state">
            <Icon name="Search" />
            <h3>暂未找到合适的好物</h3>
            <button
              class="button"
              @click="
                search = '';
                category = '';
                filter();
              "
            >
              查看全部商品
            </button>
          </div>
        </section>
      </template>
      <template v-else-if="page === 'product' && product"
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
                <button
                  aria-label="减少数量"
                  :disabled="qty <= 1"
                  @click="qty--"
                >
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
            ><button class="button full" @click="addCart(true)">
              立即选购
            </button>
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
      <template
        v-else-if="
          [
            'cart',
            'checkout',
            'orders',
            'account',
            'addresses',
            'share',
            'withdraw',
          ].includes(page)
        "
      >
        <section class="account-heading">
          <div class="eyebrow">YOUR EVERYDAY, CONSIDERED</div>
          <h1>
            {{
              (
                {
                  cart: "我的购物袋",
                  checkout: "确认这份美好",
                  orders: "我的订单",
                  account: "你好，" + (profile?.name || "素见朋友"),
                  addresses: "收货地址",
                  share: "好物，值得分享。",
                  withdraw: "佣金提现",
                } as any
              )[page]
            }}
          </h1>
        </section>
        <div v-if="!state.member" class="empty-state">
          <Icon name="User" :size="40" />
          <h3>登录，让美好与你相遇</h3>
          <p>查看订单、管理积分，收藏属于你的日常。</p>
          <button class="button dark" @click="modal = 'login'">登录素见</button>
        </div>
        <template v-else>
          <section v-if="page === 'cart'" class="section bag-layout">
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
          <section v-if="page === 'checkout'" class="section bag-layout">
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
                <article
                  v-for="i in cart"
                  :key="i.sku_id"
                  class="checkout-item"
                >
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
                    >可用
                    {{ profile?.points }} 点，每点抵1元，受商品上限限制</span
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
                  <span>商品金额</span
                  ><span>¥{{ money(quote.itemAmountFen) }}</span>
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
                :disabled="busy || !quote || !accepted || !cart.length"
                @click="checkout"
              >
                提交订单 <Icon name="ArrowRight" :size="17" />
              </button>
              <p class="caption">价格和库存以下单时的服务端校验为准</p>
            </aside>
          </section>
          <section v-if="page === 'orders'" class="section narrow">
            <template v-if="!order"
              ><div class="category-pills order-tabs">
                <button
                  v-for="t in [
                    'ALL',
                    'CREATED',
                    'PAID',
                    'SHIPPED',
                    'COMPLETED',
                    'REFUNDED',
                  ]"
                  :key="t"
                  :class="{ active: orderTab === t }"
                  @click="orderTab = t"
                >
                  {{ t === "ALL" ? "全部订单" : label(t) }}
                </button>
              </div>
              <RouterLink
                v-for="o in displayedOrders"
                :key="o.id"
                :to="'/orders/' + o.id"
                class="order-card"
                ><div class="order-card-head">
                  <span
                    >{{ date(o.created_at) }} <small>{{ o.id }}</small></span
                  ><span class="badge">{{ label(o.state) }}</span>
                </div>
                <div v-for="i in o.items" :key="i.id" class="checkout-item">
                  <img :src="i.image" :alt="i.name" />
                  <div>
                    <strong>{{ i.name }}</strong>
                    <p>× {{ i.quantity }}</p>
                  </div>
                  <span>¥{{ money(i.cash_amount) }}</span>
                </div>
                <div class="order-card-foot">
                  <span
                    >实付 <strong>¥{{ money(o.cash_due) }}</strong>
                    <small v-if="o.cash_refunded"
                      >已退 ¥{{ money(o.cash_refunded) }}</small
                    ></span
                  ><span class="text-link"
                    >查看详情 <Icon name="ArrowRight" :size="16"
                  /></span></div
              ></RouterLink>
              <div v-if="loaded && !displayedOrders.length" class="empty-state">
                <Icon name="Package" :size="40" />
                <h3>这里还没有订单</h3>
                <RouterLink to="/catalog" class="text-link"
                  >去发现好物 <Icon name="ArrowRight"
                /></RouterLink></div></template
            ><template v-else
              ><RouterLink to="/orders" class="back-link"
                ><Icon name="ArrowLeft" :size="16" /> 返回订单列表</RouterLink
              >
              <div class="order-status panel">
                <Icon
                  :name="
                    order.state === 'COMPLETED' ? 'CheckCircle2' : 'Package'
                  "
                  :size="36"
                />
                <div>
                  <h2>{{ label(order.state) }}</h2>
                  <p>订单号 {{ order.id }}</p>
                  <p v-if="order.state === 'CREATED'">
                    请于 {{ date(order.expires_at) }} 前完成支付
                  </p>
                </div>
              </div>
              <div class="panel" v-if="order.carrier">
                <h3>物流信息</h3>
                <p>{{ order.carrier }} · {{ order.tracking_no }}</p>
                <button
                  v-if="state.config.logisticsEnabled"
                  class="text-link"
                  @click="queryLogistics"
                >
                  查询物流轨迹
                </button>
                <div
                  v-for="track in logistics"
                  :key="track.time + track.context"
                  class="history-row"
                >
                  <small>{{ track.time }}</small
                  ><span>{{ track.context }}</span>
                </div>
              </div>
              <div class="panel">
                <h3>收货信息</h3>
                <p>{{ order.address.name }} {{ order.address.phone }}</p>
                <p>{{ order.address.region }} {{ order.address.detail }}</p>
              </div>
              <div class="panel">
                <div v-for="i in order.items" :key="i.id" class="checkout-item">
                  <img :src="i.image" :alt="i.name" />
                  <div>
                    <strong>{{ i.name }}</strong>
                    <p>
                      数量 {{ i.quantity }}
                      <span v-if="i.refunded_quantity">
                        / 已退 {{ i.refunded_quantity }}</span
                      >
                    </p>
                  </div>
                  <span
                    >¥{{ money(i.cash_amount) }} +
                    {{ i.points_amount }}积分</span
                  >
                </div>
                <div class="divider"></div>
                <div class="summary-line">
                  <span>运费</span><span>¥{{ money(order.freight) }}</span>
                </div>
                <div class="summary-line">
                  <span>实付现金</span
                  ><strong>¥{{ money(order.cash_due) }}</strong>
                </div>
                <div v-if="order.cash_refunded" class="summary-line">
                  <span>已退款</span
                  ><strong>¥{{ money(order.cash_refunded) }}</strong>
                </div>
                <div class="button-row">
                  <template v-if="order.state === 'CREATED'"
                    ><button
                      class="button"
                      :disabled="busy"
                      @click="modal = 'cancelOrder'"
                    >
                      取消订单</button
                    ><button
                      class="button"
                      :disabled="busy"
                      @click="orderAction('payment-status')"
                    >
                      查询支付结果</button
                    ><button class="button dark" :disabled="busy" @click="pay">
                      前往支付
                    </button></template
                  ><button
                    v-if="order.state === 'SHIPPED'"
                    class="button dark"
                    @click="modal = 'receiveOrder'"
                  >
                    确认收货</button
                  ><button
                    v-if="
                      ['PAID', 'SHIPPED', 'COMPLETED'].includes(order.state)
                    "
                    class="button"
                    @click="openRefund"
                  >
                    申请售后
                  </button>
                </div>
              </div>
              <div v-if="order.refunds.length" class="panel">
                <h3>售后记录</h3>
                <div v-for="r in order.refunds" :key="r.id" class="history-row">
                  <div>
                    <strong>{{ label(r.state) }}</strong>
                    <p>{{ r.reason }} · {{ date(r.created_at) }}</p>
                  </div>
                  <span>¥{{ money(r.amount) }}</span>
                </div>
              </div></template
            >
          </section>
          <section v-if="page === 'account' && profile" class="section narrow">
            <div class="member-card">
              <div class="avatar">{{ profile.name.slice(0, 1) }}</div>
              <div>
                <small>WELCOME BACK</small>
                <h2>{{ profile.name }}</h2>
                <span>每一份喜欢，都值得认真对待。</span>
              </div>
              <button class="text-link" @click="modal = 'name'">
                修改昵称
              </button>
            </div>
            <div class="asset-grid">
              <RouterLink
                v-for="a in assets"
                :key="a[0]"
                :to="a[0] === 'points' ? '/points' : '/withdraw'"
                class="asset-card"
                ><Icon :name="a[2]" /><span>{{ a[1] }}</span
                ><strong>{{
                  a[0] === "points" ? profile[a[0]] : money(profile[a[0]])
                }}</strong></RouterLink
              >
            </div>
            <div class="account-links">
              <RouterLink
                v-for="link in [
                  ['orders', '我的订单', 'Package'],
                  ['addresses', '地址管理', 'MapPin'],
                  ['share', '分享与佣金', 'Share2'],
                  ['withdraw', '佣金提现', 'Wallet'],
                ]"
                :key="link[0]"
                :to="'/' + link[0]"
                ><Icon :name="link[2]" /><span>{{ link[1] }}</span
                ><Icon name="ChevronRight" :size="16"
              /></RouterLink>
            </div>
            <div class="panel">
              <h3>最近资产明细</h3>
              <div
                v-for="l in ledger.slice(0, 8)"
                :key="l.id"
                class="history-row"
              >
                <div>
                  <strong>{{ l.note }}</strong>
                  <p>{{ label(l.asset) }} · {{ date(l.created_at) }}</p>
                </div>
                <span :class="{ green: l.amount > 0 }"
                  >{{ l.amount > 0 ? "+" : ""
                  }}{{
                    l.asset.startsWith("points") ? l.amount : money(l.amount)
                  }}</span
                >
              </div>
              <p v-if="!ledger.length" class="muted">暂无资产变动记录。</p>
            </div>
            <button
              class="text-link muted"
              @click="
                action(async () => {
                  await logout();
                  profile = null;
                  router.push('/');
                })
              "
            >
              <Icon name="LogOut" :size="16" /> 退出登录
            </button>
          </section>
          <section v-if="page === 'addresses'" class="section narrow">
            <div class="panel-title">
              <p class="muted">每一个地址，都是生活的坐标。</p>
              <button class="button dark" @click="openAddress()">
                <Icon name="Plus" :size="17" /> 新增地址
              </button>
            </div>
            <div v-for="a in addresses" :key="a.id" class="panel address-card">
              <div>
                <h3>
                  {{ a.name }} <span class="muted">{{ a.phone }}</span>
                  <span v-if="a.is_default" class="badge">默认</span>
                </h3>
                <p>{{ a.region }} {{ a.detail }}</p>
              </div>
              <div class="button-row">
                <button class="text-link" @click="openAddress(a)">编辑</button
                ><button
                  class="text-link muted"
                  @click="
                    addressForm = a;
                    modal = 'deleteAddress';
                  "
                >
                  删除
                </button>
              </div>
            </div>
            <div v-if="!addresses.length" class="empty-state">
              <Icon name="MapPin" :size="40" />
              <h3>还没有收货地址</h3>
              <p>添加地址，让美好准确抵达。</p>
            </div>
          </section>
          <section v-if="page === 'share' && profile" class="section narrow">
            <div class="share-card">
              <div class="eyebrow">GOOD THINGS ARE BETTER SHARED</div>
              <h2>分享喜欢，收获心意。</h2>
              <p>
                推荐真实好物，在有效成交后获得佣金。<br />不收取入门费用，不因邀请注册获得现金奖励。
              </p>
              <template v-if="profile.promoter"
                ><div class="invite-code">{{ profile.invite_code }}</div>
                <button class="button dark" @click="copyInvite">
                  <Icon name="Copy" :size="16" /> 复制邀请链接
                </button></template
              ><template v-else
                ><label class="checkbox-label"
                  ><input type="checkbox" v-model="accepted" /><span
                    >我已阅读<RouterLink to="/legal/distribution"
                      >《推广服务规则》</RouterLink
                    ></span
                  ></label
                ><button
                  class="button dark"
                  :disabled="!accepted || busy"
                  @click="promote"
                >
                  成为好物分享者
                </button></template
              >
            </div>
            <div class="asset-grid">
              <div class="asset-card">
                <span>直接邀请</span><strong>{{ team.level1.length }}</strong
                ><small>位朋友</small>
              </div>
              <div class="asset-card">
                <span>二级归因</span><strong>{{ team.level2.length }}</strong
                ><small>位朋友</small>
              </div>
              <div class="asset-card">
                <span>待结算佣金</span
                ><strong>¥{{ money(pendingCommission) }}</strong
                ><small>以有效成交及售后结果为准</small>
              </div>
            </div>
            <div class="panel">
              <h3>成交佣金明细</h3>
              <div
                v-for="t in team.commissions"
                :key="t.id"
                class="history-row"
              >
                <div>
                  <strong
                    >{{ t.level_no === 1 ? "一级" : "二级" }}成交佣金
                    <span class="badge">{{ label(t.state) }}</span></strong
                  >
                  <p>{{ t.order_id }} · {{ date(t.created_at) }}</p>
                </div>
                <span>¥{{ money(t.amount) }}</span>
              </div>
              <p v-if="!team.commissions.length" class="muted">
                尚无有效成交佣金。
              </p>
            </div>
            <div class="panel">
              <h3>我的直接邀请</h3>
              <div v-for="m in team.level1" :key="m.id" class="history-row">
                <span>{{ m.name }}</span
                ><small>{{ date(m.created_at) }}</small>
              </div>
              <p v-if="!team.level1.length" class="muted">
                用你的专属邀请链接，分享给真正喜欢好物的朋友。
              </p>
            </div>
          </section>
          <section
            v-if="page === 'withdraw' && profile"
            class="section bag-layout"
          >
            <div class="panel">
              <div class="eyebrow">YOUR EARNINGS</div>
              <h2 class="balance">¥{{ money(profile.available) }}</h2>
              <p class="muted">
                可申请提现佣金 · 冻结 ¥{{ money(profile.reserved) }}
              </p>
              <p v-if="profile.debt" class="warning">
                待追偿 ¥{{ money(profile.debt) }}，请先联系客服核对。
              </p>
              <label class="input-label"
                >申请金额（元）<input
                  v-model="withdrawAmount"
                  inputmode="decimal"
                  placeholder="请输入提现金额"
              /></label>
              <p class="caption left">
                提现需经财务审核及另一名管理员执行；税额按审核后的适用税务依据确定，渠道成功前不会标记为到账。
              </p>
              <button
                class="button dark"
                :disabled="busy || !withdrawAmount"
                @click="withdraw"
              >
                提交提现申请 <Icon name="ArrowRight" :size="17" />
              </button>
            </div>
            <div class="panel">
              <h3>提现记录</h3>
              <div
                v-for="r in withdrawals"
                :key="r.id"
                class="withdraw-history"
              >
                <div class="summary-line">
                  <strong>¥{{ money(r.gross) }}</strong
                  ><span class="badge">{{ label(r.state) }}</span>
                </div>
                <p>税款 ¥{{ money(r.tax) }} · 税后 ¥{{ money(r.net) }}</p>
                <small>{{ date(r.created_at) }}</small>
                <p v-if="r.reason">{{ r.reason }}</p>
                <p v-if="r.state === 'CONFIRMING'" class="warning">
                  请在微信小程序内确认收款。
                </p>
              </div>
              <p v-if="!withdrawals.length" class="muted">暂无提现记录。</p>
            </div>
          </section>
        </template></template
      >
      <template v-else-if="page === 'legal'"
        ><section class="legal-page">
          <div class="eyebrow">SIMPLY SEEN · WITH CARE</div>
          <template v-if="route.path.endsWith('about')"
            ><h1>素见，生活本来的样子。</h1>
            <p class="lead">少一些纷扰，多一些用心。</p>
            <p>
              素见好物关注真实的日常需要，选择兼具实用与美感的器物。我们相信，好的设计应当自然融入生活，长期陪伴，而非短暂吸引。
            </p>
            <h2>我们的选择</h2>
            <p>
              材质、手感、耐用与合理价格，是我们选择商品的共同依据。每一件商品的规格、价格、库存与售后信息，以商品详情及结算页为准。
            </p>
            <h2>联系与支持</h2>
            <p>
              {{
                state.config.support
              }}。订单售后请在“我的订单”中提交，我们会保留完整的处理记录。
            </p></template
          ><template v-else-if="route.path.endsWith('distribution')"
            ><h1>好物分享者规则</h1>
            <p class="lead">版本 DIST-1.0 · 仅依据真实商品成交计佣</p>
            <h2>归因与资格</h2>
            <p>
              会员自愿加入，无入门费、无强制购买或囤货要求。邀请归属在首次注册时确定且不可自行变更，最多计算两级，自邀、循环归因和虚假成交不产生佣金。
            </p>
            <h2>佣金与预算</h2>
            <p>
              当前一级基准 {{ rules.level1Bps / 100 }}%，二级基准
              {{
                rules.level2Bps / 100
              }}%；实际金额受商品佣金比例、有效现金实收、可分配毛利与活动总预算约束，不包含运费和积分抵扣部分。规则以订单创建时的版本为准，不承诺收益。
            </p>
            <h2>结算与退款</h2>
            <p>
              确认收货后经过
              {{ rules.settlementDays }}
              天售后观察期，且不存在进行中的售后，佣金才可结算。部分退款按来源订单有效现金比例重算。已出款金额不足冲回时生成追偿记录，新佣金优先抵偿；不将钱包可用余额扣成负数。
            </p>
            <h2>提现与税务</h2>
            <p>
              提现需财务审核、双人复核和渠道确认。税务处理依据实际适用的收入性质及法定义务执行。推广者不得作保本、固定收益或消费解锁收益承诺。
            </p></template
          ><template v-else-if="route.path.endsWith('privacy')"
            ><h1>隐私与个人信息保护</h1>
            <p>
              我们仅在提供服务所必需的范围内处理信息：微信身份标识用于登录与订单归属；姓名、手机和地址用于履约；交易及账本用于售后、对账与法定义务。
            </p>
            <h2>使用与共享</h2>
            <p>
              收货信息仅在履约需要时提供给对应物流服务方；支付与转账通过微信商户服务处理。我们不向推广者提供被邀请用户的联系方式、地址或微信身份标识。
            </p>
            <h2>您的权利</h2>
            <p>
              您可以在地址页修改或删除地址，在账户页修改昵称。个人信息查询、复制、撤回授权及注销申请，请联系
              {{
                state.config.privacy
              }}。未完成交易及依法应留存的财务记录，将按实际法律义务处理。
            </p>
            <h2>安全保护</h2>
            <p>
              接口采用身份校验和权限隔离；管理操作留痕，凭据仅由服务端使用。请勿向任何人提供登录凭证或支付密码。
            </p></template
          ><template v-else
            ><h1>交易与售后规则</h1>
            <h2>下单与支付</h2>
            <p>
              商品价格、运费、积分上限由服务端报价，提交订单时再次校验。未支付订单有明确有效期，过期后确认渠道未支付并关闭订单，释放库存与积分。支付以渠道确认结果为准。
            </p>
            <h2>积分权益</h2>
            <p>
              每满100元合格商品现金消费赠
              {{ rules.pointsPer100Yuan }}
              积分，按每笔订单计算，运费、退款和积分抵扣部分不再计赠。1积分可在参与商品的限额内抵1元，积分不可提现、转让或兑换现金。规则变更不追溯修改历史订单。
            </p>
            <h2>发货与售后</h2>
            <p>
              发货后可查看物流公司及运单号。支持依法适用的七日无理由退货，商品质量问题按法定责任处理。订单页可按商品数量申请部分或全额退款，财务审核后原路退款，最终以渠道结果为准。
            </p>
            <h2>异常与争议</h2>
            <p>
              重复提交不会重复下单或扣款。渠道结果未确定时显示“确认中”，请使用查询功能。争议请通过
              {{ state.config.support }}
              联系，并提供订单号；我们会保留可追溯记录。
            </p></template
          >
        </section></template
      >
      <div v-else-if="loaded" class="empty-state">
        <h2>这页好物暂未找到</h2>
        <RouterLink to="/catalog" class="button dark">返回全部好物</RouterLink>
      </div>
    </main>
    <footer class="store-footer">
      <div class="footer-top">
        <RouterLink to="/" class="brand"
          ><span class="brand-mark">素</span
          ><span>素见好物<small>SIMPLY SEEN</small></span></RouterLink
        >
        <p>让好物回归日常，让生活简单而丰盈。</p>
        <span>LESS, BUT BETTER.</span>
      </div>
      <div class="footer-bottom">
        <small>© {{ new Date().getFullYear() }} 素见好物</small>
        <div>
          <RouterLink to="/legal/privacy">隐私政策</RouterLink
          ><RouterLink to="/legal/terms">交易与售后</RouterLink
          ><RouterLink to="/legal/distribution">推广规则</RouterLink
          ><RouterLink to="/admin">管理后台</RouterLink>
        </div>
      </div>
    </footer>
    <Teleport to="body"
      ><div v-if="modal" class="modal-backdrop" @click.self="modal = ''">
        <section class="modal" role="dialog" aria-modal="true">
          <button
            class="modal-close icon-button"
            aria-label="关闭弹窗"
            @click="modal = ''"
          >
            <Icon name="X" /></button
          ><template v-if="modal === 'login'"
            ><span class="brand-mark">素</span>
            <h2>欢迎来到素见</h2>
            <p class="muted">登录，开启你的质感日常。</p>
            <form v-if="state.config.devMode" @submit.prevent="login">
              <p class="notice">当前为开发环境，测试登录不代表微信实名身份。</p>
              <label class="input-label"
                >测试身份名称<input
                  v-model="loginName"
                  required
                  minlength="2"
                  maxlength="30"
                  autocomplete="nickname" /></label
              ><label class="input-label"
                >邀请码（首次注册可选）<input
                  v-model="inviteCode"
                  maxlength="24" /></label
              ><button class="button dark full" :disabled="busy">
                登录开发账户 <Icon name="ArrowRight" :size="17" />
              </button>
            </form>
            <template v-else
              ><p>请在微信小程序中授权登录，网页支持公开商品浏览。</p>
              <p class="caption">微信内搜索「素见好物」小程序</p></template
            >
            <p class="caption">
              登录即表示同意
              <RouterLink to="/legal/terms" @click="modal = ''"
                >服务规则</RouterLink
              >
              与
              <RouterLink to="/legal/privacy" @click="modal = ''"
                >隐私政策</RouterLink
              >
            </p></template
          >
          <template v-if="modal === 'address'"
            ><h2>{{ addressForm.id ? "编辑地址" : "添加收货地址" }}</h2>
            <form @submit.prevent="saveAddress">
              <label class="input-label"
                >收货人<input
                  v-model="addressForm.name"
                  required
                  maxlength="60"
                  autocomplete="name" /></label
              ><label class="input-label"
                >手机号码<input
                  v-model="addressForm.phone"
                  required
                  pattern="1[3-9][0-9]{9}"
                  inputmode="tel"
                  autocomplete="tel" /></label
              ><label class="input-label"
                >省 / 市 / 区<input
                  v-model="addressForm.region"
                  required
                  maxlength="160"
                  placeholder="例如：浙江省 杭州市 西湖区" /></label
              ><label class="input-label"
                >详细地址<textarea
                  v-model="addressForm.detail"
                  required
                  maxlength="240"
                  autocomplete="street-address"
                ></textarea></label
              ><label class="checkbox-label"
                ><input v-model="addressForm.is_default" type="checkbox" />
                设为默认地址</label
              ><button class="button dark full" :disabled="busy">
                保存地址
              </button>
            </form></template
          >
          <template v-if="modal === 'deleteAddress'"
            ><h2>删除这个地址？</h2>
            <p>已创建订单的收货快照会保留。</p>
            <button
              class="button dark full"
              :disabled="busy"
              @click="
                deleteAddress(addressForm.id);
                modal = '';
              "
            >
              确认删除
            </button></template
          >
          <template v-if="modal === 'cancelOrder' || modal === 'receiveOrder'"
            ><h2>
              {{
                modal === "cancelOrder" ? "取消这笔订单？" : "确认已收到商品？"
              }}
            </h2>
            <p>
              {{
                modal === "cancelOrder"
                  ? "渠道确认未支付后，库存及积分会解除预占。"
                  : "确认后开始计算售后观察期，您的法定售后权利不受影响。"
              }}
            </p>
            <button
              class="button dark full"
              :disabled="busy"
              @click="
                orderAction(modal === 'cancelOrder' ? 'cancel' : 'receive')
              "
            >
              确认操作
            </button></template
          >
          <template v-if="modal === 'testPayment'"
            ><h2>开发测试付款</h2>
            <p class="notice">此操作仅在开发环境可用，不会产生真实微信扣款。</p>
            <div class="balance">¥{{ money(order.cash_due) }}</div>
            <button class="button dark full" :disabled="busy" @click="testPay">
              确认测试支付
            </button></template
          >
          <template v-if="modal === 'refund'"
            ><h2>申请售后退款</h2>
            <form @submit.prevent="refund">
              <div
                v-for="i in refundForm.lines"
                :key="i.orderItemId"
                class="refund-line"
              >
                <label class="checkbox-label"
                  ><input v-model="i.selected" type="checkbox" />{{
                    i.name
                  }}</label
                ><input
                  v-model.number="i.quantity"
                  type="number"
                  min="1"
                  :max="i.max"
                  :disabled="!i.selected"
                />
              </div>
              <label class="input-label"
                >退款原因<textarea
                  v-model="refundForm.reason"
                  required
                  minlength="2"
                  maxlength="240"
                ></textarea>
              </label>
              <p class="caption left">
                退款按原始订单行现金与积分分摊计算；提交后需审核，渠道成功后才会入账。
              </p>
              <button class="button dark full" :disabled="busy">
                提交申请
              </button>
            </form></template
          >
          <template v-if="modal === 'name'"
            ><h2>怎么称呼你？</h2>
            <form @submit.prevent="saveName">
              <label class="input-label"
                >昵称<input
                  v-model="nickname"
                  minlength="2"
                  maxlength="40"
                  required /></label
              ><button class="button dark full" :disabled="busy">
                保存昵称
              </button>
            </form></template
          >
          <template v-if="modal === 'pointsRules'"
            ><h2>积分，把心意留住</h2>
            <p>
              每满100元合格商品现金消费赠
              {{ rules.pointsPer100Yuan }}
              积分，按单计算。运费与积分抵扣部分不再赠送积分。
            </p>
            <p>
              1积分在参与商品限额内抵1元，不能提现或转让。未付订单预占积分，取消后原路释放；退款按订单快照退回及回收积分。
            </p>
            <RouterLink to="/legal/terms" class="text-link" @click="modal = ''"
              >查看完整交易规则
              <Icon name="ArrowRight" :size="16" /></RouterLink
          ></template>
        </section></div
    ></Teleport>
  </div>
</template>
