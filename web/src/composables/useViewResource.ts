import { onBeforeUnmount, onMounted, ref, shallowRef } from "vue";
export function useViewResource<T>(loader: () => Promise<T>, initial: T, immediate = true) {
  const data = shallowRef<T>(initial), loading = ref(false), error = ref("");
  const loaded = ref(false);
  let generation = 0;
  async function load() {
    const epoch = ++generation;
    loading.value = true;
    error.value = "";
    try {
      const value = await loader();
      if (epoch !== generation) return false;
      data.value = value;
      loaded.value = true;
      return true;
    } catch (failure) {
      if (epoch === generation) error.value = failure instanceof Error ? failure.message : "加载失败，请稍后重试";
      return false;
    } finally {
      if (epoch === generation) loading.value = false;
    }
  }
  if (immediate) onMounted(load);
  onBeforeUnmount(() => generation++);
  return { data, loading, error, loaded, load };
}
