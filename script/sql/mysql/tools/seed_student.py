#!/usr/bin/env python3
"""创建学员档案并绑定兼职端 H5 登录账号。

对齐应用侧的真实生成口径：
  person_no  = XY + yyyyMMddHHmmss + 4 位当日日序（zsjos_person_no_daily_counter）
  order_no   = OD + yyyyMMddHHmmss + 4 位当日日序（zsjos_order_no_daily_counter）
  partner_no = PT + yyyyMMddHHmmss + 4 位随机数（PartnerInvitationServiceImpl）
  password   = BCrypt，强度 4（SecurityProperties.passwordEncoderLength 默认值）

主键一律交给 AUTO_INCREMENT，避免与实时写入的数据撞号。
"""
from __future__ import annotations

import argparse
import random
import sys
from datetime import datetime

import bcrypt
import pymysql

# 系统自身的「未明确课程」快照，等价于 LeadProductSnapshot.unknown()
UNKNOWN_COURSE_SNAPSHOT = (
    '{"name": "未明确课程", "price": 880.0, "specs": [], "skuRef": null, '
    '"skuName": "未明确具体班次/方案", "categoryId": null, "productRef": null, '
    '"skuUnknown": true, "spuUnknown": true, "productName": "未明确课程", '
    '"categoryName": null, "categoryPath": [], "level1CategoryId": null, '
    '"level2CategoryId": null, "level1CategoryName": null, "level2CategoryName": null, '
    '"selectedAttrValuesJson": null}'
)
PENDING_CLASS_ID = 1      # PENDING / 待分班 系统班
DIRECTOR_STAGE = "precheck"
MAX_DAILY_SEQUENCE = 9999


class SeedError(RuntimeError):
    pass


def bcrypt_hash(raw: str) -> str:
    # prefix=b"2a" 与应用现存的 $2a$04$ 哈希保持一致；Spring 也接受 $2b$。
    return bcrypt.hashpw(raw.encode("utf-8"), bcrypt.gensalt(rounds=4, prefix=b"2a")).decode("ascii")


def next_daily_number(cur, tenant_id: int, table: str, now: datetime) -> int:
    """预占并读取当日序号，语义与 Java 侧 *NumberService 完全一致。"""
    day = now.date()
    if table == "zsjos_person_no_daily_counter":
        cur.execute(
            f"INSERT INTO {table} (sequence_date,current_value,creator,create_time,updater,update_time,deleted,tenant_id) "
            "VALUES (%s,1,'seed-student',NOW(),'seed-student',NOW(),b'0',%s) "
            "ON DUPLICATE KEY UPDATE current_value=IF(current_value>=%s,1,current_value+1), update_time=NOW()",
            (day, tenant_id, MAX_DAILY_SEQUENCE),
        )
    else:
        cur.execute(
            f"INSERT IGNORE INTO {table} (sequence_date,current_value,creator,create_time,updater,update_time,deleted,tenant_id) "
            "VALUES (%s,0,'seed-student',NOW(),'seed-student',NOW(),b'0',%s)",
            (day, tenant_id),
        )
        cur.execute(
            f"UPDATE {table} SET current_value=IF(current_value>=%s,1,current_value+1), update_time=NOW() "
            "WHERE tenant_id=%s AND sequence_date=%s",
            (MAX_DAILY_SEQUENCE, tenant_id, day),
        )
    cur.execute(
        f"SELECT current_value FROM {table} WHERE tenant_id=%s AND sequence_date=%s FOR UPDATE",
        (tenant_id, day),
    )
    return int(cur.fetchone()["current_value"])


def resolve_user(cur, username: str, tenant_id: int) -> dict:
    cur.execute(
        "SELECT id,nickname,status FROM system_users WHERE tenant_id=%s AND username=%s AND deleted=b'0'",
        (tenant_id, username),
    )
    row = cur.fetchone()
    if row is None:
        raise SeedError(f"用户不存在：username={username}")
    return row


