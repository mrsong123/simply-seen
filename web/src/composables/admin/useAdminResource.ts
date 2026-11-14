import { onMounted, ref } from "vue";
import { useUiStore } from "@/store/ui";

export function useAdminResource<T = any>(loader: () => Promise<T>) {
  const data = ref<T | null>(null);
  const loading = ref(false);
  const busy = ref(false);
  const ui = useUiStore();
  async function load() {
    loading.value = true;
    try {
      data.value = await loader();
    } catch {
      /* The request interceptor reports errors. */
    } finally {
      loading.value = false;
    }
  }
  async function run(task: () => Promise<void>, success = "操作已保存") {
    if (busy.value) return false;
    busy.value = true;
    try {
      await task();
      await load();
      if (success) ui.message(success);
      return true;
    } catch {
      return false;
    } finally {
      busy.value = false;
    }
  }
  onMounted(load);
  return { data, loading, busy, load, run };
}
