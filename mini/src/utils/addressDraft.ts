import type { Address } from "../types/models";
import { authStorage } from "./storage";

let draft: { address: Address; token: string } | undefined;

/** Imported native address stays in memory until the member explicitly saves it to the API. */
export function saveAddressDraft(address: Address): void {
  uni.removeStorageSync("sj-address-draft");
  draft = { address: { ...address }, token: authStorage.token() };
}

export function takeAddressDraft(): Address | undefined {
  uni.removeStorageSync("sj-address-draft");
  const current = draft;
  draft = undefined;
  return current?.token && current.token === authStorage.token()
    ? current.address
    : undefined;
}
