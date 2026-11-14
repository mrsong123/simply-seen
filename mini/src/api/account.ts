import { request } from "../utils/request";
import type { Member, Session } from "../types/models";
import type {
  PhoneLogin,
  PhoneProof,
  SmsChallenge,
  IdentityVerification,
} from "../types/account";

export const accountApi = {
  sendLoginCode: (phone: string) =>
    request.post<SmsChallenge>(
      "/auth/phone/code",
      { phone },
      { anonymous: true },
    ),
  loginPhone: (body: PhoneLogin) =>
    request.post<Session>("/auth/phone", body, { anonymous: true }),
  sendBindingCode: (phone: string) =>
    request.post<SmsChallenge>("/me/phone/code", { phone }),
  bindPhone: (body: PhoneProof) => request.post<Member>("/me/phone", body),
  bindWechat: (code: string) => request.post<Member>("/me/wechat", { code }),
  identity: () => request.get<IdentityVerification>("/me/identity"),
  verifyIdentity: (name: string, idNumber: string, accepted: boolean) =>
    request.post<IdentityVerification>("/me/identity", {
      name,
      idNumber,
      accepted,
    }),
};
