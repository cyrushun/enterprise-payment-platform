import { describe, expect, it } from "vitest";
import { formatMoney, isReplayEligible, isUuid } from "../src/lib/operations/format";

describe("operations validation and formatting", () => {
  it("accepts a valid UUID and rejects arbitrary identifiers", () => {
    expect(isUuid("550e8400-e29b-41d4-a716-446655440000")).toBe(true);
    expect(isUuid("not-a-uuid")).toBe(false);
    expect(isUuid("")).toBe(false);
  });

  it("formats MYR values as currency", () => {
    expect(formatMoney("125.50", "MYR")).toContain("125.50");
    expect(formatMoney(10, "MYR")).toContain("10.00");
  });

  it("allows replay only for DEAD outbox events", () => {
    expect(isReplayEligible("DEAD")).toBe(true);
    for (const status of ["PENDING", "PROCESSING", "RETRY", "PUBLISHED", "dead"]) {
      expect(isReplayEligible(status)).toBe(false);
    }
  });
});
