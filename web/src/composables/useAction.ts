import { ref } from "vue";
export function useAction() {
  const busy = ref(false);
  async function run(action: () => Promise<void>) {
    if (busy.value) return;
    busy.value = true;
    try {
      await action();
    } catch {
      /* Transport errors are displayed by the shared response interceptor. */
    } finally {
      busy.value = false;
    }
  }
  return { busy, run };
}
