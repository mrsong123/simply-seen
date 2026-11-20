import { beforeEach, afterEach, describe, it, vi, expect } from "vitest";
const fake = vi.hoisted(() => ({
  refresh: vi.fn(),
  load: vi.fn(),
  products: vi.fn(),
}));
vi.mock("../src/store/app", () => ({
  useAppStore: () => ({ load: fake.load, categories: [] }),
}));
vi.mock("../src/store/session", () => ({
  useSessionStore: () => ({ refresh: fake.refresh }),
}));
vi.mock("../src/api/catalog", () => ({
  catalogApi: { page: fake.products },
}));
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("uni", {
    showLoading: vi.fn(),
    hideLoading: vi.fn(),
    showToast: vi.fn(),
  });
  fake.load.mockResolvedValue(undefined);
  fake.refresh.mockResolvedValue(undefined);
  fake.products.mockResolvedValue({
    items: [{ id: 1, name: "公开商品", points_percent: 20 }],
    total: 1,
    page: 1,
    size: 20,
    totalPages: 1,
    states: [],
    summary: {},
  });
});
afterEach(() => vi.unstubAllGlobals());
describe("Public catalog optional session loading", () => {
  it("still renders products after an expired session returns 401", async () => {
    fake.refresh.mockRejectedValue({ status: 401, code: "AUTH_REQUIRED" });
    const { useCatalog } = await import("../src/composables/useCatalog");
    const catalog = useCatalog(() => false);
    await catalog.reload();
    expect(fake.products).toHaveBeenCalledOnce();
    expect(catalog.visible.value.map((item) => item.name)).toEqual([
      "公开商品",
    ]);
  });
  it("shows a recoverable failed state instead of claiming there are no products", async () => {
    fake.products.mockRejectedValueOnce(new Error("network"));
    const { useCatalog } = await import("../src/composables/useCatalog");
    const catalog = useCatalog(() => false);
    await catalog.reload();
    expect(catalog.loadError.value).toBe(true);
    expect(catalog.visible.value).toEqual([]);
    await catalog.reload();
    expect(catalog.loadError.value).toBe(false);
    expect(catalog.visible.value).toHaveLength(1);
    expect(catalog.total.value).toBe(1);
  });
  it("does not wait for a slow optional member request before finishing public loading", async () => {
    fake.refresh.mockReturnValue(new Promise(() => undefined));
    const { useCatalog } = await import("../src/composables/useCatalog");
    const catalog = useCatalog(() => false);
    await catalog.reload();
    expect(catalog.visible.value).toHaveLength(1);
  });
});
