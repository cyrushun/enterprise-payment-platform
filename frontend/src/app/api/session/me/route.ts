import { NextResponse } from "next/server";
import { readSession } from "@/lib/session/server";
export const runtime = "nodejs";
export async function GET() {
  const session = await readSession();
  if (!session) return NextResponse.json({ message: "Unauthenticated" }, { status: 401 });
  return NextResponse.json({ user: session.user });
}
