import { createHash } from "node:crypto";
import type { AppSession } from "./crypto";

const backend = () => (process.env.API_PROXY_TARGET ?? "http://127.0.0.1:8080").replace(/\/$/, "");

// Refresh-token rotation is single-use. Coalesce concurrent refreshes inside this Node process
// so parallel customer queries do not race the same refresh token and trigger family revocation.
const refreshFlights = new Map<string, Promise<AppSession | null>>();
const refreshResults = new Map<string, { session: AppSession; expiresAt: number }>();

export async function refreshSession(session: AppSession): Promise<AppSession | null> {
  const key = createHash("sha256").update(session.refreshToken).digest("hex");
  const cached = refreshResults.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.session;
  if (cached) refreshResults.delete(key);
  const active = refreshFlights.get(key);
  if (active) return active;
  const flight = (async (): Promise<AppSession | null> => {
    try {
      const response = await fetch(`${backend()}/api/v1/auth/refresh`, {
        method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ refreshToken: session.refreshToken }), cache: "no-store",
      });
      if (!response.ok) return null;
      const tokens = await response.json() as { accessToken: string; refreshToken: string; expiresIn: number };
      const refreshed = { ...session, ...tokens, accessTokenExpiresAt: Date.now() + tokens.expiresIn * 1000 };
      refreshResults.set(key, { session: refreshed, expiresAt: Date.now() + 120_000 });
      if (refreshResults.size > 500) {
        for (const [oldKey, result] of refreshResults) if (result.expiresAt <= Date.now()) refreshResults.delete(oldKey);
      }
      return refreshed;
    } catch { return null; }
  })();
  refreshFlights.set(key, flight);
  try { return await flight; } finally { refreshFlights.delete(key); }
}

export function backendBaseUrl(): string { return backend(); }
