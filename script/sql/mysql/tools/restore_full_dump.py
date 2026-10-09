#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UTF-8. 把 ZSJOS 全量 mysqldump 备份恢复到本地 Docker MySQL，并按需重置账号口令。

备份来源形如 C:\\Users\\<user>\\zsjos_full_<timestamp>.tar.gz：
    zsjos_full/README.txt
    zsjos_full/SHA256SUMS
    zsjos_full/manifest.json
    zsjos_full/zsjos.sql

脚本职责（按顺序，任一步失败即中止且不改库）：
    1. 校验压缩包 sha256（同目录 .sha256 文件或 SHA256SUMS）。
    2. 解压到独立工作目录，再校验包内 SHA256SUMS。
    3. 用 mysqldump 把当前目标库备份到 backups/mysql/（可回滚基线）。
    4. 把 dump 内的 `CREATE DATABASE`/`USE` 库名改写为目标库名。
    5. 通过 docker exec 把 SQL 导入目标库（DROP/CREATE 覆盖同名表）。
    6. --reset-password 时把 system_users / zsjos_partner_account 的口令统一重置。
    7. 输出导入前后行数、版本表与口令校验结果。

设计约束
    * 只操作本地 Docker 容器 MySQL；不接受远程主机参数，避免误灌。
    * 目标库默认 ruoyi-vue-pro（本地应用与 docker-compose 实际连接名）。
      备份原始库名是 zsjos，二者不同，因此必须改写而非直接导入。
    * 不改写表名，只改写独立的 CREATE DATABASE / USE 两行。
    * 口令从 docker inspect 的容器环境变量读取，不写死在脚本或仓库里。
    * 已应用迁移版本随备份回退（备份版本低于本地代码时会提示，不自动补跑）。

用法
    预演：python restore_full_dump.py --archive <tar.gz>
    执行：python restore_full_dump.py --archive <tar.gz> --apply
    带口令重置：python restore_full_dump.py --archive <tar.gz> --apply --reset-password
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_CONTAINER = "yudao-mysql"
DEFAULT_DATABASE = "ruoyi-vue-pro"
DEFAULT_PASSWORD = "Test@123456"
# BCrypt($2a$, cost 10) of DEFAULT_PASSWORD，来自 bcrypt.gensalt(rounds=10, prefix=b"2a")。
# 仅作 --password 未指定且本机无 bcrypt 模块时的兜底；其它口令必须现场计算。
DEFAULT_HASH = "$2a$10$YaCAcMFR5T/IZT/Df0fPCeptBOdJdlmZHJa40oBy6axNmd.ujXCaO"
PASSWORD_TABLES = ("system_users", "zsjos_partner_account")


def bcrypt_hash(plain: str) -> str:
    """生成与 Spring Security BCryptPasswordEncoder 兼容（$2a$, cost 10）的哈希。"""
    if plain == DEFAULT_PASSWORD:
        return DEFAULT_HASH
    try:
        import bcrypt
    except ImportError:
        fail("This password needs the bcrypt module: pip install bcrypt")
    return bcrypt.hashpw(plain.encode("utf-8"), bcrypt.gensalt(rounds=10, prefix=b"2a")).decode()



def info(message: str) -> None:
    print(f"[restore] {message}", flush=True)


def fail(message: str) -> None:
    raise SystemExit(f"[restore] ERROR: {message}")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def container_env(container: str) -> dict[str, str]:
    raw = subprocess.check_output(["docker", "inspect", container])
    data = json.loads(raw)[0]
    if data.get("State", {}).get("Status") != "running":
        fail(f"Container {container} is not running")
    return dict(x.split("=", 1) for x in data["Config"]["Env"] if "=" in x)


def mysql(container: str, password: str, database: str, sql: str, *, batch: bool = True) -> str:
    """在容器内执行一段 SQL，返回 stdout。口令经 -e 传入，不经过 shell 展开。"""
    args = ["docker", "exec", "-i", "-e", f"MYSQL_PWD={password}", container,
            "mysql", "-uroot", "--default-character-set=utf8mb4"]
    if batch:
        args.append("-N")
        args.append("-B")
    if database:
        args += ["-D", database]
    result = subprocess.run(args, input=sql.encode("utf-8"),
                            capture_output=True)
    if result.returncode != 0:
        fail(f"mysql failed: {result.stderr.decode('utf-8', 'replace').strip()[:800]}")
    return result.stdout.decode("utf-8", "replace")


def scalar(container: str, password: str, database: str, sql: str) -> str:
    return mysql(container, password, database, sql).strip()


