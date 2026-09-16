#!/usr/bin/env python3
"""
把备份库(ruoyi-vue-pro, 2026-09-15)的业务数据合并进当前 zsjos 测试库。

为什么不能直接灌库
------------------
1. 备份里 `*_user_id` 这类列名是**混用**的:同一个列名在不同表、甚至同一表不同行
   指向不同 ID 空间。只按 system_users 校验会得出"大量悬空"的假象。
   - 员工列   -> system_users.id
   - 多态列   -> source_user_id / provider_owner_id,由同表 provider_owner_type 判定:
                 'partner' -> zsjos_partner_account.id,其余 -> system_users.id
   - 兼职主体 -> partner_id 是 zsjos_partner.id(**不是** partner_account.id)
   - creator/updater 也被兼职账号污染,需"先员工、后兼职账号"两段兜底
2. 备份的 210 个员工里,当前库只认得出 44 个(按 wecom/mobile/username/昵称匹配),
   其余必须原样补建,否则业务数据的归属人全部指向不存在的用户。
3. 字典/菜单/角色权限**不能**从备份导 —— 测试库的是重建版,更全
   (备份独有 dict type 2 个,测试库独有 3 个)。

用法
----
    python3 merge_legacy_business_data.py --analyze   只分析引用,不写库
    python3 merge_legacy_business_data.py --plan      打印导入计划
    python3 merge_legacy_business_data.py --apply     执行导入(先停服!)
    python3 merge_legacy_business_data.py --verify    逐表行数校验
"""
import argparse
import base64
import collections
import json
import os
import subprocess
import sys

MYSQL_CONTAINER = os.environ.get("ZSJOS_MYSQL_CONTAINER", "zsjos-mysql-1")
PASSWORD_FILE = os.environ.get(
    "ZSJOS_MYSQL_PASSWORD_FILE", "/opt/zsjos-runtime/secrets/mysql-root-password"
)
TARGET_DB = os.environ.get("ZSJOS_TARGET_DB", "zsjos")
# 源库默认是 zsjos_staging —— 那个库 2026-09-16 合并完成后已删除。
# 要重跑合并,先用 backups/ruoyi-vue-pro-after-legacy-*.sql 重建一个暂存库,
# 然后 ZSJOS_SOURCE_DB=<库名> 指过去。
SOURCE_DB = os.environ.get("ZSJOS_SOURCE_DB", "zsjos_staging")

NEW_USER_ID_BASE = 1000          # 补建员工的起始 ID(避开测试库现有 1-61)
BATCH_ROWS = 200                 # 每条 INSERT 的行数
PAGE_ROWS = 3000                 # 每次查询取回的行数(单批 JSON 需小于 packet 上限)


# ---------------------------------------------------------------------------
# 不导入的表
# ---------------------------------------------------------------------------
SKIP_EXACT = {
    # 迁移版本记录:测试库更全,反向覆盖会破坏迁移体系
    "zsjos_schema_version",
    "zsjos_module_schema_version",
    "zsjos_material_schema_version",
    # 测试库独有的支付/排行榜配置(含商户私钥),备份里根本没有
    "zsjos_payment_subject",
    "zsjos_product_payment_subject",
    "zsjos_partner_leaderboard_config",
    # 临时备份表
    "zsjos_role_menu_backup_20260904",
    # 运行期/日志类,单独处理
    "zsjos_impersonation_session",
    "zsjos_export_task",
    "zsjos_business_task_notify_stage",
    "zsjos_business_audit_log",
    "zsjos_business_event",
}

