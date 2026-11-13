export function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

export function formatMoney(value: string | number, currency: string): string {
  return new Intl.NumberFormat("en-MY", { style: "currency", currency }).format(Number(value));
}

export function isReplayEligible(status: string): boolean {
  return status === "DEAD";
}
