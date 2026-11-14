import request from "@/utils/request";
import type { MemberProfile } from "@/types/models";
import type {
  PhoneChallenge,
  PhoneCredentials,
  IdentityStatus,
  IdentitySubmission,
} from "@/types/account";
export const accountApi = {
  sendBindingCode: (phone: string) =>
    request.post<PhoneChallenge>("/me/phone/code", { phone }),
  bindPhone: (body: PhoneCredentials) =>
    request.post<MemberProfile>("/me/phone", body),
  getIdentity: () => request.get<IdentityStatus>("/me/identity"),
  verifyIdentity: (body: IdentitySubmission) =>
    request.post<IdentityStatus>("/me/identity", body),
};
