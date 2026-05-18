# Plan 2 Slice A — Auto-Fire + Fire-Mode Toggle + rifle_01

**Date:** 2026-05-15
**Status:** Approved, ready for implementation plan
**Target environment:** Paper 1.21.11, vanilla Java 1.21.11 clients
**Predecessor:** v0.1.0 MVP (pistol_01, semi-auto only)

---

## 1. Purpose

The MVP shipped one weapon (`pistol_01`) on a semi-auto-only fire pipeline. Plan 2 fills out the weapon roster and the mechanics that differentiate weapons. This slice — the first slice of Plan 2 — adds the **infrastructure for automatic fire and selectable fire modes**, and bundles the first weapon that exercises both (`rifle_01`).

Bundling the rifle is deliberate: auto-fire and mode-toggle are pure infrastructure with no player-visible effect on `pistol_01` (which is and remains semi-only). Shipping the slice with no exercising weapon would mean shipping untested code paths. `rifle_01` is the only Plan 2 weapon defined to have *both* modes, so it exercises every new code path the slice introduces.

## 2. Goals

- Players holding LMB on an `rifle_01` set to AUTO fire continuously at the weapon's `rate_per_second`, with no extra clicks needed.
- Players can Sneak+Q to cycle a weapon through its declared fire modes; selection persists with the gun item across drops, deaths, and chest storage.
- The action-bar HUD shows the currently-selected mode (replacing the hardcoded `"SEMI"` in v0.1.0).
- `pistol_01` remains functionally identical: semi-auto only, Sneak+Q does nothing useful.
- `rifle_01` is obtainable via `/fooweapons give rifle_01`, has a hand-written cuboid model and procedural texture in the resource pack, and exercises both modes end-to-end.

## 3. Non-Goals (Out of Scope for this Slice)

- The other four Plan 2 weapons (`smg_01`, `battle_rifle_01`, `sniper_01`, `shotgun_01`). They land in subsequent slices.
- Aim-down-sights, recoil, consumable magazines, muzzle-flash particles. Each is its own future slice.
- Global `config.yml`. Configuration remains per-weapon YAML only.
- Custom sound assets. Continue using vanilla sound event IDs.
- Any client-side mod or companion. Vanilla-client target is unchanged.

## 4. Architecture

Two new packages plus targeted extensions to existing ones:

```
plugin/src/main/java/com/fooweapons/
├── fire/
│   ├── (existing) FireListener.java         — extended: skip click-firing on AUTO-mode weapons
│   ├── (existing) FireService.java          — unchanged
│   ├── (existing) FireRateCooldown.java     — unchanged
│   ├── auto/
│   │   ├── AutoFireTracker.java             — NEW: per-player swing-timestamp map, tick scheduler
│   │   └── ArmSwingListener.java            — NEW: PlayerAnimationEvent → tracker update
│   └── mode/
│       ├── FireMode.java                    — NEW: enum SEMI, AUTO
│       ├── FireModeCycle.java               — NEW: pure-logic cycle(current, modes) → next
│       └── FireModeListener.java            — NEW: cancelled-drop Sneak+Q handler
├── weapon/
│   ├── (existing) Weapon.java               — record gains modes + defaultMode fields
│   └── (existing) WeaponLoader.java         — parses + validates new YAML fields
├── item/
│   ├── (existing) ItemState.java            — gains getFireMode / setFireMode (PDC)
│   ├── (existing) WeaponItemFactory.java    — sets initial fire_mode PDC on weapon creation
│   └── (existing) PdcKeys.java              — adds FIRE_MODE key
└── hud/
    └── (existing) HudService.java           — reads current mode from PDC instead of hardcoded "SEMI"
```

`FireService.tryFire(...)` remains the single fire entry point. Both `FireListener` (for SEMI clicks) and `AutoFireTracker` (for AUTO pulses) call into it. Cooldown, ammo decrement, spread, hitscan, damage all remain in one place — auto-fire is purely a different *trigger source*, not different fire logic.

## 5. Components

