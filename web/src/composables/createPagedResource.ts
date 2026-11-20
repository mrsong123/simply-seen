import { ref, shallowRef } from "vue";
import type { PageQuery, PageResult } from "@/types/pagination";
export function createPagedResource<T>(loader: (query: PageQuery) => Promise<PageResult<T>>) {
  const items = shallowRef<T[]>([]), summary = shallowRef<Record<string, number>>({});
  const page = ref(1), size = ref(10), search = ref(""), state = ref(""), asset = ref("");
  const total = ref(0), totalPages = ref(1), states = ref<string[]>([]);
  const loading = ref(false), loaded = ref(false), error = ref("");
  let generation = 0, debounce: ReturnType<typeof setTimeout> | undefined;
  async function load() {
    if (debounce) clearTimeout(debounce);
    const epoch = ++generation;
    loading.value = true; error.value = "";
    try {
      const response = await loader({ page: page.value, size: size.value, search: search.value, state: state.value, ...(asset.value ? { asset: asset.value } : {}) });
      if (epoch !== generation) return false;
      items.value = response.items;
      summary.value = response.summary || {};
      page.value = response.page;
      size.value = response.size;
      total.value = response.total;
      totalPages.value = Math.max(1, response.totalPages);
      states.value = response.states || [];
      loaded.value = true;
      return true;
    } catch (failure) {
      if (epoch === generation) error.value = failure instanceof Error ? failure.message : "数据加载失败，请重试";
      return false;
    } finally { if (epoch === generation) loading.value = false; }
  }
  function setSearch(value: string) {
    search.value = value; page.value = 1; generation++;
    if (debounce) clearTimeout(debounce);
    debounce = setTimeout(() => void load(), 300);
  }
  function setState(value: string) { state.value = value; page.value = 1; void load(); }
  function setAsset(value: string) { asset.value = value; page.value = 1; void load(); }
  function setPage(value: number) {
    page.value = Math.min(Math.max(1, Math.trunc(value)), totalPages.value);
    void load();
  }
  function setSize(value: number) { size.value = Math.max(1, Math.min(100, Number(value) || 10)); page.value = 1; void load(); }
  function reset() { page.value = 1; search.value = ""; state.value = ""; asset.value = ""; items.value = []; void load(); }
  function dispose() { generation++; if (debounce) clearTimeout(debounce); }
  return { items, summary, page, size, search, state, asset, total, totalPages, states, loading, loaded, error, load, setSearch, setState, setAsset, setPage, setSize, reset, dispose };
}
export type PagedResource<T = any> = ReturnType<typeof createPagedResource<T>>;
