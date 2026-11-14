import { computed, onScopeDispose, ref, watch } from "vue";
import type { PhoneProof, SmsChallenge } from "../types/account";

export const validPhone = (value: string) => /^1[3-9]\d{9}$/.test(value);

/** A challenge belongs to one phone and purpose; none of its values are persisted. */
export function usePhoneChallenge(
  deliver: (phone: string) => Promise<SmsChallenge>,
) {
  const phone = ref(""),
    code = ref(""),
    sending = ref(false);
  const challenge = ref<SmsChallenge | null>(null);
  const now = ref(Date.now()),
    expiresAt = ref(0),
    retryAt = ref(0);
  let generation = 0,
    challengePhone = "";
  const timer = setInterval(() => (now.value = Date.now()), 1000);
  const retrySeconds = computed(() =>
    Math.max(0, Math.ceil((retryAt.value - now.value) / 1000)),
  );
  const expired = computed(
    () => !!challenge.value && now.value >= expiresAt.value,
  );
  const canSend = computed(
    () => validPhone(phone.value) && !sending.value && !retrySeconds.value,
  );
  function clearChallenge() {
    generation++;
    challenge.value = null;
    code.value = "";
    expiresAt.value = 0;
    challengePhone = "";
  }
  watch(
    phone,
    () => {
      clearChallenge();
      retryAt.value = 0;
    },
    { flush: "sync" },
  );
  onScopeDispose(() => {
    clearInterval(timer);
    clearChallenge();
    phone.value = "";
  });
  async function send() {
    if (!canSend.value)
      throw new Error("请填写有效手机号，并等待重发倒计时结束");
    const target = phone.value;
    clearChallenge();
    const current = generation;
    sending.value = true;
    try {
      const result = await deliver(target);
      if (generation !== current || phone.value !== target) return;
      if (!result.challengeId || result.expiresIn <= 0 || result.retryAfter < 0)
        throw new Error("验证码响应无效，请稍后重新获取");
      now.value = Date.now();
      challenge.value = result;
      challengePhone = target;
      expiresAt.value = now.value + result.expiresIn * 1000;
      retryAt.value = now.value + result.retryAfter * 1000;
    } finally {
      sending.value = false;
    }
  }
  function proof(): PhoneProof {
    now.value = Date.now();
    if (!challenge.value || challengePhone !== phone.value || expired.value)
      throw new Error("请先获取当前手机号的有效验证码");
    if (!/^\d{6}$/.test(code.value)) throw new Error("请输入6位短信验证码");
    return {
      phone: phone.value,
      code: code.value,
      challengeId: challenge.value.challengeId,
    };
  }
  function reset() {
    clearChallenge();
    phone.value = "";
    retryAt.value = 0;
  }
  return {
    phone,
    code,
    sending,
    challenge,
    retrySeconds,
    expired,
    canSend,
    send,
    proof,
    reset,
    discardProof: clearChallenge,
  };
}
