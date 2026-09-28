#!/usr/bin/env python3
"""修复遗留数据导入造成的提现/返现状态错位（2026-09-24）。

背景
    旧库 ptml 迁移到 zsjos 时，提现单与返现（reward）状态被分别照抄，
    两侧没有互相校验，产生了应用状态机下不可能出现的组合。
    另有 WD202609170001 一笔把「旧库 reward_id」误当成「新库 cashback id」
    写进了关联表，导致 4 笔属于别人的返现被挂到该提现单下。

口径
    business_key = concat('legacy-reward-', ptml.commission_reward.id) 是
    新旧库返现的唯一可靠对应键。绝不能用数字 id 直接对应（会撞号）。

四组修复
    A. 提现单 135/136（WD202609180001/9）：旧库 reward 状态为 withdrawing、
       paid_at IS NULL（未打款），新库却写成 withdrawn 且填了 settled_at。
       回退为 withdrawing + settled_at=NULL，让财务能重新登记打款。
       同时补齐旧库里有、新库丢失的银行卡快照。
    B. 35 笔「提现单已 paid、返现仍 withdrawing」：旧库说钱已付出，
       补完 withdrawing → withdrawn 并回填 settled_at（取旧库 reward 的结算时间）。
    C. 提现单 134（WD202609170001）关联行被写成了旧库 reward_id：
       真实应挂 2111/2160/2268/2276（partner 39），实际挂了
       2376/2467/2649/2662（分别属于 partner 91/74/31/21）。
       重挂关联行，并把 2111/2160/2268/2276 置为 withdrawn。
    D. 被误挂的 2376/2467/2649/2662 恢复 available（它们旧库状态本就是
       withdrawable / pending_settlement，从未被提现）。

用法
    python3 repair_withdrawal_state_20260924.py            # 只打印 SQL（dry-run）
    python3 repair_withdrawal_state_20260924.py --apply    # 执行
"""
from __future__ import annotations

import argparse
import subprocess
import sys

CONTAINER = "zsjos-mysql-1"
DB = "zsjos"
SECRET = "/opt/zsjos-runtime/secrets/mysql-root-password"
MARK = "state-repair-20260924"

# A. 提现单 135/136：返现回退
ROLLBACK_CASHBACKS = [2412, 2450]
# A. 两个提现单的银行卡快照补齐：id -> (account_name, card_no, bank_name, branch)
WITHDRAWAL_SNAPSHOTS = {
    135: ("史璐丹", "6222030200040099859", "中国工商银行", "亚运村北辰路支行"),
    136: ("徐魏", "6217755010001701374", "徽商银行", "合肥五里墩支行"),
}
# C. 提现单 134：应挂 / 误挂
WD134_CORRECT = [2111, 2160, 2268, 2276]   # partner 39
WD134_WRONG = [2376, 2467, 2649, 2662]     # 他人返现，需释放
WD134 = 134


def sql(statements: list[str]) -> str:
    return "\n".join(s.strip() + ";" for s in statements)