# 列语义:'staff' | 'partner_account' | 'partner_main' | 'poly' |
#         'staff_or_partner_account'(两空间兜底) | 'dept'
# 未声明的 *_id 列视为本库内表引用,随主键保持原值。
COL_SPACE = {
    "zsjos_lead": {
        "source_user_id": "poly",
        "provider_owner_id": "poly",
        "source_provider_user_id": "poly",
        "partner_id": "partner_main",
        "owner_user_id": "staff",
        "recycle_source_owner_user_id": "staff",
        "qualified_by_user_id": "staff",
        "pending_assignee_user_id": "staff",
        "source_dept_id": "dept",
    },
    "zsjos_order": {
        # 实测:纯兼职提交的订单把 partner_account.id 写进了 submitter_user_id
        "submitter_user_id": "staff_or_partner_account",
        "formal_sales_user_id": "staff_or_partner_account",
        "zero_amount_approved_by_user_id": "staff",
        "lead_id": "lead",
        "person_id": "person",
        "opportunity_id": "opportunity",
        "supersedes_order_id": "order",
        "superseded_by_order_id": "order",
        "current_approval_round_id": "order_approval_round",
    },
    "zsjos_order_item": {
        "order_id": "order", "product_id": "product", "sku_id": "product_sku",
    },
    "zsjos_order_command": {
        "order_id": "order", "approval_round_id": "order_approval_round",
        "operator_user_id": "staff",
    },
    "zsjos_order_approval_round": {
        "order_id": "order", "submitted_by_user_id": "staff",
    },
    "zsjos_order_supervisor_confirmation": {
        "order_id": "order", "approval_round_id": "order_approval_round",
        "requester_user_id": "staff", "supervisor_user_id": "staff",
    },
    "zsjos_person": {},
    "zsjos_person_contact_claim": {"person_id": "person"},
    "zsjos_person_merge_event": {"operator_user_id": "staff"},
    "zsjos_lead_follow_up_record": {
        "operator_user_id": "staff", "lead_id": "lead",
    },
    "zsjos_lead_follow_up_image": {"follow_up_record_id": "lead_follow_up_record"},
    "zsjos_lead_assignment_history": {
        "from_owner_user_id": "staff", "to_owner_user_id": "staff",
        # 早期数据把兼职账号 id 也写进了 operator_user_id
        "operator_user_id": "staff_or_partner_account",
        "candidate_user_id": "staff_or_partner_account",
        "lead_id": "lead", "assignment_rule_id": "lead_assignment_rule",
    },
    "zsjos_lead_assignment_cursor": {"last_sales_user_id": "staff"},
    "zsjos_lead_intended_product": {"lead_id": "lead"},
    "zsjos_lead_attachment": {"lead_id": "lead"},
    "zsjos_lead_appeal": {
        "applicant_user_id": "staff", "reviewer_user_id": "staff",
        "partner_id": "partner_main", "lead_id": "lead",
    },
    "zsjos_lead_urge": {
        "submitter_user_id": "staff_or_partner_account",
        "target_sales_user_id": "staff", "partner_id": "partner_main",
        "lead_id": "lead",
    },
    "zsjos_lead_duplicate_review": {
        "submitter_user_id": "staff_or_partner_account",
        "submission_partner_id": "partner_main",
        "selected_sales_user_id": "staff", "reviewer_user_id": "staff",
        "matched_person_id": "person", "matched_lead_id": "lead",
    },
    "zsjos_lead_complaint": {
        "complainant_user_id": "staff_or_partner_account",
        "sales_user_id": "staff", "handler_user_id": "staff",
    },
    "zsjos_lead_claim_daily_counter": {"sales_user_id": "staff"},
    "zsjos_lead_aging_pool_event": {
        "operator_user_id": "staff", "previous_collaborator_user_id": "staff",
        "collaborator_user_id": "staff", "lead_id": "lead",
    },
    "zsjos_lead_aging_pool_cycle": {
        "original_owner_user_id": "staff", "collaborator_user_id": "staff",
        "lead_id": "lead",
    },
    "zsjos_advanced_filter_template": {"owner_user_id": "staff"},
    "zsjos_partner": {"bound_system_user_id": "staff"},
    "zsjos_partner_account": {"partner_id": "partner_main"},
    "zsjos_partner_bank_card": {"owner_user_id": "staff", "partner_id": "partner_main"},
    "zsjos_partner_ownership": {"employee_user_id": "staff", "partner_id": "partner_main"},
    "zsjos_partner_ownership_log": {
        "previous_employee_user_id": "staff", "employee_user_id": "staff",
        "operator_user_id": "staff", "partner_id": "partner_main",
    },
    "zsjos_partner_invitation": {
        "initiated_by_director_user_id": "staff",
        "assigned_operator_user_id": "staff", "created_by_user_id": "staff",
    },
    "zsjos_partner_student_link": {"operated_by_user_id": "staff"},
    "zsjos_cashback": {
        "beneficiary_user_id": "staff_or_partner_account",
        "partner_id": "partner_main", "lead_id": "lead", "order_id": "order",
        "order_item_id": "order_item",
    },
    "zsjos_withdrawal": {
        "applicant_user_id": "staff_or_partner_account",
        "reviewed_by_user_id": "staff", "cancelled_by_user_id": "staff",
        "paid_by_user_id": "staff", "partner_id": "partner_main",
    },
    "zsjos_withdrawal_item": {
        "withdrawal_id": "withdrawal", "cashback_id": "cashback",
        "active_cashback_id": "cashback",
    },
    "zsjos_subordinate_sales_audit_log": {
        "operator_user_id": "staff", "target_user_id": "staff", "lead_id": "lead",
    },
    "zsjos_subordinate_sales_command": {"operator_user_id": "staff"},
    "zsjos_user_relation": {
        "source_user_id": "staff", "target_user_id": "staff",
    },
    "zsjos_user_relation_log": {"operator_user_id": "staff"},
    "zsjos_opportunity": {
        "owner_user_id": "staff", "person_id": "person", "lead_id": "lead",
        "previous_order_id": "order",
        "source_service_relation_id": "service_relation",
    },
    "zsjos_opportunity_follow_up_record": {
        "operator_user_id": "staff", "opportunity_id": "opportunity",
        "lead_id": "lead",
    },
    "zsjos_opportunity_follow_up_image": {
        "follow_up_record_id": "opportunity_follow_up_record",
    },
    "zsjos_registration_case": {
        "study_planner_user_id": "staff", "completed_by_user_id": "staff",
        "owner_user_id": "staff", "order_id": "order",
    },
    "zsjos_registration_case_route": {"assignee_user_id": "staff"},
    "zsjos_registration_case_checklist_item": {"checked_by_user_id": "staff"},
    "zsjos_registration_item": {"recorded_by_user_id": "staff"},
    "zsjos_registration_command": {"operator_user_id": "staff"},
    "zsjos_registration_item_attachment": {"uploaded_by_user_id": "staff"},
    "zsjos_registration_route_option": {},
    "zsjos_registration_checklist_template_item": {},
    "zsjos_registration_checklist_version": {},
    "zsjos_service_relation": {
        "owner_user_id": "staff", "accepted_by_user_id": "staff",
        "content_director_user_id": "staff", "career_planner_user_id": "staff",
        "operator_user_id": "staff", "person_id": "person", "order_id": "order",
        "registration_case_id": "registration_case",
        "collaboration_group_id": "collaboration_group",
    },
    "zsjos_collaboration_group": {
        "director_user_id": "staff", "operator_user_id": "staff",
        "student_person_id": "person",
        "source_service_relation_id": "service_relation",
    },
    "zsjos_student_collaborator_assignment_log": {
        "previous_user_id": "staff", "assigned_user_id": "staff",
        "operator_user_id": "staff", "service_relation_id": "service_relation",
    },
    "zsjos_student_contact_record": {
        "operator_user_id": "staff", "service_relation_id": "service_relation",
    },
    "zsjos_student_contact_config_command": {},
    "zsjos_student_contact_config_version": {},
    "zsjos_media_account": {
        "owner_operator_user_id": "staff", "director_user_id": "staff",
        "s_stage_judged_by_user_id": "staff",
        "rebind_requested_by_user_id": "staff",
        "rebind_reviewer_user_id": "staff",
        "create_operator_user_id": "staff",
        "student_person_id": "person",
        "rebind_target_student_person_id": "person",
    },
    "zsjos_media_account_maintenance_revision": {"operated_by_user_id": "staff"},
    "zsjos_media_account_student_link": {
        "operated_by_user_id": "staff", "student_person_id": "person",
    },
    "zsjos_media_screen_daily_snapshot": {
        "supervisor_id": "staff", "member_id": "staff",
    },
    "zsjos_media_account_field_config": {},
    "zsjos_feedback": {
        "submitter_user_id": "staff_or_partner_account",
        "assignee_user_id": "staff", "partner_id": "partner_main",
    },
    "zsjos_feedback_survey": {
        "requested_by_user_id": "staff", "submitter_user_id": "staff",
    },
    "zsjos_feedback_reply": {"author_user_id": "staff"},
    "zsjos_feedback_round": {},
    "zsjos_feedback_config": {},
    "zsjos_work_order": {
        "source_user_id": "staff_or_partner_account",
        "target_user_id": "staff", "command_user_id": "staff",
    },
    "zsjos_work_order_history": {"operator_user_id": "staff"},
    "zsjos_work_order_scene": {"published_version_id": "work_order_scene_version"},
    "zsjos_work_order_scene_version": {"scene_id": "work_order_scene"},
    "zsjos_product": {},
    "zsjos_product_sku": {},
    "zsjos_product_attr": {},
    "zsjos_product_attr_value": {},
    "zsjos_product_category": {},
    "zsjos_exam_schedule": {"product_id": "product"},
    "zsjos_delivery_class": {"homeroom_user_id": "staff", "product_id": "product"},
    "zsjos_sales_dispatch_preference": {"user_id": "staff"},
    "zsjos_lead_inbox_filter_version": {},
    "zsjos_lead_inbox_filter_scheme": {},
    "zsjos_director_form_template_version": {
        "published_by_user_id": "staff", "template_id": "director_form_template",
    },
    "zsjos_director_form_template": {},
    "zsjos_director_config": {},
    "zsjos_material_schema_version": {
        "published_by_user_id": "staff", "material_type_id": "material_type",
    },
    "zsjos_material_type": {},
    "zsjos_content_review_config": {},
    "zsjos_account_stage_log": {"judged_by_user_id": "staff"},
}

