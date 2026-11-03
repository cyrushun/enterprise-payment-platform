# Phase 1 API examples

Base URL: `http://localhost:8080`

## Create customer

```powershell
$customer = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/customers -ContentType 'application/json' -Body (@{ firstName='Alex'; lastName='Tan'; email='alex.tan@example.test'; phoneNumber='+60123456789' } | ConvertTo-Json)
$customer | ConvertTo-Json -Depth 5
$customerId = $customer.customerId
```

## Get customer

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/customers/$customerId
```

## Open savings account

```powershell
$account = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/accounts -ContentType 'application/json' -Body (@{ customerId=$customerId; accountType='SAVINGS'; currency='MYR' } | ConvertTo-Json)
$account | ConvertTo-Json -Depth 5
$accountId = $account.accountId
```

## Get account and balance

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/accounts/$accountId
Invoke-RestMethod http://localhost:8080/api/v1/accounts/$accountId/balance
```

## Expected validation behavior

- Invalid email or missing fields: HTTP 400 with `VALIDATION_ERROR`.
- Unknown customer/account UUID: HTTP 404.
- Duplicate email: HTTP 409.
- Attempt to open an account for a suspended/closed customer: HTTP 422.
