import { describe, expect, it } from "vitest";
import { customerNavigation, operationsNavigation } from "../src/lib/navigation/portal-navigation";

describe("portal navigation", () => {
  it("keeps customer and operations route namespaces separate", () => {
    expect(customerNavigation.every((item) => item.href.startsWith("/customer/"))).toBe(true);
    expect(operationsNavigation.every((item) => item.href.startsWith("/operations/"))).toBe(true);
  });
  it("includes the primary operational controls", () => {
    const routes = operationsNavigation.map((item) => item.href);
    expect(routes).toContain("/operations/reconciliation");
    expect(routes).toContain("/operations/audit-logs");
    expect(routes).toContain("/operations/outbox");
  });
});
