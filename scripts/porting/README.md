# Python tools of the resource and oracle pipeline

`tt_assets.py` (converts the 1.7.10 textures and language files of Thaumic Tinkerer into the 1.21.1 resource tree and writes
`tt_assets_map.json` and `docs/lang-key-families.md`) and `tt_oracle.py` (extracts the research tree and the recipes of the 1.7.10
source into `common/src/gameTest/resources/tt_oracle`), with their unit tests `test_tt_assets.py` and `test_tt_oracle.py`. The documents
in `docs/` name them (`docs/PARITY_REPORT.md`, `docs/DEVIATIONS.md`, `NOTICE.md`).

**Licence: MIT** (`LICENSE-MIT` in the root of this repository). They contain no Thaumic Tinkerer text; the data they read is the 1.7.10
source (CC BY-NC-SA 3.0), the data they write is part of the add-on and under its licence.

They read two checkouts next to this repository and write into it:

- the original 1.7.10 mod, <https://github.com/GTNewHorizons/ThaumicTinkerer> at the pinned commit (`SOURCE_COMMIT` in both tools,
  `NOTICE.md`): `--source`, else the environment variable `TT_1710_SOURCE`, else `../ThaumicTinkerer-1.7.10`;
- Thaumcraft 4 Reload (only `tt_oracle.py`: the aspect constants of `thaumcraft.api` and the legacy id table): `--thaumcraft`, else
  `THAUMCRAFT_DIR`, else `../Thaumcraft-1.21.1`, where `tools/publish-thaumcraft.sh` clones it.

```bash
tools/porting.sh test                      # unit tests (no network); the tests against the real checkouts are skipped when one is missing
tools/porting.sh tt_assets.py --check      # the checked-in resources, tt_assets_map.json and docs/lang-key-families.md are current
tools/porting.sh tt_oracle.py --check      # the checked-in oracle is current
TT_1710_SOURCE=PATH tools/porting.sh tt_oracle.py   # regenerate
```