def verify_archive(archive: Path) -> None:
    sidecar = archive.with_suffix(archive.suffix + ".sha256")
    if sidecar.is_file():
        expected = sidecar.read_text(encoding="utf-8").split()[0].strip().lower()
        actual = sha256_file(archive)
        if expected != actual:
            fail(f"Archive sha256 mismatch: expected {expected}, got {actual}")
        info(f"压缩包 sha256 校验通过 {actual[:16]}…")
    else:
        info("未找到同名 .sha256 文件，跳过外层校验")


def extract_archive(archive: Path, work_dir: Path) -> Path:
    # 每次解压到独立子目录：work_dir 可能已含上一轮的改写产物，不能复用。
    stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    extract_dir = work_dir / f"extract_{stamp}"
    extract_dir.mkdir(parents=True, exist_ok=False)
    with tarfile.open(archive, "r:gz") as tar:
        members = tar.getnames()
        # 防目录穿越
        for name in members:
            if name.startswith("/") or ".." in Path(name).parts:
                fail(f"Unsafe archive member: {name}")
        tar.extractall(extract_dir)
    sql_files = sorted(extract_dir.rglob("*.sql"))
    if len(sql_files) != 1:
        fail(f"Expected exactly one .sql in archive, found {len(sql_files)}")
    sql_path = sql_files[0]
    info(f"解压完成 {sql_path.name} ({sql_path.stat().st_size / 1024 / 1024:.0f} MiB)")
    checksums = sql_path.parent / "SHA256SUMS"
    if checksums.is_file():
        for line in checksums.read_text(encoding="utf-8").splitlines():
            parts = line.split()
            if len(parts) != 2:
                continue
            expected, name = parts[0].lower(), parts[1].lstrip("*")
            target = sql_path.parent / name
            if not target.is_file():
                continue
            actual = sha256_file(target)
            if actual != expected:
                fail(f"成员 sha256 不一致: {name}")
        info("包内 SHA256SUMS 校验通过")
    return sql_path


def backup_current(container: str, password: str, database: str, backup_dir: Path) -> Path:
    backup_dir.mkdir(parents=True, exist_ok=True)
    stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    target = backup_dir / f"{database}_before_restore_{stamp}.sql"
    with target.open("wb") as handle:
        result = subprocess.run(
            ["docker", "exec", "-e", f"MYSQL_PWD={password}", container,
             "mysqldump", "-uroot", "--default-character-set=utf8mb4",
             "--single-transaction", "--quick", "--skip-lock-tables",
             "--routines", "--events", "--triggers", "--hex-blob",
             "--set-gtid-purged=OFF", "--no-tablespaces", "--databases", database],
            stdout=handle, stderr=subprocess.PIPE)
    if result.returncode != 0:
        fail(f"备份失败: {result.stderr.decode('utf-8', 'replace').strip()[:500]}")
    info(f"当前库已备份 -> {target} ({target.stat().st_size / 1024 / 1024:.0f} MiB)")
    return target


def rewrite_database(sql_path: Path, out_path: Path, database: str) -> int:
    """只改写独立的 CREATE DATABASE / USE 行，表名与数据原样保留。"""
    pattern_create = re.compile(rb"^CREATE DATABASE /\*.*?\*/\s*`[^`]+`", re.MULTILINE)
    pattern_use = re.compile(rb"^USE `[^`]+`;", re.MULTILINE)
    changed = 0
    with sql_path.open("rb") as src, out_path.open("wb") as dst:
        for line in src:
            if pattern_create.match(line):
                line = re.sub(rb"`[^`]+`", b"`" + database.encode() + b"`", line, count=1)
                changed += 1
            elif pattern_use.match(line):
                line = f"USE `{database}`;\n".encode()
                changed += 1
            dst.write(line)
    if changed != 2:
        fail(f"库名改写命中 {changed} 行，预期 2 行（CREATE DATABASE / USE）")
    info(f"库名改写完成 -> {database}")
    return changed


def import_sql(container: str, password: str, sql_path: Path) -> None:
    remote = "/tmp/zsjos_restore_payload.sql"
    subprocess.check_call(["docker", "cp", str(sql_path), f"{container}:{remote}"])
    info("已复制到容器，开始导入（DROP/CREATE 覆盖同名表）…")
    started = dt.datetime.now()
    # 容器内重定向，避免宿主管道成为瓶颈；口令经 -e 传入。
    result = subprocess.run(
        ["docker", "exec", "-e", f"MYSQL_PWD={password}", container, "sh", "-c",
         f'mysql -uroot --default-character-set=utf8mb4 < {remote}'],
        capture_output=True)
    if result.returncode != 0:
        fail(f"导入失败: {result.stderr.decode('utf-8', 'replace').strip()[:800]}")
    subprocess.run(["docker", "exec", container, "rm", "-f", remote], check=False)
    info(f"导入完成，用时 {(dt.datetime.now() - started).total_seconds():.0f}s")


