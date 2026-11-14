import { ref } from "vue";
import { useUiStore } from "@/store/ui";
export function useAdminMutation() {
  const busy = ref(false);
  const ui = useUiStore();
  async function execute(task: () => Promise<unknown>, success = "操作已保存") {
    if (busy.value) return false;
    busy.value = true;
    try {
      await task();
      if (success) ui.message(success);
      return true;
    } catch {
      return false;
    } finally {
      busy.value = false;
    }
  }
  return { busy, execute };
}
