import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

export interface AppSession {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: number;
  user: { userId: string; customerId: string | null; email: string; roles: string[] };
}

function key(): Buffer {
  const secret = process.env.SESSION_SECRET;
  if (!secret || secret.length < 32 || secret.startsWith("replace-with-a-unique-random-secret")) throw new Error("Set a unique SESSION_SECRET containing at least 32 characters");
  return Buffer.from(secret, "utf8").subarray(0, 32);
}

export function assertSessionKeyConfigured(): void { key(); }

export function sealSession(session: AppSession): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key(), iv);
  const encrypted = Buffer.concat([cipher.update(JSON.stringify(session), "utf8"), cipher.final()]);
  return [iv, cipher.getAuthTag(), encrypted].map((part) => part.toString("base64url")).join(".");
}

export function openSession(value: string): AppSession | null {
  try {
    const [iv, tag, encrypted] = value.split(".").map((part) => Buffer.from(part, "base64url"));
    if (!iv || !tag || !encrypted) return null;
    const decipher = createDecipheriv("aes-256-gcm", key(), iv);
    decipher.setAuthTag(tag);
    const json = Buffer.concat([decipher.update(encrypted), decipher.final()]).toString("utf8");
    const session = JSON.parse(json) as AppSession;
    if (!session.accessToken || !session.refreshToken || !session.user?.userId) return null;
    return session;
  } catch { return null; }
}

export const SESSION_COOKIE = "epp_session";
export const SESSION_MAX_AGE = 7 * 24 * 60 * 60;
