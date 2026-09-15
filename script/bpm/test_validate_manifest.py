import copy
import json
import pathlib
import subprocess
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import validate_manifest


class ValidateManifestTest(unittest.TestCase):

    @patch("validate_manifest.subprocess.run")
    def test_missing_baseline_manifest_is_first_asset_rollout(self, run):
        run.side_effect = [
            subprocess.CompletedProcess([], 0, "commit", ""),
            subprocess.CompletedProcess([], 128, "", "missing path"),
        ]

        self.assertIsNone(validate_manifest.read_base_manifest("base-sha"))

    def test_version_business_metadata_is_immutable(self):
        asset = {
            "processKey": "sample_process",
            "assetVersion": "1.0.0",
            "recommended": True,
            "path": "sample_process/1.0.0/process.bpmn20.xml",
            "sha256": "abc",
            "businessVariables": ["businessId"],
        }
        changed = copy.deepcopy(asset)
        changed["businessVariables"] = ["differentId"]

        with self.assertRaises(SystemExit):
            validate_manifest.validate_immutability({"assets": [asset]}, {"assets": [changed]})

    def test_process_key_cannot_escape_asset_root(self):
        asset = {"processKey": "../outside", "assetVersion": "1.0.0"}

        with self.assertRaises(SystemExit):
            validate_manifest.validate_asset(asset, set(), set())

    def test_simple_asset_requires_stable_tasks_and_expressions(self):
        model = {
            "key": "sample_process",
            "type": 20,
            "simpleModel": {
                "id": "StartUserNode",
                "type": 10,
                "childNode": {
                    "id": "review",
                    "type": 11,
                    "candidateStrategy": 60,
                    "candidateParam": "${reviewUsers}",
                    "childNode": {"id": "EndEvent", "type": 1},
                },
            },
        }
        asset = {
            "processKey": "sample_process",
            "path": "sample_process/1.0.0/process-model.json",
            "taskKeys": ["review"],
            "businessVariables": [],
            "assigneeVariables": ["reviewUsers"],
            "runtimeOnlyVariables": [],
        }

        validate_manifest.validate_simple_asset(asset, json.dumps(model).encode())

    def test_simple_asset_finds_tasks_in_parallel_branches(self):
        def task(task_id, variable):
            return {
                "id": task_id,
                "type": 11,
                "candidateStrategy": 60,
                "candidateParam": "${" + variable + "}",
            }

        model = {
            "key": "parallel_process",
            "type": 20,
            "simpleModel": {
                "id": "StartUserNode",
                "type": 10,
                "childNode": {
                    "id": "parallel",
                    "type": 52,
                    "conditionNodes": [
                        {"id": "flow1", "type": 50, "childNode": task("review1", "reviewers1")},
                        {"id": "flow2", "type": 50, "childNode": task("review2", "reviewers2")},
                    ],
                    "childNode": {"id": "EndEvent", "type": 1},
                },
            },
        }
        asset = {
            "processKey": "parallel_process",
            "path": "parallel_process/1.0.0/process-model.json",
            "taskKeys": ["review1", "review2"],
            "businessVariables": [],
            "assigneeVariables": ["reviewers1", "reviewers2"],
            "runtimeOnlyVariables": [],
        }

        validate_manifest.validate_simple_asset(asset, json.dumps(model).encode())


if __name__ == "__main__":
    unittest.main()
