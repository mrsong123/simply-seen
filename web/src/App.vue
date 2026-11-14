<script setup lang="ts">
import { useUiStore } from "@/store/ui";
const ui = useUiStore();
</script>
<template>
  <div class="loading-line" :class="{ active: ui.pending > 0 }"></div>
  <RouterView /><Transition name="toast"
    ><div
      v-if="ui.toast"
      class="toast"
      :class="{ error: ui.error }"
      role="status"
    >
      {{ ui.toast }}
    </div></Transition
  >
</template>
