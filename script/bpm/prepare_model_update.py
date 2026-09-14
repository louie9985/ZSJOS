#!/usr/bin/env python3
"""Build an offline update payload while retaining tenant-owned model settings."""
import argparse
import copy
import json
import pathlib
import sys


def prepare(existing, asset, model_id):
    if "code" in existing:
        if existing["code"] != 0:
            raise ValueError("model export API did not succeed")
        existing = existing["data"]
    if existing.get("key") != asset.get("key") or asset.get("type") != 20:
        raise ValueError("process key mismatch or asset is not SIMPLE")
    if not isinstance(asset.get("simpleModel"), dict):
        raise ValueError("SIMPLE node tree is missing")
    if existing.get("id") and str(existing["id"]) != model_id:
        raise ValueError("model ID does not match the exported model")
    result = copy.deepcopy(existing)
    result.update(id=model_id, type=20, bpmnXml=None,
                  simpleModel=copy.deepcopy(asset["simpleModel"]))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--export", required=True, type=pathlib.Path)
    parser.add_argument("--asset", required=True, type=pathlib.Path)
    parser.add_argument("--model-id", required=True)
    args = parser.parse_args()
    result = prepare(json.loads(args.export.read_text(encoding="utf-8-sig")),
                     json.loads(args.asset.read_text(encoding="utf-8-sig")), args.model_id)
    sys.stdout.reconfigure(encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
