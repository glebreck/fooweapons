# Plan 2 Slice B — SMG + Shotgun + Muzzle Flash

**Date:** 2026-05-18
**Status:** Approved, ready for implementation plan
**Target environment:** Paper 1.21.11, vanilla Java 1.21.11 clients
**Predecessor:** v0.2.0 — Slice A (rifle_01, auto-fire, fire-mode toggle)

---

## 1. Purpose

Slice A built the auto-fire and fire-mode infrastructure and shipped `rifle_01` to exercise it. Slice B extends the weapon roster with two more weapons and introduces the slice's one genuinely new mechanic: per-pellet hitscan with independent spread for shotguns. It also adds muzzle-flash particle feedback — server-side, visible to vanilla clients with no client-mod requirement.

Bundling these three pieces is deliberate. `smg_01` exercises only existing AUTO infrastructure, so it ships cheaply and validates that the AUTO pipeline generalizes beyond `rifle_01`. `shotgun_01` is the only Plan 2 weapon that needs new fire-logic (multi-pellet). Muzzle-flash is gameplay-visible polish that the existing weapons benefit from as soon as it ships — it does not introduce a separate code path per weapon.

## 2. Goals

- `smg_01` is obtainable via `/fooweapons give smg_01`, fires fully automatic at ~15 rounds/sec, lower damage per shot than the rifle, with looser moving spread.
- `shotgun_01` is obtainable via `/fooweapons give shotgun_01`, fires 8 pellets per shot with independent spread, devastating at close range and falls off sharply.
- Each successful (non-dry) shot from any fooWeapons weapon spawns a muzzle-flash particle burst at the player's barrel, visible to nearby players on vanilla 1.21.11 clients.
- `FireMode.PUMP` is added as a distinct enum value, used by `shotgun_01`. Pump weapons are single-mode (Q-cycle no-ops).
- Existing weapons (`pistol_01`, `rifle_01`) behavior is unchanged except for the new muzzle-flash visual.

## 3. Non-Goals (Out of Scope for this Slice)

- The remaining two Plan 2 weapons (`battle_rifle_01`, `sniper_01`). Future slice.
- ADS, recoil, consumable magazines. Each is its own future slice.
- Custom sound assets — continue using vanilla sound-event IDs for SMG and shotgun, same approach as pistol and rifle.
- Per-pellet visual trails, shell-eject particles, or special pump-action animations.
- Global `config.yml`. Per-weapon YAML remains the only configuration.
- Any client-side mod or companion.

## 4. Architecture

Targeted extensions to existing packages plus one new package:

```
plugin/src/main/java/com/fooweapons/
├── fire/
│   ├── (existing) FireService.java         — extended: loops pellets_per_shot raycasts,
│   │                                          aggregates damage per-target into one damage() call,
│   │                                          invokes MuzzleFlashService on fire
│   ├── (existing) FireRateCooldown.java    — unchanged
│   ├── (existing) SpreadCalculator.java    — unchanged (already supports per-call randomization)
│   ├── (existing) DamageCalculator.java    — unchanged
│   ├── (existing) Hitscan.java             — unchanged
│   ├── (existing) FireListener.java        — unchanged
│   ├── auto/                               — unchanged
│   └── mode/
│       └── (existing) FireMode.java        — adds PUMP enum value
├── feedback/
│   ├── (existing) SoundService.java        — unchanged
│   └── MuzzleFlashService.java             — NEW: spawn(player, weapon) → particle burst
├── weapon/
│   ├── (existing) Weapon.java              — record gains pelletsPerShot (int)
│   │                                          and muzzleFlash (MuzzleFlashConfig) fields
│   ├── MuzzleFlashConfig.java              — NEW: record (particleType, count, smokeCount, forwardOffset)
│   └── (existing) WeaponLoader.java        — parses optional fire.pellets_per_shot
│                                              and optional feedback.muzzle_flash block
└── plugin entry/wiring                     — instantiates MuzzleFlashService, injects into FireService
```

`FireService.tryFire(...)` remains the sole fire entry point. The pellet loop and damage aggregation live inside it — callers (semi-click and auto-tick) are unchanged. This keeps the slice's mechanical surface area inside one method.

## 5. Components

### 5.1 `FireMode.PUMP` — enum value
```java
public enum FireMode {
    SEMI("SEMI", "semi"),
    AUTO("AUTO", "auto"),
    PUMP("PUMP", "pump");
    // existing constructor and accessors unchanged
}
```
`PUMP` weapons declare `modes: [pump]` in YAML. Single-element lists already cycle to themselves and `FireModeListener` plays the denied-click sound on Q-cycle attempts (Slice A behavior). Pump is mechanically `SEMI` with a long fire-rate cooldown; the distinct enum exists for HUD clarity and to give a future "rechamber animation" feature a clean hook.

