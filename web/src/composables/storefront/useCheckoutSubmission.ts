import { ref } from "vue";
interface OrderReceipt {
  id: string;
}
interface CheckoutDependencies {
  create: () => Promise<OrderReceipt>;
  refreshCart: () => Promise<unknown>;
  navigate: (orderId: string) => Promise<unknown>;
  onCreated?: () => void;
}
export function useCheckoutSubmission(dependencies: CheckoutDependencies) {
  const createdOrder = ref<OrderReceipt | null>(null);
  async function submit() {
    if (!createdOrder.value) {
      createdOrder.value = await dependencies.create();
      dependencies.onCreated?.();
    }
    const receipt = createdOrder.value;
    // Cart refresh must never block navigation to an already committed order.
    void dependencies.refreshCart().catch(() => {});
    await dependencies.navigate(receipt.id);
    return receipt;
  }
  return { createdOrder, submit };
}
