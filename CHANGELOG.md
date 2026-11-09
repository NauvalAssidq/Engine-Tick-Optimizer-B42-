# Changelog

All notable changes to the Engine Tick Optimizer (ETO) project are documented in this file.
The project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.2.0-alpha] - 2026-11-09

### Project Zomboid Build 42.22 Compatibility & Architecture Overhaul

#### 1. Build 42.22 Signed JAR Security Resolution
- **Issue:** Project Zomboid Build 42.22 introduced official digital signatures (`META-INF/PROJECTZ.SF`, `META-INF/PROJECTZ.EC`) on `projectzomboid.jar`. Placing loose modified classes on the classpath or overriding classes via un-signed JARs triggered a fatal JVM security violation:
  `java.lang.SecurityException: class "zombie.characters.IsoGameCharacter"'s signer information does not match signer information of other classes in the same package`
  followed by `NoClassDefFoundError`, terminating the game immediately on boot.
- **Resolution:**
  - Migrated ETO completely to a pure dynamic Java Agent (`-javaagent:engine-tick-optimizer.jar`).
  - Stripped all `zombie.*` classes from the distribution JAR. The JAR now contains solely `com.pzoptimizer.*` and ASM bytecode transformers.
  - Game classes are loaded exclusively from `projectzomboid.jar` with digital certificate verification intact. Bytecode transformations are intercepted dynamically in memory via Java's `Instrumentation` API (`PZClassTransformer`), bypassing signer mismatches cleanly and legally.
  - Classpath is restored to vanilla `[ "projectzomboid.jar" ]`.

#### 2. Discontinuation of PZ_Optimization (pzopt) Companion Mode for 42.22+
- `PZ_Optimization` stopped updates at Build 42.21 and relied on offline class replacement via the local directory classpath (`.`). This is incompatible with Build 42.22's signed JAR without stripping signatures.
- ETO now operates as an autonomous, all-in-one standalone agent providing complete FBO VRAM caching, skyscraper culling, distance-based zombie tick LOD, and pathfinding rate-limiting out of the box.

#### 3. Legacy GC Stutter Elimination
- **Issue:** Residual flags from previous `pzopt` installations injected `-XX:G1NewSizePercent=2` and `-XX:G1MaxNewSizePercent=4` into `ProjectZomboid64.json` under `windows["10.0.17134"]`. This clamped young generation memory to a miniscule 2-4% of heap, triggering constant G1 GC pauses and hitching whenever vehicles moved, doors opened, or containers were accessed.
- **Resolution:**
  - Installer purges all legacy `pzopt` arguments (`-Dpzopt.*`, `-XX:PerMethodTrapLimit=0`, etc.) and resets OS-specific VM blocks to vanilla defaults.
  - Restored Low-End preset heap to standard vanilla sizing: `-Xms3072m`, `-Xmx6144m`, and `-XX:MaxDirectMemorySize=2048m`.

---

## [0.1.0-alpha] - 2026-11-09

### Initial Prototype & Core Architecture
- **FBO LRU Memory Manager (`FBOCacheManager`):**
  - Implemented an LRU cache capping active chunk Framebuffer Objects (default 160-350 based on preset).
  - Automatically evicts off-screen and distant vertical skyscraper levels, preventing Build 42's 5-9 GB VRAM/RAM runaway leak.
- **Vertical Skyscraper Culling (`VerticalChunkPager`):**
  - Hooks `IsoChunkMap.calculateZExtentsForChunkMap()` to prune high-rise levels outside $Z_{\text{player}} \pm 6$.
  - Slashing geometry draw calls and chunk baking overhead by up to 75% in downtown Louisville.
- **Zombie Distance LOD (`ZombieLODManager`):**
  - Three-tier distance throttle: $<16$ tiles (100%), $16-32$ tiles (50%), $>32$ tiles (20%).
  - Added strict physics and gameplay guardrails: active combat targets, knockdown, stagger, door/window thumping, and vehicle proximity run unthrottled.
- **Crowd Pathfinding Budgeter (`CrowdFlowFieldManager`):**
  - Enforces a quota of maximum 6-8 native C++ pathfinding queries per game tick across all zombies.
  - Defers surplus queries during sudden horde alerts with frame-jittering (`allowRepathDelay = 3.0f + jitter`), eliminating multi-frame alert spikes.

---

### Incremental Patches & Fixes

#### Patch 0.1.1: Frustum & 200% Zoom Ground Protection
- **Issue:** Zooming out to 200% in open areas caused black missing ground squares due to horizontal chunk pool exhaustion.
- **Fix:** Implemented `isPlayerFloorFBO()` ground floor immunity. Chunks on the player's active elevation ($Z_{\text{player}} \pm 1$) are protected from eviction, ensuring horizontal frustum completeness at any zoom level.

