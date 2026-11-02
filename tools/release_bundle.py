# SPDX-License-Identifier: MIT
"""Collect production jars only after checking real test reports and dependency provenance."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile


def properties(path):
    return dict(line.split('=', 1) for raw in path.read_text(encoding='utf-8-sig').splitlines()
                if (line := raw.strip()) and not line.startswith('#') and '=' in line)


def commit(path):
    return subprocess.check_output(['git', '-C', str(path), 'rev-parse', 'HEAD'], text=True).strip()


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def test_summary(paths):
    if not paths:
        raise ValueError('No test reports were produced')
    result = dict(passed=0, failed=0, errors=0, skipped=0)
    for path in paths:
        cases = list(ET.parse(path).getroot().iter('testcase'))
        if not cases:
            raise ValueError(f'Empty test report: {path}')
        for case in cases:
            problem = next((key for key, element in [('failed', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]
                            if case.find(element) is not None), None)
            result[problem or 'passed'] += 1
    if any(result[key] for key in ('failed', 'errors', 'skipped')):
        raise ValueError(f'Tests did not all pass: {result}')
    return result


def validate_jar(path, loader, version):
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        bad = [name for name in names if name.startswith(('thaumcraft/', 'net/minecraft/'))
               or any(part in name for part in ('/testing/', '/clientsmoke/', '/mpserver/')) or name.endswith('.jar')]
        if bad or not any(name.endswith('.class') for name in names) or jar.testzip() is not None:
            raise ValueError(f'Invalid production jar: {path.name}; forbidden={bad}')
        if 'META-INF/LICENSE' not in names:
            raise ValueError(f'Missing licence: {path.name}')
        if loader == 'fabric':
            metadata = json.loads(jar.read('fabric.mod.json'))
            mixins = json.loads(jar.read('thaumictinkerer.mixins.json'))
            if metadata['id'] != 'thaumictinkerer' or metadata['version'] != version:
                raise ValueError('Wrong Fabric mod or version')
            if not mixins.get('refmap') or mixins['refmap'] not in names:
                raise ValueError('Missing Fabric production refmap')
        else:
            text = jar.read('META-INF/neoforge.mods.toml').decode()
            if 'modId="thaumictinkerer"' not in text or f'version="{version}"' not in text:
                raise ValueError('Wrong NeoForge mod or version')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--thaumcraft-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    props = properties(root / 'gradle.properties')
    tc = args.thaumcraft_dir.resolve()
    if not re.fullmatch('[0-9a-f]{40}', props['thaumcraft_ref']) or commit(tc) != props['thaumcraft_ref']:
        raise ValueError('Thaumcraft checkout does not match thaumcraft_ref')
    if properties(tc / 'gradle.properties')['mod_version'] != props['thaumcraft_version']:
        raise ValueError('Thaumcraft version and pinned source disagree')
    tests = {
        'junit': test_summary(sorted((root / 'build/test-results/tinkererTest').glob('TEST-*.xml'))),
        'fabric_native': test_summary([root / 'build/tinkerer-gametest-results.xml']),
        'neoforge_native': test_summary([root / 'neoforge/build/tinkerer-gametest-results.xml']),
    }
    output = args.output.resolve()
    if output.exists() and any(output.iterdir()):
        raise ValueError('Release output directory contains stale files')
    output.mkdir(parents=True, exist_ok=True)
    artifacts = []
    for loader, directory in [('fabric', root), ('neoforge', root / 'neoforge')]:
        path = directory / 'build/libs' / f"ThaumicTinkerer-{loader}-{props['mod_version']}.jar"
        validate_jar(path, loader, props['mod_version'])
        shutil.copyfile(path, output / path.name)
        artifacts.append(dict(loader=loader, file=path.name, bytes=path.stat().st_size, sha256=sha256(path)))
    info = dict(version=props['mod_version'], minecraft=props['minecraft_version'], java=21,
                source_commit=commit(root), tested_thaumcraft=dict(version=props['thaumcraft_version'], source_commit=commit(tc)),
                artifacts=artifacts, tests=tests,
                limitations='Client smoke and remote multiplayer are not run by this release workflow.')
    (output / 'BUILD_INFO.json').write_text(json.dumps(info, indent=2) + '\n', encoding='utf-8')
    (output / 'SHA256SUMS.txt').write_text(''.join(f"{a['sha256']}  {a['file']}\n" for a in artifacts), encoding='ascii')
    print(json.dumps(info, indent=2))


if __name__ == '__main__':
    main()
