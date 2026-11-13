import { NextRequest, NextResponse } from "next/server";
import { backendBaseUrl, refreshSession } from "@/lib/session/backend";
import { readSession, writeSession, clearSession } from "@/lib/session/server";
import type { AppSession } from "@/lib/session/crypto";
export const runtime = "nodejs";

async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const method = request.method;
  if (!["GET", "HEAD", "OPTIONS"].includes(method) && request.headers.get("origin") !== request.nextUrl.origin)
    return NextResponse.json({ message: "Invalid request origin" }, { status: 403 });
  let session = await readSession();
  if (!session) return NextResponse.json({ message: "Unauthenticated" }, { status: 401 });
  const target = `${backendBaseUrl()}/api/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`;
  const requestBody = ["GET", "HEAD"].includes(method) ? undefined : await request.arrayBuffer();
  if (session.accessTokenExpiresAt <= Date.now() + 30_000) {
    const refreshed = await refreshSession(session);
    if (!refreshed) { await clearSession(); return NextResponse.json({ message: "Session expired" }, { status: 401 }); }
    session = refreshed; await writeSession(session);
  }
  const send = (current: AppSession) => fetch(target, {
    method, headers: { Authorization: `Bearer ${current.accessToken}`, Accept: request.headers.get("accept") ?? "application/json", ...(request.headers.get("content-type") ? { "Content-Type": request.headers.get("content-type")! } : {}), ...(request.headers.get("idempotency-key") ? { "Idempotency-Key": request.headers.get("idempotency-key")! } : {}) },
    body: requestBody, cache: "no-store",
  });
  let upstream: Response;
  try { upstream = await send(session); } catch { return NextResponse.json({ message: "Payment API is unavailable" }, { status: 502 }); }
  if (upstream.status === 401) {
    const refreshed = await refreshSession(session);
    if (!refreshed) { await clearSession(); return NextResponse.json({ message: "Session expired" }, { status: 401 }); }
    session = refreshed; await writeSession(session);
    try { upstream = await send(session); } catch { return NextResponse.json({ message: "Payment API is unavailable" }, { status: 502 }); }
  }
  const headers = new Headers();
  for (const name of ["content-type", "cache-control", "x-request-id"]) { const value = upstream.headers.get(name); if (value) headers.set(name, value); }
  return new NextResponse(upstream.body, { status: upstream.status, headers });
}
export const GET = proxy; export const POST = proxy; export const PUT = proxy; export const PATCH = proxy; export const DELETE = proxy;
