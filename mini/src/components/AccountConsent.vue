<script setup lang="ts">
import { navigate } from "../utils/router";
withDefaults(
  defineProps<{ modelValue: boolean; purpose?: string; terms?: boolean }>(),
  {
    purpose: "我已阅读并同意",
    terms: true,
  },
);
const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
</script>
<template>
  <view class="account-consent" @tap="emit('update:modelValue', !modelValue)">
    <text>{{ modelValue ? "☑" : "□" }} {{ purpose }}</text>
    <template v-if="terms">
      <text class="link" @tap.stop="navigate('legal', { type: 'terms' })"
        >服务规则</text
      >
      <text>与</text>
    </template>
    <text class="link" @tap.stop="navigate('legal', { type: 'privacy' })"
      >隐私政策</text
    >
  </view>
</template>
