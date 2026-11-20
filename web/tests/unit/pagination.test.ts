import { afterEach, describe, expect, it, vi } from "vitest";
import { createPagedResource } from "../../src/composables/createPagedResource";
import { normalizeInvite, readInvite } from "../../src/utils/invite";
import { reportLocalFailure } from "../../src/utils/actionError";
import { safeCsvCell } from "../../src/composables/admin/tableFormat";
import { ApiError } from "../../src/utils/request";
const page = (items: any[] = [], current = 1) => ({ items, page: current, size: 10, total: 203, totalPages: 21, states: ["PAID"], summary: { pendingAmount: 645 } });
afterEach(() => vi.useRealTimers());
describe("server history pagination and action recovery", () => {
  it("queries historical pages and filters on the server instead of only the first loaded batch", async () => {
    const loader = vi.fn().mockResolvedValue(page([{ id: "old-order" }], 18));
    const records = createPagedResource(loader);
    records.page.value = 18;
    await records.load();
    expect(loader).toHaveBeenCalledWith({ page: 18, size: 10, search: "", state: "" });
    expect(records.items.value[0].id).toBe("old-order");
    expect(records.total.value).toBe(203);
    expect(records.summary.value.pendingAmount).toBe(645);
    records.setState("PAID");
    await Promise.resolve();
    expect(loader).toHaveBeenLastCalledWith({ page: 1, size: 10, search: "", state: "PAID" });
    records.dispose();
  });
  it("debounces global search, resets the page and ignores earlier responses", async () => {
    vi.useFakeTimers();
    let finish!: (value: ReturnType<typeof page>) => void;
    const loader = vi.fn().mockImplementationOnce(() => new Promise(done => { finish = done; })).mockResolvedValue(page([{ id: "matching" }]));
    const records = createPagedResource(loader);
    const earlier = records.load();
    records.setSearch("old-order");
    await vi.advanceTimersByTimeAsync(300);
    finish(page([{ id: "stale" }])); await earlier;
    expect(records.items.value).toEqual([{ id: "matching" }]);
    expect(loader).toHaveBeenLastCalledWith({ page: 1, size: 10, search: "old-order", state: "" });
    records.dispose();
  });
  it("exposes failure and recovers through a real reload without inventing zero totals", async () => {
    const loader = vi.fn().mockRejectedValueOnce(new Error("数据库暂不可用")).mockResolvedValue(page([{ id: 1 }]));
    const records = createPagedResource(loader);
    expect(await records.load()).toBe(false);
    expect(records.error.value).toBe("数据库暂不可用");
    expect(records.loaded.value).toBe(false);
    expect(await records.load()).toBe(true);
    expect(records.error.value).toBe("");
    expect(records.total.value).toBe(203);
    records.dispose();
  });
  it("filters ledger assets using the dedicated server field and clears it when the resource changes", async () => {
    const loader = vi.fn().mockResolvedValue(page([{ id: "asset-row" }]));
    const records = createPagedResource(loader);
    records.page.value = 9;
    records.setAsset("points");
    await Promise.resolve();
    expect(loader).toHaveBeenLastCalledWith({ page: 1, size: 10, search: "", state: "", asset: "points" });
    records.reset();
    await Promise.resolve();
    expect(loader).toHaveBeenLastCalledWith({ page: 1, size: 10, search: "", state: "" });
    expect(records.asset.value).toBe("");
    records.dispose();
  });
  it("rejects poisoned invitation state and preserves valid uppercase referral data", () => {
    const storage = { getItem: vi.fn().mockReturnValue("x".repeat(100)), removeItem: vi.fn() };
    expect(readInvite(storage)).toBe("");
    expect(storage.removeItem).toHaveBeenCalledWith("sj-invite");
    expect(normalizeInvite(["ABC"])).toBeNull();
    expect(normalizeInvite("<script>")).toBeNull();
    expect(normalizeInvite(" abc123 ")).toBe("ABC123");
  });
  it("neutralizes exported formulas even after leading whitespace and quotes values", () => {
    expect(safeCsvCell(" =HYPERLINK(\"bad\")")).toBe("\"' =HYPERLINK(\"\"bad\"\")\"");
    expect(safeCsvCell("\t=1+1")).toBe("\"'\t=1+1\"");
    expect(safeCsvCell("普通商品")).toBe("\"普通商品\"");
  });
  it("surfaces local exceptions while letting transport own API notifications", () => {
    const notify = vi.fn();
    reportLocalFailure(new Error("本地金额无效"), notify);
    expect(notify).toHaveBeenCalledWith("本地金额无效", true);
    reportLocalFailure(new ApiError("API已通知", "BAD", 400), notify);
    expect(notify).toHaveBeenCalledTimes(1);
  });
});
