export interface TokenResponse { accessToken: string; refreshToken: string; expiresIn: number; tokenType: "Bearer" | string; }
export interface MeResponse { userId: string; customerId: string | null; email: string; roles: string[]; }
export interface AccountResponse { accountId: string; customerId: string; accountNumber: string; accountType: string; currency: string; balance: string | number; status: string; openedAt: string; }
export interface AccountBalanceResponse { accountId: string; balance: string | number; currency: string; }
export interface TransferResponse { id: string; reference: string; sourceAccountId: string; destinationAccountId: string; amount: string | number; currency: string; status: string; createdAt: string; }
export interface PageResponse<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number; }
export interface ApiErrorBody { code: string; message: string; timestamp: string; details: unknown[]; }
