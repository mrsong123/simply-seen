export interface SmsChallenge {
  challengeId: string;
  expiresIn: number;
  retryAfter: number;
  development: boolean;
  devCode?: string;
}

export interface PhoneProof {
  phone: string;
  code: string;
  challengeId: string;
}

export interface PhoneLogin extends PhoneProof {
  inviteCode?: string;
  accepted: boolean;
}

export type IdentityStatus = "UNVERIFIED" | "PENDING" | "VERIFIED" | "REJECTED";

export interface IdentityVerification {
  status: IdentityStatus;
  nameMasked: string;
  idNumberMasked: string;
  verifiedAt: number | null;
  rejectionReason: string;
  development: boolean;
  provider: string;
  retryAfter?: number;
}
