import { NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export function GET() {
  return NextResponse.json({ status: "UP", service: "payment-platform-frontend" }, {
    headers: { "Cache-Control": "no-store" },
  });
}
