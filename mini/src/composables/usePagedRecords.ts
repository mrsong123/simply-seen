import {
  computed,
  getCurrentScope,
  onScopeDispose,
  ref,
  shallowRef,
} from "vue";
import type { RecordPage } from "../types/pagination";
import { authStorage } from "../utils/storage";
import { requireCurrentSession } from "../utils/router";

/** Page data commits only for the current query and, for private records, its original member. */
export function usePagedRecords<T>(
  fetch: (page: number, size: number) => Promise<RecordPage<T>>,
  privateRecords = false,
  size = 20,
) {
  const items = shallowRef<T[]>([]),
    page = ref(0),
    total = ref(0),
    totalPages = ref(1),
    loaded = ref(false),
    loading = ref(false),
    failed = ref(false),
    summary = shallowRef<Record<string, number>>({});
  let revision = 0,
    retryReset = true;
  const more = computed(() => loaded.value && page.value < totalPages.value);
  function clear() {
    revision++;
    items.value = [];
    page.value = total.value = 0;
    totalPages.value = 1;
    loaded.value = loading.value = failed.value = false;
    summary.value = {};
  }
  if (privateRecords) uni.$on("session-expired", clear);
  if (getCurrentScope())
    onScopeDispose(() => {
      if (privateRecords) uni.$off("session-expired", clear);
      clear();
    });
  async function load(reset: boolean) {
    if (!reset && (loading.value || !more.value)) return;
    if (privateRecords && !requireCurrentSession()) {
      clear();
      return;
    }
    const current = ++revision,
      token = privateRecords ? authStorage.token() : "",
      requested = reset ? 1 : page.value + 1;
    if (reset) {
      items.value = [];
      total.value = page.value = 0;
      loaded.value = false;
      summary.value = {};
    }
    loading.value = true;
    failed.value = false;
    retryReset = reset;
    try {
      const response = await fetch(requested, size);
      if (current !== revision) return;
      if (privateRecords && token !== authStorage.token()) {
        clear();
        return;
      }
      if (
        !response ||
        !Array.isArray(response.items) ||
        !Number.isSafeInteger(response.total) ||
        response.total < 0 ||
        !Number.isSafeInteger(response.page) ||
        response.page < 1 ||
        !Number.isSafeInteger(response.totalPages) ||
        response.totalPages < 1 ||
        response.page > response.totalPages
      )
        throw new Error("分页数据格式不正确，请重新读取");
      if (
        !reset &&
        response.page < requested &&
        response.totalPages < requested &&
        response.page === response.totalPages
      ) {
        // A concurrent status change can remove the last page. Re-read the
        // current collection instead of appending a clamped, duplicate page.
        await load(true);
        return;
      }
      if (response.page !== requested)
        throw new Error("分页数据格式不正确，请重新读取");
      items.value = reset
        ? response.items
        : [...items.value, ...response.items];
      total.value = response.total;
      page.value = response.page;
      totalPages.value = response.totalPages;
      summary.value = response.summary || {};
      loaded.value = true;
    } catch (error) {
      if (current === revision) {
        if (privateRecords && token !== authStorage.token()) clear();
        else failed.value = true;
      }
      throw error;
    } finally {
      if (current === revision) loading.value = false;
    }
  }
  return {
    items,
    page,
    total,
    totalPages,
    loaded,
    loading,
    failed,
    summary,
    more,
    reload: () => load(true),
    next: () => load(false),
    retry: () => load(retryReset),
    clear,
  };
}
