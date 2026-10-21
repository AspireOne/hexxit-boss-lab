"""Local, fail-closed Prism workflow. Game files never belong in Git."""

import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import uuid
import zipfile

from world_name import rename_world

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / '.lab'
PRISM = Path(os.environ.get('APPDATA', Path.home())) / 'PrismLauncher'
SOURCE = PRISM / 'instances' / 'Hexxit 1.0.11 OptiFine'
TARGET = PRISM / 'instances' / 'Hexxit Boss Lab'
SOURCE_WORLD = 'Kratom - Inventory restored 2026-10-15'
WORLD = 'Kratom - Boss Lab'
MARKER = '.hexxit-boss-lab.json'
MOD_FILE = 'hexxit-boss-lab.jar'
HARNESS_FILE = 'hexxit-boss-lab-harness.jar'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def require_child(path, parent):
    if not path.resolve().is_relative_to(parent.resolve()) or path.resolve() == parent.resolve():
        raise ValueError('Path escapes the managed directory: ' + str(path))
    # Reject reparse points, including Windows junctions, along the operation path.
    for part in [path, *path.parents]:
        if part.is_symlink() or part.is_junction():
            raise ValueError('Linked paths are not managed: ' + str(part))
        if part == parent:
            break


def validate_lab(target=TARGET, state=STATE):
    require_child(target, PRISM / 'instances')
    expected = read_json(state / 'instance.json')
    actual = read_json(target / MARKER)
    if actual != expected or actual['target'] != str(target.resolve()):
        raise ValueError('Unrecognized lab destination; run setup or inspect .lab/instance.json')
    for path in (target / 'minecraft', target / 'minecraft/mods', target / 'minecraft/saves'):
        require_child(path, target)
    return actual


def ensure_stopped():
    # Do not print command lines: launch arguments may contain account credentials.
    script = "$p = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -match 'org.prismlauncher|org.multimc|net.minecraft|net.fabricmc|launchwrapper' }); if ($p.Count -gt 0) { exit 9 }"
    result = subprocess.run(['powershell', '-NoProfile', '-Command', script], capture_output=True)
    if result.returncode != 0:
        raise RuntimeError('Close Minecraft before changing lab files (or process inspection failed).')


def gradle(*tasks):
    subprocess.run([str(ROOT / 'gradlew.bat'), *tasks, '--console=plain'], cwd=ROOT, check=True)


def tree_hashes(directory):
    result = {}
    for path in sorted(directory.rglob('*')):
        require_child(path, directory)
        if path.is_file():
            result[path.relative_to(directory).as_posix()] = digest(path)
    return result


