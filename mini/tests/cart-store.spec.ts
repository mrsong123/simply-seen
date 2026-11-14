import { createPinia, setActivePinia } from "pinia";
import { beforeEach, afterEach, describe, it, vi, expect } from "vitest";
const fake = vi.hoisted(() => ({ list: vi.fn(), quantity: vi.fn() }));
vi.mock("../src/api/trade", () => ({ cartApi: fake }));
const item = {
  sku_id: 10,
  product_id: 1,
  name: "商品",
  sku_name: "规格",
  image: "",
  color: "#ffffff",
  price: 12900,
  quantity: 2,
  stock: 9,
  status: "ON_SALE",
};
let expire: () => void;
beforeEach(() => {
  setActivePinia(createPinia());
  vi.resetAllMocks();
  fake.quantity.mockResolvedValue(undefined);
  vi.stubGlobal("uni", {
    getStorageSync: vi.fn(() => "session-test"),
    $on: vi.fn((_event: string, callback: () => void) => (expire = callback)),
  });
});
afterEach(() => vi.unstubAllGlobals());
describe("Cart accepted mutations and refresh ordering", () => {
  it("resolves add-to-cart after an accepted write even when the follow-up fetch fails", async () => {
    fake.list
      .mockResolvedValueOnce([{ ...item }])
      .mockRejectedValueOnce(new Error("network"));
    const { useCartStore } = await import("../src/store/cart");
    const cart = useCartStore();
    await expect(cart.add(10, 1)).resolves.toBeUndefined();
    expect(fake.quantity).toHaveBeenCalledWith(10, 3);
    expect(cart.items[0].quantity).toBe(3);
  });
  it("does not allow a late older fetch to overwrite a newer cart result", async () => {
    let first!: (value: unknown) => void, second!: (value: unknown) => void;
    fake.list
      .mockReturnValueOnce(new Promise((resolve) => (first = resolve)))
      .mockReturnValueOnce(new Promise((resolve) => (second = resolve)));
    const { useCartStore } = await import("../src/store/cart");
    const cart = useCartStore();
    const old = cart.refresh(),
      fresh = cart.refresh();
    second([{ ...item, quantity: 4 }]);
    await fresh;
    first([{ ...item, quantity: 1 }]);
    await old;
    expect(cart.items[0].quantity).toBe(4);
  });
  it("cannot restore another signed-out session cart from a late in-flight request", async () => {
    let finish!: (value: unknown) => void;
    fake.list.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const { useCartStore } = await import("../src/store/cart");
    const cart = useCartStore();
    const pending = cart.refresh();
    expire();
    finish([{ ...item }]);
    await pending;
    expect(cart.items).toEqual([]);
  });
});
