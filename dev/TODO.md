# Tarbound: saved for later

## 0.67.0-alpha: the Steward (2026-10-28) - test in game
- Headless: 30/30 (keeps 10 sticks and tops them up after 6 are taken; watches a furnace -> stonecutter order itself
  with no Shade; picked up with list and tuning; can't be hurt). Also: composter playtest given 128 seeds (it was flaky).
- Not seen yet (no client here): the model and glow in game, the screen (layout, search, scroll, amount box), JEI and
  EMI drag onto rows, R/U on rows and the craftables. Check the head turns but the body stays put, and the lower hands
  move only while it has work.
- Next asked for: radio crafting grid auto-queue of missing craftable ingredients (AE2-style; open question: deliver
  to storage or the player?), Spirit fan run on encased fans.

## 0.66.1-alpha: big mechanical crafting patterns (2026-10-28) - test in game
- Headless: 27/27 (folding test; crafter grid tests unchanged).
- In game: write a Crushing Wheel or Extendo Grip contract from JEI and from EMI: the lectern shows 3-4 inputs with
  counts, and the Reliquary fills every cell of the grid. A recipe with 10+ different ingredients shows the refusal tooltip.
- Next: Steward (requester spirit), in progress.

## 0.66.0-alpha: Create Mechanical Crafter grids (2026-10-28) - test in game
- Headless with Create 6.0.11: 26/26 (3x5 Extendo Grip grid with gaps; 2x2 lopsided no-mirror recipe facing the other
  way, which would get thrown out if laid wrong). Also passes without Create (tests skip).
- In game: write the contract from JEI and from EMI (Extendo Grip, Potato Cannon); a grid in Kragnoth's pack (Create 6.0.10).
- Also fixed: the radio menu crashed for NeoForge FakePlayers (their connection has no channel to ask).

## 0.65.0-alpha: stonecutter + composter stations, job waiting reasons (2026-10-28) - test in game
- Headless: 24/24 playtests pass (stonecutter job, order through a stonecutter, composter, waiting reasons).
- In game: write a stonecutter contract at the lectern from JEI and from EMI (stone -> slabs): the contract's output
  count is 2 and the Reliquary on a stonecutter runs it. Composter with no contract eats seeds, not logs.
- Hover a waiting job: "Short of 1 x Stone" + "Being made: the Stone job". A refused machine load shows its reason.
- Next asked for: radio crafting grid auto-queues craftable missing ingredients (AE2-style, click combo TBD);
  Mechanical Crafter grid support (map pattern to the crafter grid); Spirit fan run on encased fans.

## 0.64.6-alpha: headless playtests (2026-10-26)
- `./gradlew runGameTestServer` passes 20/20 headless (see README "Playtests"). Found and fixed: the radio's menu sent
  its screen payloads to players without Tarbound's channel (fake/mock players) and crashed the request; it now skips them.
- Not covered headless: screens and client sync, JEI/EMI transfer, worldgen placement (datapack loads clean), real
  chunk unloading, multiplayer, other mods' machines.

## 0.64.5-alpha: catalysts drain back (2026-10-24) - test
- Basin recipe ghast tear + powdered obsidian + 250 mB lava -> crying obsidian + ghast tear: after each mix, BOTH
  the crying obsidian and the tear come back to storage; the next op loads a tear again and runs.
Cause: the drain ledger protected "1 ghast tear we loaded" for as long as any tear sat in the basin, so the tear the
recipe handed back (in the output slot) never drained.

## 0.64.4-alpha: Create basin loads (2026-10-24) - test
- Mixer + basin contract with two different items + a fluid (ghast tear + powdered obsidian + 250 mB lava): loads and mixes.
- A basin recipe with two of the same item listed separately: both end up in one basin slot, op runs.
- Furnaces/other machines still load as before (the in-order rule only kicks in where a plain slot check was refused).
Cause (from Kragnoth's trace): Create's BasinInventory only takes an item into its FIRST empty slot, so planning the 2nd
input into slot 1 (while slot 0 was still empty) was refused every pass.

## 0.64.3-alpha: trace log (2026-10-24) - test
- `/tarbound trace` toggles; logs/tarbound-debug.log gets: scan tank list, load OK/REFUSED lines (with machine tank
  dump), pool tank drains, job waiting/no-machine reasons, transient push/pull fluid lines.
- Kragnoth's open bug: lava not pushed into a Create tank by a push Transient Antenna, and fluid recipes into a basin not
  loading. Get his trace and read it.

## 0.64.2-alpha: push antenna says why it's stuck (2026-10-24) - test
- Push antenna with a lava bucket filter on a Create tank: right-click with an empty hand. If nothing moves, chat says why
  (no tank on that side / tank already in the pool / no lava in the pool's tanks / tank refuses). Report which one shows.

## 0.64.1-alpha: transient fluid filter feedback (2026-10-24) - test
- Shift-right-click a push Transient Antenna with a water/lava/modded bucket: message reads "Pushes: Water (fluid, from the pool's tanks)", bucket isn't poured.
- Target tank (not touching the tar) fills from a pool tank; plain right-click status shows the fluid too.

## 0.64.0-alpha: tar that swallows (2026-10-23) - test
- Drop a stack into a radio's tar: it slows, bubbles, and after ~2s gulps into storage (sound, item gone, count up in the radio).
- Mob farm: zombies dying in tar - drops get swallowed, radio Spirit goes up per death.
- Storage full (or only Scry antennas): item hops back up with a spit, retries every ~5s, never vanishes early.
- Reliquary with the hoard on near the tar: items inside its radius float on untouched.
- Wild seep tar: items sink and sit; nothing swallowed.
- Client config tarGulpSound=false mutes it; tarSwallowParticles=false stops the bubbles.

## 0.63.0-alpha: Weakened Blaze model (2026-10-23) - test
- Weakened Blazes (spawn egg, or a scorched spawner) draw the new model and texture, not the tinted vanilla blaze.
- Core and wisps glow and gutter; the body darkens with the world's light. No missing-texture or crash on spawn.
- Looks right in Jade's/JEI's entity view and with Excalibur on (the texture is Tarbound's own, so packs replace it).

## 0.62.0-alpha: Weeping Seep fixes (2026-10-23) - test
- Crushing wheels (or millstone) with a cobblestone -> gravel contract, no cobblestone in storage, seep on the tar:
  ordering 64 gravel runs, Spirit drops 2 per cobblestone, no cobblestone ever shows up in storage.
- A contract that loads 64 cobblestone per operation: loads within ~4 passes and keeps going.
- A multi-step order with seep filler (e.g. something made from gravel): starts (no "not enough"), the Shade holds
  only what storage had, the seep makes the rest as the jobs load.

## 0.61.0-alpha: cast-iron antennas (2026-10-23) - test
- All antenna stages and the Transient Antenna show dark cast iron (no missing-texture purple/black); break particles are iron too.
- Mode lamps still gold (Scry) and amethyst (Tithe); Commune tips are dark iron knobs.
- Classic Cubes pack still uses its own vanilla-iron antennas (unchanged on purpose).

## 0.60.0-alpha: lore structures (2026-10-23) - test
- `/locate structure tarbound:wayside_shrine`, `tarbound:drowned_radio`, `tarbound:charnel_crypt` all find one; no worldgen errors in the log.
- Shrine: sits on the ground (no floating, no buried floor), Reliquary on the furnace, barrel loot, advancement on entry.
- Drowned radio: tar pool flush with the ground and held in by its bank (no tar spilling), mast standing on the set, chest on the bank.
- Crypt: hatch flush with the ground at the candle posts, ladder reaches the floor, room intact; on a slope the hatch may sit a block high/low.
- Each advancement shows under "Someone Was Here". Books read right.
- Rarity feels right (shrine 36-chunk spacing, radio 30, crypt 56); tune in worldgen/structure_set.

## 0.59.1-alpha: recipe pass (2026-10-23) - test
- JEI/EMI: no tar-soaked planks recipe from planks; log -> 4 planks still there. Tar Antenna recipe shows the Weakened Soul.
- Antenna advancement still unlocks on crafting one.

## 0.59.0-alpha: release fixes (2026-10-23) - test
- Game starts with no access transformer error; a furnace under a Reliquary still stays lit with no fuel (Spirit Fire).
- Antenna settings screen still changes mode and priority. In a claimed chunk (FTB Chunks / OPAC) a non-member can't open or change it.
- Hopper or pipe on a Reliquary over a furnace (Reliquary on top): fills the input slot only, can't pull from it.
  Same on the side: fuel slot only. Machine Extender and Entangled chains still pass through.
- Athanor under a Reliquary with Vitae in the pool: pool Vitae drops 1:1 with what the tank takes, no Spirit spent
  while the pool has some. Altar craft with pool Vitae: still ~50 Spirit + ~500 pool mB for a blank slate.

## 0.58.1-alpha: hoard radius 4 by default (2026-10-23) - test
- New world: hoard message says 4 blocks; magnets blocked out to 4. An existing world's serverconfig keeps its old 3 until edited.

## 0.58.0-alpha: the spirits' hoard, villages removed (2026-10-23) - test
- Gold nugget on a Reliquary: nugget used up, "The spirits hoard..." message, soul wisps drift inward and gold glints at the book.
- A magnet mod (e.g. Simple Magnets) can't pull drops within the hoard radius; a hopper still picks them up.
- Nugget again: "The spirits let go", no nugget spent; magnets pull again. Creative: no nugget spent.
- Reliquary that had the ward on before 0.58 still has it on (save key unchanged).
- Redstone dust on a Reliquary does nothing now.
- New world in plains: no reinforced villages; cabins still generate; no worldgen/datapack errors in the log.

## 0.57.0-alpha: Reliquary Vitae for NeoVitae (2026-10-22) - test
- Altar job (blank slate): no blood in the altar, Reliquary on it; craft finishes, radio Spirit drops ~100.
- Same with Essentia Vitae in the pool: pool Vitae drops ~500, Spirit ~50.
- Orb in the altar: nothing fed. Idle altar: nothing fed.
- Athanor under a Reliquary: input tank sits at 1,000 mB.
- Log has no "altar changed shape" warning.

## 0.56.0-alpha: Weeping Seep (2026-10-22) - test
- Seep touching tar/antenna; order torches with no cobblestone/sticks crafted from logs: cobble comes from the seep, Spirit drops 2 each.
- Not in the board/pool counts; player can't take cobblestone out; standing contracts don't use it.
- Contract that makes cobblestone (e.g. a generator) on the radio: seep stops supplying cobblestone.
- Out of Spirit: job waits (HUNGRY) instead of conjuring.

## Create compat: souls from soul sand (planned, 2026-10-10)
- Create crushing and/or milling recipe: soul sand -> "souls" (new item).
- Icon: like the soul particle that floats above soul sand.
- Souls are a Spirit offering, fed to the radio like candles: add to `data/tarbound/data_maps/item/spirit_offerings.json`.
- Recipes behind `neoforge:conditions` `mod_loaded: create`, like the plant-wax mixing recipe.


## Recursive crafting (run 1 done in 0.30.0: planner, preview, sub-crafts queued as jobs)
Agreed with Kragnoth: hard reservations; concurrency fits future builds (maxJobs now, a buildable
crafting-capacity unlock later); depth capped so a weak server can't be locked (maxPlanDepth, maxPlanNodes,
plus a fixed work budget in CraftPlanner).
- Run 2 (done in 0.31.0): Shades. A multi-step order needs a free Shade tuned to the radio; the plan's
  stored inputs move into it, sub-craft outputs are delivered to it, order jobs borrow from it each pass
  (lend/reclaim in ScryingFontBlockEntity), cancel cascades, sub-crafts add up to totalOps/4 extra ops
  when short. Shared Shades, any mob head for now.
  - Later: "strong souls" (harder than weakened souls) for better Shades; job list doesn't group an order's jobs yet.
  - Fluids (done in 0.37.0): fluid-only contracts are ordered by the bucket and tracked in mB; fluid sub-crafts
    deliver to the Shade. Still open: a contract making items AND fluid tracks its item, so its fluid stays in the
    pool rather than going to the Shade.
- Run 3 (0.53.0): job-done notices (chat + chime to whoever asked, config jobNotices), orders grouped in the job list
  (one header per order, click to open its steps), planner picks between equally good contracts by idle machines,
  fluid made alongside an item goes to the order's Shade. The crafting-capacity block is the Votive Rack (0.54.0).

## Pre-Nether Weakened Blaze ritual (spitballed 2026-10-12, paused)
Tar seeps (0.32) are the only pre-Nether tar, so build on them plus cabin lore.
1. Kindling the seep (favourite): candle ring around a tar pool at night, offering thrown in (coal block + bone?),
   light the tar with flint and steel; it burns off and a Weakened Blaze rises from the smoke. Burned tar is
   consumed (limits a pre-Nether soul loop). Field notes page 6 hints it ("Knocked a candle into the seep...").
2. Storm call: lightning rod in a tar pool, struck by lightning -> blaze. Weather-gated.
3. Taken by the tar: a mob dying in burning tar has a small chance to rise as a Weakened Blaze (cap it).
4. Smoldering Brazier block: cauldron + tar-soaked planks + iron, fill with tar, feed fuel at night.
Suggested: 1 as the main ritual, 3 as a rare side effect.
Open questions: is all burned tar consumed or just the centre; plain offering vs a cabin-only "Researcher's Ember";
summoned blaze hostile, or bound/passive for a while.

## Weakened Blaze: its own model (built 2026-10-15 on a 0.42.0 side branch, merged into 0.63.0)
`WeakenedBlazeModel` (client/): 6 rods in two lopsided, stuttering orbits (two snapped, with loose tips), slouched
tilted head + a chipped-off fragment, an ember core, 4 sputtering flame wisps, 3 drifting shards. Only the core and wisps
draw full-bright and flicker; the body uses `BODY_LIGHT` in `WeakenedBlazeRenderer`. The flame flares while it's shooting.
Texture: `python3 dev/make_weakened_blaze_texture.py` writes `textures/entity/weakened_blaze.png` (64x32) and
`dev/weakened_blaze_uv_guide.png`. Repaint the PNG in Blockbench/any editor, keeping the layout; re-running the script
overwrites hand-painting. Things to eyeball in game: body brightness (`BODY_LIGHT`), glow flicker rates
(`coreGlow`/`flameGlow` in setupAnim), and whether it sits too high for the 0.6x1.8 hitbox.

## Machine compat follow-ups (from the 0.42-0.44 runs, 2026-10-20)
Tested in game by Kragnoth (2026-10-22): machine loading/draining across the tested mods, the 0.50 ledger drain,
scan reuse, outlines/comparators, guide-book fix, returned-orb planning and lectern chains all work. Done:
- Mekanism machine: load, drain items and fluid outputs (MachineAccess reflection adapter).
- IE coke oven (any block) and metal press (input block, output face into tar-touching storage).
- EnderIO / Oritech output slots drain; Oritech full input slot is NOT drained.
- 0.43 scan reuse: chest placed/broken next to tar mid-job, Reliquary wrench turn, Entangled re-bind under a Reliquary.
- 0.44: crouch-look outlines on a radio and a Reliquary; comparator against a Reliquary.
Deferred ideas, not started:
- Stamp contracts with the machine type they were written for; pause with a warning if the Reliquary's machine changes.
- Reliquary candles gutter when facing nothing usable / last load refused (new blockstate property: allowed).
- `reliquaryWhitelist` config flipping `reliquary_ignores` into an allow-list (Entangled pattern).
- Remote ("spirit-tethered") Reliquary bound to a distant machine, Spirit cost by distance.
- Mekanism chemical tanks (gases, slurries) are not moved.
- Loading through machine faces was rejected (face slot -> physical slot mapping unknowable while empty).

## 0.50.0-alpha: ledger-first drain (2026-10-20)
Fallback: git tag `v0.44.1` (zip tarbound-0.44.1). 0.44.1 ignores the new `loaded` key, so worlds go back cleanly.
Removed: contract made/used/both product rules, the face drain pass, the generic read-only fallback.
Tested 2026-10-22: works.
