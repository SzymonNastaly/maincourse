import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
from screenshots.main import (
    duplicate_target_frame,
    navigate,
    resolve_plan,
    selection,
    wait_for_images,
)
from screenshots.render import metadata_path, produce, render_artwork


class PipelineTest(unittest.TestCase):
    def test_duplicate_ipad_tabs_resolve_only_when_they_share_one_rectangle(self):
        node = {"AXLabel": "Shopping List", "type": "RadioButton", "enabled": True,
                "frame": {"x": 400, "y": 36, "width": 140, "height": 36}}
        step = {"label": "Shopping List", "element_type": "RadioButton"}
        self.assertEqual(duplicate_target_frame([node, node], step, "label"), (400, 36, 140, 36))
        other = {**node, "frame": {**node["frame"], "x": 0}}
        self.assertIsNone(duplicate_target_frame([node, other], step, "label"))
        self.assertIsNone(duplicate_target_frame([node], step, "label"))

    def test_plans_resolve_selectors_and_launch_locale_without_touching_source(self):
        source = Path(__file__).resolve().parents[2] / "screenshots/plans/detail.json"
        before = source.read_text()
        plan = resolve_plan(source, {"tomato-orzo": 123}, "de-DE")
        self.assertEqual(plan["steps"][1]["id"], "recipe.123")
        self.assertEqual(plan["app"]["launch_arguments"][-4:], ["-AppleLanguages", "(de)", "-AppleLocale", "de_DE"])
        self.assertEqual(source.read_text(), before)

    def test_missing_fixture_selector_fails_before_running_automation(self):
        source = Path(__file__).resolve().parents[2] / "screenshots/plans/detail.json"
        with self.assertRaisesRegex(ValueError, "Unresolved"):
            resolve_plan(source, {}, "en-US")

    def test_unknown_or_duplicate_devices_are_rejected(self):
        for devices in ("iphone,unknown", "iphone,iphone", ""):
            with self.assertRaises(ValueError):
                selection(devices, {"iphone": {}, "ipad": {}}, "devices")

    def test_typed_tap_runs_between_asc_segments_without_losing_steps(self):
        with tempfile.TemporaryDirectory() as directory:
            plan = {"version": 1, "app": {"bundle_id": "example"}, "steps": [
                {"action": "wait_for", "id": "recipe.add"},
                {"action": "tap", "id": "recipe.add", "element_type": "Button"},
                {"action": "wait_for", "label": "Review"},
            ]}
            with patch("screenshots.main.run", return_value="{}") as runner:
                path = Path(directory) / "navigation.json"
                navigate(plan, path, "device-uuid")
                calls = [call.args for call in runner.call_args_list]
                self.assertEqual([call[0] for call in calls], ["asc", "axe", "asc"])
                self.assertIn("Button", calls[1])
                self.assertEqual(json.loads((path.parent / "navigation-1.json").read_text())["steps"], plan["steps"][:1])
                self.assertEqual(json.loads((path.parent / "navigation-3.json").read_text())["steps"], plan["steps"][2:])

    def test_image_failure_never_becomes_a_successful_capture(self):
        with tempfile.TemporaryDirectory() as directory:
            status = Path(directory) / "status.json"
            status.write_text(json.dumps({"stage": "ready", "pending_images": 0,
                                          "failures": ["Image download failed"], "updated_at": 0}))
            with self.assertRaisesRegex(RuntimeError, "Image download failed"):
                wait_for_images(status)

    def test_pending_images_timeout_instead_of_capturing_placeholders(self):
        with tempfile.TemporaryDirectory() as directory:
            status = Path(directory) / "status.json"
            status.write_text(json.dumps({"stage": "ready", "pending_images": 1,
                                          "failures": [], "updated_at": 0}))
            with (
                patch("screenshots.main.time.sleep"),
                patch("screenshots.main.time.monotonic", side_effect=[0, 0, 0, 61]),
                self.assertRaisesRegex(RuntimeError, "never became ready"),
            ):
                wait_for_images(status)

    def test_renderer_rejects_changed_source_before_invoking_koubou(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            raw = output / "raw/ios/en-US/iphone/recipe-detail.png"
            raw.parent.mkdir(parents=True)
            raw.write_bytes(b"changed screenshot")
            raw.with_suffix(".json").write_text(json.dumps({"sha256": "previous-image-hash"}))
            args = SimpleNamespace(locale="en-US", platform="ios", preset="app-store")
            with patch("screenshots.render.OUTPUT", output), patch("screenshots.render.koubou") as renderer:
                with self.assertRaisesRegex(ValueError, "Raw capture changed"):
                    render_artwork(args, {"devices": {"iphone": {"size": [1320, 2868]}}}, ["iphone"], ["recipe-detail"])
                renderer.assert_not_called()

    def test_rendered_upload_directory_contains_images_only(self):
        import struct
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output"
            destination = output / "rendered/app-store/ios/en-US/iphone/01-library.png"

            def fake_renderer(config, config_path):
                generated = Path(config["project"]["output_dir"]) / "device/01-library.png"
                generated.parent.mkdir(parents=True)
                generated.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\0" * 8 + struct.pack(">II", 1, 1))
                generated.with_suffix(".layout.json").write_text('{"version":1,"elements":[]}')

            with patch("screenshots.render.OUTPUT", output), patch("screenshots.render.koubou", side_effect=fake_renderer):
                produce({"project": {"output_size": [1, 1]}}, root / "work", "slide", destination)
                self.assertEqual([p.name for p in destination.parent.iterdir()], ["01-library.png"])
                self.assertTrue(metadata_path(destination).with_suffix(".layout.json").is_file())


if __name__ == "__main__":
    unittest.main()
