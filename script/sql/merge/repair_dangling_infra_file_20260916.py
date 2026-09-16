#!/usr/bin/env python3
"""
补齐悬空引用的 infra_file 行。

背景
----
2026-09-16 的增量合并之后，目标库有 23 个不同的 infra_file id 被业务表引用，
但 infra_file 里没有对应行：

  zsjos_lead_attachment              id 1..16     -> 16 行
  zsjos_lead_follow_up_image         2266,2286,2314,2329,2340  -> 5 行
  zsjos_opportunity_follow_up_image  2267         -> 1 行
  zsjos_registration_item_attachment 2333         -> 1 行
  zsjos_lead.invalid_evidence_refs   2268         -> 1 行（KZ 客资 id=8）

这些行在 2026-09-15 18:41 的备份
(`ruoyi-vue-pro-after-legacy-20260915-184129.sql`) 里**是存在的**（id 2260-2364
整段连续、无缺号），到 2026-09-16 19:39 的备份里整段消失 —— 也就是说源头数据库
在这两个时间点之间把这批文件记录删了。目标库只是忠实继承了备份的缺号。

这些 id 引用的对象在 COS 上基本都还在（23 个里 22 个 HEAD 200），
本轮只补**数据库记录**，不动对象存储。

来源与安全
----------
行内容取自 2026-09-15 的备份；即它是这批行的权威快照，不是本仓库构造出来的。
补的行全部保持 `deleted=b'0'`（备份原值），config_id 改写为目标库启用中的配置。

用法
----
    python3 repair_dangling_infra_file_20260916.py            # 打印计划
    python3 repair_dangling_infra_file_20260916.py --write     # 生成 /tmp/repair_infra_file.sql
"""

import argparse
import re
import subprocess
import sys

BACKUP = "/opt/zsjos-runtime/backups/ruoyi-vue-pro-after-legacy-20260915-184129.sql"
PW_FILE = "/opt/zsjos-runtime/secrets/mysql-root-password"
TARGET = "zsjos"

# 业务表 -> 引用 infra_file 的列（用于发现悬空 id）
REFS = [
    ("zsjos_lead_attachment", "infra_file_id"),
    ("zsjos_lead_follow_up_image", "infra_file_id"),
    ("zsjos_opportunity_follow_up_image", "infra_file_id"),
    ("zsjos_registration_item_attachment", "infra_file_id"),
    ("system_notice_attachment", "infra_file_id"),
    ("zsjos_content_version_file", "infra_file_id"),
    ("zsjos_forced_form_submission_file", "infra_file_id"),
    ("zsjos_lead_submitter_feedback_attachment", "file_id"),
    ("zsjos_material_file", "infra_file_id"),
    ("zsjos_student_positioning_interview_attachment", "file_id"),
    ("zsjos_work_attachment", "infra_file_id"),
    ("zsjos_work_order_attachment", "file_id"),
    ("zsjos_withdrawal", "proof_file_id"),
    ("zsjos_export_task", "result_file_id"),
]

# 内嵌 infraFileId 的 JSON 列
JSON_REFS = [
    ("zsjos_lead", "invalid_evidence_refs"),
    ("zsjos_lead_appeal", "evidence_refs"),
    ("zsjos_lead_appeal", "invalid_evidence_refs_snapshot"),
    ("zsjos_lead_appeal", "decision_evidence_refs"),
    ("zsjos_lead_complaint", "evidence_refs"),
    ("zsjos_lead_complaint", "handler_evidence_refs"),
    ("zsjos_lead_duplicate_review", "review_attachments"),
    ("zsjos_lead_submitter_assist_request", "attachment_snapshots_json"),
    ("zsjos_order", "payment_voucher_refs"),
    ("zsjos_payment_transaction", "evidence_refs"),
    ("zsjos_refund_case", "evidence_refs"),
    ("zsjos_registration_item", "evidence_refs"),
    ("zsjos_service_record", "evidence_refs"),
    ("zsjos_business_event", "evidence_refs"),
    ("zsjos_feedback", "result_attachment_ids_json"),
    ("zsjos_feedback_reply", "attachment_ids_json"),
    ("zsjos_work_order", "attachment_ids_json"),
    ("zsjos_work_order", "completion_attachment_ids_json"),
    ("zsjos_work_order_history", "attachment_ids_json"),
    ("zsjos_course_calendar_event", "attachment_ids_json"),
    ("zsjos_interview_record", "attachments_json"),
    ("zsjos_media_account_profile_entry", "files_json"),
    ("zsjos_media_student_talk_record", "attachment_file_ids_json"),
    ("zsjos_student_contact_record", "attachment_file_ids_json"),
    ("zsjos_student_contact_extension", "attachment_file_ids_json"),
]


