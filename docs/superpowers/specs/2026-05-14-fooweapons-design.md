# fooWeapons — Design Spec

**Date:** 2026-05-14
**Status:** Draft, pending user review
**Target environment:** Paper 1.21.11 Java Edition, hosted on GPortal, vanilla Java clients

---

## 1. Overview

fooWeapons is an original Paper plugin in the modern-firearms genre, paired with a server-pushed resource pack so that vanilla Java clients see custom gun visuals without installing any mod. The plugin handles all weapon behavior (firing, reloading, ammo, recoil, hit detection). The resource pack handles all weapon appearance (3D item models and textures applied to vanilla item bases via `CustomModelData`).

This is an original work inspired by the genre. It does not reuse code, assets, or designs from Vic's Modern Warfare or any other existing mod.

## 2. Goals

- A working modern-warfare-style weapons experience on a Paper 1.21.11 server.
- No client mod required. Players play in vanilla Java Edition and accept the server resource pack.
- Designed to run on GPortal's Paper hosting without special infrastructure.
- A pluggable model layer: hand-written blocky models in v1 can be replaced with commissioned high-quality Blockbench models later without touching plugin code.
- Server-authoritative gameplay (clients cannot fake shots, ammo, or hit registration).

## 3. Non-Goals (Out of Scope for v1)

- Real custom keybinds (R to reload, etc.) — impossible without a client mod.
- Smooth ADS animations and FOV zoom beyond what vanilla item-model transforms allow.
- Vehicles, killstreaks, custom HUDs beyond the action bar.
- Attachments / weapon modification system.
- Inventory management beyond vanilla (no separate gun-slot inventory).
- Cross-version support. This targets 1.21.11 specifically.

## 4. Target Environment Details

