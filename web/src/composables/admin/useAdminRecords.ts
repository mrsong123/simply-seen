import { onBeforeUnmount, onMounted, provide, toValue, watch, type InjectionKey, type MaybeRefOrGetter } from "vue";
import { adminApi } from "@/api/admin";
import { createPagedResource, type PagedResource } from "@/composables/createPagedResource";
import type { AdminRecordKind } from "@/types/pagination";
export const adminRecordsKey: InjectionKey<PagedResource> = Symbol("admin-records");
export function useAdminRecords(kind: MaybeRefOrGetter<AdminRecordKind>) {
  const records = createPagedResource(query => adminApi.getRecords(toValue(kind), query));
  provide(adminRecordsKey, records);
  onMounted(records.load);
  watch(() => toValue(kind), records.reset);
  onBeforeUnmount(records.dispose);
  return records;
}
