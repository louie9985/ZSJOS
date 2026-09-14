"""Execute original V203 development correction in a private MySQL container."""
import re
import subprocess
import time
import zsjos_db as db


def check(container):
    def sql(statement, success=True):
        result = subprocess.run([
            "docker", "exec", "-w", "/workspace", container, "mysql",
            "--default-character-set=utf8mb4", "-uroot", "--batch", "--raw",
            "--skip-column-names", "zsjos_test", "-e", statement,
        ], capture_output=True)
        if success and result.returncode:
            raise AssertionError(result.stderr.decode("utf-8"))
        return result

    def read(statement):
        return sql(statement).stdout.decode("utf-8").strip()

    # The image's initialization server accepts socket queries with port 0 before restarting.
    for _ in range(60):
        probe = sql("SELECT @@port", False)
        if probe.returncode == 0 and probe.stdout.strip() == b"3306":
            break
        time.sleep(1)
    else:
        raise AssertionError("Final MySQL server never became ready")
    db.docker_mysql_file(container, db.SQL_ROOT / "bootstrap.sql")
    migration = db.SQL_ROOT / "migrations/V203__positioning_interview_template.sql"
    db.docker_mysql_file(container, migration)
    initial = read("SHOW CREATE TABLE zsjos_student_positioning_interview")
    # Simulate the documented earlier development V203 expression on this owned, empty table.
    sql("ALTER TABLE zsjos_student_positioning_interview MODIFY COLUMN active_draft_student bigint "
        "GENERATED ALWAYS AS (CASE WHEN status='draft' AND deleted=b'0' THEN student_person_id ELSE NULL END) STORED")
    def draft(row_id, relation_id, tenant=1):
        return ("INSERT INTO zsjos_student_positioning_interview "
                "(id,student_person_id,service_relation_id,director_user_id,template_id,template_version_id,template_snapshot_json,tenant_id) "
                f"VALUES({row_id},42,{relation_id},99,1,1,'[]',{tenant})")
    sql(draft(901, 301))
    assert sql(draft(902, 302), False).returncode != 0
    # Execute the complete checked-in V203 file, including its controlled repair block.
    db.docker_mysql_file(container, migration)
    sql(draft(902, 302))
    assert sql(draft(903, 301), False).returncode != 0
    sql(draft(904, 301, 2))
    db.docker_mysql_file(container, migration)
    assert read("SELECT COUNT(*) FROM zsjos_student_positioning_interview") == "3"
    assert read("SELECT COUNT(*) FROM zsjos_student_positioning_interview WHERE student_person_id=42 AND director_user_id=99 AND tenant_id=1") == "2"
    schema = read("SHOW CREATE TABLE zsjos_student_positioning_interview")
    assert re.sub(r" AUTO_INCREMENT=\d+", "", schema) == re.sub(r" AUTO_INCREMENT=\d+", "", initial)
    assert read("SELECT HEX(table_comment) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_student_positioning_interview'") == "E5ADA6E59198E5AE9AE4BD8DE8AEBFE8B088"
    print("PASS: actual bootstrap and full V203, old expression repair, same-director independent relation drafts, "
          "same-relation duplicate rejection, tenant isolation, repeatability, fresh/repaired schema equality and Chinese HEX")


if __name__ == "__main__":
    db.static_check()
    db.with_test_mysql(check)
