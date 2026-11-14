import { computed, ref } from "vue";
import { useAppStore } from "../store/app";
import { useSessionStore } from "../store/session";
import { catalogApi } from "../api/catalog";
import { useTask } from "../utils/useTask";
import type { Product } from "../types/models";
export function useCatalog(pointsOnly: () => boolean) {
  const app = useAppStore(),
    session = useSessionStore();
  const { run } = useTask();
  const products = ref<Product[]>([]),
    search = ref(""),
    category = ref("");
  const visible = computed(() =>
    pointsOnly()
      ? products.value.filter((item) => item.points_percent > 0)
      : products.value,
  );
  async function searchProducts() {
    products.value = await catalogApi.products(category.value, search.value);
  }
  async function reload() {
    // Session data is optional on the public catalog and must never gate it.
    void session.refresh().catch(() => undefined);
    await run(async () => {
      await app.load();
      await searchProducts();
    });
  }
  function selectCategory(id: string) {
    category.value = id;
    void run(searchProducts);
  }
  return {
    app,
    session,
    search,
    category,
    visible,
    run,
    reload,
    searchProducts,
    selectCategory,
  };
}
