<script setup lang="ts">
import { computed, ref, onUnmounted } from "vue";
import { accountApi } from "@/api/account";
import type { IdentityStatus } from "@/types/account";
import { identityInputError } from "@/utils/account";
import { date } from "@/utils/format";
import { useIdentityRetry } from "@/composables/account/useIdentityRetry";
const props = defineProps<{
  identity: IdentityStatus;
  enabled: boolean;
  developmentMode: boolean;
}>();
const emit = defineEmits<{ updated: [value: IdentityStatus]; refresh: [] }>();
const name = ref(""),
  idNumber = ref(""),
  accepted = ref(false),
  busy = ref(false),
  error = ref("");
const labels = {
  UNVERIFIED: "未认证",
  PENDING: "核验处理中",
  VERIFIED: "认证通过",
  REJECTED: "未通过",
};
const { remaining, requested, requestRetry } = useIdentityRetry(
  () => props.identity,
);
const editable = computed(
  () =>
    props.identity.status === "UNVERIFIED" ||
    (props.identity.status === "REJECTED" && remaining.value === 0) ||
    (props.identity.status === "PENDING" && requested.value) ||
    (!props.developmentMode && props.identity.development),
);
function clear() {
  name.value = "";
  idNumber.value = "";
  accepted.value = false;
}
onUnmounted(clear);
async function submit() {
  if (busy.value || !accepted.value) return;
  error.value = identityInputError(name.value, idNumber.value);
  if (error.value) return;
  busy.value = true;
  try {
    const result = await accountApi.verifyIdentity({
      name: name.value.trim(),
      idNumber: idNumber.value.trim().toUpperCase(),
      accepted: accepted.value,
    });
    clear();
    emit("updated", result);
  } catch (failure) {
    error.value =
      failure instanceof Error
        ? failure.message
        : "核验暂时无法完成，请稍后重试";
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="panel security-panel">
    <div class="security-title">
      <h3>实名认证</h3>
      <span class="badge" :class="{ warning: identity.development }"
        >{{ identity.development ? "开发模拟 · " : ""
        }}{{ labels[identity.status] }}</span
      >
    </div>
    <p class="muted">
      通过姓名与中国大陆居民身份证号码二要素核验身份，用于提现资格及账户安全。认证通过后不会在页面展示完整证件信息。
    </p>
    <p v-if="identity.development" class="notice">
      此结果为开发模拟，不代表已通过真实身份核验，不能用于生产提现。
    </p>
    <div
      v-if="identity.nameMasked || identity.idNumberMasked"
      class="identity-summary"
    >
      <span
        >姓名 <strong>{{ identity.nameMasked }}</strong></span
      ><span
        >证件号码 <strong>{{ identity.idNumberMasked }}</strong></span
      >
      <span v-if="identity.verifiedAt"
        >核验时间 <strong>{{ date(identity.verifiedAt) }}</strong></span
      >
    </div>
    <p
      v-if="identity.status === 'REJECTED' && identity.rejectionReason"
      class="warning"
      role="alert"
    >
      {{ identity.rejectionReason }}
    </p>
    <div v-if="identity.status === 'PENDING'" class="notice" role="status">
      身份核验处理中。
      <span v-if="remaining"
        >请在{{ remaining }}秒后刷新结果或重新填写核验。</span
      >
      <span v-else>若结果仍未返回，可重新填写证件并明确提交核验。</span>
      <button class="text-link" type="button" @click="emit('refresh')">
        刷新认证结果
      </button>
      <button
        v-if="!requested"
        class="text-link"
        type="button"
        :disabled="remaining > 0"
        @click="requestRetry"
      >
        重新填写并核验
      </button>
    </div>
    <p v-if="identity.status === 'REJECTED' && remaining" class="notice">
      请在{{ remaining }}秒后重新提交核验。
    </p>
    <form
      v-if="editable && enabled"
      @submit.prevent="submit"
      autocomplete="off"
    >
      <label class="input-label"
        >真实姓名<input
          v-model="name"
          required
          minlength="2"
          maxlength="40"
          autocomplete="off"
          :disabled="busy"
      /></label>
      <label class="input-label"
        >身份证号码<input
          v-model="idNumber"
          required
          minlength="18"
          maxlength="18"
          autocomplete="off"
          spellcheck="false"
          autocapitalize="characters"
          :disabled="busy"
      /></label>
      <label class="checkbox-label"
        ><input
          v-model="accepted"
          type="checkbox"
          required
          :disabled="busy"
        /><span
          >我明确同意为身份核验向认证服务商提交姓名及身份证号码，并已阅读<RouterLink
            to="/legal/privacy"
            >《隐私政策》</RouterLink
          >。该信息不用于商品推广。</span
        ></label
      >
      <p v-if="error" class="warning" role="alert">{{ error }}</p>
      <button class="button dark" :disabled="busy || !accepted">
        {{
          busy
            ? "正在核验…"
            : identity.status === "REJECTED"
              ? "重新提交核验"
              : "提交实名认证"
        }}
      </button>
    </form>
    <p v-else-if="editable && !enabled" class="notice">
      实名认证服务暂未开放，请稍后再试；服务恢复前不会放行需实名的提现。
    </p>
  </div>
</template>