# 所有表通用:creator/updater 需要两段兜底
DEFAULT_SPACE = {
    "creator": "staff_or_partner_account",
    "updater": "staff_or_partner_account",
}

_INTERNAL_SPACES = {
    "lead", "person", "order", "order_item", "product", "product_sku",
    "opportunity", "registration_case", "service_relation", "collaboration_group",
    "cashback", "withdrawal", "order_approval_round", "lead_follow_up_record",
    "lead_assignment_rule", "opportunity_follow_up_record",
    "work_order_scene", "work_order_scene_version",
    "material_type", "director_form_template", "dept",
}


def _mysql(sql, db=None, init=None):
    # 必须加 --raw:默认模式会把 JSON 里的换行转义成字面 \n,
    # 解出来就变成两字符而不是换行符。raw 模式还原为真实字节。
    cmd = ["docker", "exec", "-i", MYSQL_CONTAINER, "mysql", "-uroot",
           "-p" + open(PASSWORD_FILE).read().strip(),
           "--default-character-set=utf8mb4", "-N", "-B", "--raw"]
    if db:
        cmd.append(db)
    if init:
        cmd += ["--init-command", init]
    cmd += ["-e", sql]
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0 and "Warning" not in p.stderr:
        raise RuntimeError(p.stderr.strip())
    return [l.split("\t") for l in p.stdout.rstrip("\n").split("\n") if l]


def _mysql_script(sql, db):
    # --binary-mode:bit(1) 列的 0x00 字面量里含 NUL 字节,不开这个选项
    # mysql 客户端会拒绝执行。
    cmd = ["docker", "exec", "-i", MYSQL_CONTAINER, "mysql", "-uroot",
           "-p" + open(PASSWORD_FILE).read().strip(),
           "--default-character-set=utf8mb4", "-N", "-B", "--binary-mode", db]
    p = subprocess.run(cmd, input=sql, capture_output=True, text=True)
    return p.stdout, p.stderr


def tables_in(db):
    return [r[0] for r in _mysql(
        "select table_name from information_schema.tables "
        f"where table_schema='{db}' and table_name like 'zsjos%' order by table_name;")]


def columns_of(db, t):
    return [r[0] for r in _mysql(
        "select column_name from information_schema.columns "
        f"where table_schema='{db}' and table_name='{t}' order by ordinal_position;")]


def column_types(db, t):
    return {r[0]: r[1] for r in _mysql(
        "select column_name, column_type from information_schema.columns "
        f"where table_schema='{db}' and table_name='{t}';")}


def generated_of(db, t):
    return {r[0] for r in _mysql(
        "select column_name from information_schema.columns "
        f"where table_schema='{db}' and table_name='{t}' and extra like '%GENERATED%';")}


def count(db, t):
    return int(_mysql(f"select count(*) from `{t}`;", db=db)[0][0])


def space_of(table, col):
    d = COL_SPACE.get(table, {})
    if col in d:
        return d[col]
    return DEFAULT_SPACE.get(col)


# ---------------------------------------------------------------------------
# 身份映射
# ---------------------------------------------------------------------------

def _phone(s):
    s = (s or "").strip()
    return s if (len(s) == 11 and s.isdigit() and s[0] == "1") else ""


def _norm_nick(s):
    s = (s or "").strip()
    for x in ("【管理员】", "【销售】", "【销售2】", "【销售1】",
              "老师", "兼职端", "【", "】"):
        s = s.replace(x, "")
    return s.strip()


def load_users(db):
    cols = "id,username,nickname,mobile,email,wecom_user_id"
    return _mysql(f"select {cols} from system_users order by id;", db=db)