### 5.2 `Weapon` record additions
```java
int pelletsPerShot;           // required by record, defaulted to 1 by loader if YAML omits
MuzzleFlashConfig muzzleFlash; // required by record, defaulted by loader if YAML omits
```
`MuzzleFlashConfig`:
```java
public record MuzzleFlashConfig(
    int flameCount,     // particles of Particle.FLAME (default 5)
    int smokeCount,     // particles of Particle.SMOKE (default 2)
    double forwardOffset // blocks ahead of eye location (default 1.5)
) {
    public static MuzzleFlashConfig defaults() {
        return new MuzzleFlashConfig(5, 2, 1.5);
    }
}
```

### 5.3 `WeaponLoader` extensions
- Read optional `fire.pellets_per_shot` (positive integer). Default `1` if absent.
- Reject `pellets_per_shot < 1` or > 16 with logged error; skip weapon registration on validation failure.
- Read optional `feedback.muzzle_flash` block:
  - `flame_count` (int, default 5, must be 0–32)
  - `smoke_count` (int, default 2, must be 0–32)
  - `forward_offset` (double, default 1.5, must be 0.0–4.0)
- Reject out-of-range values; skip weapon registration on validation failure.
- Existing weapons (`pistol_01.yml`, `rifle_01.yml`) need no edits — both fields default cleanly.

### 5.4 `MuzzleFlashService` — new component
```java
public final class MuzzleFlashService {
    public void spawn(Player player, Weapon weapon) {
        MuzzleFlashConfig cfg = weapon.muzzleFlash();
        Location origin = player.getEyeLocation()
            .add(player.getEyeLocation().getDirection().multiply(cfg.forwardOffset()));
        World world = player.getWorld();
        world.spawnParticle(Particle.FLAME, origin, cfg.flameCount(), 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.SMOKE,  origin, cfg.smokeCount(), 0.05, 0.05, 0.05, 0.01);
    }
}
```
Server-side particle spawn — visible to all players in render distance without a client mod. Called from `FireService.tryFire` immediately after `sounds.playFire(...)`. Dry-fire path does NOT call it (returns earlier).

### 5.5 `FireService.tryFire` — modified
After the existing cooldown / ammo / sound / spread setup, the hit logic changes from a single raycast to a pellet loop:

```java
// existing: cooldown check, ammo check, decrement ammo, last-fired timestamp, fire sound
muzzleFlash.spawn(player, weapon);  // single muzzle flash per shot, regardless of pellet count

Map<LivingEntity, Double> damageByTarget = new IdentityHashMap<>();
for (int i = 0; i < weapon.pelletsPerShot(); i++) {
    Vector dir = SpreadCalculator.applySpread(
        player.getEyeLocation().getDirection(),
        spreadDegrees, random);
    Hitscan.Hit hit = Hitscan.fire(player, dir, weapon.range());
    if (hit == null) continue;
    double dmg = DamageCalculator.compute(/* same args as before, with hit.distance() */);
    damageByTarget.merge(hit.target(), dmg, Double::sum);
}
for (Map.Entry<LivingEntity, Double> e : damageByTarget.entrySet()) {
    e.getKey().damage(e.getValue(), player);
}
```

`IdentityHashMap` because we're keying on `LivingEntity` instances and want reference identity (two different entities at the same UUID would be a bug elsewhere, not a deduplication concern). One `damage()` call per target avoids the no-damage-ticks invulnerability frame, which would otherwise absorb the second through eighth pellet at point blank range.

A single fire sound and a single muzzle flash per shot, regardless of pellet count — matches what a shotgun "feels like" and keeps server particle/sound traffic flat.

## 6. Data Flow

### 6.1 SMG shot — uses existing AUTO infrastructure
1. Player holds LMB with `smg_01` in hand.
2. Arm-swing packets → `ArmSwingListener` → `AutoFireTracker`.
3. Tracker tick → `FireService.tryFire(player, smgStack, smgWeapon)`.
4. Cooldown + ammo OK → sound + muzzle flash + 1 raycast (pellets_per_shot=1) → damage if hit.
5. HUD updates on next 10-tick pass.

### 6.2 Shotgun shot — pellet path
1. Player clicks LMB with `shotgun_01` (PUMP mode, modes=[pump]).
2. `FireListener` sees mode != AUTO → fires through.
3. `FireService.tryFire` → cooldown + ammo + sound + muzzle flash + **8 independent raycasts**, each with its own spread roll.
4. Hits aggregated per target by `IdentityHashMap`. One `damage()` call per target.
5. Cooldown is the standard `last_fired_ms` check; rate_per_second of `1` gives a ~1s rechamber.

