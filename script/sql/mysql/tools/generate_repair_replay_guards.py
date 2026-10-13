# -*- coding: utf-8 -*-
"""Generate self-contained completed-replay checks; never reads a database or private plan."""
import re
from pathlib import Path
import zsjos_db as db

ROOT = Path(__file__).resolve().parents[4]
MIGRATIONS = ROOT / 'script/sql/mysql/migrations'
AUDIT = "'$.version','$.updater','$.update_time'"
TABLES = ['zsjos_lead', 'zsjos_opportunity', 'zsjos_order', 'zsjos_order_item',
          'zsjos_order_approval_round', 'zsjos_performance_attribution']


def ledgers(version):
    return (f"EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='{version}') AND "
            f"EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='{version}')")


def signal(message):
    return f"SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='{message}';"


def current_image(table):
    columns, _, _ = db.desired_schema(ROOT / 'script/sql/mysql/schema/core.sql')
    parts = []
    for (t, column), (kind, _) in columns.items():
        if t != table:
            continue
        # Later migrations add fields outside the original V305 receipt.
        if (t, column) in {('zsjos_lead', 'submission_source_type'),
                           ('zsjos_order', 'repaired_actor_snapshot')}:
            continue
        val = f'x.`{column}`'
        if kind.startswith(('datetime', 'timestamp')):
            val = f"IF(MICROSECOND({val})=0,DATE_FORMAT({val},'%Y-%m-%d %H:%i:%s'),CAST({val} AS CHAR))"
        elif kind.startswith(('decimal', 'date')):
            val = f'CAST({val} AS CHAR)'
        elif kind.startswith('bit'):
            val = f'CAST({val} AS UNSIGNED)'
        parts += [f"'{column}'", val]
    return 'JSON_OBJECT(' + ','.join(parts) + ')'