def build_staff_map(verbose=False):
    """备份 system_users.id -> 测试库 system_users.id (字符串 -> 字符串)"""
    stg = load_users(SOURCE_DB)
    live = load_users(TARGET_DB)

    idx_wecom, idx_mobile, idx_un = {}, {}, {}
    idx_nick = collections.defaultdict(list)
    for r in stg:
        uid, un, nick, mob, em, wc = r
        if wc and wc.strip() not in ("", "NULL"):
            idx_wecom.setdefault(wc.strip(), r)
        p = _phone(mob) or _phone(un) or _phone(nick)
        if p:
            idx_mobile.setdefault(p, r)
        idx_un.setdefault(un.strip().lower(), r)
        idx_nick[_norm_nick(nick)].append(r)

    old2new, basis = {}, {}
    for r in live:
        uid, un, nick, mob, em, wc = r
        hit = how = None
        if wc and wc.strip() not in ("", "NULL") and wc.strip() in idx_wecom:
            hit, how = idx_wecom[wc.strip()], "wecom"
        if not hit:
            p = _phone(mob)
            if p and p in idx_mobile:
                hit, how = idx_mobile[p], "mobile"
        if not hit and un.strip().lower() in idx_un:
            hit, how = idx_un[un.strip().lower()], "username"
        if not hit:
            c = idx_nick.get(_norm_nick(nick), [])
            if len(c) == 1:
                hit, how = c[0], "nickname"
        if hit:
            old2new[hit[0]] = uid
            basis[hit[0]] = how
    if verbose:
        print(f"  员工匹配: {len(old2new)}/{len(stg)}  "
              f"({dict(collections.Counter(basis.values()))})")
    return old2new, stg, basis


def build_partner_maps():
    """兼职主体/账号整表搬,ID 保持不变。"""
    pm = {r[0]: r[0] for r in _mysql("select id from zsjos_partner;", db=SOURCE_DB)}
    pa = {r[0]: r[0] for r in _mysql("select id from zsjos_partner_account;", db=SOURCE_DB)}
    return pm, pa


def build_pools():
    return {
        "staff": {r[0] for r in _mysql("select id from system_users;", db=SOURCE_DB)},
        "partner_account": {r[0] for r in _mysql("select id from zsjos_partner_account;", db=SOURCE_DB)},
        "partner_main": {r[0] for r in _mysql("select id from zsjos_partner;", db=SOURCE_DB)},
        "dept": {r[0] for r in _mysql("select id from system_dept;", db=SOURCE_DB)},
    }


def resolve_reference(table, col, value, pot, pools, staff_map=None):
    """把一个引用值解析成目标库可用的值。

    返回 (新值, 说明)。无法解析时返回 (None, 原因)。

    `staff_map` 是 备份员工ID -> 本库员工ID。命中就换成新 ID;
    没命中说明这个员工要按备份原 ID 补建(见 plan_user_inserts),
    此时原值本身就是目标库里的 ID,可直接保留。
    """
    if value in (None, "NULL", ""):
        return value, None
    if not value.lstrip("-").isdigit():
        return value, None            # 'migration' 这类标记原样保留
    if value == "0":
        # 0 是"系统"哨兵值(定时/自动派单写入),本库同样这么用,原样保留
        return value, None

    sp = space_of(table, col)
    if sp is None or sp in _INTERNAL_SPACES:
        return value, None            # 库内表引用,ID 原样

    staff, acct = pools["staff"], pools["partner_account"]
    main, dept = pools["partner_main"], pools["dept"]
    smap = staff_map or {}

    def as_staff(v):
        """员工口径:命中映射换新 ID;否则按补建后的原 ID 保留。"""
        return smap.get(v, v)

    if sp == "poly":
        first = acct if pot == "partner" else staff
        second = staff if pot == "partner" else acct
        if value in first:
            return (value if pot == "partner" else as_staff(value)), None
        if value in second:
            # 串空间:按值实际所属的空间解析,而不是按 provider_owner_type
            if pot == "partner":
                return as_staff(value), "poly 串空间(按员工映射)"
            return value, "poly 串空间(按兼职账号保留)"
        if value in main:
            return value, "poly 串到 partner_main(保留原值)"
        return None, f"poly 悬空({col}={value})"

    if sp == "staff_or_partner_account":
        if value in acct and value not in staff:
            return value, None
        if value in staff:
            return as_staff(value), None
        if value in main:
            return value, "串到 partner_main(保留原值)"
        return None, f"悬空({col}={value})"

    if sp == "partner_main":
        if value in main:
            return value, None
        return None, f"partner_main 悬空({col}={value})"

    if sp == "staff":
        if value in staff:
            return as_staff(value), None
        return None, f"staff 悬空({col}={value})"

    pool = pools.get(sp, set())
    if value in pool:
        return value, None
    return None, f"{sp} 悬空({col}={value})"


# ---------------------------------------------------------------------------
# 分析
# ---------------------------------------------------------------------------

def analyze():
    staff_ids = {r[0] for r in _mysql("select id from system_users;", db=SOURCE_DB)}
    acct_ids = {r[0] for r in _mysql("select id from zsjos_partner_account;", db=SOURCE_DB)}
    main_ids = {r[0] for r in _mysql("select id from zsjos_partner;", db=SOURCE_DB)}
    dept_ids = {r[0] for r in _mysql("select id from system_dept;", db=SOURCE_DB)}
    print(f"备份身份: 员工 {len(staff_ids)}  兼职账号 {len(acct_ids)}  "
          f"兼职主体 {len(main_ids)}  部门 {len(dept_ids)}")
    print()

    pools = {
        "staff": staff_ids,
        "partner_account": acct_ids,
        "partner_main": main_ids,
        "dept": dept_ids,
        "staff_or_partner_account": staff_ids | acct_ids,
    }
    problems = []
    for t in tables_in(SOURCE_DB):
        if t in SKIP_EXACT:
            continue
        n = count(SOURCE_DB, t)
        if n == 0:
            continue
        cols = columns_of(SOURCE_DB, t)
        rows = fetch_rows(SOURCE_DB, t, cols)
        idx = {c: i for i, c in enumerate(cols)}
        pot_i = idx.get("provider_owner_type")
        bad = collections.Counter()
        for r in rows:
            pot = r[pot_i] if pot_i is not None else None
            for c in cols:
                sp = space_of(t, c)
                if sp is None or sp in _INTERNAL_SPACES:
                    continue
                v = r[idx[c]]
                if v in (None, "NULL", "") or not v.lstrip("-").isdigit():
                    continue
                if sp == "poly":
                    # 主口径按 provider_owner_type 判定;早期数据存在串空间
                    # (员工 / 兼职账号 / 兼职主体三者互串),逐层兜底。
                    first = acct_ids if pot == "partner" else staff_ids
                    second = staff_ids if pot == "partner" else acct_ids
                    if v in first:
                        continue
                    if v in second:
                        bad[f"{c}:poly(串->{'staff' if pot == 'partner' else 'partner_account'})"] += 1
                    elif v in main_ids:
                        bad[f"{c}:poly(串->partner_main)"] += 1
                    else:
                        bad[f"{c}:poly(悬空)"] += 1
                    continue
                if sp == "staff_or_partner_account":
                    if v in staff_ids or v in acct_ids:
                        continue
                    bad[f"{c}:串->partner_main" if v in main_ids else f"{c}:悬空"] += 1
                    continue
                if sp == "partner_main":
                    if v not in main_ids:
                        bad[f"{c}:partner_main"] += 1
                    continue
                pool = pools[sp]
                if v not in pool:
                    bad[f"{c}:{sp}"] += 1
        if bad:
            problems.append((t, n, bad))

    print("=== 仍无法解析的引用 ===")
    if not problems:
        print("  (无)")
        return
    for t, n, bad in problems:
        print(f"  {t}  ({n} 行)")
        for k, v in bad.most_common():
            print(f"      {k}: {v} 次")


