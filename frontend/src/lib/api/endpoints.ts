export const apiEndpoints = {
  auth: { register: "/api/v1/auth/register", login: "/api/v1/auth/login", refresh: "/api/v1/auth/refresh", logout: "/api/v1/auth/logout", me: "/api/v1/auth/me" },
  accounts: "/api/v1/accounts",
  account: (id: string) => `/api/v1/accounts/${id}`,
  accountBalance: (id: string) => `/api/v1/accounts/${id}/balance`,
  accountTransfers: (id: string) => `/api/v1/accounts/${id}/transfers?page=0&size=20`,
  transfers: "/api/v1/transfers",
  notifications: (accountId: string) => `/api/v1/notifications?accountId=${encodeURIComponent(accountId)}&page=0&size=20`,
} as const;
