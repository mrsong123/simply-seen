import { describe, it, expect, vi } from "vitest";
import { useCheckoutSubmission } from "../../src/composables/storefront/useCheckoutSubmission";
describe("committed order recovery", () => {
  it("navigates to the committed order even when cart refresh fails", async () => {
    const create = vi.fn(async () => ({ id: "SJ-COMMITTED" })),
      navigate = vi.fn(async () => {}),
      refreshCart = vi.fn(async () => {
        throw new Error("cart unavailable");
      });
    const workflow = useCheckoutSubmission({ create, navigate, refreshCart });
    await expect(workflow.submit()).resolves.toEqual({ id: "SJ-COMMITTED" });
    expect(navigate).toHaveBeenCalledWith("SJ-COMMITTED");
    expect(workflow.createdOrder.value?.id).toBe("SJ-COMMITTED");
  });
  it("retries navigation with the existing order rather than submitting a consumed quote twice", async () => {
    const create = vi.fn(async () => ({ id: "SJ-COMMITTED" })),
      navigate = vi
        .fn()
        .mockRejectedValueOnce(new Error("chunk unavailable"))
        .mockResolvedValue(undefined),
      onCreated = vi.fn();
    const workflow = useCheckoutSubmission({
      create,
      navigate,
      refreshCart: async () => {},
      onCreated,
    });
    await expect(workflow.submit()).rejects.toThrow("chunk unavailable");
    await workflow.submit();
    expect(create).toHaveBeenCalledOnce();
    expect(onCreated).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledTimes(2);
  });
  it("does not rotate the request key or navigate after order creation fails", async () => {
    const create = vi.fn(async () => {
        throw new Error("order not committed");
      }),
      navigate = vi.fn(async () => {}),
      refreshCart = vi.fn(async () => {}),
      onCreated = vi.fn();
    const workflow = useCheckoutSubmission({
      create,
      navigate,
      refreshCart,
      onCreated,
    });
    await expect(workflow.submit()).rejects.toThrow("order not committed");
    expect(workflow.createdOrder.value).toBeNull();
    expect(onCreated).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    expect(refreshCart).not.toHaveBeenCalled();
  });
});