def verify():
    print("=== 行数校验(仅列出与备份不一致的表) ===")
    bad = 0
    for t in tables_in(SOURCE_DB):
        if t in SKIP_EXACT:
            continue
        a = count(SOURCE_DB, t)
        if a == 0:
            continue
        b = count(TARGET_DB, t)
        if a != b:
            print(f"  DIFF {t:52s} backup={a:>8} live={b:>8}")
            bad += 1
    print(f"不一致: {bad} 张")


def plan():
    staff_map, stg, _ = build_staff_map(verbose=True)
    new_users = [r for r in stg if r[0] not in staff_map]
    print(f"  需补建员工: {len(new_users)} 人 (id 从 {NEW_USER_ID_BASE} 起)")
    print()
    total = 0
    n_tables = 0
    for t in tables_in(SOURCE_DB):
        if t in SKIP_EXACT:
            continue
        n = count(SOURCE_DB, t)
        if n == 0:
            continue
        n_tables += 1
        total += n
        print(f"  {t:52s} {n:>8}")
    print(f"\n合计 {n_tables} 张表 / {total} 行")
    print("\n注意:执行前必须先停 zsjos-backend.service,否则应用会并发写库。")


def sql_literal(v):
    if v is None or v == "NULL":
        return "NULL"
    if v.startswith("0x") or v.startswith("_binary"):
        return v
    try:
        float(v)
        return v
    except ValueError:
        pass
    return "'" + v.replace("\\", "\\\\").replace("'", "\\'") + "'"


def emit_insert(table, cols, rows, replace=False, coltypes=None):
    verb = "REPLACE" if replace else "INSERT"
    collist = ",".join(f"`{c}`" for c in cols)
    out = []
    for i in range(0, len(rows), BATCH_ROWS):
        chunk = rows[i:i + BATCH_ROWS]
        vals = ",\n".join(
            "(" + ",".join(sql_value(v, c, coltypes) for c, v in zip(cols, r)) + ")"
            for r in chunk)
        out.append(f"{verb} INTO `{table}` ({collist}) VALUES\n{vals};")
    return "\n".join(out)


def _numeric_columns(db, t):
    """目标表里数值类型的列名集合。"""
    rows = _mysql(
        "select column_name from information_schema.columns "
        f"where table_schema='{db}' and table_name='{t}' "
        "and data_type in ('tinyint','smallint','mediumint','int','bigint',"
        "'decimal','float','double','bit','year');")
    return {r[0] for r in rows}



# 这些列在目标库里是 JSON 类型,写 hex 字面量会被当字符串再包一层,必须直接写文本
JSON_COLUMN_SUFFIX = "_json"


def _bit_columns(db, t):
    """bit 类型的列。JSON_OBJECT 会把 bit(1) 输出成 base64 或裸 NUL 字节,
    取值时统一 `+0` 转成 0/1。"""
    return {r[0] for r in _mysql(
        "select column_name from information_schema.columns "
        f"where table_schema='{db}' and table_name='{t}' and data_type='bit';")}


def fetch_rows(db, table, cols):
    """按表取全量数据,返回 [[str|None, ...], ...]。

    用 JSON_OBJECT 逐行序列化,并以 ASCII 0x1E(记录分隔符)拼接。
    JSON 会把真实换行转义成 \\n,所以每行内不会出现裸换行,多行字段
    不会被拆散 —— 这是早期按 TSV 切分丢行的根因。

    大表必须分页:整表 group_concat 的结果会超过 max_allowed_packet,
    表现是查询静默返回空(审计日志 64.7 万行就是这样丢的)。
    """
    # 数值列用 CAST(... AS CHAR) 取值:JSON_OBJECT 对 decimal 会输出
    # 333.00 -> 333.0 丢尾零,对 BIGINT 会输出 JS 数字可能丢精度。
    # 转成字符串后由 _Num 标记,写库时仍按数字字面量还原。
    numeric = _numeric_columns(db, table)
    bits = _bit_columns(db, table)
    exprs = []
    for c in cols:
        if c in bits:
            # bit(1) 在 JSON_OBJECT 里会变成 base64 或裸 NUL 字节,
            # 转成 0/1 数字最干净。
            exprs.append(f"'{c}',`{c}`+0")
        elif c in numeric:
            # decimal 会丢尾零、bigint 会丢精度,统一转字符串保留原样
            exprs.append(f"'{c}',cast(`{c}` as char)")
        else:
            exprs.append(f"'{c}',`{c}`")
    pairs = ",".join(exprs)
    total = count(db, table)
    if total == 0:
        return []
    rows = []
    for offset in range(0, total, PAGE_ROWS):
        sql = (f"select group_concat(JSON_OBJECT({pairs}) separator 0x1E) from "
               f"(select * from `{table}` order by id limit {offset}, {PAGE_ROWS}) x;")
        out = _mysql(sql, db=db,
                     init="set session group_concat_max_len=33554432")
        if not out:
            continue
        blob = out[0][0] if len(out[0]) == 1 else "".join(out[0])
        if not blob:
            continue
        for chunk in blob.split("\x1e"):
            if not chunk:
                continue
            try:
                obj = json.loads(chunk)
            except json.JSONDecodeError:
                continue
            rows.append([
            _Num(str(obj.get(c))) if (c in numeric and obj.get(c) is not None)
            else _coerce(obj.get(c))
            for c in cols
        ])
    return rows


