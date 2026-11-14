import type { PhoneChallenge, PhoneCredentials } from "@/types/account";
export const validPhone = (phone: string) => /^1[3-9]\d{9}$/.test(phone.trim());

/** In-memory challenge state. Codes, phone numbers and consent never enter storage. */
export class PhoneChallengeState {
  private challenge: PhoneChallenge | null = null;
  private challengedPhone = "";
  private expiresAt = 0;
  private retryAt = new Map<string, number>();

  accept(phone: string, challenge: PhoneChallenge, now: number) {
    if (
      !challenge.challengeId ||
      !Number.isFinite(challenge.expiresIn) ||
      challenge.expiresIn <= 0 ||
      !Number.isFinite(challenge.retryAfter) ||
      challenge.retryAfter < 0
    )
      throw new Error("验证码服务返回异常，请稍后重试");
    this.retryAt.set(phone, now + Math.max(0, challenge.retryAfter) * 1000);
    this.challenge = challenge;
    this.challengedPhone = phone;
    this.expiresAt = now + Math.max(0, challenge.expiresIn) * 1000;
  }
  clear() {
    this.challenge = null;
    this.challengedPhone = "";
    this.expiresAt = 0;
  }
  remaining(phone: string, now: number) {
    return Math.max(
      0,
      Math.ceil(((this.retryAt.get(phone) || 0) - now) / 1000),
    );
  }
  credentials(phone: string, code: string, now: number): PhoneCredentials {
    if (!validPhone(phone)) throw new Error("请输入有效的11位中国大陆手机号");
    if (!this.challenge || this.challengedPhone !== phone)
      throw new Error("请先为当前手机号获取验证码");
    if (now >= this.expiresAt) throw new Error("验证码已过期，请重新获取");
    if (!/^\d{6}$/.test(code.trim())) throw new Error("请输入6位短信验证码");
    return {
      phone,
      code: code.trim(),
      challengeId: this.challenge.challengeId,
    };
  }
  developmentCode() {
    return this.challenge?.development &&
      /^\d{6}$/.test(this.challenge.devCode || "")
      ? this.challenge.devCode!
      : "";
  }
}

export function identityInputError(
  name: string,
  rawId: string,
  today = new Date(),
) {
  if (name.trim().length < 2 || name.trim().length > 40)
    return "请输入证件上的真实姓名（2至40个字符）";
  const id = rawId.trim().toUpperCase();
  if (!/^[1-9]\d{16}[0-9X]$/.test(id))
    return "请输入18位中国大陆居民身份证号码";
  const year = Number(id.slice(6, 10)),
    month = Number(id.slice(10, 12)),
    day = Number(id.slice(12, 14));
  const date = new Date(year, month - 1, day);
  if (
    year < 1900 ||
    date.getFullYear() !== year ||
    date.getMonth() !== month - 1 ||
    date.getDate() !== day ||
    date > today
  )
    return "身份证出生日期无效";
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  const checksum = "10X98765432"[
    weights.reduce((sum, weight, i) => sum + weight * Number(id[i]), 0) % 11
  ];
  return checksum === id[17] ? "" : "身份证校验位不正确，请核对证件号码";
}

export function withdrawalRequirements(
  profile: {
    identityStatus?: string;
    identityDevelopment?: boolean;
    wechatBound?: boolean;
  },
  config: { devMode?: boolean; identityRequiredForWithdrawal?: boolean },
) {
  const identityRequired =
    !config.devMode || config.identityRequiredForWithdrawal !== false;
  const identityReady =
    profile.identityStatus === "VERIFIED" &&
    (Boolean(config.devMode) || !profile.identityDevelopment);
  return {
    needsIdentity: identityRequired && !identityReady,
    needsWechat: !config.devMode && !profile.wechatBound,
  };
}

/** Terminal proofs must not be resubmitted after consumption or lockout. */
export function phoneChallengeMustReset(failure: unknown) {
  const code =
    typeof failure === "object" && failure !== null && "code" in failure
      ? String(failure.code)
      : "";
  return [
    "SMS_CODE_EXPIRED",
    "SMS_CODE_LOCKED",
    "SMS_CODE_USED",
    "SMS_CODE_SUPERSEDED",
    "SMS_CODE_DEVELOPMENT",
    "PHONE_IN_USE",
    "PHONE_ALREADY_BOUND",
  ].includes(code);
}
