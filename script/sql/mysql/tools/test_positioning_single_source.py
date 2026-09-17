"""UTF-8. Replay the scoped correction in a new, retained local verification database."""
import datetime
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]
TABLES = ["zsjos_director_form_template", "zsjos_director_form_template_version", "zsjos_media_account_field_config"]

def query(database, sql):
    result = subprocess.run(["docker", "exec", "-i", "yudao-mysql", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "' + database + '"'],
        input=("SET NAMES utf8mb4;\n" + sql).encode("utf-8"), capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result.stdout.decode("utf-8")

def signature(database):
    return query(database, ";".join("SELECT COUNT(*),SUM(CRC32(CONCAT(id,':',status,':',fields_json))) FROM " + table
        for table in TABLES[1:]))

if __name__ == "__main__":
    db = "positioning_source_verify_" + datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    query("ruoyi-vue-pro", "CREATE DATABASE `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    for table in TABLES:
        query(db, f"CREATE TABLE `{table}` LIKE `ruoyi-vue-pro`.`{table}`; INSERT INTO `{table}` SELECT * FROM `ruoyi-vue-pro`.`{table}`;")
    script = (ROOT / "script/sql/mysql/positioning-single-source.sql").read_text(encoding="utf-8")
    before = query(db, "SELECT id,HEX(fields_json) FROM zsjos_director_form_template_version ORDER BY id;")
    query(db, script)
    once = signature(db)
    query(db, script)
    assert signature(db) == once, "Correction must be idempotent"
    fields = json.loads(query(db, "SELECT v.fields_json FROM zsjos_director_form_template t JOIN zsjos_director_form_template_version v ON v.id=t.published_version_id WHERE t.tenant_id=1 AND t.template_code='default_positioning';"))
    assert len(fields) == 47 and sum(not f.get("referenceFor") for f in fields) == 37
    assert fields[0]["title"] == "账号名称建议"
    assert sum(f["type"] == "material_picker" for f in fields) == 10
    for row in before.strip().splitlines():
        row_id, old = row.split("\t")
        assert query(db, f"SELECT HEX(fields_json) FROM zsjos_director_form_template_version WHERE id={int(row_id)};").strip() == old
    active = query(db, "SELECT COUNT(*) FROM zsjos_media_account_field_config c, JSON_TABLE(c.fields_json,'$[*]' COLUMNS(k VARCHAR(80) PATH '$.key',g VARCHAR(80) PATH '$.group',enabled INT PATH '$.enabled')) f WHERE c.status='published' AND c.deleted=0 AND (f.g='POSITIONING' OR LEFT(f.k,3)='pc_' OR f.k IN ('positioning_snapshot','positioning_history'));")
    assert int(active.strip()) == 0
    print("PASS", db, "37 primary fields, 10 references, retired profile authoring, original definitions preserved, repeatable")
    print(query(db, "SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(v.fields_json,'$[0].title'))) FROM zsjos_director_form_template t JOIN zsjos_director_form_template_version v ON v.id=t.published_version_id WHERE t.tenant_id=1 AND t.template_code='default_positioning';").strip())
