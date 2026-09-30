#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-off tenant-1 current-organization attribution repair for order 8854.

Requires V279+, the exact effective first-purchase order totaling 2380.00,
matching current-sourced receipt and System organization mapping. Run without
--apply to freeze a private plan and rehearse, then with --apply to insert only
the absent ORDER fact. Uses the preceding single-order repair's CLI, conflict
guards, transaction, rollback/repeat tests, UTF-8 checks and private evidence.
No existing order/fact, schema or version ledger is changed. Post-commit removal
requires separate approval and the saved exact inserted ID and after-image.
Organization remains current provenance, never historical frozen evidence.
"""
import repair_single_order_attribution_20260930 as repair


def main():
    # Reuse the guarded executor without expanding the earlier repair's targets.
    old_guard = ('o.total_amount=3480.00 AND o.id=721 '
                 'AND o.formal_sales_user_id=41 AND o.lead_id=5985')
    new_guard = ('o.total_amount=2380.00 AND o.id=8854 '
                 'AND o.formal_sales_user_id=291 AND o.lead_id=5109')
    repair.require(repair.SOURCE.count(old_guard) == 1,
                   'Referenced repair changed; inspect before proceeding')
    repair.SOURCE = repair.SOURCE.replace(old_guard, new_guard)
    repair.SOURCE = repair.SOURCE.replace("a.org_source='current'",
                                         "a.org_source='current' AND a.fact_id=74877")

    repair.ORDERS = ('OR202609180020',)
    repair.MARK = 'order-8854-attribution-20260930'
    repair.main()


if __name__ == '__main__':
    main()
