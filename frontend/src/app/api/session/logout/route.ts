import { NextRequest, NextResponse } from "next/server";
import { clearSession, readSession } from "@/lib/session/server";
import { backendBaseUrl } from "@/lib/session/backend";
export const runtime = "nodejs";
export async function POST(request: NextRequest) {
  if (request.headers.get("origin") !== request.nextUrl.origin) return NextResponse.json({ message: "Invalid request origin" }, { status: 403 });
  const session = await readSession();
  if (session) await fetch(`${backendBaseUrl()}/api/v1/auth/logout`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken: session.refreshToken }), cache: "no-store" }).catch(() => undefined);
  await clearSession();
  return new NextResponse(null, { status: 204 });
}