class _Num(str):
    """数字值:保持字符串外观,但标记为应按数字字面量写出。

    引用映射表用字符串做键,所以取数统一归一成 str;但数值列不能走
    hex 字面量(0x31 对 INT 列是无意义的),必须原样写数字。
    """
    __slots__ = ()


def _coerce(v):
    """统一成 str / None。引用映射表用字符串做键,取数也必须归一。

    - JSON 列解出来是 dict/list,要用 json.dumps 还原成合法 JSON;
      直接 str() 会得到 Python 单引号字面量({'a': 1}),写回会被拒绝。
    - 数字用 _Num 包裹,写库时走数字字面量而不是 hex。
    """
    if v is None:
        return None
    if isinstance(v, bool):
        return _Num("1" if v else "0")
    if isinstance(v, (dict, list)):
        return json.dumps(v, ensure_ascii=False, separators=(", ", ": "))
    if isinstance(v, _Num):
        return v
    if isinstance(v, (int, float)):
        return _Num(repr(v))
    return str(v)


def sql_value(v, col=None, coltypes=None):
    """取回值 -> SQL 字面量。

    字符串统一走 hex 字面量,规避反斜杠/引号/换行带来的转义坑。

    关键:hex 字面量必须再 CAST(... AS CHAR)。裸 `0x3631` 在 MySQL 里
    是"二进制字符串",赋给 varchar 列时会按数字解读成 13873,而不是
    字符串 '61' —— 这正是之前 owner_user_id 变成 13873 的原因。
    CAST 之后既保留原字节,又强制按字符语义写入。
    """
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float, _Num)):
        return str(v)
    raw = str(v)
    if raw.startswith("base64:"):
        payload = raw[len("base64:"):]
        if payload.startswith("type") and ":" in payload:
            _, payload = payload.split(":", 1)
        try:
            raw = base64.b64decode(payload).decode("utf-8", "surrogateescape")
        except Exception:
            return "NULL"
    if raw == "":
        return "''"
    return "CAST(0x" + raw.encode("utf-8", "surrogateescape").hex().upper() + " AS CHAR)"


def apply_():
    """执行导入。调用前必须停服。"""
    staff_map, stg_users, basis = build_staff_map(verbose=True)
    pools = build_pools()
    print(f"  兼职: {len(pools['partner_main'])} 主体 / "
          f"{len(pools['partner_account'])} 账号(整表导入, ID 不变)")

    live_user_cols = columns_of(TARGET_DB, "system_users")
    live_user_gen = generated_of(TARGET_DB, "system_users")

    # ---- 1. 补建员工 -----------------------------------------------------
    # 匹配不上的员工**沿用备份原 ID**。理由:
    #   - 本库现有员工占 1-61,备份员工在 107-522,两段不重叠;
    #   - 业务数据里凡是引用这些人的地方,ID 不需要翻译就自动对齐,
    #     省掉一整套易错的重映射。
    # 唯一的例外是备份 id=1,本库已被 admin 占用,直接跳过(它本来就
    # 会被身份匹配命中,不会出现在 new_users 里)。
    new_users = [r for r in stg_users if r[0] not in staff_map]
    stg_cols = ("id,username,password,nickname,remark,dept_id,post_ids,email,"
                "mobile,wecom_user_id,wecom_enabled,sex,avatar,status,login_ip,"
                "login_date,creator,create_time,updater,update_time,deleted,tenant_id")
    stg_cols = [c for c in stg_cols.split(",") if c in columns_of(SOURCE_DB, "system_users")]
    src_users = {r[0]: r for r in fetch_rows(SOURCE_DB, "system_users", stg_cols)}
    sidx = {c: i for i, c in enumerate(stg_cols)}
    live_ids = {r[0] for r in _mysql("select id from system_users;", db=TARGET_DB)}

    user_rows, user_cols = [], None
    skipped = []
    for r in new_users:
        if r[0] in live_ids:
            skipped.append(r[0])          # 极少数情况,保守跳过
            continue
        src = src_users[r[0]]
        vals = {c: src[sidx[c]] for c in stg_cols}
        # creator/updater 按员工映射换算,换算不出的落到 admin(1)
        for f in ("creator", "updater"):
            if f in vals:
                v = vals[f]
                vals[f] = staff_map.get(v, v) if (v or "").isdigit() else "1"
        user_cols = [c for c in stg_cols if c in live_user_cols and c not in live_user_gen]
        user_rows.append([vals[c] for c in user_cols])

    print(f"\n[1/4] 补建员工 {len(user_rows)} 人(沿用备份原 ID)")
    if skipped:
        print(f"      跳过 {len(skipped)} 人(ID 已被占用): {skipped}")
    full_staff_map = dict(staff_map)

    # ---- 2. 兼职主体 + 账号(整表,连带引用重映射)----------------------
    partner_tables = ["zsjos_partner", "zsjos_partner_account"]
    print("\n[2/4] 导入兼职主体与账号")

    # ---- 3. 业务表 -------------------------------------------------------
    biz_tables = []
    for t in tables_in(SOURCE_DB):
        if t in SKIP_EXACT or t in partner_tables:
            continue
        if count(SOURCE_DB, t) > 0:
            biz_tables.append(t)
    print(f"\n[3/4] 导入业务表 {len(biz_tables)} 张")

    # ---- 4. 组装 SQL -----------------------------------------------------
    stmts = ["SET FOREIGN_KEY_CHECKS=0;", "SET UNIQUE_CHECKS=0;",
             "SET SESSION sql_mode='NO_AUTO_VALUE_ON_ZERO';"]
    notes = []

    if user_rows:
        stmts.append(emit_insert("system_users", user_cols, user_rows,
                                 coltypes=column_types(TARGET_DB, "system_users")))

    # 兼职表原样搬(其 id 保持,引用通过 COL_SPACE 重映射)
    for t in partner_tables:
        cols = columns_of(TARGET_DB, t)
        gen = generated_of(TARGET_DB, t)
        use = [c for c in cols if c not in gen]
        rows = fetch_rows(SOURCE_DB, t, use)
        stmts.append(emit_insert(t, use, rows,
                                 coltypes=column_types(TARGET_DB, t)))
        print(f"    {t:44s} {len(rows):>7} 行")

    for t in biz_tables:
        tcols = columns_of(TARGET_DB, t)
        tgen = generated_of(TARGET_DB, t)
        scols = columns_of(SOURCE_DB, t)
        use = [c for c in scols if c in tcols and c not in tgen]
        if not use:
            notes.append(f"{t}: 无公共列,跳过")
            continue
        rows = fetch_rows(SOURCE_DB, t, scols)
        sidx = {c: i for i, c in enumerate(scols)}
        pot_i = sidx.get("provider_owner_type")
        out = []
        for r in rows:
            pot = r[pot_i] if pot_i is not None else None
            vals = []
            for c in use:
                v = r[sidx[c]]
                nv, why = resolve_reference(t, c, v, pot, pools, full_staff_map)
                if why:
                    notes.append(f"{t}.{c}={v} -> {why}")
                vals.append(nv)
            out.append(vals)
        stmts.append(emit_insert(t, use, out,
                                 coltypes=column_types(TARGET_DB, t)))
        print(f"    {t:44s} {len(out):>7} 行")

    stmts.append("SET FOREIGN_KEY_CHECKS=1;")
    sql = "\n".join(stmts)
    with open("/tmp/merge_payload.sql", "w", encoding="utf-8") as f:
        f.write(sql)

    print(f"\n[4/4] SQL 已生成: /tmp/merge_payload.sql ({len(sql) / 1024 / 1024:.1f} MB)")
    print(f"    补建员工: {len(user_rows)} 人(沿用备份原 ID)")
    print(f"    异常/说明 {len(notes)} 条,前 30 条:")
    for n in notes[:30]:
        print("      -", n)
    if len(notes) > 30:
        print(f"      ... 其余 {len(notes) - 30} 条见上文")
    print("\n下一步: 确认已停服后执行 --write")


