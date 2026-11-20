<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import QRCode from "qrcode";
import BaseModal from "@/components/common/BaseModal.vue";
import Icon from "@/components/common/Icon.vue";
import { money } from "@/utils/format";
import { paymentRemaining } from "@/utils/payment";
import type { PaymentProgress, WebPayment } from "@/types/payment";
const props = defineProps<{
  payment: WebPayment;
  amount: number;
  progress: PaymentProgress;
  error: string;
  checking: boolean;
}>();
const emit = defineEmits<{ close: []; query: [] }>();
const qr = ref(""), qrError = ref(""), now = ref(Date.now());
const clock = setInterval(() => (now.value = Date.now()), 1000);
onBeforeUnmount(() => clearInterval(clock));
const expired = computed(() => now.value >= props.payment.expiresAt || props.progress === "EXPIRED");
const remaining = computed(() => paymentRemaining(props.payment.expiresAt, now.value));
watch(() => props.payment, async (value) => {
  qr.value = "";
  qrError.value = "";
  if (value.mode !== "NATIVE") return;
  try {
    const image = await QRCode.toDataURL(value.codeUrl, {
      width: 280, margin: 2, errorCorrectionLevel: "M", color: { dark: "#183d2fff", light: "#ffffffff" },
    });
    if (props.payment === value) qr.value = image;
  } catch {
    qrError.value = "付款码生成失败，请关闭后重新打开支付";
  }
}, { immediate: true });
</script>
<template>
  <BaseModal :model-value="true" @update:model-value="emit('close')">
    <div class="web-payment">
      <Icon name="Shield" :size="30" />
      <h2>{{ payment.mode === 'NATIVE' ? '微信扫码支付' : '微信支付' }}</h2>
      <div class="balance">¥{{ money(amount) }}</div>
      <p class="caption">订单 {{ payment.orderNo }}</p>
      <template v-if="!expired">
        <template v-if="payment.mode === 'NATIVE'">
          <img v-if="qr" class="payment-qr" :src="qr" alt="微信付款二维码" />
          <p v-else role="status">{{ qrError || '正在生成付款二维码…' }}</p>
          <p class="muted">请使用微信扫描付款码，付款后系统会自动核对结果。</p>
          <a v-if="qr" :href="qr" :download="payment.orderNo + '-payment.png'" class="text-link">保存付款码</a>
        </template>
        <template v-else>
          <p class="notice">点击下方按钮进入微信支付，完成后返回本页查询订单。</p>
          <a :href="payment.h5Url" class="button dark full" referrerpolicy="origin">打开微信支付 <Icon name="ArrowRight" :size="17" /></a>
        </template>
        <p class="caption" role="timer">支付凭证剩余 {{ remaining }}</p>
      </template>
      <p v-else class="notice" role="status">支付凭证已过期。请先查询结果；如仍未付款，可关闭后取消订单并重新下单。</p>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <p v-if="progress === 'PAUSED'" class="notice">自动查询已暂停，你可以手动查询付款结果。</p>
      <div class="button-row">
        <button class="button" :disabled="checking" @click="emit('query')">{{ checking ? '正在查询…' : '我已付款，查询结果' }}</button>
        <button class="button outline" @click="emit('close')">返回订单</button>
      </div>
      <p class="caption">只有渠道确认成功后，订单才会变为已付款。</p>
    </div>
  </BaseModal>
</template>
