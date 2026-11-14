import { configureRequestHooks } from "@/utils/request";
import { pinia } from "@/store";
import { useAuthStore } from "@/store/auth";
import { useUiStore } from "@/store/ui";
export function installRequestHooks() {
  configureRequestHooks({
    onPendingChange: (pending) => {
      useUiStore(pinia).pending = pending;
    },
    onError: (error) => useUiStore(pinia).message(error.message, true),
    onUnauthorized: (kind) => useAuthStore(pinia).clearSession(kind),
  });
}
