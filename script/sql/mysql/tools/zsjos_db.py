#!/usr/bin/env python3
"""ZSJ-OS schema generation, migration, drift detection, and verification CLI."""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[4]
SQL_ROOT = ROOT / "script" / "sql" / "mysql"
MODULE_ROOT = SQL_ROOT / "modules"
ATLAS_IMAGE = os.environ.get("ZSJOS_ATLAS_IMAGE", "arigaio/atlas:0.36.2")
MYSQL_IMAGE = os.environ.get("ZSJOS_MYSQL_IMAGE", "mysql:8")
MIGRATION_PATTERN = re.compile(r"^V(\d+)__([a-z0-9_]+)\.sql$")
TABLE_PATTERN = re.compile(
    r"CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`([^`]+)`\s*\((.*?)\)\s*ENGINE\s*=",
    re.IGNORECASE | re.DOTALL,
)


class CommandError(RuntimeError):
    pass


@dataclasses.dataclass(frozen=True)
class Migration:
    module: str
    version: str
    number: int
    description: str
    path: Path
    checksum: str


@dataclasses.dataclass(frozen=True)
class DbConfig:
    host: str
    port: int
    database: str
    user: str
    password: str
    release: str
    backup_dir: Path


def info(message: str) -> None:
    print(message)


def fail(message: str) -> None:
    raise CommandError(message)


