import { effectScope } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { usePagedRecords } from "../src/composables/usePagedRecords";
const page = (number: number, items: number[], total = 3) => ({
  items,
  total,
  page: number,
  size: 2,
  totalPages: Math.ceil(total / 2) || 1,
  states: [],
  summary: { pendingAmount: 900, level1Count: 31, level2Count: 42 },
});
let token = "member-a";
let expire: (() => void) | undefined;
beforeEach(() => {
  token = "member-a";
  expire = undefined;
  vi.stubGlobal("uni", {
    getStorageSync: () => token,
    $on: vi.fn((event, handler) => {
      if (event === "session-expired") expire = handler;
    }),
    $off: vi.fn(),
  });
});
afterEach(() => vi.unstubAllGlobals());
describe("Native paginated records", () => {
  it("appends pages without replacing all-account totals with a page-local count", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(page(1, [1, 2]))
      .mockResolvedValueOnce(page(2, [3]));
    const records = usePagedRecords(fetch, false, 2);
    await records.reload();
    expect(records.more.value).toBe(true);
    await records.next();
    expect(records.items.value).toEqual([1, 2, 3]);
    expect(records.total.value).toBe(3);
    expect(records.summary.value).toMatchObject({
      pendingAmount: 900,
      level1Count: 31,
    });
    expect(records.more.value).toBe(false);
    await records.next();
    expect(fetch).toHaveBeenCalledTimes(2);
  });
  it("retries a failed next page while preserving previously loaded data", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(page(1, [1, 2]))
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(page(2, [3]));
    const records = usePagedRecords(fetch, false, 2);
    await records.reload();
    await expect(records.next()).rejects.toThrow("network");
    expect(records.items.value).toEqual([1, 2]);
    expect(records.page.value).toBe(1);
    expect(records.failed.value).toBe(true);
    await records.retry();
    expect(fetch).toHaveBeenLastCalledWith(2, 2);
    expect(records.items.value).toEqual([1, 2, 3]);
    expect(records.failed.value).toBe(false);
  });
  it("ignores an old filtered response after a newer query reset", async () => {
    let old!: (result: ReturnType<typeof page>) => void;
    const fetch = vi
      .fn()
      .mockReturnValueOnce(
        new Promise((resolve) => {
          old = resolve;
        }),
      )
      .mockResolvedValueOnce(page(1, [9], 1));
    const records = usePagedRecords(fetch, false, 2);
    const first = records.reload();
    await records.reload();
    old(page(1, [1, 2]));
    await first;
    expect(records.items.value).toEqual([9]);
    expect(records.total.value).toBe(1);
  });
  it("reloads the collection if concurrent changes remove the requested last page", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(page(1, [1, 2]))
      .mockResolvedValueOnce(page(1, [9], 1))
      .mockResolvedValueOnce(page(1, [9], 1));
    const records = usePagedRecords(fetch, false, 2);
    await records.reload();
    await records.next();
    expect(fetch.mock.calls).toEqual([[1, 2], [2, 2], [1, 2]]);
    expect(records.items.value).toEqual([9]);
    expect(records.total.value).toBe(1);
    expect(records.more.value).toBe(false);
    expect(records.failed.value).toBe(false);
  });
  it("discards a private response if another phone account logs in", async () => {
    let complete!: (result: ReturnType<typeof page>) => void;
    const records = usePagedRecords(
      () =>
        new Promise<ReturnType<typeof page>>((resolve) => {
          complete = resolve;
        }),
      true,
      2,
    );
    const pending = records.reload();
    token = "member-b";
    complete(page(1, [7, 8]));
    await pending;
    expect(records.items.value).toEqual([]);
    expect(records.total.value).toBe(0);
    expect(records.loading.value).toBe(false);
  });
  it("clears loaded private data immediately when the session expires and unregisters on page disposal", async () => {
    const scope = effectScope();
    const records = scope.run(() =>
      usePagedRecords(vi.fn().mockResolvedValue(page(1, [1, 2])), true, 2),
    )!;
    await records.reload();
    expire?.();
    expect(records.items.value).toEqual([]);
    expect(records.summary.value).toEqual({});
    scope.stop();
    expect(uni.$off).toHaveBeenCalledWith(
      "session-expired",
      expect.any(Function),
    );
  });
  it("rejects invalid response metadata without a false successful page", async () => {
    const records = usePagedRecords(
      vi.fn().mockResolvedValue({ ...page(1, [1]), page: 2 }),
      false,
      2,
    );
    await expect(records.reload()).rejects.toThrow("分页数据格式");
    expect(records.loaded.value).toBe(false);
    expect(records.failed.value).toBe(true);
  });
});
