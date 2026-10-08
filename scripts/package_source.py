#!/usr/bin/env python3
"""Package public source files without local server data or build output."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIRECTORIES = [
    'src', 'resource-pack', 'preview', 'docs', 'licenses', '.github', 'gradle',
    'client-verification/src', 'client-verification/gradle',
]
FILES = [
    '.gitignore', '.gitattributes', 'README.md', 'RESOURCE-PACK.md', 'LICENSE',
    'CONTRIBUTING.md', 'THIRD-PARTY-NOTICES.md',
    'build.gradle.kts', 'settings.gradle.kts', 'gradlew', 'gradlew.bat',
    'scripts/build_font_atlas.py', 'scripts/build_resource_pack.py',
    'scripts/package_source.py', 'scripts/font-requirements.txt',
    'client-verification/build.gradle', 'client-verification/settings.gradle',
    'client-verification/gradlew', 'client-verification/gradlew.bat',
]


def public_files():
    paths = {ROOT / name for name in FILES}
    for name in DIRECTORIES:
        paths.update(path for path in (ROOT / name).rglob('*') if path.is_file())
    for path in sorted(paths):
        relative = path.relative_to(ROOT)
        if any(part in {'__pycache__', '.DS_Store'} or part.startswith('._') for part in relative.parts):
            continue
        if path.is_symlink() or not path.is_file():
            raise ValueError(f'Expected a regular source file: {relative}')
        yield relative


def build():
    version = re.search(r'^version = "([^"]+)"', (ROOT / 'build.gradle.kts').read_text(), re.MULTILINE).group(1)
    destination = ROOT / 'dist' / f'DrunkShyt-{version}-source.zip'
    destination.parent.mkdir(exist_ok=True)
    files = list(public_files())
    with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for relative in files:
            entry = zipfile.ZipInfo('DrunkShyt/' + relative.as_posix(), (2026, 10, 8, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = (0o100755 if relative.name == 'gradlew' else 0o100644) << 16
            archive.writestr(entry, (ROOT / relative).read_bytes())
    checksum = hashlib.sha256(destination.read_bytes()).hexdigest()
    destination.with_suffix('.zip.sha256').write_text(f'{checksum}  {destination.name}\n')
    print(json.dumps({'file': destination.name, 'files': len(files), 'sha256': checksum}))


if __name__ == '__main__':
    build()