def find_person(cur, name: str, mobile: str, tenant_id: int) -> dict | None:
    """按 姓名+手机号 → 手机号 → 姓名(无手机号) 的优先级定位既有档案。"""
    cur.execute(
        "SELECT id,name,mobile,identity_status FROM zsjos_person "
        "WHERE tenant_id=%s AND deleted=b'0' AND name=%s AND mobile=%s ORDER BY id LIMIT 1",
        (tenant_id, name, mobile),
    )
    if (row := cur.fetchone()):
        return row
    cur.execute(
        "SELECT id,name,mobile,identity_status FROM zsjos_person "
        "WHERE tenant_id=%s AND deleted=b'0' AND mobile=%s ORDER BY id LIMIT 1",
        (tenant_id, mobile),
    )
    if (row := cur.fetchone()):
        return row
    cur.execute(
        "SELECT id,name,mobile,identity_status FROM zsjos_person "
        "WHERE tenant_id=%s AND deleted=b'0' AND name=%s AND (mobile IS NULL OR mobile='') ORDER BY id LIMIT 1",
        (tenant_id, name),
    )
    return cur.fetchone()


def mobile_taken_by_other(cur, person_id: int | None, mobile: str, tenant_id: int) -> dict | None:
    """手机号已被别的 person 占用时返回该 person，用于阻断写入。"""
    cur.execute(
        "SELECT id,name FROM zsjos_person WHERE tenant_id=%s AND deleted=b'0' AND mobile=%s "
        "AND (%s IS NULL OR id<>%s) ORDER BY id LIMIT 1",
        (tenant_id, mobile, person_id, person_id),
    )
    return cur.fetchone()


def active_relation(cur, person_id: int, tenant_id: int) -> dict | None:
    cur.execute(
        "SELECT id,content_director_user_id,operator_user_id,status,acceptance_status "
        "FROM zsjos_service_relation WHERE tenant_id=%s AND person_id=%s AND deleted=b'0' "
        "AND status IN ('active','paused') ORDER BY id LIMIT 1",
        (tenant_id, person_id),
    )
    return cur.fetchone()


def ensure_person(cur, name, mobile, tenant_id, now, seq_ts) -> tuple[int, bool]:
    existing = find_person(cur, name, mobile, tenant_id)
    if existing:
        pid = existing["id"]
        if conflicting := mobile_taken_by_other(cur, pid, mobile, tenant_id):
            raise SeedError(
                f"手机号 {mobile} 已被其他学员占用：person_id={conflicting['id']} 姓名={conflicting['name']}"
            )
        cur.execute(
            "UPDATE zsjos_person SET identity_status='student', mobile=%s, last_seen_at=%s, "
            "version=version+1 WHERE id=%s",
            (mobile, now, pid),
        )
        return pid, True

    seq = next_daily_number(cur, tenant_id, "zsjos_person_no_daily_counter", now)
    person_no = f"XY{seq_ts}{seq:04d}"
    cur.execute(
        "INSERT INTO zsjos_person (person_no,name,mobile,identity_status,first_seen_at,last_seen_at,"
        "version,creator,create_time,updater,update_time,deleted,tenant_id) "
        "VALUES (%s,%s,%s,'student',%s,%s,0,'seed-student',%s,'seed-student',%s,b'0',%s)",
        (person_no, name, mobile, now, now, now, now, tenant_id),
    )
    return cur.lastrowid, False


def ensure_claim(cur, person_id, mobile, tenant_id, now):
    cur.execute(
        "INSERT INTO zsjos_person_contact_claim (contact_value,person_id,reservation_key,creator,"
        "create_time,updater,update_time,deleted,tenant_id) "
        "SELECT %s,%s,NULL,'seed-student',%s,'seed-student',%s,b'0',%s FROM DUAL "
        "WHERE NOT EXISTS (SELECT 1 FROM zsjos_person_contact_claim c WHERE c.tenant_id=%s "
        "AND c.contact_value=%s AND c.deleted=b'0')",
        (mobile, person_id, now, now, tenant_id, tenant_id, mobile),
    )


