# Third-party notices

## Minecraft Five Regular

The unmodified font in `resource-pack/assets/drunkshyt/font/` comes from
[Mojang/web-theme-bootstrap](https://github.com/Mojang/web-theme-bootstrap)
at commit `92d9913110cf79db5813e6335f97c6dc689854ee`.
Its embedded copyright notice is `(c) 2015-2017 Mojang AB. All rights reserved.`
Its name table credits Patrick Griffin and P22 / Canada Type.

The upstream font directory includes
[LICENSE_OFL.txt](https://github.com/Mojang/web-theme-bootstrap/blob/92d9913110cf79db5813e6335f97c6dc689854ee/assets/fonts/LICENSE_OFL.txt),
copied unchanged to `resource-pack/UPSTREAM-FONT-LICENSE.txt`.
The generated bitmap atlas is derived from that font and distributed with the
same font license. The project's main code license does not replace it.
The upstream repository license is retained in `resource-pack/MOJANG-LICENSE.txt`.
No image assets from the upstream repository are included.

## Gradle wrapper

The wrapper scripts and JARs are distributed under Apache License 2.0.
Original notices in the scripts are retained; a copy of the license is in
`licenses/Apache-2.0.txt`.

## Runtime and test dependencies

Paper is provided by the server. Adventure is provided by Paper. JUnit and the
optional Fabric test tooling are development dependencies. The plugin JAR does
not bundle their code. Python font-build dependencies are installed separately.
