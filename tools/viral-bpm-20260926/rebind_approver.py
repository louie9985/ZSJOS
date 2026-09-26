#!/usr/bin/env python3
"""Rebind the two viral material review nodes to a single fixed approver.

Reads the model exports produced by GET /admin-api/bpm/model/get, rewrites only the
approve node's candidate binding and display name, and writes the PUT payload.
Dry-run by default; pass --apply to actually call PUT /admin-api/bpm/model/update.
"""
import argparse
import copy
import json
import pathlib
import sys
import urllib.request

BASE = "http://127.0.0.1:48080/admin-api/bpm/model"
HERE = pathlib.Path(__file__).resolve().parent

TARGETS = [
    ("account", "2be5a425-b388-11f1-a04c-d2ce0d52b6f7"),
    ("content", "69eed618-b388-11f1-a04c-d2ce0d52b6f7"),
]

NEW_NAME = "爆款审核"
NEW_STRATEGY = 30      # BpmTaskCandidateStrategyEnum.USER
NEW_PARAM = "12"       # AdminUserDO id 12 = 程伟 (enabled, username ChengWei)
NEW_SHOW_TEXT = "程伟老师"


def find_approve_node(simple_model):
    node = simple_model
    while node is not None:
        if node.get("type") == 11:
            return node
        node = node.get("childNode")
    return None


def build_payload(tag, model_id):
    exported = json.loads((HERE / f"model-export-{tag}.json").read_text(encoding="utf-8"))
    if exported.get("code") != 0:
        raise SystemExit(f"{tag}: export did not succeed: {exported.get('msg')}")
    data = exported["data"]
    if data.get("id") != model_id or data.get("type") != 20:
        raise SystemExit(f"{tag}: model identity mismatch")

    # Keep every tenant-owned setting; replace the node tree only.
    payload = copy.deepcopy(data)
    simple = copy.deepcopy(data["simpleModel"])
    node = find_approve_node(simple)
    if node is None:
        raise SystemExit(f"{tag}: no approve node (type 11) in the tree")

    before = {k: node.get(k) for k in ("id", "name", "showText", "candidateStrategy", "candidateParam")}
    node["name"] = NEW_NAME
    node["candidateStrategy"] = NEW_STRATEGY
    node["candidateParam"] = NEW_PARAM
    node["showText"] = NEW_SHOW_TEXT
    after = {k: node.get(k) for k in ("id", "name", "showText", "candidateStrategy", "candidateParam")}

    payload["simpleModel"] = simple
    return payload, before, after


def put_model(payload, token):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        f"{BASE}/update", data=body, method="PUT",
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Authorization": f"Bearer {token}",
            "tenant-id": "1",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--token", help="admin access token")
    parser.add_argument("--apply", action="store_true", help="actually PUT (default: dry-run)")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    for tag, model_id in TARGETS:
        payload, before, after = build_payload(tag, model_id)
        print(f"[{tag}] {model_id}")
        print(f"  node id : {before['id']}")
        print(f"  before  : {json.dumps(before, ensure_ascii=False)}")
        print(f"  after   : {json.dumps(after, ensure_ascii=False)}")
        other = [k for k in ("approveMethod", "reasonRequire", "signEnable")
                 if payload["simpleModel"].get("childNode", {}).get(k)
                 != json.loads((HERE / f"model-export-{tag}.json").read_text(encoding="utf-8"))["data"]["simpleModel"]["childNode"].get(k)]
        print(f"  other node fields changed: {other or 'none'}")
        if args.apply:
            if not args.token:
                raise SystemExit("--apply requires --token")
            print(f"  PUT -> {json.dumps(put_model(payload, args.token), ensure_ascii=False)}")


if __name__ == "__main__":
    main()
