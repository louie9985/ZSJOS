"""Controlled V218 integration checks; never connects to a shared database."""
import subprocess
import zsjos_db as db


def check(container):
    def query(sql):
        result = subprocess.run([
            "docker", "exec", "-w", "/workspace", container, "mysql",
            "--default-character-set=utf8mb4", "-uroot", "--batch", "--raw",
            "--skip-column-names", "zsjos_test", "-e", sql,
        ], check=True, capture_output=True)
        return result.stdout.decode("utf-8").strip()

    def apply():
        db.docker_mysql_file(container, db.SQL_ROOT / "migrations/V218__student_collaboration_group.sql")

    # Use actual repository DDL and prerequisites; isolate upgrade simulation in this container.
    db.docker_mysql_file(container, db.SQL_ROOT / "bootstrap.sql")
    apply()
    assert query("SELECT COUNT(*) FROM zsjos_collaboration_group") == "0"
    table_ddl = query("SHOW CREATE TABLE zsjos_collaboration_group")
    column = query("SELECT COLUMN_TYPE,IS_NULLABLE,COLUMN_COMMENT FROM information_schema.columns "
                   "WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' "
                   "AND column_name='collaboration_group_id'")
    # This database is owned by the test and contains no shared or production rows.
    query("DROP TABLE zsjos_collaboration_group; ALTER TABLE zsjos_service_relation DROP COLUMN collaboration_group_id")
    query("INSERT INTO zsjos_service_relation "
          "(id,tenant_id,person_id,order_id,order_item_id,registration_case_id,status,activated_at,"
          "content_director_user_id,operator_user_id,deleted) VALUES "
          "(91001,910,990,1,1,1,'active',NOW(),991,992,b'0'),"
          "(91002,910,990,2,2,2,'active',NOW(),991,992,b'0'),"
          "(91003,911,990,3,3,3,'paused',NOW(),991,992,b'0'),"
          "(91004,910,990,4,4,4,'completed',NOW(),NULL,NULL,b'0'),"
          "(91005,910,990,5,5,5,'active',NOW(),991,992,b'1')")
    apply()
    assert query("SELECT COUNT(*),COUNT(DISTINCT collaboration_group_id) FROM zsjos_service_relation WHERE deleted=b'0'") == "4\t4"
    assert query("SELECT COUNT(*) FROM zsjos_collaboration_group") == "4"
    assert query("SELECT status FROM zsjos_collaboration_group WHERE source_service_relation_id=91004") == "closed"
    assert query("SELECT director_user_id IS NULL AND operator_user_id IS NULL FROM zsjos_collaboration_group WHERE source_service_relation_id=91004") == "1"
    assert query("SELECT collaboration_group_id IS NULL FROM zsjos_service_relation WHERE id=91005") == "1"
    assert query("SELECT COLUMN_TYPE,IS_NULLABLE,COLUMN_COMMENT FROM information_schema.columns "
                 "WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' "
                 "AND column_name='collaboration_group_id'") == column
    # AUTO_INCREMENT is data-dependent; compare all actual columns and indexes via zero-row fresh schema.
    import re
    assert re.sub(r" AUTO_INCREMENT=\d+", "", query("SHOW CREATE TABLE zsjos_collaboration_group")) == re.sub(r" AUTO_INCREMENT=\d+", "", table_ddl)
    before = query("SELECT id,collaboration_group_id FROM zsjos_service_relation ORDER BY id")
    apply()
    assert query("SELECT id,collaboration_group_id FROM zsjos_service_relation ORDER BY id") == before
    assert query("SELECT COUNT(*) FROM zsjos_collaboration_group") == "4"
    assert query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V218'") == "1"
    assert query("SELECT HEX(table_comment) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_collaboration_group'") == "E5ADA6E59198E7BC96E5AFBCE8BF90E890A5E58D8FE4BD9CE7BB84"
    assert query("SELECT HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='collaboration_group_id'") == "E7BC96E5AFBCE8BF90E890A5E58D8FE4BD9CE7BB84"
    # Existing bad bindings must survive for explicit repair, and read-only verification must expose them.
    query("UPDATE zsjos_service_relation SET collaboration_group_id=(SELECT id FROM zsjos_collaboration_group WHERE source_service_relation_id=91003) WHERE id=91001")
    apply()
    mismatches = query("SELECT COUNT(*) FROM zsjos_service_relation sr LEFT JOIN zsjos_collaboration_group cg "
                       "ON cg.id=sr.collaboration_group_id AND cg.tenant_id=sr.tenant_id AND cg.deleted=b'0' "
                       "WHERE sr.deleted=b'0' AND (cg.id IS NULL OR cg.source_service_relation_id<>sr.id OR cg.student_person_id<>sr.person_id)")
    assert mismatches == "1"
    print("PASS: fresh baseline, upgrade, identical-member isolation, tenants, null members, deleted history, "
          "repeatability, schema equality, version record, Chinese HEX and invalid-binding detection")


if __name__ == "__main__":
    db.static_check()
    db.with_test_mysql(check)