def ensure_chain(cur, person_id, name, mobile, director_id, operator_id, tenant_id, now, seq_ts) -> int:
    """建订单链路与服务关系；若已有 active 关系则只补齐编导/运营，不重复建单。"""
    if rel := active_relation(cur, person_id, tenant_id):
        if rel["operator_user_id"] is None and operator_id:
            cur.execute(
                "UPDATE zsjos_service_relation SET operator_user_id=%s, version=version+1 WHERE id=%s",
                (operator_id, rel["id"]),
            )
            cur.execute(
                "UPDATE zsjos_collaboration_group cg JOIN zsjos_service_relation sr "
                "ON sr.id=cg.source_service_relation_id SET cg.operator_user_id=%s WHERE sr.id=%s",
                (operator_id, rel["id"]),
            )
        return rel["id"]

    oseq = next_daily_number(cur, tenant_id, "zsjos_order_no_daily_counter", now)
    order_no = f"OD{seq_ts}{oseq:04d}"
    cur.execute(
        "INSERT INTO zsjos_order (order_no,person_id,order_type,status,submitter_user_id,"
        "formal_sales_user_id,formal_owner_identity,buyer_name,student_name,student_mobile,"
        "total_amount,discount_amount,payable_amount,customer_paid_at,student_source,"
        "submitted_at,effective_at,version,creator,create_time,updater,update_time,deleted,tenant_id) "
        "VALUES (%s,%s,'first_purchase','effective',%s,%s,'content_director',%s,%s,%s,"
        "880.00,0.00,880.00,%s,'direct_enrollment',%s,%s,0,'seed-student',%s,'seed-student',%s,b'0',%s)",
        (order_no, person_id, director_id, director_id, name, name, mobile,
         now, now, now, now, now, tenant_id),
    )
    order_id = cur.lastrowid

    cur.execute(
        "INSERT INTO zsjos_order_item (order_id,product_ref,sku_ref,quantity,unit_price,"
        "discount_amount,payable_amount,product_snapshot,creator,create_time,updater,update_time,deleted,tenant_id) "
        "VALUES (%s,NULL,NULL,1.0000,880.00,0.00,880.00,CAST(%s AS JSON),'seed-student',%s,'seed-student',%s,b'0',%s)",
        (order_id, UNKNOWN_COURSE_SNAPSHOT, now, now, tenant_id),
    )
    item_id = cur.lastrowid

    cur.execute(
        "INSERT INTO zsjos_registration_case (order_id,status,assignment_mode,study_planner_user_id,"
        "registration_approved_at,completed_by_user_id,owner_user_id,started_at,completed_at,"
        "version,creator,create_time,updater,update_time,deleted,tenant_id) "
        "VALUES (%s,'completed','legacy_planner',%s,%s,%s,%s,%s,%s,0,'seed-student',%s,'seed-student',%s,b'0',%s)",
        (order_id, director_id, now, director_id, director_id, now, now, now, now, tenant_id),
    )
    case_id = cur.lastrowid

    cur.execute(
        "INSERT INTO zsjos_service_relation (person_id,order_id,order_item_id,registration_case_id,"
        "class_id,status,owner_user_id,acceptance_status,accepted_by_user_id,accepted_at,"
        "content_director_user_id,operator_user_id,director_stage,service_snapshot,activated_at,"
        "version,creator,create_time,updater,update_time,deleted,tenant_id) "
        "VALUES (%s,%s,%s,%s,%s,'active',NULL,'accepted',%s,%s,%s,%s,%s,CAST(%s AS JSON),%s,"
        "0,'seed-student',%s,'seed-student',%s,b'0',%s)",
        (person_id, order_id, item_id, case_id, PENDING_CLASS_ID, operator_id, now,
         director_id, operator_id, DIRECTOR_STAGE, UNKNOWN_COURSE_SNAPSHOT, now, now, now, tenant_id),
    )
    rel_id = cur.lastrowid

    cur.execute(
        "INSERT INTO zsjos_collaboration_group (tenant_id,source_service_relation_id,student_person_id,"
        "director_user_id,operator_user_id,status,creator,create_time,updater,update_time,deleted,version) "
        "VALUES (%s,%s,%s,%s,%s,'active','seed-student',%s,'seed-student',%s,b'0',0)",
        (tenant_id, rel_id, person_id, director_id, operator_id, now, now),
    )
    group_id = cur.lastrowid
    cur.execute("UPDATE zsjos_service_relation SET collaboration_group_id=%s WHERE id=%s", (group_id, rel_id))
    return rel_id


