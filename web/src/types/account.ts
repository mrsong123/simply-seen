export interface PhoneChallenge {
  challengeId: string;
  expiresIn: number;
  retryAfter: number;
  development: boolean;
  devCode?: string;
}
export interface PhoneCredentials {
  phone: string;
  code: string;
  challengeId: string;
}
export interface PhoneLogin extends PhoneCredentials {
  inviteCode?: string;
  accepted: boolean;
}
export interface IdentityStatus {
  status: "UNVERIFIED" | "PENDING" | "VERIFIED" | "REJECTED";
  nameMasked: string;
  idNumberMasked: string;
  verifiedAt: number | null;
  rejectionReason: string;
  development: boolean;
  provider: string;
  retryAfter?: number;
}
export interface IdentitySubmission {
  name: string;
  idNumber: string;
  accepted: boolean;
}
