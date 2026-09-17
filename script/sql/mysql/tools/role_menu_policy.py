#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Reject role-menu mutations in deployable SQL; authorization belongs to System UI."""
import re
from pathlib import Path


def executable_sql(text: str) -> str:
    """Mask comments/literals without changing offsets (including quoted semicolons)."""
    pattern = r"--[^\n]*|\#[^\n]*|/\*[\s\S]*?\*/|'(?:\\.|''|[^'\\])*'|\"(?:\\.|\"\"|[^\"\\])*\""
    return re.sub(pattern, lambda m: ''.join('\n' if c == '\n' else ' ' for c in m[0]), text)


WRITE = re.compile(
    r'\b(?:INSERT\s+(?:IGNORE\s+)?INTO|REPLACE(?:\s+INTO)?|UPDATE|DELETE\s+FROM)'
    r'\s+`?system_role_menu`?\b|'
    r'\bDELETE\s+\w+\s+FROM\s+`?system_role_menu`?\b', re.I)


def mutations(text: str):
    sql = executable_sql(text)
    for match in WRITE.finditer(sql):
        end = sql.find(';', match.end())
        if end < 0:
            raise ValueError('Unterminated role-menu mutation')
        yield match.start(), end + 1


def check(root: Path) -> list[str]:
    failures = []
    for path in sorted(root.rglob('*.sql')):
        text = path.read_text(encoding='utf-8-sig')
        for start, _ in mutations(text):
            failures.append(f'{path.relative_to(root)}:{text.count(chr(10), 0, start) + 1}')
        # Executable comments and prepared strings must not bypass the ordinary SQL scan.
        for match in re.finditer(r'/\*![\s\S]*?\*/|[\'\"][^\n]*system_role_menu[^\n]*[\'\"]', text, re.I):
            if re.search(r'\b(INSERT|UPDATE|DELETE|REPLACE)\b', match[0], re.I):
                failures.append(f'{path.relative_to(root)}: dynamic role-menu SQL requires review')
    return failures


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[2]
    errors = check(root) + check(Path(__file__).resolve().parents[4] / 'sql')
    print('\n'.join(errors) if errors else 'PASS: deployment SQL contains no role-menu mutations')
    raise SystemExit(bool(errors))
