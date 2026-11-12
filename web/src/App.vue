<script setup lang="ts">
import { onMounted } from "vue";
import { api, state } from "./api";
onMounted(async () => {
  try {
    state.config = await api("/config");
  } catch {}
});
</script>
<template>
  <div class="loading-line" :class="{ active: state.pending > 0 }"></div>
  <RouterView /><Transition name="toast"
    ><div
      v-if="state.toast"
      class="toast"
      :class="{ error: state.error }"
      role="status"
    >
      {{ state.toast }}
    </div></Transition
  >
</template>