def v305_verifier():
    text = """DROP PROCEDURE IF EXISTS zsjos_v305_completed;
DELIMITER $$
CREATE PROCEDURE zsjos_v305_completed(OUT completed BOOLEAN)
check_completed: BEGIN
 DECLARE receipt JSON;
 DECLARE row_table VARCHAR(64);
 DECLARE record_id BIGINT;
 DECLARE before_doc JSON;
 DECLARE after_doc JSON;
 DECLARE expected_doc JSON;
 DECLARE field_names JSON;
 DECLARE field_path VARCHAR(200);
 DECLARE field_index INT;
 DECLARE done BOOLEAN DEFAULT FALSE;
 DECLARE later CURSOR FOR SELECT table_name,row_id,before_image,after_image FROM v305_later ORDER BY step,table_name,row_id;
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=TRUE;
 SET completed=FALSE;
 IF NOT (""" + ledgers('V305') + """ ) THEN LEAVE check_completed; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 completion evidence table missing';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='order-history-v305'
   GROUP BY table_name,row_id,column_name HAVING COUNT(*)<>1) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 duplicate completion evidence';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='order-history-v305' AND table_name='__plan__' AND row_id=0 AND column_name='manifest') THEN
   IF EXISTS(SELECT 1 FROM zsjos_order WHERE tenant_id=1 AND deleted=0 AND status='effective'
      AND COALESCE(creator,'') NOT IN ('seed-liudandan','seed-students','seed-llyilin','seed-batch4'))
      OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='order-history-v305') THEN
     SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 completed ledger lacks reviewed plan receipt';
   END IF;
   SET completed=TRUE; LEAVE check_completed;
 END IF;
 SELECT CAST(old_value AS JSON) INTO receipt FROM zsjos_data_repair_backup
 WHERE repair_key='order-history-v305' AND table_name='__plan__' AND row_id=0 AND column_name='manifest';
 IF COALESCE(receipt->>'$.sha256','') NOT REGEXP '^[a-f0-9]{64}$'
   OR COALESCE(receipt->>'$.sourceSha','') NOT REGEXP '^[a-f0-9]{64}$'
   OR COALESCE(JSON_TYPE(receipt->'$.counts'),'')<>'OBJECT'
   OR JSON_LENGTH(receipt->'$.counts')=0 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 invalid reviewed plan receipt';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS v305_completion;
 CREATE TEMPORARY TABLE v305_completion(table_name VARCHAR(64),row_id BIGINT,expected_image JSON,current_image JSON,
   PRIMARY KEY(table_name,row_id)) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 INSERT INTO v305_completion(table_name,row_id,expected_image)
 SELECT table_name,row_id,CAST(old_value AS JSON) FROM zsjos_data_repair_backup
 WHERE repair_key='order-history-v305' AND column_name IN ('__after__','__inserted__');
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='order-history-v305' AND b.column_name='__before__'
   AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup a WHERE a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'))
 OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup a WHERE a.repair_key='order-history-v305' AND a.column_name='__after__'
   AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key=a.repair_key AND b.table_name=a.table_name AND b.row_id=a.row_id AND b.column_name='__before__')) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 incomplete before/after evidence';
 END IF;
 IF EXISTS(SELECT 1 FROM JSON_TABLE(JSON_KEYS(receipt->'$.counts'),'$[*]' COLUMNS(k VARCHAR(100) PATH '$')) counts
 WHERE COALESCE(JSON_EXTRACT(receipt,CONCAT('$.counts."',counts.k,'"')),-1)<>
   (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='order-history-v305'
    AND CONCAT(IF(b.column_name='__inserted__','insert:','update:'),b.table_name)=counts.k
    AND b.column_name IN ('__after__','__inserted__')))
 OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='order-history-v305' AND b.column_name IN ('__after__','__inserted__')
   AND JSON_CONTAINS_PATH(receipt,'one',CONCAT('$.counts."',IF(b.column_name='__inserted__','insert:','update:'),b.table_name,'"'))=0) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 evidence counts differ from reviewed receipt';
 END IF;
"""
    for table in TABLES:
        text += f" UPDATE v305_completion c JOIN {table} x ON c.table_name='{table}' AND c.row_id=x.id AND x.tenant_id=1 SET c.current_image={current_image(table)};\n"
    text += """ IF EXISTS(SELECT 1 FROM v305_completion WHERE current_image IS NULL OR NOT(expected_image->'$.id' <=> CAST(row_id AS JSON)) OR NOT(expected_image->'$.tenant_id' <=> CAST(1 AS JSON))) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 completion target missing or wrong identity';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS v305_later;
 CREATE TEMPORARY TABLE v305_later(step INT,table_name VARCHAR(64),row_id BIGINT,before_image JSON,after_image JSON,
   PRIMARY KEY(step,table_name,row_id)) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"""
    steps = [('order-identity-v306', 'V306'), ('lead-provider-v307', 'V307'),
             ('confirmed-provider-v308', 'V308'), ('media-assisted-source-v309', 'V309'),
             ('lead-source-confirmed-v309-r2', 'V309'), ('provider-identity-20261011', 'V308')]
    for n, (key, version) in enumerate(steps):
        completed = ledgers(version)
        if key == 'provider-identity-20261011':
            import json
            from provider_identity_repair import MANIFEST, digest
            manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
            completed += f" AND (SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='{key}')={len(manifest['rows'])*3+1}"
            completed += f" AND EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='{key}' AND table_name='__repair__' AND row_id=0 AND column_name='__manifest__' AND old_value='{digest(manifest)}')"
        text += f""" IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id WHERE b.repair_key='{key}') THEN
  IF NOT ({completed}) THEN {signal('V305 later repair lacks ledgers or completion receipt: '+version)} END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
    WHERE b.repair_key='{key}' GROUP BY b.table_name,b.row_id HAVING SUM(b.column_name='__before__')<>1 OR SUM(b.column_name='__after__')<>1) THEN
    {signal('V305 later repair evidence incomplete: '+version)}
  END IF;
  INSERT INTO v305_later SELECT {n},b.table_name,b.row_id,CAST(b.old_value AS JSON),CAST(a.old_value AS JSON)
  FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
  JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
  WHERE b.repair_key='{key}' AND b.column_name='__before__';
 END IF;
"""
    text += """ UPDATE v305_later SET before_image=JSON_OBJECT('formal_owner_identity',before_image->'$.identity'),after_image=JSON_OBJECT('formal_owner_identity',after_image->'$.identity') WHERE step=0 AND table_name='zsjos_order';
 UPDATE v305_later SET before_image=JSON_OBJECT('order_snapshot',before_image->'$.snapshot'),after_image=JSON_OBJECT('order_snapshot',after_image->'$.snapshot') WHERE step=0 AND table_name='zsjos_order_approval_round';
 OPEN later;
 replay_chain: LOOP
  FETCH later INTO row_table,record_id,before_doc,after_doc;
  IF done THEN LEAVE replay_chain; END IF;
  SELECT expected_image INTO expected_doc FROM v305_completion WHERE table_name=row_table AND row_id=record_id;
  SET field_names=JSON_KEYS(after_doc),field_index=0;
  WHILE field_index<JSON_LENGTH(field_names) DO
   SET field_path=CONCAT('$.',JSON_QUOTE(JSON_UNQUOTE(JSON_EXTRACT(field_names,CONCAT('$[',field_index,']')))));
   IF JSON_CONTAINS_PATH(expected_doc,'one',field_path)=1
     AND JSON_UNQUOTE(JSON_EXTRACT(field_names,CONCAT('$[',field_index,']'))) NOT IN ('version','updater','update_time')
     AND NOT(JSON_EXTRACT(before_doc,field_path) <=> JSON_EXTRACT(after_doc,field_path)) THEN
    IF JSON_CONTAINS_PATH(expected_doc,'one',field_path)=1
       AND NOT(JSON_EXTRACT(expected_doc,field_path) <=> JSON_EXTRACT(before_doc,field_path)) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair chain does not match original evidence';
    END IF;
    SET expected_doc=JSON_SET(expected_doc,field_path,JSON_EXTRACT(after_doc,field_path));
   END IF;
   SET field_index=field_index+1;
  END WHILE;
  UPDATE v305_completion SET expected_image=expected_doc WHERE table_name=row_table AND row_id=record_id;
 END LOOP;
 CLOSE later;
 IF EXISTS(SELECT 1 FROM v305_completion c
   JOIN JSON_TABLE(JSON_KEYS(c.expected_image),'$[*]' COLUMNS(k VARCHAR(100) PATH '$')) fields
   WHERE fields.k NOT IN ('version','updater','update_time') AND NOT(
     JSON_EXTRACT(c.current_image,CONCAT('$.',JSON_QUOTE(fields.k))) <=>
     JSON_EXTRACT(c.expected_image,CONCAT('$.',JSON_QUOTE(fields.k))))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 completed business fields conflict with repair evidence';
 END IF;
 DROP TEMPORARY TABLE v305_later;
 DROP TEMPORARY TABLE v305_completion;
 SET completed=TRUE;
END$$
DELIMITER ;
"""
    return text