- **Server:** Paper 1.21.11 Java, running on GPortal.
- **Build target:** Java 21 (required by Paper 1.21.x).
- **Plugin API:** Paper API (`io.papermc.paper:paper-api`).
- **Build system:** Gradle (Kotlin DSL).
- **Clients:** Vanilla Minecraft Java Edition 1.21.11. No mod loader. Players must accept the server resource pack on join (server-side prompt is automatic if the pack URL is configured).
- **Resource pack delivery:** Pack hosted at a public URL (initially generated alongside the plugin build; user provides hosting URL, or we use a free static host). Configured in `server.properties` via `resource-pack=` and `resource-pack-sha1=` (both editable through GPortal's web file editor). The Paper server pushes the pack to each client on join. Plugin does not need to push the pack itself in v1.

## 5. Architecture

Two artifacts, one project:

```
fooWeapons/
├── plugin/                 # Paper plugin (Java, Gradle)
│   ├── src/main/java/...
│   └── src/main/resources/
│       ├── plugin.yml
│       └── weapons/        # YAML weapon definitions, shipped with plugin
└── resourcepack/           # Vanilla resource pack
    ├── pack.mcmeta
    └── assets/fooweapons/
        ├── models/item/    # 3D model JSON per weapon
        ├── textures/item/  # PNG textures per weapon
        └── items/          # 1.21.4+ item model definitions
```

The plugin and resource pack share one source of truth: a list of weapon IDs and their `CustomModelData` integer values. A small generator script (run at build time) emits the resource pack's `items/` JSON definitions from the same YAML the plugin reads, so the two artifacts cannot drift out of sync.

## 6. v1 Weapon Roster

Six weapons spanning the standard classes:

| ID | Class | Fire mode | Mag size | Notes |
|---|---|---|---|---|
| `pistol_01` | Pistol | Semi-auto | 12 | Sidearm |
| `smg_01` | SMG | Full-auto | 30 | High fire rate, low damage |
| `rifle_01` | Assault rifle | Selectable: semi/auto | 30 | General-purpose |
| `battle_rifle_01` | Battle rifle | Semi-auto | 20 | Higher damage, slower fire |
| `sniper_01` | Bolt-action sniper | Bolt | 5 | High damage, slow rechamber, scope via ADS |
| `shotgun_01` | Pump shotgun | Pump | 6 | Pellet spread, short range |

Naming uses generic class names with numeric suffixes. The plugin does not ship with any real-world brand or model names; weapon display names are configurable in YAML so server operators can choose their own.

## 7. Components (Plugin)

Each component is its own package with a narrow responsibility:

- **`weapon` — Weapon definitions and registry.** Loads YAML definitions on plugin enable, validates them, exposes them to other components by ID. A `Weapon` is an immutable record describing class, damage, fire rate, recoil curve, mag size, reload time, sound IDs, model data ID, etc.
- **`item` — Item creation and recognition.** Creates `ItemStack`s for weapons and magazines with correct `CustomModelData` and PDC tags. Recognizes whether a given `ItemStack` is a fooWeapons gun and which one.
- **`state` — Per-item runtime state via PDC.** Reads and writes ammo count, fire-mode selection, and last-fired timestamp on the item itself. State travels with the item across drops, chests, deaths.
- **`fire` — Fire control.** Handles left-click and continuous-fire ticks. Enforces fire rate cooldown, consumes ammo from state, dispatches to hit detection, schedules recoil.
- **`reload` — Reload.** Triggered by the chosen vanilla input (see §9). Consumes a magazine item from inventory, applies reload-time delay, restores ammo on the gun item.
- **`aim` — ADS state.** Tracks which players are aiming (server-side flag), applies a movement-speed attribute modifier, narrows spread for fire calculations.
- **`hit` — Hit detection.** Hitscan raycast for bullets (with optional travel time for snipers, modeled as a scheduled task). Computes damage with distance falloff and headshot multiplier. Applies damage via Paper API.
- **`recoil` — Recoil.** Adjusts the firing player's view via `Player#teleport` with rotation deltas, applied across a few ticks for a kick-then-recover curve.
- **`feedback` — Sounds, particles, action-bar.** Emits gunshot sounds, muzzle-flash particles, and updates the action-bar with current ammo count.
- **`hud` — Action bar.** Maintains a per-player action-bar string showing weapon name and ammo (e.g. `Pistol  ▮▮▮▮▮▮▮▮▮▮▮▮  12/12`).
- **`config` — Plugin config (separate from weapon defs).** Global toggles: friendly fire, damage multipliers, list of worlds where guns are active.

## 8. Data Flow

**A shot:**
1. `PlayerInteractEvent` (left click with a fooWeapons item in hand) → `fire` package.
2. Check cooldown (last-fired + fire-rate interval). Reject if too soon.
3. Check ammo from `state`. If zero, play dry-fire sound, abort.
4. Decrement ammo, update `state`.
5. Compute spread (base + movement + non-ADS penalty), call `hit` with origin/direction.
6. `hit` raycasts. If entity hit, apply damage. If block hit, optional impact particle.
7. `feedback` plays sound and muzzle particle for nearby players.
8. `recoil` applies kick to firer.
9. `hud` updates firer's action bar.

**A reload:**
1. Reload input detected (see §9) → `reload` package.
2. Check current ammo < mag size. If full, abort.
3. Check inventory for a matching magazine item. If none, play empty-click sound, abort.
4. Remove one magazine from inventory.
5. Schedule a delayed task at `reload_time_ticks`. Lock firing during this window.
6. On completion, set ammo to full on the gun item via `state`. Play reload-complete sound.

## 9. Input Scheme

All inputs are vanilla — no client mod.

| Action | Input |
|---|---|
| Fire | Left click |
| Continuous fire (auto weapons) | Hold left click (server polls per-player on a 1-tick timer while held) |
| Aim down sights | Hold right click |
| Reload | Swap-hands key (default `F`) — fires `PlayerSwapHandItemsEvent`, which we cancel and reinterpret as reload while holding a gun |
| Fire-mode toggle | Sneak + drop key (`Shift+Q`) — captures `PlayerDropItemEvent` while sneaking with a gun in hand, cancels the drop, toggles fire mode |

Rationale for `F` (swap-hands) as reload: it's a single discrete keypress with a clean Paper event hook, the player can't accidentally lose the gun (we cancel the swap), and it doesn't conflict with movement.

## 10. Configuration

**`weapons/<id>.yml`** — one file per weapon, shipped in plugin resources and overridable in `plugins/fooWeapons/weapons/` on the server:

```yaml
id: rifle_01
display_name: "Assault Rifle"
class: assault_rifle
custom_model_data: 1003
magazine_item_id: mag_556
fire:
  modes: [semi, auto]
  default_mode: auto
  rate_per_second: 12
  damage: 5.5
  headshot_multiplier: 2.0
  range: 80
  falloff_start: 40
  falloff_end: 80
  spread:
    base_degrees: 1.5
    moving_penalty_degrees: 2.0
    ads_factor: 0.4
mag:
  size: 30
  reload_time_ticks: 50
recoil:
  vertical_degrees: 1.2
  horizontal_degrees_range: [-0.4, 0.4]
  recover_ticks: 8
sounds:
  fire: "fooweapons:rifle_01.fire"
  reload: "fooweapons:rifle_01.reload"
  dry_fire: "fooweapons:common.dry_fire"
```

**`config.yml`** — global settings:

```yaml
worlds_enabled: [world, world_nether, world_the_end]
friendly_fire: true
damage_multiplier_pvp: 1.0
damage_multiplier_pve: 1.0
resource_pack:
  enforce: true  # if true, kick players who decline the pack
```

## 11. Persistence (PDC schema)

Stored on each gun `ItemStack`'s `PersistentDataContainer`:

- `fooweapons:weapon_id` (String) — which weapon this is
- `fooweapons:ammo` (Integer) — current rounds in magazine
- `fooweapons:fire_mode` (String) — current selected mode (semi/auto/etc.)
- `fooweapons:last_fired_ms` (Long) — server time of last shot, for cooldown

Magazines (separate items) carry only:
- `fooweapons:mag_type` (String) — caliber/type ID, matched against `magazine_item_id` in weapon config

## 12. Models — v1 Approach

I (the assistant) hand-write the model JSON and generate simple textures. The result will be blocky cuboid silhouettes — recognizable as pistol / rifle / sniper / etc., not aesthetically polished.

Each weapon's resource pack contribution:
- `assets/fooweapons/models/item/<id>.json` — cuboid `elements` array forming the gun shape, with `display` transforms for first-person, third-person, GUI, and ground views.
- `assets/fooweapons/textures/item/<id>.png` — small (typically 16×16 to 32×32 per face) procedurally-generated texture. Solid colors and simple bands.
- `assets/fooweapons/items/<id>.json` — 1.21.4+ item model definition pointing at the model above.

The base item we attach to is the **crossbow** (`minecraft:crossbow`), chosen because its default first-person posing is roughly weapon-like and the model override system on crossbow works cleanly with `CustomModelData` in 1.21.11's `items/` definition format.

## 13. Models — v2 Path (Future)

When the server owner commissions a Blockbench artist for polished models, the swap-in path is:
1. Artist exports model JSON and texture PNG from Blockbench in the Java Block/Item format.
2. Files drop into `resourcepack/assets/fooweapons/models/item/` and `.../textures/item/` replacing the v1 files.
3. The same `custom_model_data` value in the weapon YAML continues to point at the new model. No plugin code changes.

This decoupling is the reason v1's "ugly models" are not a blocker for shipping.

## 14. Testing Approach

- **Plugin unit tests:** weapon definition loading, spread math, damage falloff math, PDC read/write round trips. Run via JUnit, no Paper runtime needed for pure logic.
- **Plugin integration tests:** spin up a Paper test server (via `paper-mojangmapped` runtime or MockBukkit) and verify event handlers respond correctly to synthesized events.
- **Manual playtest checklist:** firing, ammo decrement, reload, all six weapons spawn-and-fire, resource pack loads on a vanilla 1.21.11 client, recoil feels right, sounds play.

The "feel" of guns is not unit-testable. v1 success criterion is: server operator can `/give` themselves each of the six weapons, fire them, reload them, kill mobs with them, and the visuals show up as intended on a vanilla client.

## 15. Open Decisions Deferred to Implementation

- Exact recoil curve shape (server adjusts pitch/yaw; needs play-testing to feel right).
- Whether sniper bullets use travel-time or hitscan (will start hitscan, revisit if needed).
- Exact magazine item visuals (likely paper-base items with `CustomModelData`).
- Resource pack hosting URL (user provides at deployment time; not a build-time concern).

## 16. Risks

- **Resource pack acceptance.** If a player declines the pack, all guns appear as a crossbow. Config option `resource_pack.enforce: true` solves this by kicking decliners.
- **Tick budget.** Fast-firing automatic weapons + many players + raycasts could pressure server tick. Mitigation: cap per-player simultaneous projectiles, profile early.
- **View manipulation for recoil.** Paper's `Player#teleport` with rotation works but causes a tiny client-side stutter on some clients. Acceptable for v1; revisit if it feels bad.
- **`Player#teleport` for recoil is server-authoritative.** This means recoil cannot be cancelled client-side, which is correct, but means the player's mouse position effectively gets nudged. Standard approach for vanilla-client gun plugins.

## 17. Success Criteria

v1 is done when:
- Server operator can deploy the plugin jar + resource pack URL to a Paper 1.21.11 GPortal server.
- A vanilla 1.21.11 player joining the server is prompted to accept the resource pack and, after accepting, sees custom models on the six v1 weapons.
- All six weapons can be obtained via `/give` (or a `/foo give <weapon>` admin command), fired, reloaded, and used to damage entities.
- Ammo persists across death, drop/pickup, chest storage.
- Configuration changes in `weapons/<id>.yml` and `config.yml` take effect on `/reload` or server restart.
