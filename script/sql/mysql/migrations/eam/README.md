# EAM database module

Execution order is `V001` through `V008`. `V007` adds lightweight office procurement,
company-wide inventory, employee holdings, lifecycle tasks, and their menu permissions.
`V008` makes HRM `employee_id` the only EAM ownership key. Historical System user IDs
in ownership columns are discarded rather than reinterpreted as employee IDs; development
records that still need an owner must be reassigned through the HRM employee selector.

The module is non-destructive and repeatable. It never creates purchase, inventory,
employee-asset, or test business rows. Existing categories are deliberately left with
null delivery/custody policies; an administrator must confirm root-category policies
before those categories can be used by new procurement records.

Demand, purchase, inventory-balance, and receipt records retain custom-field values,
display-label snapshots, and dictionary-type snapshots. Receipt rows store the normalized
actual snapshot even when the operator keeps the original purchase values unchanged.

The payment selector reads administrator-maintained dictionary type
`eam_purchase_payment_mode`. This migration creates the empty dictionary type so tenants
can configure it through System, but it does not invent or seed any payment options.
Configure and review the options separately before enabling purchase creation.

The existing custom-field key `package_expiry` is the due-date source for digital-asset
reminder projections. Its value remains in the category custom-field snapshot; the stock
balance stores only the queryable nearest-date projection used by the tenant job.

The BPM definitions `eam_asset_demand`, `eam_office_purchase`,
`eam_purchase_expense`, and `eam_employee_asset_review` must be deployed through the BPM
module. Approval users, countersigning, rejection, cancellation, copying, and history are
owned by BPM, not by EAM tables.

Rollback is schema-retaining once business rows exist. Disable the added menus and stop
the EAM jobs if runtime rollback is required; do not drop populated tables.
