# Minecraft Five resource pack

The optional pack uses `drunkshyt:five` for counters, menus, and plugin messages.
Usernames keep Minecraft's default font. The pack does not replace any
`assets/minecraft` files, and unsupported characters fall back to the default
font.

The letters come from Minecraft Five Regular, rendered on its native five-pixel
grid. The atlas uses opaque pixels, whole-pixel spacing, and a baseline aligned
with vanilla usernames. Pack 1.1 supports resource-pack formats 84–88
(Java 26.1–26.2).

## Build

Python 3.10 or newer is enough to package the included atlas:

```sh
python3 scripts/build_resource_pack.py
```

This creates the ZIP, a self-contained HTML preview, checksums, and a
`server.properties` snippet in `dist/`. Open `dist/MinecraftFive-preview.html`
to try the counter at different scales. The HTML is only a preview; Minecraft
loads the ZIP.

To regenerate the atlas from the included font:

```sh
python3 -m venv build/font-tools
build/font-tools/bin/python -m pip install -r scripts/font-requirements.txt
build/font-tools/bin/python scripts/build_font_atlas.py
python3 scripts/build_resource_pack.py
```

On Windows, use `py -3` to create the environment and
`build/font-tools/Scripts/python.exe` for the remaining Python commands.

## Install

1. Host `dist/DrunkShyt-MinecraftFive-1.1.zip` at a direct HTTPS download URL.
2. Stop the Minecraft server before editing configuration files.
3. Set `font: drunkshyt:five` in `plugins/DrunkShyt/config.yml`.
4. Set `resource-pack` to your ZIP URL and `resource-pack-sha1` to the SHA-1
   from `dist/checksums.json` in `server.properties`.
5. Set `require-resource-pack=true` if players must accept the pack.
6. Start the server, reconnect, and check `/shots` and the Tab list.

Keep the ZIP and its checksum together. If you rebuild or combine packs,
calculate a new checksum. When merging with another pack, copy the
`assets/drunkshyt` folder and retain the font license notices.

## Testing and attribution

The optional [Minecraft client test](docs/TESTING.md) checks bitmap glyphs,
Unicode fallback, vanilla font isolation, and mixed username/counter lines at
GUI scales 1, 2, and 3. A browser preview does not replace that check.

Minecraft Five Regular comes from Mojang's
[web-theme-bootstrap repository](https://github.com/Mojang/web-theme-bootstrap/tree/92d9913110cf79db5813e6335f97c6dc689854ee/assets/fonts).
The original font is retained without changes. Its source, copyright, and
upstream license notices are included in `resource-pack/` and in the ZIP.
See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