def set_ini(path, values):
    lines = path.read_text(encoding='utf-8').splitlines()
    start = lines.index('[General]') + 1
    end = next((i for i in range(start, len(lines)) if lines[i].startswith('[')), len(lines))
    section = [line for line in lines[start:end] if line.partition('=')[0] not in values]
    lines[start:end] = section + [key + '=' + str(value) for key, value in values.items()]
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def setup():
    subprocess.run(['git', 'config', 'core.hooksPath', '.githooks'], cwd=ROOT, check=True)
    if TARGET.exists():
        validate_lab()
        print('Lab already configured; world and deployed artifacts preserved.')
        return
    ensure_stopped()
    staging = TARGET.with_name(TARGET.name + '.setup')
    if staging.exists():
        raise RuntimeError('Previous setup staging exists; inspect before retrying: ' + str(staging))
    pack = read_json(SOURCE / 'mmc-pack.json')
    versions = {item['uid']: item.get('version') for item in pack['components']}
    if versions.get('net.minecraft') != '1.5.2' or versions.get('net.minecraftforge') != '7.8.1.737':
        raise ValueError('Source must use Minecraft 1.5.2 / Forge 7.8.1.737')
    print('Copying the isolated instance and selected save...', flush=True)
    staging.mkdir()
    for name in ('instance.cfg', 'mmc-pack.json'):
        shutil.copy2(SOURCE / name, staging / name)
    for name in ('patches', 'jarmods'):
        shutil.copytree(SOURCE / name, staging / name)
    game = staging / 'minecraft'
    game.mkdir()
    for path in (SOURCE / 'minecraft').iterdir():
        if path.name in ('saves', 'screenshots', 'crash-reports', 'stats') or path.is_symlink() or path.is_junction():
            continue
        if path.is_dir():
            shutil.copytree(path, game / path.name)
        elif path.suffix in ('.txt', '.png') and not path.name.startswith('debug.'):
            shutil.copy2(path, game / path.name)
    shutil.copytree(SOURCE / 'minecraft/saves' / SOURCE_WORLD, game / 'saves' / WORLD)
    rename_world(game / 'saves' / WORLD / 'level.dat', WORLD)
    previous_level = game / 'saves' / WORLD / 'level.dat_old'
    if previous_level.exists():
        rename_world(previous_level, WORLD)
    set_ini(staging / 'instance.cfg', {
        'name': 'Hexxit Boss Lab', 'uuid': uuid.uuid4().hex,
        'lastLaunchTime': 0, 'lastTimePlayed': 0, 'totalTimePlayed': 0,
        'OverrideJavaArgs': 'true', 'JvmArgs': '-Dhexxitlab.enabled=true',
        'OverrideCommands': 'true', 'PreLaunchCommand': '', 'PostExitCommand': '', 'WrapperCommand': ''
    })
    identity = {'id': str(uuid.uuid4()), 'target': str(TARGET.resolve()), 'source': str(SOURCE.resolve()), 'world': WORLD}
    write_json(staging / MARKER, identity)
    baseline = STATE / 'baseline'
    if baseline.exists():
        raise RuntimeError('Baseline already exists; refusing to replace it.')
    shutil.copytree(game / 'saves' / WORLD, baseline)
    write_json(STATE / 'baseline-hashes.json', tree_hashes(baseline))
    write_json(STATE / 'instance.json', identity)
    staging.rename(TARGET)
    write_json(STATE / 'environment.json', fingerprint())
    print('Created Hexxit Boss Lab and Kratom - Boss Lab.')


def fingerprint():
    return {name: tree_hashes(TARGET / 'minecraft' / name)
            for name in ('mods', 'coremods', 'config')}


def checked_artifact():
    gradle('build')
    artifact = ROOT / 'build/libs/hexxit-boss-lab-0.1.0.jar'
    with zipfile.ZipFile(artifact) as jar:
        if any(name.startswith('dev/hexxitlab/harness/') for name in jar.namelist()):
            raise RuntimeError('Development harness leaked into the release JAR')
        if 'dev/hexxitlab/BossLab.class' not in jar.namelist():
            raise RuntimeError('Release artifact lacks the mod entry point')
    return artifact


def install_artifact(artifact, mods, history):
    """Replace our single owned filename, retaining a recoverable previous artifact."""
    mods.mkdir(parents=True, exist_ok=True)
    history.mkdir(parents=True, exist_ok=True)
    destination = mods / MOD_FILE
    require_child(destination, mods)
    incoming_hash = digest(artifact)
    retained = history / (incoming_hash + '.jar')
    if not retained.exists():
        shutil.copy2(artifact, retained)
    if digest(retained) != incoming_hash:
        raise RuntimeError('Retained artifact checksum mismatch')
    previous = digest(destination) if destination.exists() else None
    if previous == incoming_hash:
        return incoming_hash
    if previous:
        shutil.copy2(destination, history / (previous + '.jar'))
    temporary = mods / (MOD_FILE + '.incoming')
    shutil.copy2(artifact, temporary)
    if digest(temporary) != incoming_hash:
        raise RuntimeError('Copy verification failed; installed artifact preserved')
    temporary.replace(destination)
    write_json(history / 'deployment.json', {'current': incoming_hash, 'previous': previous})
    return incoming_hash


def apply():
    validate_lab()
    ensure_stopped()
    artifact = checked_artifact()
    ensure_stopped()
    result = install_artifact(artifact, TARGET / 'minecraft/mods', STATE / 'artifacts')
    harness = TARGET / 'minecraft/mods' / HARNESS_FILE
    require_child(harness, TARGET)
    harness.unlink(missing_ok=True)
    print('Installed verified build: ' + result)


def rollback():
    validate_lab()
    ensure_stopped()
    history = STATE / 'artifacts'
    previous = read_json(history / 'deployment.json')['previous']
    if not previous or len(previous) != 64 or any(c not in '0123456789abcdef' for c in previous):
        raise RuntimeError('No valid previous deployment')
    artifact = history / (previous + '.jar')
    if digest(artifact) != previous:
        raise RuntimeError('Previous artifact checksum mismatch')
    install_artifact(artifact, TARGET / 'minecraft/mods', history)
    print('Restored previous build: ' + previous)