def build() -> str:
    parts = [
        f"-- ===== {MARK} =====",
        "SET NAMES utf8mb4;",
        "SET SESSION sql_mode='STRICT_ALL_TABLES';",
        "START TRANSACTION;",
    ]

    # ---- 前置断言：与本脚本假设不符就中止 ----
    parts.append(f"""
-- 前置断言：135/136 必须仍是 approved 且未打款
SELECT 'precheck-blocked-approved' AS check_name, COUNT(*) AS hits
FROM zsjos_withdrawal WHERE id IN (135,136) AND status='approved' AND paid_at IS NULL;

-- 前置断言：2412/2450 必须仍是 withdrawn（否则已被人工改过，停手）
SELECT 'precheck-still-withdrawn' AS check_name, COUNT(*) AS hits
FROM zsjos_cashback WHERE id IN (2412,2450) AND status='withdrawn';

-- 前置断言：2367/2467/2649/2662 当前不应被 134 以外的单占用
SELECT 'precheck-wrong-links' AS check_name, COUNT(*) AS hits
FROM zsjos_withdrawal_item
WHERE withdrawal_id={WD134} AND cashback_id IN ({','.join(map(str, WD134_WRONG))})
  AND active_flag=1 AND deleted=0;
""")

    # ---- A. 提现单 135/136：返现回退为 withdrawing，清 settled_at ----
    parts.append(sql([
        f"""UPDATE zsjos_cashback
   SET status='withdrawing', settled_at=NULL, version=version+1,
       updater='{MARK}', update_time=NOW()
 WHERE id IN ({','.join(map(str, ROLLBACK_CASHBACKS))}) AND status='withdrawn'"""
    ]))

    for wid, (acct, card, bank, branch) in WITHDRAWAL_SNAPSHOTS.items():
        parts.append(sql([
            f"""UPDATE zsjos_withdrawal
   SET account_name_snapshot='{acct}', card_number_snapshot='{card}',
       bank_name_snapshot='{bank}', branch_name_snapshot='{branch}',
       updater='{MARK}', update_time=NOW()
 WHERE id={wid} AND (card_number_snapshot IS NULL OR card_number_snapshot='')"""
        ]))

    # ---- B. 提现单已 paid、返现仍 withdrawing：补完终态 ----
    # 只处理「旧库已 withdrawn」且「新库挂着的提现单已 paid」的那些，避免误伤。
    parts.append(f"""
-- B: 补完「已打款但返现没结掉」的 35 笔
UPDATE zsjos_cashback c
JOIN zsjos_withdrawal_item i ON i.cashback_id=c.id AND i.active_flag=1 AND i.deleted=0
JOIN zsjos_withdrawal w ON w.id=i.withdrawal_id AND w.status='paid' AND w.deleted=0
JOIN ptml.commission_reward r ON c.business_key = CONCAT('legacy-reward-', r.id)
   SET c.status='withdrawn',
       c.settled_at = COALESCE(w.paid_at, NOW()),
       c.version=c.version+1,
       c.updater='{MARK}', c.update_time=NOW()
 WHERE c.status='withdrawing'
   AND r.reward_status='withdrawn';
""")

    # ---- C/D. 提现单 134 的关联行重挂 ----
    parts.append(f"""
-- C1: 释放 134 上被误挂的关联行（保留历史，deactivate 而非删除）
UPDATE zsjos_withdrawal_item
   SET active_flag=0, updater='{MARK}', update_time=NOW()
 WHERE withdrawal_id={WD134} AND cashback_id IN ({','.join(map(str, WD134_WRONG))})
   AND active_flag=1 AND deleted=0;

-- C2: 挂上正确的 4 笔（金额取旧库关联表的快照）
INSERT INTO zsjos_withdrawal_item
  (withdrawal_id, cashback_id, amount_snapshot, active_flag, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT {WD134}, c.id, l.amount, b'1', '{MARK}', NOW(), '{MARK}', NOW(), b'0', 1
FROM zsjos_cashback c
JOIN ptml.commission_reward r ON c.business_key = CONCAT('legacy-reward-', r.id)
JOIN ptml.commission_withdrawal_reward l ON l.reward_id = r.id
JOIN ptml.commission_withdrawal w ON w.id = l.withdrawal_id AND w.withdrawal_no='WD202609170001'
WHERE c.id IN ({','.join(map(str, WD134_CORRECT))});

-- C3: 正确的 4 笔置为 withdrawn（旧库即 withdrawn），结算时间取提现单打款时间
UPDATE zsjos_cashback c
JOIN zsjos_withdrawal_item i ON i.cashback_id=c.id AND i.withdrawal_id={WD134} AND i.active_flag=1
JOIN zsjos_withdrawal w ON w.id={WD134}
   SET c.status='withdrawn', c.settled_at=w.paid_at, c.version=c.version+1,
       c.updater='{MARK}', c.update_time=NOW()
 WHERE c.id IN ({','.join(map(str, WD134_CORRECT))}) AND c.status='available';

-- D: 被误挂的 4 笔恢复可用（它们旧库为 withdrawable/pending_settlement，从未提现）
UPDATE zsjos_cashback c
JOIN ptml.commission_reward r ON c.business_key = CONCAT('legacy-reward-', r.id)
   SET c.status='available', c.settled_at=NULL, c.version=c.version+1,
       c.updater='{MARK}', c.update_time=NOW()
 WHERE c.id IN ({','.join(map(str, WD134_WRONG))})
   AND c.status='available' AND r.reward_status IN ('withdrawable','pending_settlement');
""")

    # ---- 后置校验 ----
    parts.append("""
-- 后置校验：不该再存在「approved 单 + withdrawn 返现」
SELECT 'postcheck-approved-withdrawn' AS check_name, COUNT(*) AS hits
FROM zsjos_withdrawal w
JOIN zsjos_withdrawal_item i ON i.withdrawal_id=w.id AND i.active_flag=1 AND i.deleted=0
JOIN zsjos_cashback c ON c.id=i.cashback_id
WHERE w.status='approved' AND c.status='withdrawn';

-- 后置校验：不该再存在「paid 单 + withdrawing 返现」
SELECT 'postcheck-paid-withdrawing' AS check_name, COUNT(*) AS hits
FROM zsjos_withdrawal w
JOIN zsjos_withdrawal_item i ON i.withdrawal_id=w.id AND i.active_flag=1 AND i.deleted=0
JOIN zsjos_cashback c ON c.id=i.cashback_id
WHERE w.status='paid' AND c.status='withdrawing';

-- 后置校验：提现单 134 的关联金额应与旧库一致
SELECT 'postcheck-wd134-total' AS check_name,
       (SELECT SUM(amount_snapshot) FROM zsjos_withdrawal_item
         WHERE withdrawal_id=134 AND active_flag=1 AND deleted=0) AS zsjos_total,
       (SELECT SUM(l.amount) FROM ptml.commission_withdrawal_reward l
         WHERE l.withdrawal_id=60) AS legacy_total;
""")
    parts.append("COMMIT;")
    return "\n".join(parts)


def run_sql(statement: str) -> tuple[int, str]:
    cmd = ["docker", "exec", "-i", "-e",
           f"MYSQL_PWD={open(SECRET).read().strip()}", CONTAINER,
           "mysql", "-uroot", "--default-character-set=utf8mb4", DB]
    proc = subprocess.run(cmd, input=statement, capture_output=True, text=True)
    return proc.returncode, (proc.stdout + proc.stderr).strip()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正执行（默认仅打印）")
    args = ap.parse_args()

    script = build()
    if not args.apply:
        print(script)
        print(f"\n-- dry-run；加 --apply 执行", file=sys.stderr)
        return 0

    rc, out = run_sql(script)
    print(out)
    return 0 if rc == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
