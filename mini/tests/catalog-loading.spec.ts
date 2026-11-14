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
  catalogApi: { products: fake.products },
}));
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("uni", {
    showLoading: vi.fn(),
    hideLoading: vi.fn(),
    showToast: vi.fn(),
  });
  fake.load.mockResolvedValue(undefined);
  fake.products.mockResolvedValue([
    { id: 1, name: "公开商品", points_percent: 20 },
  ]);
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
  it("does not wait for a slow optional member request before finishing public loading", async () => {
    fake.refresh.mockReturnValue(new Promise(() => undefined));
    const { useCatalog } = await import("../src/composables/useCatalog");
    const catalog = useCatalog(() => false);
    await catalog.reload();
    expect(catalog.visible.value).toHaveLength(1);
  });
});