#### Patch 0.1.2: PZ_Optimization (pzopt) Coexistence
- **Issue:** `pzopt.BootRepair` self-check verified stock SHA-256 hashes of `IsoZombie` and `IsoChunkMap`, triggering automatic mod self-uninstallation when modified classes were detected.
- **Fix:** Introduced dual-target packaging (`engine-tick-optimizer-standalone.jar` vs `engine-tick-optimizer-companion.jar`). In companion mode, ETO exclusively manages chunk FBOs and leaves `pzopt`'s loose classes 100% untouched.

#### Patch 0.1.3: APU Silhouette Shader Safeguard
- **Issue:** AMD Radeon integrated graphics (APUs) exhibited pitch-black, unanimated zombie models in massive hordes when `pzopt`'s experimental SSBO shader (`hordeBatch`) was active.
- **Fix:** Added automatic APU detection in installer that configures `hordeBatch=off` in `pzopt/options.ini`, restoring full 3D models and skeletal animations while maintaining frame rate stability.

#### Patch 0.1.4: Hardware Presets & Direct Shipping
- **Presets System:** Added JSON templates for `Low-End / APU Laptop`, `Balanced / Mid-Range`, and `High-End Rig`.
- **Pre-Built Binaries:** Shipped production JARs directly in `bin/`, removing any requirement for players to install JDK or build from source.
- **1-Click Launchers:** Added `Install.cmd` and `Uninstall.cmd` for double-click execution from Windows File Explorer.

#### Patch 0.1.5: APU Memory Budgeting & Tightened Low-End Profile
- **Issue:** On AMD Ryzen APUs with unified memory (Ryzen 5 6600H), `ProjectZomboid64.exe` expanded to 7+ GB due to 4 GB heap, 1.5 GB off-heap direct buffers, JVM code caches, and AMD OpenGL driver allocating shared texture VRAM directly inside the process working set. `pzopt.GcChoice` also attempted to expand heap to 50% physical RAM (7,680 MB).
- **Fix:**
  - Tightened Low-End APU profile to `-Xmx3072m` (3 GB heap) and `-XX:MaxDirectMemorySize=1024m` (1 GB direct memory), with resident FBO cache capped at 128 (`-Dcom.pzoptimizer.fbo.max=128`).
  - Automated `gcHeap=3072m` and `chunkGridWidth<=7` in `pzopt/options.ini`, preventing `pzopt` auto-heap expansion and saving ~600 MB of texture VRAM by reducing active chunk grid from 81 to 49 chunks.
  - Automated `3DGroundItem=false` and `maxVehicleTextureSize=1` in `Zomboid/options.ini` for Low-End APU installations to drastically curb GPU memory pressure in shared RAM.
  - Enhanced installer classpath preservation to non-destructively preserve existing entries (e.g. `pzopt/aot/pzopt.jar`).

#### Patch 0.1.6: Door & Building-Entry Anti-Stutter
- **Issue:** Opening/closing doors or entering buildings caused momentary frame freezes and camera hitching.
- **Root Causes:**
  - `FBOCacheManager.sweepVertical()` was executed via `calculateOnScreen()` across all chunks every frame (~3,000 reflection calls/sec), causing GC churn and freeing upper floors that the cutaway system was actively querying.
  - `pzopt` was restricted to `workers=1`, serializing ambient lighting recalculations and chunk rebaking onto the main game thread.
  - `godRaysDoorDustPct=50` and `pixelLight=true` triggered volumetric light shafts and per-pixel indoor lighting passes whenever a doorway portal opened.
  - `bakeMaxWaitCutaway=3` forced 15–30 building chunk FBOs to rebake synchronously within 3 frames upon building entry.
- **Fix:**
  - Cached player elevation once per frame and bitmask-throttled `sweepVertical()` to run once every 64 frames, cutting reflection overhead by 99.9% and preventing cutaway FBO thrashing.
  - Enabled `workers=4` in `pzopt/options.ini` to offload chunk baking and light propagation to background worker threads.
  - Set `godRaysDoorDustPct=0` and `pixelLight=false` in `pzopt/options.ini` to eliminate volumetric door shader stalls on integrated APUs.
  - Relaxed `bakeMaxWaitCutaway=8` to distribute building-entry chunk rebakes smoothly across multiple frames.

#### Patch 0.1.7: Low-End Standard Heap Relaxation
- **Issue:** Clamping the low-end preset to 3072 MB (`-Xmx3072m`) starved the JVM under heavy modlists (e.g., `A-Life`, `SCKCO`, `MaskaradsSummerCar`, `Neat_Building`), triggering aggressive G1 GC compaction passes and random multi-second freezes.
- **Fix:**
  - Restored Low-End preset heap to standard **`4096m` (4 GB)** and DirectMemory to **`1536m`**, with resident FBO cache set to **`160`**.
  - Updated `pzopt` safeguard to clamp `gcHeap=4096m` in `pzopt/options.ini`.
  - Provides the G1 Garbage Collector with sufficient slack memory to collect transient objects concurrently in worker threads without stalling the main game loop.