def reset_world():
    validate_lab()
    ensure_stopped()
    baseline = STATE / 'baseline'
    if tree_hashes(baseline) != read_json(STATE / 'baseline-hashes.json'):
        raise RuntimeError('Baseline changed; refusing restoration')
    world = TARGET / 'minecraft/saves' / WORLD
    require_child(world, TARGET)
    staged = world.with_name(WORLD + '.restore')
    if staged.exists():
        raise RuntimeError('Interrupted restore exists: ' + str(staged))
    shutil.copytree(baseline, staged)
    snapshot = STATE / 'snapshots' / (time.strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8])
    snapshot.parent.mkdir(parents=True, exist_ok=True)
    if world.exists():
        shutil.move(str(world), snapshot)
    staged.rename(world)
    print('World restored; previous state retained at ' + str(snapshot))


def launch():
    validate_lab()
    executable = Path(os.environ['LOCALAPPDATA']) / 'Programs/PrismLauncher/prismlauncher.exe'
    subprocess.Popen([str(executable), '--launch', TARGET.name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def smoke():
    apply()
    gradle('harnessJar')
    harness = ROOT / 'build/libs/hexxit-boss-lab-0.1.0-harness.jar'
    shutil.copy2(harness, TARGET / 'minecraft/mods' / HARNESS_FILE)
    run = STATE / 'runs' / (time.strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8])
    run.mkdir(parents=True)
    write_json(run / 'environment.json', fingerprint())
    # Two real game launches exercise the world persistence boundary.
    for phase in ('create', 'reload'):
        ensure_stopped()
        request = TARGET / 'minecraft/bosslab-smoke.properties'
        request.write_text('phase=' + phase + '\nworld=' + WORLD + '\nrun=' + run.name + '\n', encoding='ascii')
        result = TARGET / 'minecraft' / ('bosslab-' + phase + '-result.properties')
        result.unlink(missing_ok=True)
        launch()
        deadline = time.monotonic() + 240
        while not result.exists() and time.monotonic() < deadline:
            time.sleep(1)
        if not result.exists():
            raise RuntimeError('Smoke timed out; inspect Minecraft and logs. No success result was produced.')
        shutil.copy2(result, run / result.name)
        text = result.read_text(encoding='ascii')
        if 'status=PASS' not in text or ('run=' + run.name) not in text:
            raise RuntimeError('Runtime smoke failed: ' + text)
        # The harness saves and exits; allow that process to finish before the next launch.
        while True:
            try:
                ensure_stopped()
                break
            except RuntimeError:
                if time.monotonic() > deadline:
                    raise
                time.sleep(1)
        for log in (TARGET / 'minecraft').glob('ForgeModLoader-client-0.log'):
            shutil.copy2(log, run / (phase + '-' + log.name))
    (TARGET / 'minecraft/bosslab-smoke.properties').unlink(missing_ok=True)
    (TARGET / 'minecraft/mods' / HARNESS_FILE).unlink()
    print('Runtime smoke passed. Evidence: ' + str(run))


@contextlib.contextmanager
def operation_lock():
    STATE.mkdir(exist_ok=True)
    path = STATE / 'operation.lock'
    try:
        with path.open('x') as stream:
            stream.write(str(os.getpid()))
    except FileExistsError as error:
        raise RuntimeError('Another lab command is active; inspect .lab/operation.lock') from error
    try:
        yield
    finally:
        path.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['setup', 'check', 'build', 'apply', 'play', 'smoke', 'reset-world', 'rollback'])
    command = parser.parse_args().command
    with operation_lock():
        if command == 'setup':
            setup()
        elif command == 'check':
            gradle('check')
        elif command == 'build':
            print(checked_artifact())
        elif command == 'apply':
            apply()
        elif command == 'play':
            apply()
            launch()
        elif command == 'smoke':
            smoke()
        elif command == 'reset-world':
            reset_world()
        elif command == 'rollback':
            rollback()


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print('ERROR: ' + str(error), file=sys.stderr)
        sys.exit(1)
