import { NextResponse } from "next/server";
import { backendBaseUrl, refreshSession } from "@/lib/session/backend";
import { clearSession, readSession, writeSession } from "@/lib/session/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type ActuatorHealth = { status?: string; components?: Record<string, { status?: string }> };

export async function GET() {
  let session = await readSession();
  if (!session) return NextResponse.json({ message: "Unauthenticated" }, { status: 401 });
  if (!session.user.roles.some((role) => ["OPERATIONS", "ADMIN"].includes(role))) {
    return NextResponse.json({ message: "Forbidden" }, { status: 403 });
  }
  try {
    if (session.accessTokenExpiresAt <= Date.now() + 30_000) {
      const refreshed = await refreshSession(session);
      if (!refreshed) { await clearSession(); return NextResponse.json({ message: "Session expired" }, { status: 401 }); }
      session = refreshed; await writeSession(session);
    }
    let response = await fetch(`${backendBaseUrl()}/actuator/health`, {
      headers: { Authorization: `Bearer ${session.accessToken}`, Accept: "application/json" },
      cache: "no-store",
    });
    if (response.status === 401) {
      const refreshed = await refreshSession(session);
      if (!refreshed) { await clearSession(); return NextResponse.json({ message: "Session expired" }, { status: 401 }); }
      session = refreshed; await writeSession(session);
      response = await fetch(`${backendBaseUrl()}/actuator/health`, { headers: { Authorization: `Bearer ${session.accessToken}`, Accept: "application/json" }, cache: "no-store" });
    }
    if (!response.ok) return NextResponse.json({ message: "Health endpoint unavailable" }, { status: 502 });
    const body = await response.json() as ActuatorHealth;
    const components = Object.fromEntries(
      Object.entries(body.components ?? {}).map(([name, component]) => [name, component.status ?? "UNKNOWN"]),
    );
    return NextResponse.json({ status: body.status ?? "UNKNOWN", components });
  } catch {
    return NextResponse.json({ message: "Payment API is unavailable" }, { status: 502 });
  }
}
