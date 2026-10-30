# mc-mol-viewer agent notes

## Build and verification

- Java 25 and Gradle 9.8.0.
- Shared code lives in the `common/` submodule (included with `includeBuild("common")`,
  package `dev.taikin.miviewer.common`) and is shaded into this JAR.
- Run `./gradlew test shadowJar` before a release or server deployment.
- After updating the `common` submodule, build and test `../mc-cif-viewer` as well.
- Build artifact: `build/libs/mc-mol-viewer-<version>.jar`.

## Shared wands

Wands are created with `dev.taikin.miviewer.common.WandItems`:

- `Material.STICK` + `miviewer:wand-type` / `miviewer:wand-target` PDC
- item models `miviewer:wand_menu/view/atom/frame`
- target `MOL` for this plugin; a model click switches the shared wand to MOL
- `WandItems.refreshAppearance` migrates legacy `mcmolview:wand-type` wands

See `../AGENTS.md` for the full workspace contract and common-first rules.

## CI and releases

- `.github/workflows/build.yml` builds on push/PR; `release.yml` runs on `v*`
  tags and attaches the shaded JAR to a GitHub Release.
- Both workflows check out the public `common` submodule. The
  `MIVIEWER_COMMON_TOKEN` secret is only required if the common repo becomes
  private (workflows fall back to `github.token`).

## Server testing and deployment

- Backend `mc-backend` on host `mc` (`192.168.100.13`) is the test target.
- Copy the JAR to `/opt/mc/servers/main/plugins/` and reload with RCON:
  `python3 ../mc-cif-viewer/scripts/rcon.py "plugman reload McMolView"`
  over SSH to `mc`.
- Resource pack changes require rebuilding `../resourcepack` and restarting the
  backend to advertise the new SHA-1.