def install_block(text, name, body, anchor):
    begin, end = f'-- BEGIN {name}', f'-- END {name}'
    block = begin + '\n' + body.rstrip() + '\n' + end
    if begin in text:
        return re.sub(re.escape(begin) + r'.*?' + re.escape(end), lambda _: block, text, flags=re.S)
    assert anchor in text, name
    return text.replace(anchor, block + '\n' + anchor, 1)


def generate():
    p = next(MIGRATIONS.glob('V299__*.sql'))
    text = p.read_text(encoding='utf-8').replace('CREATE PROCEDURE zsjos_v299_apply()\nBEGIN', 'CREATE PROCEDURE zsjos_v299_apply()\napply_body: BEGIN')
    text = re.sub(r'-- BEGIN V299 COMPLETED REPLAY.*?-- END V299 COMPLETED REPLAY\n', '', text, flags=re.S)
    v300 = next(MIGRATIONS.glob('V300__*.sql')).read_text(encoding='utf-8')
    mapping = re.search(r'INSERT INTO v300_label_map\(label_snapshot,dict_value\) VALUES(.*?);', v300, re.S)[1]
    body = f"""  IF EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V300')
    OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V300') THEN
    IF NOT ({ledgers('V299')}) OR NOT ({ledgers('V300')}) THEN
      {signal('V299 completed payment repair requires both V299/V300 ledgers')}
    END IF;
    DROP TEMPORARY TABLE IF EXISTS v299_completed_payment_map;
    CREATE TEMPORARY TABLE v299_completed_payment_map(label_snapshot VARCHAR(64) PRIMARY KEY,dict_value VARCHAR(64))
      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    INSERT INTO v299_completed_payment_map VALUES {mapping};
    IF EXISTS(SELECT 1 FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no AND o.tenant_id=1
      JOIN v299_completed_payment_map p ON p.label_snapshot=m.pay_label WHERE m.pay_null=1 AND m.order_no<>''
      AND (o.deleted<>0 OR NOT(o.payment_method <=> p.dict_value)
        OR (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='order-payment-legacy-label-backfill-20261010'
            AND b.table_name='zsjos_order' AND b.row_id=o.id AND b.column_name='payment_method' AND b.old_value IS NULL)<>1)) THEN
      {signal('V299 later V300 payment result or evidence conflicts')}
    END IF;
    UPDATE v299_order_map m JOIN v299_completed_payment_map p ON p.label_snapshot=m.pay_label
      SET m.pay_code=p.dict_value,m.pay_null=0 WHERE m.pay_null=1;
    IF EXISTS(SELECT 1 FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no AND o.tenant_id=1
      WHERE m.order_no<>'' AND (o.deleted<>0 OR NOT(o.fee_mode <=> m.fee_code)
        OR NOT(o.fee_mode_label_snapshot <=> m.fee_label)
        OR NOT(o.payment_method <=> CASE WHEN m.pay_null=1 THEN NULL ELSE m.pay_code END)
        OR NOT(o.payment_method_label_snapshot <=> m.pay_label))) THEN
      {signal('V299 completed payment fields conflict; later changes preserved')}
    END IF;
    COMMIT;
    DROP TEMPORARY TABLE v299_completed_payment_map;
    DROP TEMPORARY TABLE v299_order_map;
    SELECT 'V299' AS migration,'already_completed_verified' AS replay_status;
    LEAVE apply_body;
  END IF;
"""
    text = install_block(text, 'V299 COMPLETED REPLAY', body, '  -- Pre-images of the original state, captured once per column and never overwritten on replay.')
    p.write_text(text, encoding='utf-8', newline='\n')
    for version in ('V298', 'V301', 'V303'):
        p = next(MIGRATIONS.glob(version + '__*.sql'))
        text = p.read_text(encoding='utf-8')
        original = re.sub(r'-- BEGIN ' + version + r' COMPLETED REPLAY.*?-- END ' + version + r' COMPLETED REPLAY', '', text, flags=re.S)
        # Reuse the migration's own business postconditions for completed replay.
        if version == 'V298':
            start = original.index("  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0'\n              AND source_type=")
        elif version == 'V301':
            start = original.index('  IF EXISTS(SELECT 1 FROM zsjos_performance_attribution p\n')
        else:
            start = original.index('  -- Postconditions: no residual reference')
        stop = original.index('  INSERT INTO zsjos_schema_version', start)
        checks = original[start:stop]
        if version == 'V298':
            checks = checks.replace("AND source_type='internal_new_media' AND owner_identity='education')", """AND source_type='internal_new_media' AND owner_identity='education'
              AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                WHERE b.repair_key IN ('media-assisted-source-v309','lead-source-confirmed-v309-r2')
                AND b.table_name='zsjos_lead' AND b.row_id=zsjos_lead.id AND b.column_name='__after__'
                AND JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.source_type'))=zsjos_lead.source_type
                AND JSON_EXTRACT(b.old_value,'$.source_user_id') <=> CAST(zsjos_lead.source_user_id AS JSON)))""")
        body = f"""  IF EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309')
     OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309') THEN
    IF NOT ({ledgers(version)}) OR NOT ({ledgers('V309')}) THEN
      {signal('Older source repair lacks complete ledgers; reviewed recovery required')}
    END IF;
{checks}
    SELECT '{version}' AS migration,'already_completed_verified' AS replay_status;
    LEAVE apply_body;
  END IF;
"""
        begin = '-- BEGIN ' + version + ' COMPLETED REPLAY'
        if begin not in text:
            text = re.sub(r'  -- Development repair revisions are ordered;.*?  END IF;', '', text, count=1, flags=re.S)
        text = text.replace(f'CREATE PROCEDURE zsjos_{version.lower()}_apply()\nBEGIN', f'CREATE PROCEDURE zsjos_{version.lower()}_apply()\napply_body: BEGIN')
        anchor = '  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;'
        # Insert after declarations so the block remains valid MySQL procedure SQL.
        if begin not in text:
            text = text.replace(anchor, anchor + '\n' + begin + '\n' + body + '-- END ' + version + ' COMPLETED REPLAY', 1)
        else:
            text = install_block(text, version + ' COMPLETED REPLAY', body, anchor)
        p.write_text(text, encoding='utf-8', newline='\n')

    p = next(MIGRATIONS.glob('V304__*.sql'))
    text = p.read_text(encoding='utf-8').replace('CREATE PROCEDURE zsjos_v304_apply()\nBEGIN', 'CREATE PROCEDURE zsjos_v304_apply()\napply_body: BEGIN')
    body = f"""  IF {ledgers('V304')} THEN
    IF EXISTS(SELECT 1 FROM zsjos_v304_images i WHERE
      (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304'
       AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__after__')<>1
      OR (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304'
       AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name IN ('__before__','__inserted__'))<>1) THEN
      {signal('V304 completed repair evidence incomplete')}
    END IF;
    IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304' AND b.column_name='__after__'
      AND NOT EXISTS(SELECT 1 FROM zsjos_v304_images i WHERE i.table_name=b.table_name AND i.row_id=b.row_id)) THEN
      {signal('V304 completed repair evidence incomplete')}
    END IF;
    IF NOT EXISTS(SELECT 1 FROM zsjos_v304_images i JOIN zsjos_data_repair_backup a
      ON a.repair_key='legacy-deal-state-v304' AND a.table_name=i.table_name AND a.row_id=i.row_id AND a.column_name='__after__'
      WHERE NOT(JSON_REMOVE(CAST(i.before_image AS JSON),'$.version','$.updater','$.updateTime') <=>
        JSON_REMOVE(CAST(a.old_value AS JSON),'$.version','$.updater','$.updateTime'))) THEN
      COMMIT;
      DROP TEMPORARY TABLE zsjos_v304_images;
      DROP TEMPORARY TABLE zsjos_v304_target;
      SELECT 'V304' AS migration,'already_completed_verified' AS replay_status;
      LEAVE apply_body;
    END IF;
  END IF;
"""
    text = install_block(text, 'V304 COMPLETED REPLAY', body, '  IF EXISTS(SELECT 1 FROM zsjos_v304_images i JOIN zsjos_data_repair_backup b')
    p.write_text(text, encoding='utf-8', newline='\n')

    p = next(MIGRATIONS.glob('V306__*.sql'))
    text = p.read_text(encoding='utf-8').replace('CREATE PROCEDURE zsjos_v306_apply()\nBEGIN', 'CREATE PROCEDURE zsjos_v306_apply()\napply_body: BEGIN')
    body = f""" IF {ledgers('V306')} THEN
  IF EXISTS(SELECT 1 FROM v306_images i WHERE (SELECT COUNT(*) FROM zsjos_data_repair_backup b
    WHERE b.repair_key='order-identity-v306' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name IN ('__before__','__after__'))<>2) THEN
    {signal('V306 completed repair evidence incomplete')}
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='order-identity-v306'
    AND NOT EXISTS(SELECT 1 FROM v306_images i WHERE i.table_name=b.table_name AND i.row_id=b.row_id)) THEN
    {signal('V306 completed repair evidence incomplete')}
  END IF;
  IF NOT EXISTS(SELECT 1 FROM v306_images WHERE
    (table_name='zsjos_order' AND NOT(current_image->'$.identity' <=> after_image->'$.identity'))
    OR (table_name='zsjos_order_approval_round' AND
      (NOT(current_image->'$.snapshot.order.formalOwnerIdentity' <=> after_image->'$.snapshot.order.formalOwnerIdentity')
       OR NOT(current_image->'$.snapshot.formalSales.identity' <=> after_image->'$.snapshot.formalSales.identity')))) THEN
    COMMIT;
    DROP TEMPORARY TABLE v306_images;
    SELECT 'V306' AS migration,'already_completed_verified' AS replay_status;
    LEAVE apply_body;
  END IF;
 END IF;
"""
    text = install_block(text, 'V306 COMPLETED REPLAY', body, ' IF EXISTS(SELECT 1 FROM v306_images WHERE NOT(current_image=before_image OR current_image=after_image)) THEN')
    p.write_text(text, encoding='utf-8', newline='\n')

    p = next(MIGRATIONS.glob('V305__*.sql'))
    text = p.read_text(encoding='utf-8')
    text = install_block(text, 'V305 COMPLETION VERIFIER', v305_verifier(), 'DROP PROCEDURE IF EXISTS zsjos_v305_prepare;')
    text = text.replace('CREATE PROCEDURE zsjos_v305_prepare()\nBEGIN', 'CREATE PROCEDURE zsjos_v305_prepare()\nprepare_body: BEGIN\n  DECLARE completed BOOLEAN DEFAULT FALSE;')
    head, prepare = text.split('CREATE PROCEDURE zsjos_v305_prepare()', 1)
    prepare = install_block(prepare, 'V305 PREPARE REPLAY', '  CALL zsjos_v305_completed(completed);\n  IF completed THEN LEAVE prepare_body; END IF;', "  IF EXISTS(SELECT 1 FROM zsjos_order WHERE tenant_id=1")
    text = head + 'CREATE PROCEDURE zsjos_v305_prepare()' + prepare
    text = text.replace('CREATE PROCEDURE zsjos_v305_finalize()\nBEGIN', 'CREATE PROCEDURE zsjos_v305_finalize()\nfinalize_body: BEGIN\n  DECLARE completed BOOLEAN DEFAULT FALSE;')
    # Place after the handler, without relying on a stale session variable from PREPARE.
    anchor = '  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;'
    body = f"""  IF NOT ({ledgers('V304')}) OR NOT EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
    {signal('V305 finalize requires verified preparation prerequisites')}
  END IF;
  CALL zsjos_v305_completed(completed);
  IF completed THEN LEAVE finalize_body; END IF;
"""
    if '-- BEGIN V305 FINALIZE REPLAY' not in text:
        text = text.replace(anchor, anchor + '\n-- BEGIN V305 FINALIZE REPLAY\n' + body + '-- END V305 FINALIZE REPLAY', 1)
    else:
        text = install_block(text, 'V305 FINALIZE REPLAY', body, anchor)
    # Keep helper available to the private executor, which installs routines in two phases.
    cleanup = '\nDROP PROCEDURE IF EXISTS zsjos_v305_completed;\n'
    if cleanup.strip() not in text.split('-- END FINALIZE')[-1]:
        text += cleanup
    p.write_text(text, encoding='utf-8', newline='\n')


if __name__ == '__main__':
    generate()
