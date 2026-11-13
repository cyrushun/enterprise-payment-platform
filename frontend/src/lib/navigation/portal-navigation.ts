export type PortalKind = "customer" | "operations";
export interface NavigationItem { label: string; href: string; icon: string; }
export const customerNavigation: NavigationItem[] = [
  { label: "Overview", href: "/customer/dashboard", icon: "layout-dashboard" },
  { label: "My accounts", href: "/customer/accounts", icon: "wallet" },
  { label: "Transfers", href: "/customer/transfers", icon: "arrow-left-right" },
  { label: "Activity", href: "/customer/activity", icon: "receipt-text" },
  { label: "Notifications", href: "/customer/notifications", icon: "bell" },
];
export const operationsNavigation: NavigationItem[] = [
  { label: "Overview", href: "/operations/dashboard", icon: "layout-dashboard" },
  { label: "Customers", href: "/operations/customers", icon: "users" },
  { label: "Transfers", href: "/operations/transfers", icon: "arrow-left-right" },
  { label: "Reconciliation", href: "/operations/reconciliation", icon: "scale" },
  { label: "Audit logs", href: "/operations/audit-logs", icon: "clipboard-list" },
  { label: "Outbox events", href: "/operations/outbox", icon: "workflow" },
  { label: "Notifications", href: "/operations/notifications", icon: "bell" },
];
