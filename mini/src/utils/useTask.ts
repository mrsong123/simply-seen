import { ref } from "vue";
export function useTask() {
  const busy = ref(false);
  async function run<T>(operation: () => Promise<T>): Promise<T | undefined> {
    if (busy.value) return;
    busy.value = true;
    uni.showLoading({ title: "请稍候" });
    try {
      return await operation();
    } catch (error: unknown) {
      const e = error as {
        message?: string;
        errMsg?: string;
        notified?: boolean;
      };
      if (!e.notified)
        uni.showToast({
          title: e.message || e.errMsg || "操作未完成，请稍后重试",
          icon: "none",
        });
    } finally {
      busy.value = false;
      uni.hideLoading();
    }
  }
  return { busy, run };
}
export function confirm(content: string, title = "请确认"): Promise<boolean> {
  return new Promise((resolve) =>
    uni.showModal({
      title,
      content,
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    }),
  );
}
