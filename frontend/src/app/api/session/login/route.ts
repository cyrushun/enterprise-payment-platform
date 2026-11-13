import { NextRequest, NextResponse } from "next/server";
import { writeSession } from "@/lib/session/server";
import { assertSessionKeyConfigured } from "@/lib/session/crypto";
import { backendBaseUrl } from "@/lib/session/backend";

export const runtime = "nodejs";
export async function POST(request: NextRequest) {
  if (request.headers.get("origin") !== request.nextUrl.origin) return NextResponse.json({ message: "Invalid request origin" }, { status: 403 });
  try { assertSessionKeyConfigured(); } catch { return NextResponse.json({ message: "Session encryption is not configured" }, { status: 500 }); }
  const body = await request.json().catch(() => null) as { email?: string; password?: string } | null;
  if (!body?.email || !body.password) return NextResponse.json({ message: "Email and password are required" }, { status: 400 });
  try {
    const login = await fetch(`${backendBaseUrl()}/api/v1/auth/login`, { method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify(body), cache: "no-store" });
    if (!login.ok) return NextResponse.json({ message: login.status === 401 ? "Invalid email or password" : "Unable to sign in" }, { status: login.status });
    const tokens = await login.json() as { accessToken: string; refreshToken: string; expiresIn: number };
    const meResponse = await fetch(`${backendBaseUrl()}/api/v1/auth/me`, { headers: { Authorization: `Bearer ${tokens.accessToken}`, Accept: "application/json" }, cache: "no-store" });
    if (!meResponse.ok) { await fetch(`${backendBaseUrl()}/api/v1/auth/logout`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken: tokens.refreshToken }), cache: "no-store" }).catch(() => undefined); return NextResponse.json({ message: "Could not load user profile" }, { status: 502 }); }
    const me = await meResponse.json() as { userId: string; customerId: string | null; email: string; roles?: string[] };
    await writeSession({ ...tokens, accessTokenExpiresAt: Date.now() + tokens.expiresIn * 1000, user: { ...me, roles: me.roles ?? [] } });
    return NextResponse.json({ user: { ...me, roles: me.roles ?? [] } });
  } catch { return NextResponse.json({ message: "Payment API is unavailable" }, { status: 502 }); }
}
