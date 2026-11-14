<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { onMounted, ref } from "vue";
import { catalogApi } from "@/api/catalog";
import ProductCard from "@/components/storefront/ProductCard.vue";
import StoryBanner from "@/components/storefront/StoryBanner.vue";
import type { ProductCard as Product } from "@/types/models";

const products = ref<Product[]>([]);
const loaded = ref(false);
const loadError = ref(false);

async function loadProducts() {
  loaded.value = false;
  loadError.value = false;
  try {
    products.value = await catalogApi.getProducts();
  } catch {
    loadError.value = true;
  } finally {
    loaded.value = true;
  }
}

onMounted(loadProducts);
</script>
<template>
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
    <RouterLink
      :to="products[0] ? '/product/' + products[0].id : '/catalog'"
      class="hero-art"
      ><div class="sun-circle"></div>
      <img src="/art/cup.svg" alt="雾白云朵随行杯" />
      <div class="hero-art-caption">
        <div><small>给生活，一点温度</small><strong>云朵随行杯</strong></div>
        <span class="circle-arrow"><Icon name="ArrowRight" /></span>
      </div>
      <span class="vertical-note">MADE FOR A SLOWER LIFE</span></RouterLink
    >
  </section>
  <section class="promise-strip">
    <div>
      <Icon name="Leaf" /><span>甄选材质<small>感受自然的质地</small></span>
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
      <Icon name="Gift" /><span>积分礼遇<small>让喜欢，多一份惊喜</small></span>
    </div>
  </section>
  <section class="section" :aria-busy="!loaded">
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
    <div v-if="!loaded" class="empty-state" role="status">
      <Icon name="Clock" />
      <h3>正在为你挑选日常好物</h3>
      <p>商品加载中，请稍候。</p>
    </div>
    <div v-else-if="loadError" class="empty-state" role="alert">
      <Icon name="RefreshCw" />
      <h3>暂时无法加载好物</h3>
      <p>请检查网络连接后再试。</p>
      <button class="button outline" @click="loadProducts">重新加载</button>
    </div>
    <div v-else-if="products.length" class="product-grid">
      <ProductCard
        v-for="(p, i) in products.slice(0, 3)"
        :key="p.id"
        :product="p"
        :tag="['日常精选', '慢生活提案', '质感之选'][i]"
      />
    </div>
    <div v-else class="empty-state" role="status">
      <Icon name="Package" />
      <h3>好物正在准备中</h3>
      <p>商品上架后将在这里展示。</p>
    </div>
  </section>
  <StoryBanner />
</template>
