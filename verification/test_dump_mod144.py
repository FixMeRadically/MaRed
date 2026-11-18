"""Exporter checks use disposable files, never an existing user's dump."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
SCRIPT = Path(__file__).resolve().parents[1] / "dump_mod.py"
spec = importlib.util.spec_from_file_location("dump_mod144", SCRIPT)
dump = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dump)
class DumpChecks144(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / "mod_dump.txt"
        self.output.write_text("KEEP", encoding="utf-8")
        self.project = self.root / "project"
        (self.project / "src").mkdir(parents=True)
        (self.project / "src/a.java").write_text("class A {}", encoding="utf-8")
    def invoke(self):
        with patch.object(dump, "OUTPUT_FILE", str(self.output)), patch("builtins.input", return_value=str(self.project)), contextlib.redirect_stdout(io.StringIO()):
            dump.main()
    def clean_temporary(self):
        self.assertEqual(list(self.root.glob(".mod-dump-*.tmp")), [])
    def test_invalid_root(self):
        r = subprocess.run([os.sys.executable, str(SCRIPT)], input=str(self.root / "missing") + "\n", cwd=self.root, capture_output=True, text=True)
        self.assertNotEqual(r.returncode, 0)
        self.assertNotIn("Готово", r.stdout)
        self.assertEqual(self.output.read_text(), "KEEP")
    def test_empty_path(self):
        with patch("builtins.input", return_value=""), self.assertRaises(SystemExit): dump.main()
        self.assertEqual(self.output.read_text(), "KEEP")
    def test_unrelated_folder(self):
        with patch("builtins.input", return_value=str(self.root)), self.assertRaises(SystemExit): dump.main()
        self.assertEqual(self.output.read_text(), "KEEP")
    def test_failed_read(self):
        with patch.object(dump, "read_text", side_effect=PermissionError("injected")), self.assertRaises(PermissionError): self.invoke()
        self.assertEqual(self.output.read_text(), "KEEP")
        self.clean_temporary()
    def test_failed_publication(self):
        with patch.object(dump.os, "replace", side_effect=PermissionError("injected")), self.assertRaises(PermissionError): self.invoke()
        self.assertEqual(self.output.read_text(), "KEEP")
        self.clean_temporary()
    def test_complete_export(self):
        (self.project / "src/build").mkdir()
        (self.project / "src/build/skip.java").write_text("SKIP")
        self.invoke()
        text = self.output.read_text()
        self.assertIn("class A {}", text)
        self.assertNotIn("SKIP", text)
        self.clean_temporary()
    def test_read_error_visible(self):
        with patch.object(Path, "read_text", side_effect=PermissionError("injected")), self.assertRaises(PermissionError): dump.read_text(self.project / "src/a.java")
    def test_legacy_encoding(self):
        p = self.project / "src/legacy.txt"
        p.write_bytes("Привет".encode("cp1251"))
        self.assertEqual(dump.read_text(p), "Привет")
if __name__ == "__main__": unittest.main()