### 5.1 `FireMode` enum
```java
public enum FireMode {
    SEMI("SEMI"),
    AUTO("AUTO");
    private final String label;
    FireMode(String label) { this.label = label; }
    public String label() { return label; }
}
```
Adding `BOLT` or `PUMP` later is a one-line change. Not adding them in this slice.

### 5.2 `FireModeCycle` — pure logic, TDD
```java
public static FireMode next(FireMode current, List<FireMode> available);
```
Returns the next mode in `available` after `current`, wrapping. If `available` has size 1, returns the single mode (caller's responsibility to play the denied-click feedback).

### 5.3 `FireModeListener` — Bukkit event handler
On `PlayerDropItemEvent`:
- If player is not sneaking → return (let vanilla drop happen).
- If held item is not a fooWeapons gun → return.
- `event.setCancelled(true)`.
- Read weapon's `modes` list. If size 1 → play denied-click sound, return.
- Cycle to next mode, write to PDC, play short click sound, return. (HUD picks up the change on its existing 10-tick scheduler.)

### 5.4 `ArmSwingListener` — Bukkit event handler
On `PlayerAnimationEvent` with type `ARM_SWING`:
- If held item is a fooWeapons gun AND `state.getFireMode(stack) == AUTO` → update `AutoFireTracker` entry for player with `now` as last-swing timestamp.
- Otherwise return.

### 5.5 `AutoFireTracker` — per-tick scheduler
Holds `Map<UUID, AutoFireEntry>` where `AutoFireEntry` is `(ItemStack heldStackSnapshot, long lastSwingMs)`.

Runs `Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L)`.

`tick()` iterates the map and for each entry:
1. Resolve `Player` by UUID. If offline → remove entry, continue.
2. If `now - lastSwingMs > 250` → remove entry (LMB released), continue.
3. Get current main-hand item. If it isn't the same gun (compare by PDC `weapon_id` + held-slot index — *not* Java reference equality, since inventory operations can rebuild the stack) → remove entry, continue.
4. If `state.getFireMode(stack) != AUTO` → remove entry, continue.
5. If `reloadListener.isReloading(playerUUID)` → continue (don't remove; player may resume after reload).
6. If `!FireRateCooldown.canFire(state.getLastFiredMs(stack), now, weapon.fireCooldownMillis())` → continue.
7. Call `fireService.tryFire(player, stack, weapon)`.

The 250ms window is intentionally generous relative to vanilla swing rate (~5 Hz = 200ms gap with no attack cooldown). One missed swing packet won't cause a phantom stop.

### 5.6 `Weapon` record additions
```java
List<FireMode> modes;       // required, non-empty
FireMode defaultMode;       // required, must be in modes
```
No default values at the record layer. `WeaponLoader` enforces presence and validity at parse time.

### 5.7 `WeaponLoader` validation rules
- Missing `modes` field → reject, log error, skip weapon.
- Empty `modes` list → reject.
- `default_mode` missing or not in `modes` → reject.
- Unknown mode string → reject (current valid set: `semi`, `auto`).

### 5.8 `ItemState` additions
```java
FireMode getFireMode(ItemStack stack);   // falls back to weapon.defaultMode if PDC tag absent
void setFireMode(ItemStack stack, FireMode mode);
```
Lazy migration: legacy v0.1.0 gun stacks (no `fire_mode` PDC tag) read as their weapon's `defaultMode` and the corrected value is written back on next read.

### 5.9 `HudService` change
[HudService.java:64](../../plugin/src/main/java/com/fooweapons/hud/HudService.java#L64) — replace `Component.text("SEMI", NamedTextColor.AQUA)` with `Component.text(state.getFireMode(stack).label(), NamedTextColor.AQUA)`.

## 6. Data Flow

### 6.1 SEMI fire — unchanged
1. `PlayerInteractEvent` LEFT_CLICK → `FireListener`.
2. `FireListener` checks: is weapon? not reloading? `state.getFireMode(stack) == SEMI`?
3. If all yes → `FireService.tryFire(...)`.
4. (If mode is AUTO, `FireListener` does NOT fire — the tracker owns AUTO fire to avoid double-firing on the first click.)

### 6.2 AUTO fire — new
1. Player holds LMB. Vanilla client streams arm-swing packets.
2. Each `PlayerAnimationEvent(ARM_SWING)` → `ArmSwingListener` writes `now` into `AutoFireTracker`.
3. `AutoFireTracker.tick()` (1L period) iterates entries:
   - Validate (item still held, mode still AUTO, swing recent).
   - Check `FireRateCooldown` and `reloadListener.isReloading`.
   - If clear → `FireService.tryFire(...)`.

### 6.3 Mode toggle — new
1. Player sneaks, presses Q. `PlayerDropItemEvent` fires.
2. `FireModeListener` cancels the drop if player sneaking and holding a gun.
3. Reads weapon's `modes`. If size 1 → denied-click sound and stop.
4. Else cycles mode, writes to PDC, plays click sound. HUD updates on next 10-tick scheduler pass.

## 7. Weapon YAML Schema

### 7.1 New required fields
```yaml
fire:
  modes: [semi, auto]
  default_mode: semi
```

### 7.2 Existing weapons — backfill
`pistol_01.yml` gets:
```yaml
fire:
  modes: [semi]
  default_mode: semi
```

### 7.3 New weapon — `rifle_01.yml`
Stats per design spec section 6 (assault rifle, semi/auto, 30-round mag, general-purpose) and section 10 (example YAML):
```yaml
id: rifle_01
display_name: "Assault Rifle"
custom_model_data: 1003
fire:
  modes: [semi, auto]
  default_mode: semi
  damage: 5.5
  headshot_multiplier: 2.0
  range: 80.0
  falloff_start: 40.0
  falloff_end: 80.0
  rate_per_second: 10
  base_spread_degrees: 1.5
  moving_spread_penalty_degrees: 2.0
mag:
  size: 30
  reload_time_ticks: 50
sounds:
  fire: "minecraft:entity.firework_rocket.blast"
  reload: "minecraft:item.crossbow.loading_middle"
  dry_fire: "minecraft:block.dispenser.fail"
```

(Sound IDs match pistol's vanilla placeholders. Slice does not introduce custom sound assets.)

## 8. Resource Pack Additions

For `rifle_01`:
- `assets/fooweapons/models/item/rifle_01.json` — hand-written cuboid model: grip + frame + magazine + barrel + receiver + iron-sight nub. Longer silhouette than pistol; same modeling discipline.
- `assets/fooweapons/textures/item/rifle_01.png` — procedurally generated by extending `tools/generate_textures.py` with a rifle variant.
- Existing `assets/minecraft/items/crossbow.json` already overrides crossbow by `custom_model_data` — adding rifle_01 (CMD `1003`) is a new case in that file's `select` block.

`pack.mcmeta` is unchanged (still pack_format 75).

## 9. Persistence (PDC)

New key: `fooweapons:fire_mode` (String). Stored on the gun ItemStack PDC alongside `weapon_id`, `ammo`, `last_fired_ms` from v0.1.0.

Value is the lowercase mode name (`"semi"` or `"auto"`). String chosen over Integer ordinal so config-editing humans can read it.

## 10. Edge Cases

| Case | Behavior |
|---|---|
| YAML missing `modes` or `default_mode` | `WeaponLoader` rejects at startup, logs error, weapon not registered. |
| YAML `default_mode` not in `modes` list | Same — reject at startup. |
| PDC has a `fire_mode` value no longer valid (config edited after gun was created) | `ItemState.getFireMode` coerces to weapon's `defaultMode` and writes corrected value back to PDC. |
| Legacy v0.1.0 gun with no `fire_mode` PDC tag | Reads as `weapon.defaultMode`, written back on next read (lazy migration). |
| Player swaps weapons mid-LMB-hold | Tracker prunes entry on next tick (held-item check fails). Burst stops. |
| Player drops gun mid-LMB-hold | Same — held item is no longer the tracked weapon. |
| Player toggles mode mid-burst | Allowed. Next tick reads new mode. If new mode is SEMI, tracker stops firing. |
| Two Sneak+Q events in same tick (network jitter) | Cycle is deterministic, PDC writes idempotent per state. Worst case: mode cycles twice. Not worth debouncing. |
| Reload starts mid-burst | Existing `reloadListener.isReloading()` gate stops fire — no slice-specific change. |
| Player Sneak+Q while holding a single-mode weapon (pistol) | Drop is cancelled, denied-click sound plays, mode unchanged. |
| Single-mode weapon's PDC tag (or absence) | Pistol always renders its single mode `"SEMI"` on the HUD. Sneak+Q does nothing functional. |

## 11. Testing Strategy

### 11.1 Pure-logic tests (JUnit, TDD)
- `FireModeCycleTest` — `[semi, auto]` cycles `semi → auto → semi`; `[semi]` returns `semi` (no-op); algorithm tolerates lists of size 3+ for future modes.
- `WeaponLoaderTest` (extend existing): rejects missing `modes`, empty `modes`, missing `default_mode`, `default_mode` not in `modes`, unknown mode strings. Accepts valid `pistol_01.yml` and `rifle_01.yml`.

### 11.2 PDC tests
- `ItemStateTest` (extend existing): round-trip `fire_mode` tag; legacy-item fallback to `defaultMode`; coercion of invalid value to `defaultMode` with PDC writeback.

### 11.3 Bukkit-runtime tests
None for this slice. Event-handler behavior (arm-swing throttling, drop cancellation, scheduler timing) is too vanilla-client-dependent to mock reliably and is covered by manual smoke testing.

### 11.4 Manual smoke test (Greg's GPortal Paper 1.21.11 server)
- `/fooweapons give rifle_01` — receives a rifle with HUD showing `SEMI`.
- Click LMB once → single shot, ammo decrements by 1, HUD updates.
- Sneak+Q → click sound, HUD now shows `AUTO`.
- Hold LMB → continuous fire at ~10 rounds/sec until mag empty.
- Reload (`F`) → reload lock works, fire stops, HUD shows progress.
- Drop rifle, pick it back up → HUD still shows last-selected mode.
- Die holding rifle, respawn, pick it back up → mode preserved.
- `/fooweapons give pistol_01` — Sneak+Q plays denied-click; pistol stays SEMI.
- Both weapons in different hotbar slots → switching hotbar mid-burst stops the burst correctly.

## 12. Risks

- **Arm-swing rate may be gated by vanilla attack cooldown.** If the client rate-limits swing packets below the weapon's `rate_per_second`, fast weapons (future SMG at 15/sec) will be capped. *Mitigation: prototype rifle_01 at 10/sec early; if capped, options are (a) accept the cap, (b) supplement swings with a separate "LMB intent" signal, (c) fall back to a different pulse source. This is a tracked risk, not a blocker for the slice.*
- **Sneak+Q debounce.** Network jitter can deliver two drop events in the same tick. Worst case is a double-cycle which the player corrects in one more Q. Acceptable; not worth implementing debounce.
- **Item-identity tracking in `AutoFireTracker`.** Comparing held `ItemStack` by reference fails if the inventory rebuilds the stack. Comparison uses PDC `weapon_id` presence + same slot, not Java reference equality. Slot-aware to avoid mis-tracking after hotbar swap.

## 13. Success Criteria

Slice is done when:
- All JUnit tests in §11.1–§11.2 pass.
- On the GPortal server with a vanilla 1.21.11 client, every manual smoke-test step in §11.4 passes.
- `pistol_01` behavior is unchanged from v0.1.0 (regression check).
- `dev` branch builds clean (`./gradlew dist` produces both artifacts).
- `PROGRESS.md` is updated: `automatic fire`, `fire mode toggle`, `rifle_01` move from `[ ]` to `[x]` under Plan 2.

Promotion to `beta` and `release` follows the existing three-branch rule (only on Greg's explicit ask).