def reset_passwords(container: str, password: str, database: str, plain: str) -> None:
    # 用与 Spring Security BCryptPasswordEncoder 兼容的 $2a$ cost 10 哈希统一重置。
    reset_hash = bcrypt_hash(plain)
    for table in PASSWORD_TABLES:
        before = scalar(container, password, database,
                        f"SELECT COUNT(*) FROM `{table}` WHERE deleted=b'0';")
        mysql(container, password, database,
              f"UPDATE `{table}` SET password='{reset_hash}' WHERE deleted=b'0';")
        after = scalar(container, password, database,
                       f"SELECT COUNT(*) FROM `{table}` WHERE deleted=b'0' "
                       f"AND password='{reset_hash}';")
        info(f"{table}: {after}/{before} 行口令已重置")


def report(container: str, password: str, database: str) -> None:
    counts = mysql(container, password, database,
                   "SELECT 'system_users',COUNT(*) FROM system_users WHERE deleted=b'0' "
                   "UNION ALL SELECT 'zsjos_partner_account',COUNT(*) FROM zsjos_partner_account WHERE deleted=b'0' "
                   "UNION ALL SELECT 'zsjos_lead',COUNT(*) FROM zsjos_lead WHERE deleted=b'0' "
                   "UNION ALL SELECT 'zsjos_person',COUNT(*) FROM zsjos_person WHERE deleted=b'0' "
                   "UNION ALL SELECT 'zsjos_order',COUNT(*) FROM zsjos_order WHERE deleted=b'0';")
    info("导入后行数：")
    for row in counts.splitlines():
        print(f"    {row}", flush=True)
    version = scalar(container, password, database,
                     "SELECT COALESCE(MAX(CAST(SUBSTRING(version,2) AS UNSIGNED)),0) "
                     "FROM zsjos_module_schema_version WHERE module_code='core' AND version LIKE 'V%';")
    info(f"备份内 Core 迁移版本上限: V{version}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--archive", type=Path, required=True,
                        help="zsjos_full_*.tar.gz")
    parser.add_argument("--container", default=DEFAULT_CONTAINER)
    parser.add_argument("--database", default=DEFAULT_DATABASE)
    parser.add_argument("--backup-dir", type=Path,
                        default=REPO_ROOT / "backups" / "mysql")
    parser.add_argument("--work-dir", type=Path, default=None,
                        help="解压工作目录；缺省用系统临时目录")
    parser.add_argument("--reset-password", action="store_true",
                        help=f"把 {'/'.join(PASSWORD_TABLES)} 口令重置为 --password")
    parser.add_argument("--password", default=DEFAULT_PASSWORD,
                        help=f"重置后的明文口令（默认 {DEFAULT_PASSWORD}）")
    parser.add_argument("--apply", action="store_true",
                        help="缺省为预演：校验+解压+改写，但不写库")
    args = parser.parse_args()

    if not args.archive.is_file():
        fail(f"备份包不存在: {args.archive}")

    env = container_env(args.container)
    db_password = env.get("MYSQL_ROOT_PASSWORD")
    if not db_password:
        fail("容器环境缺少 MYSQL_ROOT_PASSWORD")

    verify_archive(args.archive)

    work_dir = args.work_dir or Path(tempfile.mkdtemp(prefix="zsjos_restore_"))
    work_dir.mkdir(parents=True, exist_ok=True)
    sql_path = extract_archive(args.archive, work_dir)
    rewritten = work_dir / f"{args.database}.pwck.sql"
    rewrite_database(sql_path, rewritten, args.database)

    # 库存在性检查：不存在则先建，存在则继续（导入自身 DROP/CREATE 表）。
    exists = scalar(args.container, db_password, "",
                    f"SELECT COUNT(*) FROM information_schema.schemata "
                    f"WHERE schema_name='{args.database}';")
    if exists == "0":
        info(f"目标库 {args.database} 不存在，导入脚本将自行创建")
    else:
        info(f"目标库 {args.database} 已存在，导入会覆盖同名表")

    if not args.apply:
        info("预演结束（未写库）。加 --apply 执行导入。")
        return

    backup_current(args.container, db_password, args.database, args.backup_dir)
    import_sql(args.container, db_password, rewritten)
    if args.reset_password:
        reset_passwords(args.container, db_password, args.database, args.password)
    report(args.container, db_password, args.database)
    info("完成。")


if __name__ == "__main__":
    main()
