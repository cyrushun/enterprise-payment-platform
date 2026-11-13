import { cookies } from "next/headers";
import { openSession, sealSession, SESSION_COOKIE, SESSION_MAX_AGE, type AppSession } from "./crypto";

export async function readSession(): Promise<AppSession | null> {
  const jar = await cookies();
  const value = jar.get(SESSION_COOKIE)?.value;
  return value ? openSession(value) : null;
}

export async function writeSession(session: AppSession): Promise<void> {
  const jar = await cookies();
  jar.set(SESSION_COOKIE, sealSession(session), {
    httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge: SESSION_MAX_AGE,
  });
}

export async function clearSession(): Promise<void> {
  const jar = await cookies();
  jar.set(SESSION_COOKIE, "", { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge: 0 });
}
