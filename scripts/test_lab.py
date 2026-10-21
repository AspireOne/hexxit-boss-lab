import gzip
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import lab
from world_name import rename_world


class LabWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def test_process_probe_fails_closed_in_real_powershell(self):
        cases = (
            ("Write-Error 'CIM unavailable'", 10),
            ("[pscustomobject]@{ Name='javaw.exe'; CommandLine=$null }", 10),
            ("[pscustomobject]@{ Name='javaw.exe'; CommandLine='org.prismlauncher.EntryPoint' }", 9),
            ("[pscustomobject]@{ Name='java.exe'; CommandLine='org.gradle.launcher.daemon' }", 0),
            ('', 0),
        )
        for body, expected in cases:
            with self.subTest(body=body):
                script = 'function Get-CimInstance { [CmdletBinding()] param($ClassName) ' + body + ' }; ' + lab.PROCESS_CHECK
                result = subprocess.run(['powershell', '-NoProfile', '-Command', script], capture_output=True)
                self.assertEqual(expected, result.returncode)

    def test_failed_process_inspection_blocks_installation(self):
        failure = subprocess.CompletedProcess([], 10, b'', b'')
        with patch.object(lab, 'validate_lab'), patch.object(lab.subprocess, 'run', return_value=failure), \
                patch.object(lab, 'checked_artifact') as build, self.assertRaisesRegex(RuntimeError, 'inspection failed'):
            lab.apply()
        build.assert_not_called()

    def create_release(self, version):
        artifact = self.root / 'build/libs' / ('hexxit-boss-lab-' + version + '.jar')
        artifact.parent.mkdir(parents=True, exist_ok=True)
        identity = {'version': version, 'revision': 'abc123', 'dirty': 'false'}
        with zipfile.ZipFile(artifact, 'w') as jar:
            jar.writestr('dev/hexxitlab/BossLab.class', b'entry-point')
            jar.writestr('hexxitlab-build.properties', ''.join(key + '=' + value + '\n' for key, value in identity.items()))
        manifest = dict(identity, path=artifact.relative_to(self.root).as_posix(), sha256=lab.digest(artifact))
        return artifact, manifest

    def test_current_gradle_output_is_selected_even_when_old_version_remains(self):
        self.create_release('0.1.0')
        current, manifest = self.create_release('0.2.0')
        lab.write_json(self.root / 'build/artifacts/release.json', manifest)
        with patch.object(lab, 'ROOT', self.root), patch.object(lab, 'gradle'):
            self.assertEqual(current, lab.checked_artifact())

    def test_artifact_with_wrong_checksum_or_identity_is_rejected(self):
        _, manifest = self.create_release('0.2.0')
        for field, incorrect in (('sha256', '0' * 64), ('version', '0.1.0'), ('revision', 'old-revision')):
            with self.subTest(field=field):
                lab.write_json(self.root / 'build/artifacts/release.json', dict(manifest, **{field: incorrect}))
                with patch.object(lab, 'ROOT', self.root), self.assertRaises(RuntimeError):
                    lab.artifact_from_manifest('release')

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

    def test_failed_metadata_write_restores_previous_deployment(self):
        for existing in (False, True):
            with self.subTest(existing=existing), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                artifact, mods, history = root / 'build.jar', root / 'mods', root / 'history'
                if existing:
                    artifact.write_bytes(b'old')
                    lab.install_artifact(artifact, mods, history)
                previous_record = lab.read_deployment(history / 'deployment.json')
                artifact.write_bytes(b'new')
                real_write = lab.write_json

                def fail_metadata(path, value):
                    if path.name == 'deployment.json':
                        raise OSError('Metadata disk full')
                    real_write(path, value)

                with patch.object(lab, 'write_json', side_effect=fail_metadata), self.assertRaises(OSError):
                    lab.install_artifact(artifact, mods, history)
                self.assertEqual(previous_record, lab.verified_deployment(mods, history))
                self.assertFalse((history / 'deployment-pending.json').exists())
                if existing:
                    self.assertEqual(b'old', (mods / lab.MOD_FILE).read_bytes())
                else:
                    self.assertFalse((mods / lab.MOD_FILE).exists())

    def test_interrupted_deployment_recovers_before_retry_or_rollback(self):
        for replaced, committed in ((False, False), (True, False), (True, True)):
            with self.subTest(replaced=replaced, committed=committed), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                artifact, mods, history = root / 'build.jar', root / 'mods', root / 'history'
                artifact.write_bytes(b'old')
                old = lab.install_artifact(artifact, mods, history)
                artifact.write_bytes(b'new')
                new = lab.digest(artifact)
                (history / (new + '.jar')).write_bytes(b'new')
                pending = {'current': new, 'previous': old}
                lab.write_json(history / 'deployment-pending.json', pending)
                if replaced:
                    (mods / lab.MOD_FILE).write_bytes(b'new')
                if committed:
                    lab.write_json(history / 'deployment.json', pending)
                record = lab.verified_deployment(mods, history)
                self.assertEqual(b'new' if committed else b'old', (mods / lab.MOD_FILE).read_bytes())
                self.assertEqual(new if committed else old, record['current'])
                self.assertFalse((history / 'deployment-pending.json').exists())

    def test_incomplete_recovery_can_be_retried_after_storage_failure(self):
        artifact, mods, history = self.root / 'build.jar', self.root / 'mods', self.root / 'history'
        artifact.write_bytes(b'old')
        old = lab.install_artifact(artifact, mods, history)
        artifact.write_bytes(b'new')
        real_write, real_copy = lab.write_json, lab.shutil.copy2

        def fail_metadata(path, value):
            if path.name == 'deployment.json':
                raise OSError('Metadata disk full')
            real_write(path, value)

        def fail_recovery(source, destination):
            if source == history / (old + '.jar'):
                raise OSError('Recovery disk full')
            return real_copy(source, destination)

        with patch.object(lab, 'write_json', side_effect=fail_metadata), \
                patch.object(lab.shutil, 'copy2', side_effect=fail_recovery), \
                self.assertRaisesRegex(RuntimeError, 'recovery incomplete'):
            lab.install_artifact(artifact, mods, history)
        self.assertTrue((history / 'deployment-pending.json').exists())
        self.assertEqual(old, lab.verified_deployment(mods, history)['current'])
        self.assertEqual(b'old', (mods / lab.MOD_FILE).read_bytes())
        self.assertFalse((history / 'deployment-pending.json').exists())

    def test_rollback_refuses_an_unexpected_installed_jar(self):
        artifact, target, state = self.root / 'build.jar', self.root / 'target', self.root / 'state'
        mods, history = target / 'minecraft/mods', state / 'artifacts'
        artifact.write_bytes(b'old')
        lab.install_artifact(artifact, mods, history)
        artifact.write_bytes(b'new')
        lab.install_artifact(artifact, mods, history)
        (mods / lab.MOD_FILE).write_bytes(b'unexpected')
        before = lab.read_json(history / 'deployment.json')
        with patch.object(lab, 'TARGET', target), patch.object(lab, 'STATE', state), \
                patch.object(lab, 'validate_lab'), patch.object(lab, 'ensure_stopped'), \
                self.assertRaisesRegex(RuntimeError, 'differs from deployment metadata'):
            lab.rollback()
        self.assertEqual(b'unexpected', (mods / lab.MOD_FILE).read_bytes())
        self.assertEqual(before, lab.read_json(history / 'deployment.json'))

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
