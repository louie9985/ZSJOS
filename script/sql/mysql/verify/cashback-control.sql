-- UTF-8. Read-only scoped verification after V281; no business data or role changes.
SET NAMES utf8mb4;
SELECT 'control_columns', IF(COUNT(*)=4,'PASS','FAIL') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_cashback' AND column_name IN ('blocked_from_status','block_reason','blocked_by_user_id','blocked_at') AND is_nullable='YES';
SELECT 'control_permissions',IF(COUNT(*)=2,'PASS','FAIL') FROM system_menu c JOIN system_menu p ON p.id=c.parent_id WHERE c.permission IN ('zsjos:cashback:block','zsjos:cashback:unblock') AND c.type=3 AND c.deleted=b'0' AND p.permission='zsjos:cashback:finance-query' AND p.deleted=b'0';
SELECT permission,name,HEX(name) FROM system_menu WHERE permission IN ('zsjos:cashback:block','zsjos:cashback:unblock') AND deleted=b'0';
SELECT 'control_version',IF(COUNT(*)=1,'PASS','FAIL') FROM zsjos_schema_version WHERE version='V281';
