import { request } from "../utils/request";
import type { Address } from "../types/models";
export const addressApi = {
  list: () => request.get<Address[]>("/addresses"),
  save: (data: Address) => request.post<{ id: number }>("/addresses", data),
  remove: (id: number) => request.delete("/addresses/" + id),
};