def write_payload():
    path = "/tmp/merge_payload.sql"
    if not os.path.exists(path):
        print("找不到 /tmp/merge_payload.sql,先跑 --apply")
        sys.exit(1)
    sql = open(path, encoding="utf-8").read()
    out, err = _mysql_script(sql, TARGET_DB)
    print("stderr:", (err.strip() or "(clean)")[:1000])
    if out.strip():
        print("stdout:", out[:500])


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--analyze", action="store_true", help="只分析引用")
    g.add_argument("--plan", action="store_true", help="打印导入计划")
    g.add_argument("--apply", action="store_true", help="生成导入 SQL 到 /tmp/merge_payload.sql")
    g.add_argument("--write", action="store_true", help="执行 /tmp/merge_payload.sql")
    g.add_argument("--verify", action="store_true", help="逐表行数校验")
    g.add_argument("--audit-apply", action="store_true", help="生成审计日志替换 SQL")
    g.add_argument("--audit-write", action="store_true", help="执行审计日志替换")
    g.add_argument("--dict", action="store_true", help="补备份独有字典类型(只读预览)")
    g.add_argument("--dict-write", action="store_true", help="补字典并写入")
    a = ap.parse_args()

    if a.analyze:
        analyze()
    elif a.plan:
        plan()
    elif a.apply:
        apply_()
    elif a.write:
        write_payload()
    elif a.verify:
        verify()
    elif a.audit_apply:
        audit_apply()
    elif a.audit_write:
        audit_write()
    elif a.dict:
        dict_backfill(write=False)
    elif a.dict_write:
        dict_backfill(write=True)



# ---------------------------------------------------------------------------
# 审计日志与业务事件:整表替换(不是合并)
# ---------------------------------------------------------------------------

AUDIT_TABLES = ("zsjos_business_audit_log", "zsjos_business_event")


def audit_apply():
    """生成审计日志/业务事件的替换 SQL 到 /tmp/merge_audit.sql。"""
    stmts = ["SET FOREIGN_KEY_CHECKS=0;", "SET UNIQUE_CHECKS=0;"]
    for t in AUDIT_TABLES:
        tcols = columns_of(TARGET_DB, t)
        tgen = generated_of(TARGET_DB, t)
        ttypes = column_types(TARGET_DB, t)
        scols = columns_of(SOURCE_DB, t)
        use = [c for c in scols if c in tcols and c not in tgen]
        rows = fetch_rows(SOURCE_DB, t, scols)
        idx = {c: i for i, c in enumerate(scols)}
        staff_map, _, _ = build_staff_map()
        pools = build_pools()
        out = []
        for r in rows:
            vals = []
            for c in use:
                v = r[idx[c]]
                if v in (None, "NULL", "") or not v.lstrip("-").isdigit() or v == "0":
                    vals.append(v)
                    continue
                # 审计表里的用户列都是员工口径
                if space_of(t, c) in ("staff", "staff_or_partner_account") or c in (
                        "operator_user_id", "initiator_user_id", "creator", "updater"):
                    nv, _ = resolve_reference(t, c, v, None, pools, staff_map)
                    vals.append(nv)
                else:
                    vals.append(v)
            out.append(vals)
        stmts.append(f"DELETE FROM `{t}`;")
        stmts.append(emit_insert(t, use, out, coltypes=ttypes))
        print(f"  {t:34s} {len(out):>8} 行")
    stmts.append("SET FOREIGN_KEY_CHECKS=1;")
    sql = "\n".join(stmts)
    with open("/tmp/merge_audit.sql", "w", encoding="utf-8") as f:
        f.write(sql)
    print(f"  已生成 /tmp/merge_audit.sql ({len(sql) / 1024 / 1024:.1f} MB)")


