<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import { catalogApi } from "@/api/catalog";
import { useConfigStore } from "@/store/config";
import { useAuthStore } from "@/store/auth";
import ProductCard from "@/components/storefront/ProductCard.vue";
import PointsRulesDialog from "@/components/storefront/PointsRulesDialog.vue";
const props = withDefaults(defineProps<{ points?: boolean }>(), {
  points: false,
});
const page = computed(() => (props.points ? "points" : "catalog"));
const auth = useAuthStore(),
  config = useConfigStore();
const { categories } = storeToRefs(config);
const profile = computed(() => auth.memberProfile);
const products = ref<any[]>([]),
  loaded = ref(false),
  search = ref(""),
  category = ref(""),
  sort = ref("curated"),
  pointsRulesVisible = ref(false);
const visibleProducts = computed(() => {
  let rows = [...products.value];
  if (props.points) rows = rows.filter((x) => x.points_percent > 0);
  if (sort.value === "priceUp") rows.sort((a, b) => a.price - b.price);
  if (sort.value === "priceDown") rows.sort((a, b) => b.price - a.price);
  return rows;
});
async function filter() {
  try {
    products.value = await catalogApi.getProducts({
      category: category.value,
      search: search.value,
    });
  } finally {
    loaded.value = true;
  }
}
onMounted(() => filter().catch(() => {}));
</script>
<template>
  <section class="catalog-heading">
    <div class="eyebrow">
      {{ page === "points" ? "A LITTLE THANK YOU" : "THOUGHTFULLY SELECTED" }}
    </div>
    <h1>
      {{ page === "points" ? "心意，值得被回馈。" : "让喜欢，融入日常。" }}
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
    <button class="text-link" @click="pointsRulesVisible = true">
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
  <PointsRulesDialog v-model="pointsRulesVisible" />
</template>
