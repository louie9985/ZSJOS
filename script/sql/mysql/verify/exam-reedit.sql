-- UTF-8. Read-only V287 structural and dual-ledger acceptance.
SELECT 'V287 exam revoke reedit' AS check_name,
  IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND is_nullable='YES' AND column_default IS NULL
      AND ((column_name IN ('revoked_at','reedit_claimed_at') AND data_type='datetime' AND datetime_precision=3)
        OR (column_name='revoked_by' AND data_type='bigint')
        OR (column_name='reedit_operation_key' AND data_type='varchar' AND character_maximum_length=64)))=4
    AND EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V287')
    AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287'),
    'PASS','FAIL') AS result;
