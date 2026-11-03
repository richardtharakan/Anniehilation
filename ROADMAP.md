# Anniehilation game roadmap

This is the agreed design target. It is **not a claim that all 50 stages, pets, or hazards are currently implemented**.

## Campaign rules

- 50 stages total across eight visually and mechanically distinct worlds.
- Each ordinary stage contains multiple connected rooms and a distinct objective.
- Xingxing appears at the **end of each world**, not at the end of every ordinary stage.
- Each world finale has a reunion sequence and unlocks a new support pet.
- Berthu remains Annie's permanent protective companion. The player may select one additional unlocked support pet for a stage.
- World 8 contains the final eight stages and the Level 50 grand finale.

## World plan

| World | Stages | Identity | Signature mechanics | Finale |
|---|---:|---|---|---|
| 1. School Grounds | 1–6 | Bright, playful, familiar campus | Corridors, gym equipment, playground mechanisms, rooftops, moving school machinery | Level 6: Xingxing reunion + Poodle unlock |
| 2. Old City | 7–12 | Bustling, warm, unpredictable market district | Awning routes, rooftop laundry lines, canals, carts, narrow alleys, shifting bridges | Level 12: Xingxing reunion + Sheep unlock |
| 3. Forgotten Fort | 13–18 | Ancient, mysterious defensive architecture | Counterweights, drawbridges, portcullises, arrow mechanisms, crumbling battlements | Level 18: Xingxing reunion + pet 3 unlock |
| 4. Wild Jungle | 19–24 | Lush, organic, dangerous exploration | Swinging vines, roots, rolling logs, mud, rushing water, unstable trees | Level 24: Xingxing reunion + pet 4 unlock |
| 5. Underground Kingdom | 25–30 | Dark, subterranean, mechanical | Mine carts, crushers, steam vents, cave-ins, crystal puzzles, timed lifts | Level 30: Xingxing reunion + pet 5 unlock |
| 6. Mountain Passage | 31–36 | Cold, vertical, exposed | Ice slides, gusting wind, avalanches, brittle bridges, falling icicles | Level 36: Xingxing reunion + pet 6 unlock |
| 7. Night Citadel | 37–42 | Dark, dramatic, fortified | Rotating blade wheels, moving walls, chain traps, fire sweepers, advanced guards | Level 42: Xingxing reunion + pet 7 unlock |
| 8. Xingxing's Kingdom | 43–50 | Magical, colourful, surreal | Floating islands, gravity lifts, light puzzles, sky bridges, final gauntlet | Level 50: grand reunion + final pet unlock |

## Pet system

### Confirmed first two reference directions

- **Poodle (World 1 reward):** agile defensive pet. Reference sheet includes guard, run, block, and punch poses. Planned abilities: interrupt an enemy, block a hit, and quick support attacks.
- **Sheep (World 2 reward):** sturdy charge-oriented pet. Reference sheet includes guard, run, block, punch, kick, spin kick, hit, and down poses. Planned abilities: knock back groups and break designated brittle obstacles.

### Remaining pets

Pets 3–8 are design slots. Their species, names, reference art, animation sets, and mechanics must be agreed before production. Do not invent final identities as if already approved.

### Companion rules

- Berthu remains present and retains the existing protection/damage-sharing role unless separately rebalanced.
- A player selects one additional unlocked pet per stage to avoid crowding the screen.
- Pets need idle, follow, run, jump, ability, hit, and recovery states; each ability needs cooldowns and collision rules.
- Pet unlocks are saved to campaign progress.

## High-impact obstacle design

Every hazard must have a complete state machine:

1. **Idle / reset** — safe or inactive.
2. **Warning** — visual cue plus distinctive audio cue.
3. **Anticipation** — moving parts visibly prepare to activate.
4. **Active** — hazard has a clear collision volume and defined damage/knockback.
5. **Impact** — one-shot damage event, particles/debris, appropriate sound, and controlled camera feedback.
6. **Recovery** — hazard returns to a safe state or exposes a timed traversal window.

Required hazard families:

- Mechanical spike chambers with pressure plates and retraction windows.
- Giant pendulum blades with readable arcs and safe crossing windows.
- Pressure-fed flame vents with hiss, ignition, heat distortion, and cooldown.
- Boulder chase sequences that destroy designated breakable geometry.
- Ceiling collapses with cracks, warning shadows, falling debris, and persistent rubble.
- Flood chambers with changing water levels, current forces, swimming, and floating objects.
- Crumbling bridges and crushing walls with progressive structural failure.
- Rotating blade mechanisms with gears, speed phases, and timed gaps.
- Additional world-specific traversal devices: ropes, counterweight lifts, movable blocks, brittle floors, ladders, vines, and gates.

Ladders are traversal tools and gates are puzzle mechanisms; do not count them as hazards merely to inflate an obstacle list.

## Antagonist animation and combat

Use an original fictional martial-arts antagonist rather than an uncleared real-person likeness. Planned boss backflip sequence:

1. anticipation crouch;
2. take-off;
3. tuck;
4. inverted mid-air pose;
5. rotation completion;
6. controlled landing;
7. recovery / punish window.

The backflip should use pose/frame animation or a proper skeletal transform, not simply rotate one static photograph. Define attack states, telegraphs, hitboxes, invulnerability/cooldown windows, landing recovery, and counterattack opportunities.

## Audio and impact language

Every hazard and enemy action gets purpose-built audio. Use distinct warning, activation, impact, and recovery cues. Use original or appropriately licensed audio only.

Major impact feedback may include:

- short, bounded camera shake;
- brief hit-stop for major impacts only;
- particles and debris;
- directional knockback;
- character reaction animation;
- one damage event per impact, not repeated damage every frame.

Xingxing's laugh should be a new original, cheerful cartoon-monkey giggle used at world finales, with a celebratory musical sting. It should not be a recording or imitation of an unlicensed performer.

## Engineering milestones

1. Campaign data model: worlds, levels, rooms, objectives, checkpoints, finale triggers, pet rewards, and saved progress.
2. Room system: multiple connected rooms per stage, with horizontal and vertical transitions.
3. Player movement: walk/run, acceleration, jump, fall, grab, hang, pull-up, crouch, climb, combat, and landing recovery.
4. Obstacle state machines and collision events.
5. Enemy AI and boss animation/combat states.
6. Pet manager and unlock/save system.
7. World-specific rendering, parallax, particles, lighting, and audio.
8. Build and device tests for each milestone before expanding content.

## Acceptance criteria

- All 50 stage definitions load deterministically and have valid room connections.
- Xingxing appears only at levels 6, 12, 18, 24, 30, 36, 42, and 50.
- Each world finale unlocks exactly one pet and records it in saved progress.
- Berthu remains available throughout the campaign.
- Every hazard has warning, active, impact, and recovery behaviour with one-shot collision damage.
- No softlocks in room transitions, ladders, gates, checkpoints, or boss encounters.
- Audio does not overlap endlessly or trigger repeatedly on every frame.
- Dashboard, RAM/temperature UI, and cooldown-related functions still work after game changes.
- A debug APK builds and the target stages are tested on a physical Android device.
