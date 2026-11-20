import { ApiError } from "@/utils/request";
export function reportLocalFailure(
  failure: unknown,
  notify: (message: string, error?: boolean) => void,
) {
  // HTTP failures already have a single notification from the request interceptor.
  if (failure instanceof ApiError) return;
  notify(failure instanceof Error ? failure.message : "操作未完成，请稍后重试", true);
}
