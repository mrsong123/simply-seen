<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
</script>

<template>
  <section class="story-banner" aria-labelledby="story-heading">
    <div class="story-illustration">
      <img src="/art/bowl.svg" alt="日光陶瓷碗" />
    </div>
    <div class="story-copy">
      <div class="eyebrow">LESS, BUT BETTER</div>
      <h2 id="story-heading">好好生活，<br />从细微处开始。</h2>
      <p>
        我们相信，真正的好物无需喧哗。<br />它藏在清晨的一杯茶里，也在归家时的一盏暖意中。<br />素见，陪你发现平凡日常中的不凡。
      </p>
      <RouterLink to="/legal/about" class="text-link">
        认识素见 <Icon name="ArrowRight" :size="18" />
      </RouterLink>
    </div>
  </section>
</template>
