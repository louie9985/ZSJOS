import copy
import json
import pathlib
import subprocess
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import validate_manifest
from prepare_model_update import prepare


class ValidateManifestTest(unittest.TestCase):

    def test_update_preserves_existing_form_and_management_configuration(self):
        existing = {"key": "sample", "type": 10, "formType": 10, "formId": 42,
                    "managerUserIds": [7], "category": "tenant-category", "visible": False}
        asset = {"key": "sample", "type": 20, "formType": 20,
                 "simpleModel": {"id": "StartUserNode", "type": 10}}
        result = prepare(existing, asset, "model-1")
        self.assertEqual(42, result["formId"])
        self.assertEqual(10, result["formType"])
        self.assertEqual([7], result["managerUserIds"])
        self.assertEqual("tenant-category", result["category"])
        self.assertEqual(20, result["type"])
        self.assertEqual(10, existing["type"])
        with self.assertRaises(ValueError):
            prepare(existing, {**asset, "key": "another"}, "model-1")

    def test_text_asset_checksum_is_line_ending_independent(self):
        self.assertEqual(validate_manifest.text_asset_sha256(b"a\nb\n"),
                         validate_manifest.text_asset_sha256(b"a\r\nb\r\n"))

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
                    "name": "Review",
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

    def test_simple_asset_collects_tasks_from_parallel_branches(self):
        model = {
            "key": "sample_process", "type": 20,
            "simpleModel": {
                "id": "StartUserNode", "type": 10,
                "childNode": {
                    "id": "parallel", "type": 52,
                    "conditionNodes": [
                        {"id": "leftFlow", "type": 50, "childNode": {
                            "id": "leftReview", "type": 11, "name": "Left",
                            "candidateStrategy": 60, "candidateParam": "${leftUsers}"}},
                        {"id": "rightFlow", "type": 50, "childNode": {
                            "id": "rightReview", "type": 11, "name": "Right",
                            "candidateStrategy": 60, "candidateParam": "${rightUsers}"}},
                    ],
                    "childNode": {"id": "EndEvent", "type": 1},
                },
            },
        }
        asset = {
            "processKey": "sample_process", "path": "sample_process/2.0.0/process-model.json",
            "taskKeys": ["leftReview", "rightReview"], "businessVariables": [],
            "assigneeVariables": ["leftUsers", "rightUsers"], "runtimeOnlyVariables": [],
        }
        validate_manifest.validate_simple_asset(asset, json.dumps(model).encode())

    def test_simple_asset_allows_start_user_selected_assignee(self):
        model = {
            "key": "sample_process", "type": 20,
            "simpleModel": {"id": "StartUserNode", "type": 10, "childNode": {
                "id": "review", "type": 11, "name": "Review", "candidateStrategy": 35,
                "childNode": {"id": "EndEvent", "type": 1}}},
        }
        asset = {"processKey": "sample_process", "path": "sample_process/1.0.0/process-model.json",
                 "taskKeys": ["review"], "businessVariables": [], "assigneeVariables": [],
                 "runtimeOnlyVariables": []}
        validate_manifest.validate_simple_asset(asset, json.dumps(model).encode())

    def test_simple_asset_requires_expression_on_non_default_condition(self):
        model = {
            "key": "sample_process", "type": 20,
            "simpleModel": {
                "id": "StartUserNode", "type": 10,
                "childNode": {
                    "id": "gateway", "type": 51,
                    "conditionNodes": [
                        {"id": "conditional", "type": 50,
                         "conditionSetting": {"conditionType": 1, "defaultFlow": False}},
                        {"id": "default", "type": 50,
                         "conditionSetting": {"conditionType": 1, "defaultFlow": True}},
                    ],
                    "childNode": {"id": "EndEvent", "type": 1},
                },
            },
        }
        asset = {"processKey": "sample_process", "path": "sample_process/2.0.0/process-model.json",
                 "taskKeys": [], "businessVariables": [], "assigneeVariables": [],
                 "runtimeOnlyVariables": []}

        with self.assertRaises(SystemExit):
            validate_manifest.validate_simple_asset(asset, json.dumps(model).encode())


if __name__ == "__main__":
    unittest.main()