def audit_write():
    path = "/tmp/merge_audit.sql"
    if not os.path.exists(path):
        print("先跑 --audit-apply")
        sys.exit(1)
    sql = open(path, encoding="utf-8").read()
    out, err = _mysql_script(sql, TARGET_DB)
    print("stderr:", (err.strip() or "(clean)")[:800])




# ---------------------------------------------------------------------------
# 字典补齐:只补备份独有、且本库没有的类型(纯增量,不改动已有字典)
# ---------------------------------------------------------------------------

def dict_backfill(write=False):
    """把备份里本库缺失的字典**补进来**(纯增量,绝不改动已有条目)。

    实测两个缺口:
      1. eam_book_subject —— 本库有 4 条 dict_data 却没有同名的
         dict_type 行(孤儿数据),备份有完整的 10 条。
      2. zsjos_material_account_type —— 备份里只有两行重复的空类型,
         没有任何 dict_data,属于迁移脚本(V191/V194)的残留,不补。

    只按 (dict_type, value) 去重补数据,ID 冲突时重新分配。
    """
    _seq = {}

    def next_id(table):
        """取下一个可用 ID。同一批里连续调用必须递增,否则会撞主键。"""
        if table not in _seq:
            _seq[table] = int(_mysql(
                f"select coalesce(max(id),0) from `{table}`;", db=TARGET_DB)[0][0])
        _seq[table] += 1
        return str(_seq[table])

    # 只补 eam_book_subject。实测其余 zsjos_* 字典本库都比备份全
    # (备份里的是迁移脚本留下的测试条目),框架自带的 iot_* 同理不碰。
    BACKFILL_TYPES = {"eam_book_subject"}

    plan = []
    tgt_types = {r[0] for r in _mysql("select type from system_dict_type;",
                                      db=TARGET_DB)}
    src_types = _mysql("select id,type,name,status,remark from system_dict_type;",
                       db=SOURCE_DB)
    for tid, typ, name, status, remark in src_types:
        if typ in tgt_types:
            continue
        if typ not in BACKFILL_TYPES:
            continue
        # 备份里有数据、或本库已有该类型的 dict_data(补 dict_type 行)才补
        has_src = int(_mysql(
            f"select count(*) from system_dict_data where dict_type='{typ}';",
            db=SOURCE_DB)[0][0])
        has_tgt = int(_mysql(
            f"select count(*) from system_dict_data where dict_type='{typ}';",
            db=TARGET_DB)[0][0])
        if has_src == 0 and has_tgt == 0:
            continue                      # 空类型,跳过
        plan.append(("type", tid, typ, name, status, remark))

    # dict_data 补差:遍历备份里有数据的**全部**类型,不只是计划新建的。
    # 有些类型本库已存在但缺部分条目(如 eam_book_subject),
    # 上一版只遍历 plan 里的类型,导致这些永远补不上。
    # 只补 eam_book_subject。实测其余 zsjos_* 字典本库都比备份全
    # (备份里的是迁移脚本留下的测试条目),框架自带的 iot_* 同理不碰。
    BACKFILL_TYPES = {"eam_book_subject"}
    data_plan = []
    all_types = {r[0] for r in _mysql(
        "select distinct dict_type from system_dict_data;", db=SOURCE_DB)
        if r[0] in BACKFILL_TYPES}
    for typ in sorted(all_types):
        src = _mysql(
            "select id,label,value,sort,status,color_type,css_class,remark "
            f"from system_dict_data where dict_type='{typ}';", db=SOURCE_DB)
        tgt_vals = {r[0] for r in _mysql(
            f"select value from system_dict_data where dict_type='{typ}';",
            db=TARGET_DB)}
        for row in src:
            if row[2] in tgt_vals:
                continue
            data_plan.append((typ, row))

    print(f"待补 dict_type: {len(plan)} 行")
    for p in plan:
        print(f"    {p[2]:34s} {p[3]}")
    print(f"待补 dict_data: {len(data_plan)} 行")
    for typ, row in data_plan:
        print(f"    {typ:34s} {row[1]:14s} {row[2]}")

    if not write:
        print("\n(未写入。加 --dict-write 执行)")
        return

    stmts = ["SET FOREIGN_KEY_CHECKS=0;"]
    for _, tid, typ, name, status, remark in plan:
        nid = next_id("system_dict_type")
        stmts.append(
            "INSERT INTO `system_dict_type` "
            "(`id`,`name`,`type`,`status`,`remark`,`creator`,`create_time`,"
            "`updater`,`update_time`,`deleted`) VALUES "
            f"({nid},{sql_value(name)},{sql_value(typ)},{sql_value(status)},"
            f"{sql_value(remark)},'merge',NOW(),'merge',NOW(),b'0');")
    for typ, row in data_plan:
        nid = next_id("system_dict_data")
        _, label, value, sort, status, color_type, css_class, remark = row
        stmts.append(
            "INSERT INTO `system_dict_data` "
            "(`id`,`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,"
            "`css_class`,`remark`,`creator`,`create_time`,`updater`,"
            "`update_time`,`deleted`) VALUES "
            f"({nid},{sort or 0},{sql_value(label)},{sql_value(value)},"
            f"{sql_value(typ)},{status or 0},{sql_value(color_type)},"
            f"{sql_value(css_class)},{sql_value(remark)},'merge',NOW(),"
            "'merge',NOW(),b'0');")
    stmts.append("SET FOREIGN_KEY_CHECKS=1;")
    sql = "\n".join(stmts)
    open("/tmp/merge_dict.sql", "w", encoding="utf-8").write(sql)
    o, e = _mysql_script(sql, TARGET_DB)
    print("\nstderr:", (e.strip() or "(clean)")[:500])


if __name__ == "__main__":
    main()

