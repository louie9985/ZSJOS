#!/usr/bin/env python3
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent
NS = {"b": "http://www.omg.org/spec/BPMN/20100524/MODEL", "di": "http://www.omg.org/spec/BPMN/20100524/DI", "flowable": "http://flowable.org/bpmn"}
SEMVER = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
PROCESS_KEY = re.compile(r"^[a-z0-9_]+$")


def fail(message):
    print(f"BPM asset validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_base_manifest(base_ref):
    if not base_ref:
        return None
    try:
        subprocess.run(["git", "rev-parse", "--verify", f"{base_ref}^{{commit}}"], check=True,
                       capture_output=True, text=True)
    except subprocess.CalledProcessError as error:
        fail(f"cannot resolve baseline ref {base_ref}: {error.stderr.strip()}")
    result = subprocess.run(["git", "show", f"{base_ref}:script/bpm/manifest.json"],
                            capture_output=True, text=True)
    if result.returncode != 0:
        return None
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as error:
        fail(f"invalid baseline manifest {base_ref}: {error}")


def validate_asset(asset, registered, current):
    key, version = asset["processKey"], asset["assetVersion"]
    if not PROCESS_KEY.fullmatch(key):
        fail(f"invalid process key {key}")
    if not SEMVER.fullmatch(version):
        fail(f"invalid semantic version {key}@{version}")
    identity = (key, version)
    if identity in registered:
        fail(f"duplicate version {key}@{version}")
    registered.add(identity)
    if asset.get("recommended"):
        if key in current:
            fail(f"multiple recommended versions for {key}")
        current.add(key)
    asset_format = asset.get("assetFormat", "bpmn")
    filename = "process-model.json" if asset_format == "simple" else "process.bpmn20.xml"
    if asset_format not in {"bpmn", "simple"}:
        fail(f"unsupported asset format {asset_format} for {key}@{version}")
    expected_path = pathlib.PurePosixPath(key) / version / filename
    if pathlib.PurePosixPath(asset["path"]) != expected_path:
        fail(f"asset path must be {expected_path}, got {asset['path']}")
    path = (ROOT / asset["path"]).resolve()
    if not path.is_relative_to(ROOT):
        fail(f"asset path escapes BPM root: {asset['path']}")
    if not path.is_file():
        fail(f"missing file {asset['path']}")
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != asset["sha256"]:
        fail(f"checksum mismatch {asset['path']}")
    if asset_format == "simple":
        validate_simple_asset(asset, data)
        return
    root = ET.fromstring(data)
    processes = root.findall("b:process", NS)
    if len(processes) != 1 or processes[0].get("id") != key:
        fail(f"process key mismatch {asset['path']}")
    tasks = processes[0].findall("b:userTask", NS)
    if {node.get("id") for node in tasks} != set(asset["taskKeys"]):
        fail(f"task keys mismatch {asset['path']}")
    if root.find("di:BPMNDiagram", NS) is None:
        fail(f"missing BPMN DI {asset['path']}")
    for task in tasks:
        extension = task.find("b:extensionElements", NS)
        strategy = extension.find("flowable:candidateStrategy", NS) if extension is not None else None
        if strategy is None or not (strategy.text or "").strip():
            fail(f"missing candidate strategy for task {task.get('id')} in {asset['path']}")
    text = data.decode("utf-8")
    for variable in asset["businessVariables"] + asset["assigneeVariables"]:
        if variable not in text and variable not in asset.get("runtimeOnlyVariables", []):
            fail(f"variable {variable} missing from {asset['path']}")


def validate_simple_asset(asset, data):
    try:
        model = json.loads(data)
    except json.JSONDecodeError as error:
        fail(f"invalid SIMPLE model {asset['path']}: {error}")
    if model.get("key") != asset["processKey"] or model.get("type") != 20:
        fail(f"SIMPLE model key/type mismatch {asset['path']}")
    root = model.get("simpleModel")
    if not isinstance(root, dict) or root.get("id") != "StartUserNode" or root.get("type") != 10:
        fail(f"invalid SIMPLE start node {asset['path']}")
    tasks = {}
    node = root
    while isinstance(node, dict):
        if node.get("type") in {11, 13}:
            tasks[node.get("id")] = node
        node = node.get("childNode")
    if set(tasks) != set(asset["taskKeys"]):
        fail(f"task keys mismatch {asset['path']}")
    for task_id, task in tasks.items():
        if task.get("candidateStrategy") != 60 or not task.get("candidateParam"):
            fail(f"invalid SIMPLE candidate expression for task {task_id} in {asset['path']}")
    text = data.decode("utf-8")
    for variable in asset["businessVariables"] + asset["assigneeVariables"]:
        if variable not in text and variable not in asset.get("runtimeOnlyVariables", []):
            fail(f"variable {variable} missing from {asset['path']}")


def validate_immutability(base_manifest, current_manifest):
    if not base_manifest:
        return
    current_by_identity = {(asset["processKey"], asset["assetVersion"]): asset
                           for asset in current_manifest["assets"]}
    for old in base_manifest.get("assets", []):
        identity = (old["processKey"], old["assetVersion"])
        current = current_by_identity.get(identity)
        if current is None:
            fail(f"published asset was removed: {identity[0]}@{identity[1]}")
        immutable_old = {key: value for key, value in old.items() if key != "recommended"}
        immutable_current = {key: value for key, value in current.items() if key != "recommended"}
        if immutable_current != immutable_old:
            fail(f"published asset was modified: {identity[0]}@{identity[1]}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-ref", help="trusted git ref used to enforce published asset immutability")
    args = parser.parse_args()
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    registered, current = set(), set()
    for asset in manifest["assets"]:
        validate_asset(asset, registered, current)
    manifest_paths = {item["path"] for item in manifest["assets"]}
    for path in ROOT.glob("*/ */process.bpmn20.xml".replace(" ", "")):
        rel = path.relative_to(ROOT).as_posix()
        if rel not in manifest_paths:
            fail(f"unregistered BPMN {rel}")
    if current != {item["processKey"] for item in manifest["assets"]}:
        fail("each process must have one recommended version")
    validate_immutability(read_base_manifest(args.base_ref), manifest)
    print(f"Validated {len(registered)} versioned BPM assets")


if __name__ == "__main__":
    main()
