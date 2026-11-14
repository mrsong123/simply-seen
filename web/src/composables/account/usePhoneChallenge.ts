import { computed, onScopeDispose, reactive, ref, watch } from "vue";
import { PhoneChallengeState, validPhone } from "@/utils/account";
import type { PhoneChallenge } from "@/types/account";

export function usePhoneChallenge(
  send: (phone: string) => Promise<PhoneChallenge>,
) {
  const phone = ref(""),
    code = ref(""),
    error = ref(""),
    sending = ref(false);
  const now = ref(Date.now()),
    developmentCode = ref("");
  const state = new PhoneChallengeState();
  const revision = ref(0);
  const timer = setInterval(() => (now.value = Date.now()), 1000);
  onScopeDispose(() => clearInterval(timer));
  const remaining = computed(() => {
    revision.value;
    return state.remaining(phone.value.trim(), now.value);
  });
  function clear() {
    state.clear();
    code.value = "";
    developmentCode.value = "";
    error.value = "";
    revision.value++;
  }
  watch(phone, clear);
  async function sendCode() {
    if (sending.value || remaining.value > 0) return;
    const requestedPhone = phone.value.trim();
    if (!validPhone(requestedPhone)) {
      error.value = "请输入有效的11位中国大陆手机号";
      return;
    }
    error.value = "";
    sending.value = true;
    try {
      const result = await send(requestedPhone);
      state.accept(requestedPhone, result, Date.now());
      if (phone.value.trim() !== requestedPhone) state.clear();
      code.value = "";
      developmentCode.value = state.developmentCode();
      now.value = Date.now();
      revision.value++;
    } catch (failure) {
      error.value =
        failure instanceof Error
          ? failure.message
          : "验证码发送失败，请稍后重试";
    } finally {
      sending.value = false;
    }
  }
  function credentials() {
    try {
      error.value = "";
      return state.credentials(phone.value.trim(), code.value, Date.now());
    } catch (failure) {
      error.value = failure instanceof Error ? failure.message : "请检查验证码";
      return null;
    }
  }
  return reactive({
    phone,
    code,
    error,
    sending,
    remaining,
    developmentCode,
    sendCode,
    credentials,
    clear,
  });
}