### 6.3 Muzzle flash — applies to every weapon
1. `FireService.tryFire` reaches the post-ammo-decrement stage.
2. `MuzzleFlashService.spawn(player, weapon)` spawns particles at `eyeLocation + look × forwardOffset`.
3. Server broadcasts the particle packets to all players in render distance. Vanilla clients render them.

## 7. Weapon YAML Schemas

### 7.1 New optional fields (apply to all weapons)
```yaml
fire:
  pellets_per_shot: 1     # optional, default 1
feedback:
  muzzle_flash:           # optional, defaults applied if absent
    flame_count: 5
    smoke_count: 2
    forward_offset: 1.5
```

### 7.2 New weapon — `smg_01.yml`
```yaml
id: smg_01
display_name: "SMG"
custom_model_data: 1002
fire:
  modes: [auto]
  default_mode: auto
  damage: 3.5
  headshot_multiplier: 1.8
  range: 60.0
  falloff_start: 25.0
  falloff_end: 55.0
  rate_per_second: 15
  base_spread_degrees: 2.5
  moving_spread_penalty_degrees: 3.0
mag:
  size: 30
  reload_time_ticks: 60
sounds:
  fire: "minecraft:entity.firework_rocket.blast"
  reload: "minecraft:item.crossbow.loading_middle"
  dry_fire: "minecraft:block.dispenser.fail"
```

### 7.3 New weapon — `shotgun_01.yml`
```yaml
id: shotgun_01
display_name: "Pump Shotgun"
custom_model_data: 1006
fire:
  modes: [pump]
  default_mode: pump
  pellets_per_shot: 8
  damage: 4.0
  headshot_multiplier: 1.5
  range: 30.0
  falloff_start: 8.0
  falloff_end: 20.0
  rate_per_second: 1
  base_spread_degrees: 6.0
  moving_spread_penalty_degrees: 2.0
mag:
  size: 6
  reload_time_ticks: 80
feedback:
  muzzle_flash:
    flame_count: 8
    smoke_count: 4
    forward_offset: 1.8
sounds:
  fire: "minecraft:entity.firework_rocket.blast"
  reload: "minecraft:item.crossbow.loading_middle"
  dry_fire: "minecraft:block.dispenser.fail"
```

Shotgun overrides muzzle-flash with a larger burst to match the "big bang" feel; SMG accepts defaults.

## 8. Resource Pack Additions

For `smg_01`:
- `assets/fooweapons/models/item/smg_01.json` — hand-written cuboid model: short grip, compact receiver, stubby barrel, top-mounted iron sight nub, vertical magazine. Visually shorter than rifle, longer than pistol.
- `assets/fooweapons/textures/item/smg_01.png` — procedurally generated by extending `tools/generate_textures.py`.

For `shotgun_01`:
- `assets/fooweapons/models/item/shotgun_01.json` — hand-written cuboid model: longer barrel than rifle, pump grip silhouette under the barrel, wooden stock, no magazine block (tube-fed look).
- `assets/fooweapons/textures/item/shotgun_01.png` — procedurally generated.

`assets/minecraft/items/crossbow.json` — extend the existing `select` block with two new cases:
- CMD `1002` → `fooweapons:item/smg_01`
- CMD `1006` → `fooweapons:item/shotgun_01`

`pack.mcmeta` is unchanged (still `pack_format` 75).

`tools/generate_textures.py` is extended with two new variants. Existing pistol and rifle texture generation is unchanged.

## 9. Persistence (PDC)

No new PDC keys. Slice B adds no per-item state beyond what Slice A already tracks (`weapon_id`, `ammo`, `last_fired_ms`, `fire_mode`).

## 10. Edge Cases

| Case | Behavior |
|---|---|
| YAML omits `pellets_per_shot` | Defaults to 1. No behavioral change for existing weapons. |
| YAML sets `pellets_per_shot: 0` or negative | `WeaponLoader` rejects, logs error, skips weapon. |
| YAML sets `pellets_per_shot` > 16 | Rejected (hard upper bound to prevent absurd configs). |
| YAML omits `feedback.muzzle_flash` block | Uses `MuzzleFlashConfig.defaults()` (5 flame, 2 smoke, 1.5 forward). |
| YAML sets out-of-range particle counts | Rejected. |
| Multiple pellets hit the same target in same shot | Damages aggregated, single `damage()` call avoids invuln-frame absorption. |
| Pellet hits no entity | Skipped in aggregation loop. No damage call for that pellet. |
| Shotgun fired with mag empty | Dry-fire sound, no muzzle flash, no pellets. Existing dry-fire path is unchanged. |
| Shotgun mid-reload | Reload lock blocks fire (Slice A behavior, unchanged). |
| Player Q-cycles on shotgun (single-mode) | Denied-click sound, mode unchanged (Slice A behavior, unchanged). |
| Two players fire SMGs simultaneously near each other | Each generates own particle/sound stream. No interaction. |
| `Particle.SMOKE` or `Particle.FLAME` enum renamed in future Paper API | Will fail at runtime, not load-time. Acceptable risk on a pinned 1.21.11 target. |

