<script setup lang="ts">
withDefaults(defineProps<{ title: string; eyebrow?: string }>(), {
  eyebrow: "YOUR EVERYDAY, CONSIDERED",
});
</script>
<template>
  <section class="account-heading">
    <div class="eyebrow">{{ eyebrow }}</div>
    <h1>{{ title }}</h1>
  </section>
</template>
