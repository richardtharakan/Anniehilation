# Architecture notes

## Current application shape

Anniehilation is an Android application. The top-level Android module is `app/`; the project uses Java source, XML resources, Gradle Kotlin DSL build files, and a Gradle version catalog.

The uploaded project snapshot includes the dashboard, custom UI views, cleanup accessibility service, and earlier game classes. The working development branch has subsequently received the cinematic room prototype and smooth-motion game-view updates. Use the current checked-out branch as the source of truth; this document describes responsibilities, not a guarantee that every proposed class already exists.

## Existing responsibility areas

- **Dashboard / activity layer:** app entry point, navigation, and dashboard presentation.
- **Device UI components:** RAM gauge, thermometer, storage indicator, and cooldown visuals.
- **Accessibility service:** user-enabled cleanup-related service. Keep permissions explicit and user-controlled.
- **Game activity / view:** game lifecycle, input, rendering, and dashboard return path.
- **Room manager:** connected-room routing for the cinematic prototype.
- **Audio resources:** short game SFX and event-triggered playback.

## Target separation for the larger campaign

As the game grows, move responsibilities out of the main game view into focused systems:

| System | Responsibility |
|---|---|
| CampaignManager | World order, stage unlocks, completion and save progress |
| LevelDefinition / LevelLoader | Data-driven stage definitions and validation |
| RoomManager | Connected room graph and transitions |
| TileMap / CollisionSystem | Floor, wall, ledge, hazard and interaction collision |
| PlayerController | Annie movement and action state machine |
| EnemyController | Enemy/boss AI, attack timing, damage and recovery |
| ObstacleController | Warning/active/impact/recovery state machines |
| PetManager | Berthu and selected pet behaviour, unlocks and abilities |
| AnimationController | Character animation state and frame timing |
| GameRenderer | Backgrounds, rooms, sprites, lighting, particles and HUD |
| AudioManager | Event-driven music, SFX, mixing, pooling and cooldown |

Use data-driven level definitions rather than deriving every room from a stage-number formula. Keep rendering separate from game-state mutation. Physics and collision should use frame-independent time steps, and high-impact events should be triggered once per collision event.

## Safety and compatibility

- Preserve dashboard functionality while the game engine changes.
- Avoid changing package name or application ID without a planned migration.
- Keep resource filenames lowercase with underscores.
- Do not commit local SDK paths, private signing material, generated build output, or developer-specific configuration.
- Keep permissions minimal and explain any sensitive capability.
