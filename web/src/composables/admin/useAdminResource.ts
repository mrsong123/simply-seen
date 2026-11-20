import type { Ref, MaybeRefOrGetter } from "vue";
import type { AdminRecordKind } from "@/types/pagination";
import { useAdminRecords } from "./useAdminRecords";
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useUiStore } from "@/store/ui";
import { reportLocalFailure } from "@/utils/actionError";

export function useAdminResource<T = any>(loader: () => Promise<T>, kind?: MaybeRefOrGetter<AdminRecordKind>) {
  const records = kind ? useAdminRecords(kind) : null;
  const data = records ? records.items as Ref<T | null> : ref<T | null>(null);
  const loading = records?.loading || ref(false), error = records?.error || ref("");
  const busy = ref(false);
  const ui = useUiStore();
  let generation = 0;
  async function load() {
    if (records) return records.load();
    const epoch = ++generation;
    loading.value = true;
    error.value = "";
    try {
      const response = await loader();
      if (epoch !== generation) return false;
      data.value = response;
      return true;
    } catch (failure) {
      if (epoch === generation) {
        error.value = failure instanceof Error ? failure.message : "列表加载失败，请重试";
        reportLocalFailure(failure, ui.message);
      }
      return false;
    } finally {
      if (epoch === generation) loading.value = false;
    }
  }
  async function run(task: () => Promise<void>, success = "操作已保存") {
    if (busy.value) return false;
    busy.value = true;
    try {
      await task();
      const refreshed = await load();
      if (success) ui.message(refreshed ? success : "操作已保存，列表更新失败，请重新加载", !refreshed);
      return true;
    } catch (failure) {
      reportLocalFailure(failure, ui.message);
      return false;
    } finally {
      busy.value = false;
    }
  }
  if (!records) onMounted(load);
  onBeforeUnmount(() => generation++);
  return { data, loading, error, busy, load, run };
}
