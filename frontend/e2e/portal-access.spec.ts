import { expect, test } from "@playwright/test";

test("anonymous visitor is directed to sign in before opening operations", async ({ page }) => {
  await page.goto("/operations/dashboard");
  await expect(page).toHaveURL(/\/login\?next=/);
  await expect(page.getByRole("heading", { name: /sign in/i })).toBeVisible();
});

test("anonymous visitor is directed to sign in before opening customer pages", async ({ page }) => {
  await page.goto("/customer/dashboard");
  await expect(page).toHaveURL(/\/login\?next=/);
});

test("provisioned operations user can open the operational workspace", async ({ page }) => {
  test.skip(!process.env.E2E_OPERATIONS_EMAIL || !process.env.E2E_OPERATIONS_PASSWORD, "Set E2E_OPERATIONS_EMAIL and E2E_OPERATIONS_PASSWORD to run authenticated role checks.");
  await page.goto("/login");
  await page.getByLabel("Email address").fill(process.env.E2E_OPERATIONS_EMAIL!);
  await page.getByLabel("Password").fill(process.env.E2E_OPERATIONS_PASSWORD!);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/operations\/dashboard/);
  await page.goto("/operations/reconciliation");
  await expect(page.getByRole("heading", { name: "Reconciliation control" })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("navigation", { name: "Operations mobile navigation" })).toBeVisible();
  const browserTokens = await page.evaluate(() => ({ local: Object.keys(localStorage), session: Object.keys(sessionStorage), cookies: document.cookie }));
  expect(JSON.stringify(browserTokens)).not.toMatch(/accessToken|refreshToken/i);
});

test("customer identity cannot enter the operations workspace", async ({ page }) => {
  test.skip(!process.env.E2E_CUSTOMER_EMAIL || !process.env.E2E_CUSTOMER_PASSWORD, "Set E2E_CUSTOMER_EMAIL and E2E_CUSTOMER_PASSWORD to run authenticated role checks.");
  await page.goto("/login");
  await page.getByLabel("Email address").fill(process.env.E2E_CUSTOMER_EMAIL!);
  await page.getByLabel("Password").fill(process.env.E2E_CUSTOMER_PASSWORD!);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/customer\/dashboard/);
  await page.goto("/operations/dashboard");
  await expect(page).toHaveURL(/\/customer\/dashboard/);
});
