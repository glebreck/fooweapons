# fooWeapons — Progress

## Convention
- `[ ]` = Not started
- `[-]` = In progress
- `[x]` = Completed

---

## v0.1.0 — MVP (shipped 2026-05-15)

Smoke-test verified on Greg's GPortal Paper 1.21.11 server with a vanilla 1.21.11 Java client.

### Plugin
- [x] Gradle multi-project scaffold (Paper API, JUnit, MockBukkit)
- [x] `plugin.yml` descriptor (single-descriptor; no paper-plugin.yml — they conflict)
- [x] Weapon definition format (YAML → `Weapon` record via `WeaponLoader`)
- [x] `WeaponRegistry` (atomic load on `onEnable`, `ids()` for tab completion)
- [x] `PdcKeys` + `WeaponItemFactory` (crossbow + CMD + PDC tags)
- [x] `ItemState` (per-item ammo and last-fired timestamp via PDC)
- [x] Pure-logic fire components, TDD: `FireRateCooldown`, `DamageCalculator`, `SpreadCalculator`
- [x] `Hitscan` raycast wrapper with headshot detection
- [x] `FireService` orchestration (cooldown → ammo → spread → raycast → damage)
- [x] `FireListener` left-click handler
- [x] `ReloadListener` swap-hands-key (`F`) handler with reload-time lock
- [x] `HudService` action-bar (10-segment Unicode ammo bar, fire-mode label)
- [x] `SoundService` (fire / reload / dry-fire)
- [x] `/fooweapons give <id>` admin command (with tab completion for weapon IDs)

### Resource pack
- [x] `pack.mcmeta` (pack_format 75 for 1.21.11)
- [x] Crossbow item-model override (`minecraft:select` on `custom_model_data`)
- [x] `pistol_01` cuboid model JSON (grip / frame / barrel / trigger_guard)
- [x] `pistol_01` procedural texture (16x16 RGBA via `tools/generate_textures.py`)

### Build + release
- [x] Gradle `dist` task (plugin jar + resource pack zip)
- [x] Jar named `nf_fooweapons-0.1.0.jar`; plugin name `nf_fooWeapons`
- [x] GitHub repo `glebreck/fooweapons` (public)
- [x] v0.1.0 release with jar + resource pack zip attached
- [x] Three-branch model: `dev` / `beta` / `release` (release is GitHub default)

---

## Plan 2 — In Progress

### Plan 2 Slice A — Auto-fire + fire-mode toggle + rifle_01 (shipped to release as v0.2.0, 2026-05-18)

Smoke-test verified on Greg's GPortal Paper 1.21.11 server. Fire-mode toggle key was iterated from Sneak+Q (spec) to single-key Q (released) during beta testing.

### Plan 2 Slice B — SMG + shotgun + muzzle flash (in `dev`, pending smoke test)

Adds `smg_01`, `shotgun_01` (with per-pellet hitscan and per-target damage aggregation), and server-side muzzle-flash particles on every weapon. Introduces `FireMode.PUMP`.

### New weapons
- [x] SMG (`smg_01`) — full-auto, 30 rounds
- [x] Assault rifle (`rifle_01`) — selectable semi/auto, 30 rounds
- [ ] Battle rifle (`battle_rifle_01`) — semi-auto, 20 rounds, higher damage
- [ ] Bolt-action sniper (`sniper_01`) — bolt, 5 rounds, high damage
- [x] Pump shotgun (`shotgun_01`) — pump, 6 shells, pellet spread

### Combat mechanics
- [x] Automatic fire (continuous-fire polling while LMB held)
- [x] Fire mode toggle on rifles (Sneak+Q) — replaces hardcoded `"SEMI"` label in `HudService`
- [ ] Aim down sights (ADS) — hold right-click, movement-speed modifier, tighter spread
- [ ] Recoil — view kick on fire, recover over a few ticks
- [ ] Consumable magazines — reload pulls a magazine item from inventory (currently free)
- [x] Muzzle-flash particles

### Configuration
- [ ] Global `config.yml` — friendly fire toggle, world allowlist, damage multipliers
- [ ] Custom sound files (currently using vanilla sound event IDs)

---

## Optional polish (no plan)

- [ ] `README.md` for the GitHub repo
- [ ] GitHub Actions CI (auto-build on push, attach artifacts to runs)
- [ ] Bottom-right FPS-style HUD with custom graphics (requires companion client mod — out of scope for vanilla-client target)
