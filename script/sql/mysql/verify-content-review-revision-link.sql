-- Read-only verification for V228. Run with a UTF-8 MySQL client.
SET NAMES utf8mb4;

SELECT COUNT(*) AS revision_column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'zsjos_content_review_batch'
  AND column_name = 'revision_of_batch_id';

SELECT COUNT(*) AS revision_index_exists
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'zsjos_content_review_batch'
  AND index_name = 'idx_zsjos_crb_revision';

SELECT COUNT(*) AS orphan_revision_links
FROM zsjos_content_review_batch child
LEFT JOIN zsjos_content_review_batch parent
  ON parent.id = child.revision_of_batch_id
 AND parent.tenant_id = child.tenant_id
 AND parent.deleted = b'0'
WHERE child.deleted = b'0'
  AND child.revision_of_batch_id IS NOT NULL
  AND parent.id IS NULL;

SELECT id, revision_of_batch_id, status, current_stage
FROM zsjos_content_review_batch
WHERE deleted = b'0'
  AND revision_of_batch_id IS NOT NULL
ORDER BY id;
