CREATE TABLE IF NOT EXISTS pms_knowledge_attachment (
 id bigint NOT NULL AUTO_INCREMENT,
 document_id bigint NOT NULL,
 file_id bigint NOT NULL,
 file_name varchar(512) NOT NULL,
 mime_type varchar(255) DEFAULT NULL,
 file_size bigint NOT NULL,
 sort int NOT NULL DEFAULT 0,
 shared boolean NOT NULL DEFAULT FALSE,
 tenant_id bigint NOT NULL DEFAULT 1,
 creator varchar(64) DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT FALSE,
 PRIMARY KEY (id),
 KEY idx_attachment_document (tenant_id, document_id, deleted, sort)
);
DELETE FROM pms_knowledge_attachment;
