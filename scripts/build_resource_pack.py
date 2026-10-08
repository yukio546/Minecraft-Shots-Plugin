#!/usr/bin/env python3
"""Build the isolated font ZIP and its self-contained HTML preview."""
from pathlib import Path
import argparse
import base64
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'resource-pack'
DIST = ROOT / 'dist'
NAME = 'DrunkShyt-MinecraftFive-1.1.zip'
FONT = SOURCE / 'assets/drunkshyt/font/minecraft-five-regular.ttf'
EXPECTED_FONT_SHA256 = 'c57aad97df42971ebd5f9d0eedc571c9f169b077c5ead0ffa0059a89384b326a'

def build(proof=None):
    assert hashlib.sha256(FONT.read_bytes()).hexdigest() == EXPECTED_FONT_SHA256, 'Unexpected font bytes'
    assert FONT.read_bytes()[:4] == b'\x00\x01\x00\x00', 'Expected TrueType outlines'
    metadata = json.loads((SOURCE / 'pack.mcmeta').read_text())
    assert metadata['pack']['min_format'] == 84 and metadata['pack']['max_format'] == 88
    definition = json.loads((SOURCE / 'assets/drunkshyt/font/five.json').read_text())
    assert definition['providers'][0] == {'type': 'space', 'advances': {' ': 3}}
    bitmap = definition['providers'][1]
    assert bitmap['type'] == 'bitmap' and bitmap['file'] == 'drunkshyt:font/five.png'
    assert bitmap['height'] == 8 and bitmap['ascent'] == 7
    assert {c for row in bitmap['chars'] for c in row if c != '\0'} == {chr(c) for c in range(33, 127)}
    assert definition['providers'][-1] == {'type': 'reference', 'id': 'minecraft:default'}
    assets = list((SOURCE / 'assets').rglob('*'))
    assert all(p.relative_to(SOURCE).as_posix().startswith('assets/drunkshyt/') for p in assets if p.is_file())
    assert all(p.name == p.name.lower() for p in assets), 'Minecraft resource paths must be lowercase'
    DIST.mkdir(exist_ok=True)
    destination = DIST / NAME
    with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(SOURCE.rglob('*')):
            if path.is_file():
                entry = zipfile.ZipInfo(path.relative_to(SOURCE).as_posix(), (2026, 10, 6, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, path.read_bytes())
    with zipfile.ZipFile(destination) as archive:
        assert archive.testzip() is None
        assert 'pack.mcmeta' in archive.namelist()
        assert not any(name.startswith('assets/minecraft/') for name in archive.namelist())
        assert archive.read('assets/drunkshyt/font/minecraft-five-regular.ttf') == FONT.read_bytes()
    payload = destination.read_bytes()
    hashes = {'file': NAME, 'bytes': len(payload), 'sha1': hashlib.sha1(payload).hexdigest(),
              'sha256': hashlib.sha256(payload).hexdigest(), 'font_sha256': EXPECTED_FONT_SHA256,
              'font_id': 'drunkshyt:five', 'provider': 'bitmap', 'resource_pack_formats': [84, 88],
              'atlas_sha256': hashlib.sha256((SOURCE / 'assets/drunkshyt/textures/font/five.png').read_bytes()).hexdigest()}
    version = re.search(r'^version = "([^"]+)"', (ROOT / 'build.gradle.kts').read_text(), re.MULTILINE).group(1)
    plugin = ROOT / f'build/libs/DrunkShyt-{version}.jar'
    if plugin.exists():
        hashes.update(plugin_file=plugin.name, plugin_sha256=hashlib.sha256(plugin.read_bytes()).hexdigest())
    (DIST / 'checksums.json').write_text(json.dumps(hashes, indent=2) + '\n')
    template = (ROOT / 'preview/template.html').read_text()
    preview = template.replace('__ATLAS_DATA__', base64.b64encode((SOURCE / 'assets/drunkshyt/textures/font/five.png').read_bytes()).decode())
    preview = preview.replace('__ATLAS_META__', (ROOT / 'preview/five-atlas.json').read_text())
    proof_markup = '<p>No game capture included. Run the optional Minecraft client test to generate one.</p>'
    if proof is not None:
        proof = Path(proof)
        if not proof.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
            raise ValueError('The preview capture must be a PNG file.')
        proof_markup = '<img class="proof" alt="Actual Minecraft: vanilla usernames with Minecraft Five counters" src="data:image/png;base64,' + base64.b64encode(proof.read_bytes()).decode() + '">'
    preview = preview.replace('__MINECRAFT_PROOF__', proof_markup)
    preview = preview.replace('__PACK_SHA1__', hashes['sha1']).replace('__PACK_FILE__', NAME)
    (DIST / 'MinecraftFive-preview.html').write_text(preview)
    (DIST / 'server.properties-snippet.txt').write_text(
        '# Replace YOUR-HOST with the direct HTTPS download URL for the ZIP.\n'
        'resource-pack=https://YOUR-HOST/' + NAME + '\n'
        'resource-pack-sha1=' + hashes['sha1'] + '\n'
        'resource-pack-prompt={"text":"Minecraft Five for the Shots controls"}\n'
        '# Optional: require acceptance so every connected player has this font.\n'
        'require-resource-pack=true\n')
    print(json.dumps(hashes, indent=2))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--proof', type=Path, help='Optional Minecraft screenshot to include in the preview')
    build(parser.parse_args().proof)