def ensure_partner_account(cur, name, mobile, tenant_id, now, seq_ts, raw_password) -> tuple[int, int, bool]:
    cur.execute("SELECT id FROM zsjos_partner WHERE tenant_id=%s AND deleted=b'0' AND mobile=%s LIMIT 1",
                (tenant_id, mobile))
    if (row := cur.fetchone()):
        partner_id, created = row["id"], False
        cur.execute("UPDATE zsjos_partner SET name=%s, status='enabled', version=version+1 WHERE id=%s",
                    (name, partner_id))
    else:
        for _ in range(50):
            partner_no = f"PT{seq_ts}{random.randint(0, 9999):04d}"
            cur.execute("SELECT 1 FROM zsjos_partner WHERE tenant_id=%s AND partner_no=%s LIMIT 1",
                        (tenant_id, partner_no))
            if not cur.fetchone():
                break
        else:
            raise SeedError("无法生成唯一的 partner_no")
        cur.execute(
            "INSERT INTO zsjos_partner (partner_no,name,mobile,status,enabled_at,version,creator,"
            "create_time,updater,update_time,deleted,tenant_id) "
            "VALUES (%s,%s,%s,'enabled',%s,0,'seed-student',%s,'seed-student',%s,b'0',%s)",
            (partner_no, name, mobile, now, now, now, tenant_id),
        )
        partner_id, created = cur.lastrowid, True

    hashed = bcrypt_hash(raw_password)
    cur.execute("SELECT id FROM zsjos_partner_account WHERE tenant_id=%s AND deleted=b'0' AND mobile=%s LIMIT 1",
                (tenant_id, mobile))
    if (row := cur.fetchone()):
        cur.execute("UPDATE zsjos_partner_account SET password=%s, status=0, partner_id=%s, version=version+1 WHERE id=%s",
                    (hashed, partner_id, row["id"]))
        account_id = row["id"]
    else:
        cur.execute(
            "INSERT INTO zsjos_partner_account (partner_id,mobile,password,status,wecom_enabled,version,"
            "creator,create_time,updater,update_time,deleted,tenant_id) "
            "VALUES (%s,%s,%s,0,b'0',0,'seed-student',%s,'seed-student',%s,b'0',%s)",
            (partner_id, mobile, hashed, now, now, tenant_id),
        )
        account_id = cur.lastrowid

    return partner_id, account_id, created


def ensure_student_link(cur, partner_id, person_id, tenant_id, now):
    """一个兼职主体只能有一个 active 学员绑定（唯一生成列 active_partner_id）。"""
    cur.execute(
        "SELECT id,student_person_id FROM zsjos_partner_student_link "
        "WHERE tenant_id=%s AND partner_id=%s AND deleted=b'0' LIMIT 1",
        (tenant_id, partner_id),
    )
    if (row := cur.fetchone()):
        if row["student_person_id"] != person_id:
            raise SeedError(
                f"兼职主体 {partner_id} 已绑定其他学员 person_id={row['student_person_id']}，"
                f"不能改绑 person_id={person_id}"
            )
        return row["id"]
    cur.execute(
        "INSERT INTO zsjos_partner_student_link (partner_id,student_person_id,status,started_at,"
        "operated_by_user_id,reason,creator,create_time,updater,update_time,deleted,tenant_id) "
        "VALUES (%s,%s,'active',%s,0,'兼职主体转为学员身份','seed-student',%s,'seed-student',%s,b'0',%s)",
        (partner_id, person_id, now, now, now, tenant_id),
    )
    return cur.lastrowid


