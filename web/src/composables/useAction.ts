import { ref } from "vue";
import { useUiStore } from "@/store/ui";
import { reportLocalFailure } from "@/utils/actionError";
export function useAction() {
  const busy = ref(false), ui = useUiStore();
  async function run(action: () => Promise<void>) {
    if (busy.value) return;
    busy.value = true;
    try {
      await action();
    } catch (failure) {
      reportLocalFailure(failure, ui.message);
    } finally {
      busy.value = false;
    }
  }
  return { busy, run };
}
