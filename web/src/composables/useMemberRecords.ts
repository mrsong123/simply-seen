import { onBeforeUnmount, onMounted, toValue, watch, type MaybeRefOrGetter } from "vue";
import { memberApi } from "@/api/member";
import { createPagedResource } from "@/composables/createPagedResource";
import type { MemberRecordKind } from "@/types/pagination";
export function useMemberRecords<T = any>(kind: MaybeRefOrGetter<MemberRecordKind>) {
  const records = createPagedResource<T>(query => memberApi.getRecords<T>(toValue(kind), query));
  onMounted(records.load);
  watch(() => toValue(kind), records.reset);
  onBeforeUnmount(records.dispose);
  return records;
}