## 11. Testing Strategy

### 11.1 Pure-logic JUnit tests (TDD)

- **`PelletDamageAggregatorTest`** — new helper or inlined logic, tested via a public seam. Cases:
  - Single pellet, single hit → single damage call equal to pellet damage.
  - Eight pellets, all on same target → single damage call equal to sum.
  - Eight pellets, four on target A, four on target B → two damage calls, correct sums.
  - Zero pellets hit → zero damage calls.
- **`WeaponLoaderTest`** extensions:
  - `pellets_per_shot` absent → loaded weapon has `pelletsPerShot == 1`.
  - `pellets_per_shot: 8` → loaded weapon has `pelletsPerShot == 8`.
  - `pellets_per_shot: 0`, `-1`, `17` → load rejected, weapon not registered, error logged.
  - `feedback.muzzle_flash` absent → loaded weapon has `MuzzleFlashConfig.defaults()`.
  - `feedback.muzzle_flash` partial (only `flame_count` set) → other fields take defaults.
  - Out-of-range muzzle-flash values → rejected.
  - `modes: [pump]`, `default_mode: pump` → loads cleanly.
- **`FireModeTest`** (extension or new) — `FireMode.fromYamlName("pump")` returns `PUMP`.

### 11.2 PDC tests
No new PDC keys, no new tests.

### 11.3 Bukkit-runtime tests
None for this slice. `MuzzleFlashService` is a thin wrapper over `World.spawnParticle` and is covered by manual smoke test. Pellet behavior is exercised via the pure-logic tests above; the `FireService` integration with `MuzzleFlashService` is verified manually.

### 11.4 Manual smoke test (Greg's GPortal Paper 1.21.11 server)

- `/fooweapons give smg_01` — receives SMG, HUD shows `AUTO`.
- Hold LMB → continuous fire at ~15 rounds/sec (visibly faster than rifle).
- Moving while firing — spread visibly wider than rifle's.
- Reload (`F`) → 60-tick lock, HUD progresses, fires after.
- `/fooweapons give shotgun_01` — receives shotgun, HUD shows `PUMP`.
- Aim at a flat wall and fire once → 8 distinct impact points visible in a cone.
- Fire at a zombie at point-blank → one shot kill (8 × 4.0 = 32 damage).
- Fire at a zombie at ~25 blocks → little damage (falloff plus most pellets miss).
- Q while holding shotgun → denied-click sound, mode unchanged.
- All weapons (pistol, rifle, SMG, shotgun): every successful shot produces a visible muzzle-flash burst.
- Second player observes a firer from the side → sees muzzle flash on every shot.
- `pistol_01` and `rifle_01` behavior otherwise unchanged from v0.2.0 (regression check).

## 12. Risks

- **Tick cost from pellet hitscans.** Shotgun = 8 hitscans/shot at ~1 shot/sec = 8 raycasts/sec per firing player. SMG = 15 raycasts/sec per firing player. Both well within a small server's budget. Mitigation if scaling becomes an issue: cap concurrent shooters or batch raycasts. Not a slice blocker.
- **Particle visibility distance.** `World.spawnParticle` uses default visibility radius (~32 blocks). A sniper-range muzzle flash may not be visible to distant observers — acceptable for SMG and shotgun (both close-to-mid-range), revisit when `sniper_01` lands.
- **Per-target damage aggregation correctness.** `IdentityHashMap` keyed on `LivingEntity` reference is correct as long as the eight hitscans complete before any entity reference is mutated or replaced — true for single-tick fire processing. If a future change makes pellet processing async, this assumption breaks.
- **Pellet RNG correlation.** All eight pellets reuse `FireService`'s single `Random` instance. Sequential calls produce independent draws, which is correct. No correlation risk unless the `Random` is later shared across threads (not currently the case).
- **YAML schema growth.** Two new optional blocks. Both have safe defaults, so existing weapons need no edits and forward-compatibility with v0.1.0 / v0.2.0 weapon files is preserved.

## 13. Success Criteria

Slice is done when:
- All JUnit tests in §11.1 pass.
- On the GPortal server with a vanilla 1.21.11 client, every manual smoke-test step in §11.4 passes.
- `pistol_01` and `rifle_01` regression check passes (behavior unchanged except for new muzzle-flash visual).
- `dev` branch builds clean (`./gradlew dist` produces both artifacts).
- `PROGRESS.md` is updated: `SMG (smg_01)`, `Pump shotgun (shotgun_01)`, `Muzzle-flash particles` move from `[ ]` to `[x]` under Plan 2.

Promotion to `beta` and `release` follows the existing three-branch rule (only on Greg's explicit ask).