def run(command: list[str], *, cwd: Path = ROOT, env: dict[str, str] | None = None,
        input_text: str | None = None, capture: bool = False) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        command,
        cwd=cwd,
        env=env,
        input=input_text,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        check=False,
    )
    if result.returncode != 0:
        detail = (result.stderr or result.stdout or "").strip()
        fail(f"Command failed ({result.returncode}): {' '.join(command)}\n{detail}")
    return result


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_manifests() -> dict[str, dict]:
    manifests: dict[str, dict] = {}
    for path in sorted(MODULE_ROOT.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        code = data.get("code")
        if not isinstance(code, str) or not re.fullmatch(r"[a-z][a-z0-9-]*", code):
            fail(f"Invalid module code in {path}")
        if code in manifests:
            fail(f"Duplicate module code: {code}")
        data["_path"] = path
        manifests[code] = data
    if "core" not in manifests:
        fail("Missing core database module manifest")
    return manifests


def environment_values(environment: str) -> dict[str, str]:
    production_dir = ROOT / "deploy" / "production"
    values = read_env_file(production_dir / f".env.{environment}")
    if not values:
        values = read_env_file(production_dir / ".env")
    values.update({key: value for key, value in os.environ.items() if key.startswith("ZSJOS_DB_")})
    return values


def enabled_manifests(environment: str) -> dict[str, dict]:
    manifests = load_manifests()
    configured = environment_values(environment).get("ZSJOS_DB_MODULES", "core")
    enabled_codes = [code.strip() for code in configured.split(",") if code.strip()]
    if not enabled_codes or len(enabled_codes) != len(set(enabled_codes)):
        fail("ZSJOS_DB_MODULES must contain unique comma-separated module codes")
    unknown = sorted(set(enabled_codes) - manifests.keys())
    if unknown:
        fail(f"Unknown enabled database modules: {', '.join(unknown)}")
    if "core" not in enabled_codes:
        fail("ZSJOS_DB_MODULES must include core")
    selected = {code: manifests[code] for code in enabled_codes}
    for code, manifest in selected.items():
        missing = sorted(set(manifest.get("dependsOn", [])) - selected.keys())
        if missing:
            fail(f"Enabled module {code} requires disabled modules: {', '.join(missing)}")
    return selected


def module_order(manifests: dict[str, dict]) -> list[str]:
    visiting: set[str] = set()
    visited: set[str] = set()
    result: list[str] = []

    def visit(code: str) -> None:
        if code in visiting:
            fail(f"Cyclic database module dependency involving {code}")
        if code in visited:
            return
        visiting.add(code)
        for dependency in manifests[code].get("dependsOn", []):
            if dependency not in manifests:
                fail(f"Module {code} depends on missing module {dependency}")
            visit(dependency)
        visiting.remove(code)
        visited.add(code)
        result.append(code)

    for module_code in sorted(manifests):
        visit(module_code)
    return result


def resolve_sql_path(relative: str) -> Path:
    path = (SQL_ROOT / relative).resolve()
    if SQL_ROOT not in path.parents and path != SQL_ROOT:
        fail(f"SQL path escapes script/sql/mysql: {relative}")
    return path


def migrations_for(code: str, manifest: dict) -> list[Migration]:
    directory = resolve_sql_path(manifest["migrations"])
    if not directory.is_dir():
        fail(f"Migration directory does not exist for {code}: {directory}")
    migrations: list[Migration] = []
    for path in sorted(directory.glob("V*__*.sql")):
        match = MIGRATION_PATTERN.fullmatch(path.name)
        if not match:
            fail(f"Invalid migration filename: {path}")
        migrations.append(Migration(
            module=code,
            version=f"V{int(match.group(1)):03d}",
            number=int(match.group(1)),
            description=match.group(2).replace("_", " "),
            path=path,
            checksum=sha256(path),
        ))
    numbers = [migration.number for migration in migrations]
    if numbers and numbers[0] != 1:
        fail(f"Migration versions must start at V001 for {code}: {numbers}")
    if numbers and numbers != list(range(numbers[0], numbers[-1] + 1)):
        fail(f"Migration versions are not continuous for {code}: {numbers}")
    return migrations


def split_sql_items(body: str) -> list[str]:
    items: list[str] = []
    start = 0
    depth = 0
    quote: str | None = None
    escaped = False
    for index, char in enumerate(body):
        if escaped:
            escaped = False
            continue
        if char == "\\" and quote:
            escaped = True
            continue
        if quote:
            if char == quote:
                quote = None
            continue
        if char in ("'", '"', "`"):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif char == "," and depth == 0:
            items.append(body[start:index].strip())
            start = index + 1
    tail = body[start:].strip()
    if tail:
        items.append(tail)
    return items


def index_columns(item: str) -> tuple[str, ...]:
    match = re.search(r"\((.*)\)", item, re.DOTALL)
    if not match:
        return ()
    columns: list[str] = []
    for part in split_sql_items(match.group(1)):
        column_match = re.match(r"\s*`([^`]+)`(?:\s*\(\s*(\d+)\s*\))?", part)
        if column_match:
            value = column_match.group(1).lower()
            if column_match.group(2):
                value += f":{column_match.group(2)}"
            columns.append(value)
    return tuple(columns)


def desired_schema(path: Path) -> tuple[
    dict[tuple[str, str], tuple[str, str]],
    dict[tuple[str, str], tuple[int, tuple[str, ...]]],
    dict[tuple[str, str], tuple[tuple[str, ...], str, tuple[str, ...], str, str]],
]:
    text = path.read_text(encoding="utf-8")
    columns: dict[tuple[str, str], tuple[str, str]] = {}
    indexes: dict[tuple[str, str], tuple[int, tuple[str, ...]]] = {}
    foreign_keys: dict[tuple[str, str], tuple[tuple[str, ...], str, tuple[str, ...], str, str]] = {}
    for table_match in TABLE_PATTERN.finditer(text):
        table = table_match.group(1).lower()
        for item in split_sql_items(table_match.group(2)):
            column_match = re.match(r"^`([^`]+)`\s+([a-z]+(?:\([^)]*\))?)", item, re.IGNORECASE)
            if column_match:
                column = column_match.group(1).lower()
                column_type = re.sub(r"\s+", " ", column_match.group(2).lower())
                if re.search(r"\bUNSIGNED\b", item, re.IGNORECASE):
                    column_type += " unsigned"
                if re.search(r"\bZEROFILL\b", item, re.IGNORECASE):
                    column_type += " zerofill"
                nullable = "NO" if re.search(r"\bNOT\s+NULL\b", item, re.IGNORECASE) else "YES"
                columns[(table, column)] = (column_type, nullable)
                continue
            primary_match = re.match(r"^PRIMARY\s+KEY", item, re.IGNORECASE)
            if primary_match:
                indexes[(table, "primary")] = (0, index_columns(item))
                continue
            index_match = re.match(r"^(UNIQUE\s+)?KEY\s+`([^`]+)`", item, re.IGNORECASE)
            if index_match:
                indexes[(table, index_match.group(2).lower())] = (
                    0 if index_match.group(1) else 1,
                    index_columns(item),
                )
                continue
            foreign_match = re.match(
                r"^CONSTRAINT\s+`([^`]+)`\s+FOREIGN\s+KEY\s*\((.*?)\)\s+REFERENCES\s+`([^`]+)`\s*\((.*?)\)",
                item,
                re.IGNORECASE | re.DOTALL,
            )
            if foreign_match:
                local_columns = tuple(re.findall(r"`([^`]+)`", foreign_match.group(2)))
                referenced_columns = tuple(re.findall(r"`([^`]+)`", foreign_match.group(4)))
                delete_match = re.search(r"\bON\s+DELETE\s+(RESTRICT|CASCADE|SET\s+NULL|NO\s+ACTION)", item, re.IGNORECASE)
                update_match = re.search(r"\bON\s+UPDATE\s+(RESTRICT|CASCADE|SET\s+NULL|NO\s+ACTION)", item, re.IGNORECASE)
                foreign_keys[(table, foreign_match.group(1).lower())] = (
                    tuple(column.lower() for column in local_columns),
                    foreign_match.group(3).lower(),
                    tuple(column.lower() for column in referenced_columns),
                    re.sub(r"\s+", " ", delete_match.group(1).upper()) if delete_match else "RESTRICT",
                    re.sub(r"\s+", " ", update_match.group(1).upper()) if update_match else "RESTRICT",
                )
    return columns, indexes, foreign_keys


def read_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def db_config(environment: str) -> DbConfig:
    production_dir = ROOT / "deploy" / "production"
    values = environment_values(environment)

    def value(name: str, default: str | None = None) -> str:
        result = values.get(name, default)
        if result is None or result == "":
            fail(f"Missing database setting {name}; configure deploy/production/.env.{environment}")
        return result

    password = values.get("ZSJOS_DB_PASSWORD", "")
    password_file_value = values.get("ZSJOS_DB_PASSWORD_FILE", "")
    if password_file_value:
        password_file = Path(password_file_value)
        if not password_file.is_absolute():
            password_file = production_dir / password_file
        if not password_file.is_file():
            fail(f"Database password file does not exist: {password_file}")
        password = password_file.read_text(encoding="utf-8").strip()
    if not password:
        fail("Set ZSJOS_DB_PASSWORD_FILE; plaintext passwords must not be committed")
    backup_dir = Path(value("ZSJOS_DB_BACKUP_DIR", str(ROOT / "backups" / "mysql")))
    return DbConfig(
        host=value("ZSJOS_DB_HOST", "127.0.0.1"),
        port=int(value("ZSJOS_DB_PORT", "3306")),
        database=value("ZSJOS_DB_NAME", "zsjos"),
        user=value("ZSJOS_DB_MIGRATION_USER", "zsjos_migrator"),
        password=password,
        release=value("ZSJOS_DB_RELEASE_VERSION", "development"),
        backup_dir=backup_dir,
    )


class MysqlClient:
    def __init__(self, config: DbConfig):
        self.config = config
        self.mysql = shutil.which("mysql")
        self.mysqldump = shutil.which("mysqldump")
        if not self.mysql:
            fail("mysql client was not found; use the db-migrator container or install MySQL client tools")

    def env(self) -> dict[str, str]:
        result = os.environ.copy()
        result["MYSQL_PWD"] = self.config.password
        return result

    def base(self, executable: str) -> list[str]:
        return [
            executable,
            f"--host={self.config.host}",
            f"--port={self.config.port}",
            f"--user={self.config.user}",
            "--default-character-set=utf8mb4",
            "--batch",
            "--raw",
            "--unbuffered",
            "--skip-column-names",
        ]

    def query(self, sql: str) -> str:
        result = run(self.base(self.mysql) + ["--execute", sql, self.config.database], env=self.env(), capture=True)
        return result.stdout or ""

    def execute_file(self, path: Path) -> str:
        result = run(self.base(self.mysql) + [self.config.database], env=self.env(), input_text=path.read_text(encoding="utf-8"), capture=True)
        return result.stdout or ""

    def table_count(self) -> int:
        output = self.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")
        return int(output.strip() or "0")

    def table_exists(self, table: str) -> bool:
        escaped = sql_literal(table)
        return self.query(
            f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name={escaped}"
        ).strip() == "1"

    def backup(self) -> Path:
        if not self.mysqldump:
            fail("mysqldump was not found; migration cannot continue without a backup")
        self.config.backup_dir.mkdir(parents=True, exist_ok=True)
        timestamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
        target = self.config.backup_dir / f"{self.config.database}-{timestamp}.sql"
        command = [
            self.mysqldump,
            f"--host={self.config.host}",
            f"--port={self.config.port}",
            f"--user={self.config.user}",
            "--single-transaction",
            "--no-tablespaces",
            "--routines",
            "--triggers",
            "--events",
            "--default-character-set=utf8mb4",
            self.config.database,
        ]
        with target.open("wb") as stream:
            result = subprocess.run(command, cwd=ROOT, env=self.env(), stdout=stream, stderr=subprocess.PIPE)
        if result.returncode != 0:
            target.unlink(missing_ok=True)
            fail(f"mysqldump failed: {result.stderr.decode(errors='replace').strip()}")
        return target


def sql_literal(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def installed_versions(client: MysqlClient) -> dict[tuple[str, str], tuple[str, str]]:
    installed: dict[tuple[str, str], tuple[str, str]] = {}
    if client.table_exists("zsjos_module_schema_version"):
        output = client.query(
            "SELECT module_code,version,checksum,release_version FROM zsjos_module_schema_version"
        )
        for line in output.splitlines():
            parts = line.split("\t")
            if len(parts) == 4:
                installed[(parts[0], parts[1])] = (parts[2], parts[3])
    elif client.table_exists("zsjos_schema_version"):
        output = client.query("SELECT version,COALESCE(checksum,'') FROM zsjos_schema_version")
        for line in output.splitlines():
            parts = line.split("\t")
            if len(parts) == 2:
                installed[("core", parts[0])] = (hashlib.sha256(parts[1].encode()).hexdigest(), "legacy")
    return installed


def pending_migrations(manifests: dict[str, dict], installed: dict[tuple[str, str], tuple[str, str]]) -> list[Migration]:
    pending: list[Migration] = []
    for code in module_order(manifests):
        for migration in migrations_for(code, manifests[code]):
            record = installed.get((code, migration.version))
            if record:
                recorded_checksum, release = record
                if release not in ("legacy", "baseline") and recorded_checksum != migration.checksum:
                    fail(
                        f"Applied migration checksum changed: {code}/{migration.path.name}; "
                        "restore the applied file and create a new migration"
                    )
            else:
                pending.append(migration)
    return pending


def schema_drift(client: MysqlClient, manifests: dict[str, dict]) -> list[str]:
    desired_columns: dict[tuple[str, str], tuple[str, str]] = {}
    desired_indexes: dict[tuple[str, str], tuple[int, tuple[str, ...]]] = {}
    desired_foreign_keys: dict[tuple[str, str], tuple[tuple[str, ...], str, tuple[str, ...], str, str]] = {}
    allowed_extra: set[str] = set()
    for manifest in manifests.values():
        schema_path = resolve_sql_path(manifest["schema"])
        module_columns, module_indexes, module_foreign_keys = desired_schema(schema_path)
        for label, target, source in (
            ("column", desired_columns, module_columns),
            ("index", desired_indexes, module_indexes),
            ("foreign key", desired_foreign_keys, module_foreign_keys),
        ):
            duplicates = target.keys() & source.keys()
            if duplicates:
                fail(f"Database modules define duplicate {label}: {sorted(duplicates)[0]}")
            target.update(source)
        allowed_extra.update(table.lower() for table in manifest.get("allowedExtraTables", []))
    actual_columns: dict[tuple[str, str], tuple[str, str]] = {}
    output = client.query(
        "SELECT table_name,column_name,column_type,is_nullable FROM information_schema.columns "
        "WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position"
    )
    for line in output.splitlines():
        parts = line.split("\t")
        if len(parts) == 4:
            actual_columns[(parts[0].lower(), parts[1].lower())] = (parts[2].lower(), parts[3].upper())
    actual_indexes: dict[tuple[str, str], tuple[int, tuple[str, ...]]] = {}
    output = client.query(
        "SELECT table_name,index_name,non_unique,"
        "GROUP_CONCAT(CONCAT(column_name,IF(sub_part IS NULL,'',CONCAT(':',sub_part))) "
        "ORDER BY seq_in_index SEPARATOR ',') "
        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
        "GROUP BY table_name,index_name,non_unique"
    )
    for line in output.splitlines():
        parts = line.split("\t")
        if len(parts) == 4:
            actual_indexes[(parts[0].lower(), parts[1].lower())] = (
                int(parts[2]),
                tuple(column.lower() for column in parts[3].split(",")),
            )
    actual_foreign_keys: dict[tuple[str, str], tuple[tuple[str, ...], str, tuple[str, ...], str, str]] = {}
    output = client.query(
        "SELECT k.table_name,k.constraint_name,"
        "GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position SEPARATOR ','),k.referenced_table_name,"
        "GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position SEPARATOR ','),"
        "r.delete_rule,r.update_rule "
        "FROM information_schema.key_column_usage k "
        "JOIN information_schema.referential_constraints r "
        "ON r.constraint_schema=k.constraint_schema AND r.table_name=k.table_name "
        "AND r.constraint_name=k.constraint_name "
        "WHERE k.table_schema=DATABASE() AND k.referenced_table_name IS NOT NULL "
        "GROUP BY k.table_name,k.constraint_name,k.referenced_table_name,r.delete_rule,r.update_rule"
    )
    for line in output.splitlines():
        parts = line.split("\t")
        if len(parts) == 7:
            actual_foreign_keys[(parts[0].lower(), parts[1].lower())] = (
                tuple(column.lower() for column in parts[2].split(",")),
                parts[3].lower(),
                tuple(column.lower() for column in parts[4].split(",")),
                parts[5].upper(),
                parts[6].upper(),
            )

    drift: list[str] = []
    desired_tables = {table for table, _ in desired_columns}
    for key, signature in sorted(desired_columns.items()):
        actual = actual_columns.get(key)
        if actual is None:
            drift.append(f"missing column {key[0]}.{key[1]}")
        elif actual != signature:
            drift.append(
                f"column differs {key[0]}.{key[1]} expected={signature[0]}/{signature[1]} "
                f"actual={actual[0]}/{actual[1]}"
            )
    for table, column in sorted(actual_columns):
        if table in desired_tables and (table, column) not in desired_columns:
            drift.append(f"unexpected column {table}.{column}")
        elif table not in desired_tables and table not in allowed_extra:
            drift.append(f"unexpected table {table}")
    for key, signature in sorted(desired_indexes.items()):
        actual = actual_indexes.get(key)
        if actual is None:
            drift.append(f"missing index {key[0]}.{key[1]}")
        elif actual != signature:
            drift.append(f"index differs {key[0]}.{key[1]} expected={signature} actual={actual}")
    for key in sorted(actual_indexes):
        if key[0] in desired_tables and key not in desired_indexes:
            drift.append(f"unexpected index {key[0]}.{key[1]}")
    for key, signature in sorted(desired_foreign_keys.items()):
        actual = actual_foreign_keys.get(key)
        if actual is None:
            drift.append(f"missing foreign key {key[0]}.{key[1]}")
        elif actual != signature:
            drift.append(f"foreign key differs {key[0]}.{key[1]} expected={signature} actual={actual}")
    for key in sorted(actual_foreign_keys):
        if key[0] in desired_tables and key not in desired_foreign_keys:
            drift.append(f"unexpected foreign key {key[0]}.{key[1]}")
    return drift


def drift_explained_by_pending(message: str, pending: Iterable[Migration]) -> bool:
    tokens = set(re.findall(r"[a-z][a-z0-9_]+", message.lower()))
    for migration in pending:
        text = migration.path.read_text(encoding="utf-8").lower()
        significant = [token for token in tokens if "_" in token]
        if significant and all(token in text for token in significant[-2:]):
            return True
    return False


def static_check() -> None:
    manifests = load_manifests()
    module_order(manifests)
    for code, manifest in manifests.items():
        migrations = migrations_for(code, manifest)
        schema_path = resolve_sql_path(manifest["schema"])
        baseline_path = resolve_sql_path(manifest["baseline"])
        verify_path = resolve_sql_path(manifest["verify"])
        for path in (schema_path, baseline_path, verify_path):
            if not path.is_file():
                fail(f"Missing {code} database file: {path}")
        if schema_path.read_bytes() != baseline_path.read_bytes():
            fail(
                f"Desired schema differs from the fresh baseline for {code}; run `zsjos-db make {code} <name>` "
                "or synchronize the reviewed migration"
            )
        if not migrations:
            fail(f"Module {code} has no migrations")

    core = manifests["core"]
    schema_text = resolve_sql_path(core["schema"]).read_text(encoding="utf-8")
    table_names = {match.group(1).lower() for match in TABLE_PATTERN.finditer(schema_text)}
    mapped_tables: set[str] = set()
    annotation = re.compile(r'@TableName\("([^"]+)"\)')
    backend_root = ROOT / "backend"
    for module in core.get("enabledJavaModules", []):
        module_path = ROOT / "backend" / module
        if not module_path.is_dir():
            if backend_root.is_dir():
                fail(f"Enabled Java module does not exist: {module}")
            continue
        for java_path in module_path.rglob("*.java"):
            mapped_tables.update(match.group(1).lower() for match in annotation.finditer(
                java_path.read_text(encoding="utf-8", errors="ignore")
            ))
    ignored = {table.lower() for table in core.get("ignoredMappedTables", [])}
    missing = sorted(mapped_tables - table_names - ignored)
    if missing:
        fail(f"Enabled Java mappings missing from Core schema: {', '.join(missing)}")

    seed_text = (SQL_ROOT / "02-bootstrap-zsjos-seed.sql").read_text(encoding="utf-8")
    verify_text = (SQL_ROOT / "verify-bootstrap.sql").read_text(encoding="utf-8")
    for version in ("V001", "V017", "V018", "V019", "V020", "V021", "V022", "V023", "V024", "V025"):
        if version not in seed_text:
            fail(f"Fresh baseline does not register {version}")
        if version not in verify_text:
            fail(f"Bootstrap verification does not check {version}")
    info("PASS: manifests, migration order, desired schema, Java mappings, baseline versions, and verification are consistent.")
    if not shutil.which("atlas") and not shutil.which("docker"):
        info("WARN: neither Atlas nor Docker is available; `make` cannot generate schema differences.")


def atlas_diff(baseline: Path, desired: Path) -> str:
    native = shutil.which("atlas")
    dev_url = os.environ.get("ZSJOS_ATLAS_DEV_URL", "docker://mysql/8/dev")
    if native:
        command = [native, "schema", "diff", "--from", baseline.as_uri(), "--to", desired.as_uri(), "--dev-url", dev_url]
    elif shutil.which("docker"):
        root_mount = f"{ROOT}:/workspace"
        baseline_container = "/workspace/" + baseline.relative_to(ROOT).as_posix()
        desired_container = "/workspace/" + desired.relative_to(ROOT).as_posix()
        command = ["docker", "run", "--rm", "-v", root_mount]
        if dev_url.startswith("docker://"):
            command += ["-v", "/var/run/docker.sock:/var/run/docker.sock"]
        command += [
            ATLAS_IMAGE,
            "schema", "diff",
            "--from", f"file://{baseline_container}",
            "--to", f"file://{desired_container}",
            "--dev-url", dev_url,
        ]
    else:
        fail("Atlas was not found and Docker fallback is unavailable")
    result = run(command, capture=True)
    return (result.stdout or "").strip()


def make_migration(module_code: str, name: str) -> None:
    if not re.fullmatch(r"[a-z][a-z0-9_]*", name):
        fail("Migration name must use lowercase letters, numbers, and underscores")
    manifests = load_manifests()
    if module_code not in manifests:
        fail(f"Unknown database module: {module_code}")
    manifest = manifests[module_code]
    baseline = resolve_sql_path(manifest["baseline"])
    desired = resolve_sql_path(manifest["schema"])
    if baseline.read_bytes() == desired.read_bytes():
        fail(f"No desired schema changes found for {module_code}")
    existing = migrations_for(module_code, manifest)
    next_number = (existing[-1].number + 1) if existing else 1
    version = f"V{next_number:03d}"
    output_path = resolve_sql_path(manifest["migrations"]) / f"{version}__{name}.sql"
    ddl = atlas_diff(baseline, desired)
    if not ddl or not re.search(r"\b(?:CREATE|ALTER|DROP|RENAME)\b", ddl, re.IGNORECASE):
        fail("Atlas did not generate a schema change")
    risk_patterns = [r"\bDROP\b", r"\bRENAME\b", r"ALTER\s+COLUMN", r"MODIFY\s+COLUMN"]
    risky = any(re.search(pattern, ddl, re.IGNORECASE) for pattern in risk_patterns)
    header = (
        f"-- Generated candidate migration for module {module_code}.\n"
        "-- Review data compatibility, tenant scope, indexes, and rollback limitations before commit.\n"
        f"-- High-risk DDL detected: {'YES' if risky else 'NO'}\n\n"
    )
    output_path.write_text(header + ddl.rstrip() + "\n", encoding="utf-8", newline="\n")
    shutil.copyfile(desired, baseline)
    info(f"Generated {output_path.relative_to(ROOT)}")
    info(f"Synchronized {baseline.relative_to(ROOT)} with the desired schema")
    if risky:
        info("BLOCKED FOR REVIEW: generated migration contains destructive or compatibility-sensitive DDL.")


def print_plan(environment: str, *, allow_pending_drift: bool = True) -> tuple[MysqlClient, list[Migration], list[str]]:
    manifests = enabled_manifests(environment)
    client = MysqlClient(db_config(environment))
    installed = installed_versions(client)
    pending = pending_migrations(manifests, installed)
    info("Current module versions:")
    for code in module_order(manifests):
        versions = sorted(version for module, version in installed if module == code)
        info(f"  {code}: {versions[-1] if versions else 'not installed'}")
    info("Pending migrations:")
    if pending:
        for migration in pending:
            info(f"  {migration.module}: {migration.path.name}")
    else:
        info("  None")
    empty_database = client.table_count() == 0
    drift = [] if empty_database else schema_drift(client, manifests)
    unexplained = [item for item in drift if not (allow_pending_drift and drift_explained_by_pending(item, pending))]
    info("Unexpected schema drift:")
    if unexplained:
        for item in unexplained:
            info(f"  {item}")
        info("Status: BLOCKED")
    else:
        info("  None")
        if empty_database:
            info("Status: FRESH BOOTSTRAP REQUIRED")
        else:
            info("Status: READY" if not pending else "Status: MIGRATIONS PENDING")
    return client, pending, unexplained


def record_migration(client: MysqlClient, migration: Migration) -> None:
    values = [
        migration.module,
        migration.version,
        migration.description,
        migration.checksum,
        client.config.release,
    ]
    client.query(
        "INSERT INTO zsjos_module_schema_version "
        "(module_code,version,description,checksum,release_version) VALUES ("
        + ",".join(sql_literal(value) for value in values)
        + ") ON DUPLICATE KEY UPDATE "
          "description=VALUES(description),"
          "checksum=IF(release_version IN ('legacy','baseline'),VALUES(checksum),checksum),"
          "release_version=IF(release_version IN ('legacy','baseline'),VALUES(release_version),release_version)"
    )


class MigrationLock:
    def __init__(self, client: MysqlClient):
        self.client = client
        self.process: subprocess.Popen[str] | None = None

    def __enter__(self) -> "MigrationLock":
        command = self.client.base(self.client.mysql) + [self.client.config.database]
        self.process = subprocess.Popen(
            command,
            cwd=ROOT,
            env=self.client.env(),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        assert self.process.stdin and self.process.stdout
        self.process.stdin.write("SELECT GET_LOCK('zsjos_schema_migration', 30);\n")
        self.process.stdin.flush()
        result = self.process.stdout.readline().strip()
        if result != "1":
            self.__exit__(None, None, None)
            fail("Could not acquire the database migration lock within 30 seconds")
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        if self.process and self.process.poll() is None:
            assert self.process.stdin
            try:
                self.process.stdin.write("DO RELEASE_LOCK('zsjos_schema_migration');\nquit\n")
                self.process.stdin.flush()
                self.process.communicate(timeout=5)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                self.process.kill()


def verify_database(environment: str, client: MysqlClient | None = None) -> None:
    manifests = enabled_manifests(environment)
    client = client or MysqlClient(db_config(environment))
    failures: list[str] = []
    for code in module_order(manifests):
        verify_path = resolve_sql_path(manifests[code]["verify"])
        output = client.execute_file(verify_path)
        for line in output.splitlines():
            if re.search(r"(?:^|\t)(FAIL|MISSING)$", line.strip()):
                failures.append(f"{code}: {line}")
    drift = schema_drift(client, manifests)
    failures.extend(f"core drift: {item}" for item in drift)
    if failures:
        fail("Database verification failed:\n" + "\n".join(failures))
    info("PASS: database verification and schema drift checks completed.")


def migrate_database(environment: str) -> None:
    static_check()
    client, pending, unexplained = print_plan(environment)
    if unexplained:
        fail("Migration is blocked by unexpected schema drift")
    with MigrationLock(client):
        backup = client.backup()
        info(f"Backup created: {backup}")
        if client.table_count() == 0:
            info("Empty database detected; applying the reviewed fresh bootstrap.")
            client.execute_file(SQL_ROOT / "bootstrap.sql")
            pending = pending_migrations(enabled_manifests(environment), installed_versions(client))
        for migration in pending:
            info(f"Applying {migration.module}/{migration.path.name}")
            client.execute_file(migration.path)
            if not client.table_exists("zsjos_module_schema_version"):
                fail(f"Migration {migration.path.name} did not create the module version table")
            record_migration(client, migration)
        verify_database(environment, client)
    info("PASS: database migrations completed; the release may start its application services.")


def docker_wait(container: str) -> None:
    for _ in range(90):
        result = subprocess.run(
            ["docker", "exec", container, "mysql", "-uroot", "--batch", "--skip-column-names",
             "--execute", "SELECT 1", "zsjos_test"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        if result.returncode == 0:
            return
        time.sleep(1)
    fail("Temporary MySQL container did not become healthy")


def docker_mysql_file(container: str, path: Path, *, expect_success: bool = True) -> subprocess.CompletedProcess[bytes]:
    with path.open("rb") as stream:
        result = subprocess.run(
            ["docker", "exec", "-i", "-w", "/workspace", container,
             "mysql", "--default-character-set=utf8mb4", "-uroot", "zsjos_test"],
            stdin=stream,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    if expect_success and result.returncode != 0:
        fail(result.stderr.decode(errors="replace"))
    return result


def docker_mysql_query(container: str, query: str) -> str:
    result = run([
        "docker", "exec", container, "mysql", "-uroot", "--batch", "--raw", "--skip-column-names",
        "zsjos_test", "--execute", query,
    ], capture=True)
    return result.stdout or ""


class DockerTestClient:
    def __init__(self, container: str):
        self.container = container

    def query(self, sql: str) -> str:
        return docker_mysql_query(self.container, sql)

    def table_exists(self, table: str) -> bool:
        return self.query(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
            f"AND table_name={sql_literal(table)}"
        ).strip() == "1"


def with_test_mysql(callback) -> None:
    if not shutil.which("docker"):
        fail("Docker is required for controlled MySQL integration tests")
    container = "zsjos-db-test-" + uuid.uuid4().hex[:10]
    mount = f"{ROOT}:/workspace"
    run([
        "docker", "run", "--detach", "--rm", "--name", container,
        "--env", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", "--env", "MYSQL_DATABASE=zsjos_test",
        "--volume", mount, MYSQL_IMAGE,
        "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
    ], capture=True)
    try:
        docker_wait(container)
        callback(container)
    finally:
        subprocess.run(["docker", "rm", "--force", container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def test_fresh() -> None:
    static_check()

    def execute(container: str) -> None:
        docker_mysql_file(container, SQL_ROOT / "bootstrap.sql")
        output = docker_mysql_file(container, SQL_ROOT / "verify" / "core.sql").stdout.decode(errors="replace")
        failure_lines = [line for line in output.splitlines() if re.search(r"(?:^|\t)(FAIL|MISSING)$", line)]
        if failure_lines:
            fail("Fresh database verification failed:\n" + "\n".join(failure_lines))
        second = docker_mysql_file(container, SQL_ROOT / "bootstrap.sql", expect_success=False)
        if second.returncode == 0:
            fail("Fresh bootstrap unexpectedly allowed execution against a non-empty database")
        info("PASS: fresh bootstrap, verification, and non-empty guard completed.")

    with_test_mysql(execute)


def test_upgrade() -> None:
    static_check()

    def execute(container: str) -> None:
        docker_mysql_file(container, SQL_ROOT / "bootstrap.sql")
        docker_mysql_query(
            container,
            "ALTER TABLE zsjos_lead_intended_product "
            "DROP INDEX uk_tenant_lead_active_product, DROP COLUMN active_product_ref, "
            "ADD UNIQUE KEY uk_tenant_lead_product (tenant_id,lead_id,product_ref); "
            "DROP TABLE crm_owner_record,crm_performance_config,zsjos_module_schema_version; "
            "DELETE FROM zsjos_schema_version WHERE version IN ('V020','V021')",
        )
        migration_v020 = SQL_ROOT / "migrations" / "V020__unified_schema_migration_and_crm_tables.sql"
        docker_mysql_file(container, migration_v020)
        checksum = sha256(migration_v020)
        docker_mysql_query(
            container,
            "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES "
            f"('core','V020','upgrade test','{checksum}','test') ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),release_version='test'",
        )
        docker_mysql_file(container, migration_v020)

        migration_v021 = SQL_ROOT / "migrations" / "V021__lead_intended_product_active_unique_key.sql"
        docker_mysql_file(container, migration_v021)
        checksum = sha256(migration_v021)
        docker_mysql_query(
            container,
            "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES "
            f"('core','V021','upgrade test','{checksum}','test') ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),release_version='test'",
        )
        docker_mysql_query(
            container,
            "INSERT INTO zsjos_lead_intended_product "
            "(lead_id,product_ref,product_name_snapshot,is_primary,sort,deleted,tenant_id) "
            "VALUES (999999,'upgrade-test-course','Upgrade test',b'1',0,b'0',1); "
            "UPDATE zsjos_lead_intended_product SET deleted=b'1' "
            "WHERE tenant_id=1 AND lead_id=999999 AND product_ref='upgrade-test-course' AND deleted=b'0'; "
            "INSERT INTO zsjos_lead_intended_product "
            "(lead_id,product_ref,product_name_snapshot,is_primary,sort,deleted,tenant_id) "
            "VALUES (999999,'upgrade-test-course','Upgrade test',b'1',0,b'0',1)",
        )
        result = docker_mysql_query(
            container,
            "SELECT CONCAT(COUNT(*),':',SUM(deleted=b'0')) FROM zsjos_lead_intended_product "
            "WHERE tenant_id=1 AND lead_id=999999 AND product_ref='upgrade-test-course'",
        ).strip()
        if result != "2:1":
            fail(f"V021 logical-delete uniqueness check failed: {result}")
        output = docker_mysql_file(container, SQL_ROOT / "verify" / "core.sql").stdout.decode(errors="replace")
        failure_lines = [line for line in output.splitlines() if re.search(r"(?:^|\t)(FAIL|MISSING)$", line)]
        if failure_lines:
            fail("V019 to V021 upgrade verification failed:\n" + "\n".join(failure_lines))
        docker_mysql_file(container, migration_v021)
        info("PASS: V019-to-V021 upgrade, logical-delete uniqueness, and idempotent replay completed.")

    with_test_mysql(execute)


def test_guardrails() -> None:
    static_check()

    def execute(container: str) -> None:
        docker_mysql_file(container, SQL_ROOT / "bootstrap.sql")
        client = DockerTestClient(container)
        docker_mysql_query(container, "ALTER TABLE crm_owner_record MODIFY COLUMN biz_type int NOT NULL")
        drift = schema_drift(client, {"core": load_manifests()["core"]})
        if not any("crm_owner_record.biz_type" in item and "column differs" in item for item in drift):
            fail("Schema drift guard did not detect a changed CRM column type")

        docker_mysql_query(
            container,
            "UPDATE zsjos_module_schema_version SET checksum=REPEAT('0',64),release_version='test' "
            "WHERE module_code='core' AND version='V020'",
        )
        installed = installed_versions(client)
        try:
            pending_migrations(load_manifests(), installed)
        except CommandError as error:
            if "checksum changed" not in str(error):
                raise
        else:
            fail("Checksum guard accepted a changed applied migration")
        info("PASS: schema drift and applied-checksum guardrails blocked unsafe state.")

    with_test_mysql(execute)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="zsjos-db", description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    make_parser = subparsers.add_parser("make", help="Generate a reviewed candidate migration from desired schema changes")
    make_parser.add_argument("module")
    make_parser.add_argument("name")
    subparsers.add_parser("check", help="Validate manifests, migration order, baseline, and mapped tables")
    for command in ("plan", "migrate", "verify"):
        command_parser = subparsers.add_parser(command)
        command_parser.add_argument("environment")
    subparsers.add_parser("test-fresh")
    subparsers.add_parser("test-upgrade")
    subparsers.add_parser("test-guardrails")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if args.command == "make":
        make_migration(args.module, args.name)
    elif args.command == "check":
        static_check()
    elif args.command == "plan":
        _, _, unexplained = print_plan(args.environment)
        if unexplained:
            return 2
    elif args.command == "migrate":
        migrate_database(args.environment)
    elif args.command == "verify":
        verify_database(args.environment)
    elif args.command == "test-fresh":
        test_fresh()
    elif args.command == "test-upgrade":
        test_upgrade()
    elif args.command == "test-guardrails":
        test_guardrails()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (CommandError, json.JSONDecodeError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