def run_one(cur, args, tenant_id, now, seq_ts):
    director = resolve_user(cur, args.director, tenant_id)
    operator = resolve_user(cur, args.operator, tenant_id)
    # 冲突校验放在本人定位之后：既有档案自己占用手机号属正常，不算冲突。
    person_id, reused_person = ensure_person(cur, args.name, args.mobile, tenant_id, now, seq_ts)
    ensure_claim(cur, person_id, args.mobile, tenant_id, now)
    rel_id = ensure_chain(cur, person_id, args.name, args.mobile,
                          director["id"], operator["id"], tenant_id, now, seq_ts)

    cur.execute("SELECT id FROM zsjos_partner_student_link WHERE tenant_id=%s AND student_person_id=%s "
                "AND deleted=b'0' LIMIT 1", (tenant_id, person_id))
    existing_link = cur.fetchone()
    if existing_link:
        cur.execute("SELECT partner_id FROM zsjos_partner_student_link WHERE id=%s", (existing_link["id"],))
        partner_id = cur.fetchone()["partner_id"]
        cur.execute("SELECT id FROM zsjos_partner_account WHERE partner_id=%s AND deleted=b'0' LIMIT 1",
                    (partner_id,))
        acct = cur.fetchone()
        hashed = bcrypt_hash(args.password)
        if acct:
            cur.execute("UPDATE zsjos_partner_account SET password=%s, status=0, version=version+1 WHERE id=%s",
                        (hashed, acct["id"]))
            account_id = acct["id"]
        else:
            cur.execute(
                "INSERT INTO zsjos_partner_account (partner_id,mobile,password,status,wecom_enabled,version,"
                "creator,create_time,updater,update_time,deleted,tenant_id) "
                "VALUES (%s,%s,%s,0,b'0',0,'seed-student',%s,'seed-student',%s,b'0',%s)",
                (partner_id, args.mobile, hashed, now, now, tenant_id),
            )
            account_id = cur.lastrowid
        partner_created = False
    else:
        partner_id, account_id, partner_created = ensure_partner_account(
            cur, args.name, args.mobile, tenant_id, now, seq_ts, args.password)

    ensure_student_link(cur, partner_id, person_id, tenant_id, now)

    return {
        "name": args.name, "mobile": args.mobile,
        "person_id": person_id, "reused_person": reused_person,
        "relation_id": rel_id, "partner_id": partner_id,
        "account_id": account_id, "partner_created": partner_created,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="创建学员并绑定兼职端 H5 账号")
    ap.add_argument("--name", required=True)
    ap.add_argument("--mobile", required=True)
    ap.add_argument("--director", required=True, help="编导 username")
    ap.add_argument("--operator", required=True, help="运营 username")
    ap.add_argument("--password", default="Test@123456")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=3306)
    ap.add_argument("--user", default="zsjos_app")
    ap.add_argument("--db-password", dest="db_password", required=True)
    ap.add_argument("--database", default="zsjos")
    ap.add_argument("--tenant-id", type=int, default=1)
    ap.add_argument("--dry-run", action="store_true", help="只打印将要执行的操作，不提交")
    args = ap.parse_args()

    conn = pymysql.connect(host=args.host, port=args.port, user=args.user, password=args.db_password,
                           database=args.database, charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor,
                           autocommit=False)
    try:
        with conn.cursor() as cur:
            now = datetime.now()
            seq_ts = now.strftime("%Y%m%d%H%M%S")
            result = run_one(cur, args, args.tenant_id, now, seq_ts)
        if args.dry_run:
            conn.rollback()
            print("[dry-run] 已回滚，未写入任何数据")
        else:
            conn.commit()
        print(f"学员：{result['name']} / {result['mobile']}")
        print(f"  person_id   = {result['person_id']}  ({'复用既有档案' if result['reused_person'] else '新建'})")
        print(f"  服务关系    = {result['relation_id']}")
        print(f"  partner_id  = {result['partner_id']}  ({'新建' if result['partner_created'] else '复用既有'})")
        print(f"  登录账号    = {result['mobile']}  密码 = {args.password}")
        return 0
    except SeedError as exc:
        conn.rollback()
        print(f"[失败] {exc}", file=sys.stderr)
        return 2
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
