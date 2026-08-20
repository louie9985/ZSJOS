# Cashback API

ZSJOS cashback has two independent types and five states. A valid-lead cashback is a fixed product-rule amount keyed by `valid:<leadId>`. A deal cashback is an order-item snapshot amount multiplied by its rate with `HALF_UP` two-decimal rounding and is keyed by `deal:<orderItemId>`. Both begin in `pending_settlement`; the only other states are `available`, `withdrawing`, `withdrawn`, and `cancelled`.

Only a Lead whose immutable source is an enabled ordinary partner can generate new cashback. For Leads submitted through the independent Partner account introduced by V072, `sourceUserId` identifies an enabled `zsjos_partner_account` belonging to the Lead's `partnerId`; the cashback is owned by `partnerId` and leaves the System-user-only `beneficiaryUserId` empty. Pre-V072 Leads that retain the former bound System-user identifier remain compatible and retain that identifier in `beneficiaryUserId`. New-media and sales-self Leads return without generating a record. Product rules override level-one category defaults; nullable product values mean inheritance. Amounts must be non-negative and rates must be between zero and one inclusive. The observation days configuration defaults to seven, is limited to 0 through 365, and is copied into each record with the product and rule snapshot.

The independent partner frontend uses `GET /part-api/zsjos/cashback/my-page` and `GET /part-api/zsjos/cashback/my-summary`; both require `zsjos:cashback:my-query` and restrict rows by the authenticated account's `partnerId`. `GET /admin-api/zsjos/cashback/page` remains the finance-wide administrator view and requires `zsjos:cashback:finance-query`.

The hourly scheduler pauses during maintenance. Mature valid cashback does not recheck the current Lead qualification state. Mature deal cashback becomes available only after the linked order is effective. State transitions use version-and-source-state conditions.

The deal-generation API is currently a domain service boundary. Its order-create, finance-reject cancellation, and withdrawal-lock integration must be serialized after the active order workstream is integrated; they are not represented as complete by V051.
