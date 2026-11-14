<script setup lang="ts">
import { onMounted } from "vue";
import ProductCard from "./ProductCard.vue";
import { useCatalog } from "../composables/useCatalog";
const props = defineProps<{ pointsOnly?: boolean }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
const {
  app,
  session,
  search,
  category,
  visible,
  run,
  reload,
  searchProducts,
  selectCategory,
} = useCatalog(() => !!props.pointsOnly);
onMounted(reload);
defineExpose({ reload });
</script>
<template>
  <view
    ><view v-if="!pointsOnly" class="hero"
      ><view class="eyebrow">THE ART OF EVERYDAY</view
      ><view class="hero-title">少一些，<br />更好一些。</view
      ><text class="hero-note">从一件心仪的好物，开始认真生活。</text
      ><image class="hero-image" src="/static/cup.png" mode="aspectFit" /><text
        class="hero-link"
        @tap="selectCategory('')"
        >发现日常好物 ↗</text
      ></view
    ><view v-else class="points-hero"
      ><text class="eyebrow">A LITTLE THANK YOU</text
      ><view class="title">心意，值得被回馈。</view
      ><view v-if="session.profile" class="point-balance"
        >{{ session.profile.points }} <text>可用积分</text></view
      ><text class="caption">商品级限额内1积分抵1元，不可提现或转赠</text></view
    ><view class="search"
      ><input
        v-model="search"
        placeholder="寻找一件好物"
        confirm-type="search"
        @confirm="run(searchProducts)"
      /><text @tap="run(searchProducts)">搜索</text></view
    ><scroll-view scroll-x class="categories"
      ><text :class="{ active: category === '' }" @tap="selectCategory('')"
        >全部好物</text
      ><text
        v-for="item in app.categories"
        :key="item.id"
        :class="{ active: category === String(item.id) }"
        @tap="selectCategory(String(item.id))"
        >{{ item.name }}</text
      ></scroll-view
    ><view class="section-title"
      ><view>日常里的，心动之选</view
      ><text>{{ visible.length }}件好物</text></view
    ><view class="products"
      ><ProductCard
        v-for="item in visible"
        :key="item.id"
        :product="item" /></view
    ><view v-if="!visible.length" class="empty">暂无符合条件的商品</view
    ><view class="bottom-note"
      >LESS, BUT BETTER.<text>让好物回归日常，让生活简单而丰盈。</text></view
    ></view
  >
</template>
