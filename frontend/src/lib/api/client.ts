import type { ApiErrorBody } from "@/types/api";
export class ApiClientError extends Error {
  constructor(public readonly status: number, public readonly body: ApiErrorBody | null) { super(body?.message ?? `API request failed with HTTP ${status}`); this.name = "ApiClientError"; }
}
/** Browser requests go to the same-origin Next.js BFF; bearer tokens never enter browser JavaScript. */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!path.startsWith("/api/v1/")) throw new Error("API paths must start with /api/v1/");
  const headers = new Headers(init.headers); headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await fetch(`/api/backend${path.slice(4)}`, { ...init, headers, credentials: "same-origin", cache: "no-store" });
  if (!response.ok) { const body = await response.json().catch(() => null) as ApiErrorBody | null; if (response.status === 401 && typeof window !== "undefined") window.location.assign("/login"); throw new ApiClientError(response.status, body); }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