def mysql(sql, db=TARGET):
    pw = open(PW_FILE).read().strip()
    p = subprocess.run(
        ["docker", "exec", "-i", "zsjos-mysql-1", "mysql", "-uroot", "-p" + pw,
         "--batch", "--raw", "-N", "--default-character-set=utf8mb4", db],
        input=sql.encode(), capture_output=True)
    if p.returncode != 0:
        print("SQL 失败:", p.stderr.decode()[:400]); sys.exit(1)
    return [l for l in p.stdout.decode("utf8", "replace").splitlines() if l.strip()]


def dangling_ids():
    ids = set()
    for table, col in REFS:
        rows = mysql("SELECT DISTINCT `%s` FROM `%s` WHERE `%s` IS NOT NULL AND `%s`<>0 "
                     "AND NOT EXISTS (SELECT 1 FROM infra_file f WHERE f.id=`%s`.`%s`);"
                     % (col, table, col, col, table, col))
        for r in rows:
            ids.add(int(r))
    # JSON 列里的 infraFileId
    for table, col in JSON_REFS:
        rows = mysql(
            "SELECT DISTINCT j.v FROM `%s` l, JSON_TABLE(IF(JSON_VALID(l.`%s`), l.`%s`, NULL), "
            "'$[*]' COLUMNS (v BIGINT PATH '$.infraFileId')) j "
            "WHERE j.v IS NOT NULL AND NOT EXISTS (SELECT 1 FROM infra_file f WHERE f.id=j.v);"
            % (table, col, col))
        for r in rows:
            ids.add(int(r))
    return sorted(ids)


def parse_backup(ids):
    """从备份 dump 里抠出这些 id 的 infra_file 行。"""
    data = open(BACKUP, "rb").read()
    out = {}
    for block in re.findall(rb"INSERT INTO `infra_file` VALUES (.*?);\n", data, re.S):
        for m in re.finditer(
                rb"\((\d+),(\d+),'((?:[^'\\]|\\.)*)','((?:[^'\\]|\\.)*)','((?:[^'\\]|\\.)*)',"
                rb"'((?:[^'\\]|\\.)*)',(\d+),'((?:[^'\\]|\\.)*)','([^']*)','((?:[^'\\]|\\.)*)',"
                rb"'([^']*)',(0x00|0x01)\)", block):
            i = int(m.group(1))
            if i in ids:
                out[i] = m
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()

    ids = dangling_ids()
    print("悬空 infra_file id: %d 个" % len(ids))
    print(" ", ids)

    cfg = mysql("SELECT id FROM infra_file_config WHERE deleted=b'0' ORDER BY id LIMIT 1")
    if not cfg:
        print("目标库没有启用中的 infra_file_config"); sys.exit(1)
    cfg_id = cfg[0]
    print("目标有效 config_id =", cfg_id)

    rows = parse_backup(set(ids))
    missing = [i for i in ids if i not in rows]
    print("备份中可找回: %d / %d" % (len(rows), len(ids)))
    if missing:
        print("  备份里也没有:", missing)

    stmts = ["SET NAMES utf8mb4;", "START TRANSACTION;"]
    for i in ids:
        if i not in rows:
            continue
        _, _c, name, path, url, type_, size, creator, ct, updater, ut, deleted = rows[i].groups()
        lit = lambda b: "'" + b.decode("utf8", "replace").replace("\\", "\\\\").replace("'", "\\'") + "'"
        stmts.append(
            "INSERT INTO infra_file (id,config_id,name,path,url,type,size,creator,create_time,"
            "updater,update_time,deleted) SELECT %d,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s "
            "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM infra_file WHERE id=%d);"
            % (i, cfg_id, lit(name), lit(path), lit(url), lit(type_), int(size),
               lit(creator), lit(ct), lit(updater), lit(ut), "b'0'", i))
    stmts.append("COMMIT;")

    sql = "\n".join(stmts)
    print("\n生成 %d 条 INSERT" % (len(rows)))
    if args.write:
        open("/tmp/repair_infra_file.sql", "w", encoding="utf-8").write(sql)
        print("已写出 /tmp/repair_infra_file.sql")
    else:
        print(sql[:1200])


if __name__ == "__main__":
    main()
