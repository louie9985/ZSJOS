#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Validate pinned historical administrative data without touching a database."""
from __future__ import annotations

import csv
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

DATA = Path(__file__).resolve().parents[1] / "data" / "identity-area"


def load_records(directory: Path = DATA) -> list[dict[str, str]]:
    manifest = json.loads((directory / "source.json").read_text(encoding="utf-8"))
    for name, digest in manifest["sha256"].items():
        if hashlib.sha256((directory / name).read_bytes()).hexdigest() != digest:
            raise ValueError(f"Source checksum mismatch: {name}")
    with (directory / "address-codes.csv").open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        expected = ["代码", "一级行政区", "二级行政区", "名称", "级别", "状态", "启用时间", "变更/弃用时间", "新代码"]
        if reader.fieldnames != expected:
            raise ValueError("Unexpected source columns")
        records = list(reader)
    seen = set()
    for row in records:
        if not re.fullmatch(r"\d{6}", row["代码"]) or not row["名称"] or not row["一级行政区"]:
            raise ValueError("Invalid administrative record")
        identity = tuple(row.values())
        if identity in seen:
            raise ValueError("Duplicate source record")
        seen.add(identity)
        if "\ufffd" in "".join(identity):
            raise ValueError("Replacement character in source")
    return records


def audit(records: list[dict[str, str]]) -> dict:
    counties = [r for r in records if r["级别"] == "县级"]
    codes = defaultdict(list)
    for row in counties:
        codes[row["代码"]].append(row)
    return {
        "records": len(records),
        "county_records": len(counties),
        "distinct_county_codes": len(codes),
        "county_status_counts": dict(Counter(r["状态"] for r in counties)),
        "codes_with_multiple_records": sum(len(v) > 1 for v in codes.values()),
        "multi_successor_records": sum(";" in r["新代码"] for r in counties),
        "regression_examples": {code: codes.get(code, []) for code in ("420621", "362421")},
        "limitations": "Annual administrative records are not a complete police assignment registry; successor codes must not infer current household location.",
    }


if __name__ == "__main__":
    print(json.dumps(audit(load_records()), ensure_ascii=True, indent=2))
