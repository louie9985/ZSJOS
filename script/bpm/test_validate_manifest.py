import copy
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


if __name__ == "__main__":
    unittest.main()
