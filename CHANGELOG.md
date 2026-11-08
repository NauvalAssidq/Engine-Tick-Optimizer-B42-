# Changelog

All notable changes to the Engine Tick Optimizer (ETO) project are documented in this file.
The project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
