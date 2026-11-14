import { computed, onScopeDispose, ref, watch } from "vue";
import type { IdentityStatus } from "@/types/account";

/** Waiting never submits a chargeable verification; only a fresh explicit action can retry. */
export function useIdentityRetry(source: () => IdentityStatus) {
  const deadline = ref(0),
    now = ref(Date.now()),
    requested = ref(false);
  const timer = setInterval(() => (now.value = Date.now()), 1000);
  onScopeDispose(() => clearInterval(timer));
  watch(
    source,
    (identity) => {
      const fallback =
        identity.status === "PENDING"
          ? 120
          : identity.status === "REJECTED"
            ? 60
            : 0;
      const seconds = Number.isFinite(identity.retryAfter)
        ? Math.max(0, identity.retryAfter!)
        : fallback;
      now.value = Date.now();
      deadline.value = now.value + seconds * 1000;
      requested.value = false;
    },
    { immediate: true },
  );
  const remaining = computed(() =>
    Math.max(0, Math.ceil((deadline.value - now.value) / 1000)),
  );
  function requestRetry() {
    if (remaining.value > 0) return;
    requested.value = true;
  }
  return { remaining, requested, requestRetry };
}
