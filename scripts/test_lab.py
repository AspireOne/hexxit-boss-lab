import gzip
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

import lab
from world_name import rename_world


class LabWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def test_rejects_destination_outside_managed_root(self):
        with self.assertRaises(ValueError):
            lab.require_child(self.root / '..' / 'original-save', self.root)
        with self.assertRaises(ValueError):
            lab.require_child(self.root, self.root)

    def test_rejects_instance_with_wrong_marker(self):
        prism = self.root / 'prism'
        target = prism / 'instances/lab'
        state = self.root / 'state'
        lab.write_json(state / 'instance.json', {'id': 'expected', 'target': str(target)})
        lab.write_json(target / lab.MARKER, {'id': 'another-project', 'target': str(target)})
        with patch.object(lab, 'PRISM', prism), self.assertRaises(ValueError):
            lab.validate_lab(target, state)

    def test_install_retains_previous_artifact_and_unrelated_mod(self):
        mods = self.root / 'mods'
        history = self.root / 'history'
        mods.mkdir()
        (mods / 'another-mod.jar').write_bytes(b'unrelated')
        artifact = self.root / 'build.jar'
        artifact.write_bytes(b'first')
        first = lab.install_artifact(artifact, mods, history)
        artifact.write_bytes(b'second')
        second = lab.install_artifact(artifact, mods, history)
        self.assertEqual(b'first', (history / (first + '.jar')).read_bytes())
        self.assertEqual(b'second', (mods / lab.MOD_FILE).read_bytes())
        self.assertEqual({'current': second, 'previous': first}, lab.read_json(history / 'deployment.json'))
        self.assertEqual(b'unrelated', (mods / 'another-mod.jar').read_bytes())
        lab.install_artifact(artifact, mods, history)
        self.assertEqual(first, lab.read_json(history / 'deployment.json')['previous'])

    def test_failed_copy_preserves_installed_artifact(self):
        mods = self.root / 'mods'
        history = self.root / 'history'
        artifact = self.root / 'build.jar'
        artifact.write_bytes(b'known-good')
        lab.install_artifact(artifact, mods, history)
        artifact.write_bytes(b'new')
        real_copy = lab.shutil.copy2

        def fail_incoming(source, destination):
            if str(destination).endswith('.incoming'):
                raise OSError('Disk full')
            return real_copy(source, destination)

        with patch.object(lab.shutil, 'copy2', side_effect=fail_incoming), self.assertRaises(OSError):
            lab.install_artifact(artifact, mods, history)
        self.assertEqual(b'known-good', (mods / lab.MOD_FILE).read_bytes())

    def test_failed_build_never_installs(self):
        with patch.object(lab, 'validate_lab'), patch.object(lab, 'ensure_stopped'), \
                patch.object(lab, 'checked_artifact', side_effect=RuntimeError('compile failed')), \
                patch.object(lab, 'install_artifact') as install, self.assertRaises(RuntimeError):
            lab.apply()
        install.assert_not_called()

    def test_reset_keeps_previous_world_and_baseline(self):
        target = self.root / 'target'
        state = self.root / 'state'
        world = target / 'minecraft/saves' / lab.WORLD
        baseline = state / 'baseline'
        world.mkdir(parents=True)
        baseline.mkdir(parents=True)
        (world / 'region.data').write_bytes(b'played')
        (baseline / 'region.data').write_bytes(b'baseline')
        lab.write_json(state / 'baseline-hashes.json', lab.tree_hashes(baseline))
        with patch.object(lab, 'TARGET', target), patch.object(lab, 'STATE', state), \
                patch.object(lab, 'validate_lab'), patch.object(lab, 'ensure_stopped'):
            lab.reset_world()
        self.assertEqual(b'baseline', (world / 'region.data').read_bytes())
        self.assertEqual(b'baseline', (baseline / 'region.data').read_bytes())
        self.assertEqual([b'played'], [p.read_bytes() for p in (state / 'snapshots').glob('*/region.data')])

    def test_changed_baseline_refuses_reset(self):
        state = self.root / 'state'
        baseline = state / 'baseline'
        baseline.mkdir(parents=True)
        (baseline / 'region.data').write_bytes(b'baseline')
        lab.write_json(state / 'baseline-hashes.json', lab.tree_hashes(baseline))
        (baseline / 'region.data').write_bytes(b'changed')
        with patch.object(lab, 'STATE', state), patch.object(lab, 'validate_lab'), \
                patch.object(lab, 'ensure_stopped'), self.assertRaises(RuntimeError):
            lab.reset_world()

    def test_world_rename_preserves_inventory_bytes_and_other_names(self):
        def text(value):
            encoded = value.encode('utf-8')
            return struct.pack('>H', len(encoded)) + encoded

        inventory = b'\x07' + text('InventoryBlob') + struct.pack('>i', 4) + b'\x00\xff\x01\x02'
        other = b'\x08' + text('LevelName') + text('nested-name')
        original = b'\x0a\x00\x00\x0a' + text('Data') + b'\x08' + text('LevelName') + text('original') + inventory + b'\x0a' + text('Nested') + other + b'\x00\x00\x00'
        path = self.root / 'level.dat'
        path.write_bytes(gzip.compress(original))
        rename_world(path, 'Kratom - Boss Lab')
        actual = gzip.decompress(path.read_bytes())
        self.assertEqual(original.replace(text('original'), text('Kratom - Boss Lab')), actual)

    def test_truncated_save_is_not_overwritten(self):
        path = self.root / 'level.dat'
        original = gzip.compress(b'\x0a\x00\x00\x08\x00')
        path.write_bytes(original)
        with self.assertRaises(ValueError):
            rename_world(path, lab.WORLD)
        self.assertEqual(original, path.read_bytes())


if __name__ == '__main__':
    unittest.main()
