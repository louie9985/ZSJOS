# Cashback API

ZSJOS cashback has two independent types and five states. A valid-lead cashback is a fixed product-rule amount keyed by `valid:<leadId>`. A deal cashback is an order-item snapshot amount multiplied by its rate with `HALF_UP` two-decimal rounding and is keyed by `deal:<orderItemId>`. Both begin in `pending_settlement`; the only other states are `available`, `withdrawing`, `withdrawn`, and `cancelled`.

Only a Lead whose immutable source is an enabled ordinary partner can generate new cashback. New-media and sales-self Leads return without generating a record. Product rules override level-one category defaults; nullable product values mean inheritance. Amounts must be non-negative and rates must be between zero and one inclusive. The observation days configuration defaults to seven, is limited to 0 through 365, and is copied into each record with the product and rule snapshot.

`GET /admin-api/zsjos/cashback/my-page` requires `zsjos:cashback:my-query` and restricts rows to the current beneficiary. `GET /admin-api/zsjos/cashback/page` requires `zsjos:cashback:finance-query` and is the finance-wide view. Neither permission is assigned to roles by migration.

The hourly scheduler pauses during maintenance. Mature valid cashback does not recheck the current Lead qualification state. Mature deal cashback becomes available only after the linked order is effective. State transitions use version-and-source-state conditions.

The deal-generation API is currently a domain service boundary. Its order-create, finance-reject cancellation, and withdrawal-lock integration must be serialized after the active order workstream is integrated; they are not represented as complete by V051.
