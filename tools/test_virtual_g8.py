# SPDX-License-Identifier: GPL-3.0-or-later
import importlib.util,json,pathlib,unittest
from unittest.mock import patch
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('virtual_g8',ROOT/'tools/virtual_g8.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class VirtualWorkflowTests(unittest.TestCase):
    def test_target_is_always_explicit_emulator(self):
        self.assertRegex(module.CONFIG['serial'],r'^emulator-\d+$')
        with patch.object(module,'command')as run:
            module.adb('shell','getprop','ro.kernel.qemu')
            self.assertEqual(run.call_args.args[0][1:3],['-s',module.CONFIG['serial']])
    def test_verify_refuses_physical_target(self):
        with patch.object(module,'owned'), patch.object(module,'output',return_value='0'):
            with self.assertRaises(RuntimeError):module.verify()
    def test_scenarios_are_bounded_and_provenanced(self):
        cases=list((ROOT/'tools/scenarios').glob('*.json'));self.assertGreaterEqual(len(cases),7)
        for path in cases:
            r=json.loads(path.read_text());self.assertEqual(r['schema'],1);self.assertEqual(r['source'],'synthetic')
            times=[s['at_ms']for s in r['steps']];self.assertEqual(times,sorted(times));self.assertLess(max(times),60000);self.assertLess(len(times),2000)
    def test_simulation_has_no_release_entry(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text();self.assertNotIn('VirtualG8',manifest)
        self.assertFalse(list((ROOT/'app/src/main').rglob('*VirtualG8*')))
        self.assertIn('VirtualG8Instrumentation',(ROOT/'app/src/debug/AndroidManifest.xml').read_text())
    def test_pin_is_aosp_api36_x86_64(self):
        self.assertEqual(module.CONFIG['image'],'system-images;android-36;default;x86_64')
        self.assertEqual(module.CONFIG['image_revision'],'2')
