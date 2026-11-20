import { computed, ref } from "vue";
import { useAppStore } from "../store/app";
import { useSessionStore } from "../store/session";
import { catalogApi } from "../api/catalog";
import { useTask } from "../utils/useTask";
import { usePagedRecords } from "./usePagedRecords";
import type { Product } from "../types/models";
export function useCatalog(pointsOnly: () => boolean) {
  const app = useAppStore(),
    session = useSessionStore();
  const { busy, run } = useTask();
  const search = ref(""),
    category = ref("");
  const records = usePagedRecords<Product>(async (page, size) => {
    await app.load(page === 1);
    return catalogApi.page(
      page,
      size,
      category.value,
      search.value,
      pointsOnly(),
    );
  });
  const visible = computed(() => records.items.value);
  async function searchProducts() {
    await records.reload();
  }
  async function reload() {
    void session.refresh().catch(() => undefined);
    await run(searchProducts);
  }
  function selectCategory(id: string) {
    if (busy.value) return;
    category.value = id;
    void run(searchProducts);
  }
  return {
    app,
    session,
    search,
    category,
    visible,
    busy,
    loaded: records.loaded,
    loadError: records.failed,
    total: records.total,
    page: records.page,
    totalPages: records.totalPages,
    loading: records.loading,
    next: records.next,
    retry: records.retry,
    run,
    reload,
    searchProducts,
    selectCategory,
  };
}
