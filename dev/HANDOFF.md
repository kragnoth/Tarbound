# Tarbound: handoff notes (current to 0.67.0-alpha, 2026-10-23)

Read this first in a new conversation. It's written for the next Claude (or person) picking up the mod,
so it favours facts over polish. The repo's own docs go deeper:
- `README.md`: the player and pack-maker guide, kept current every release. Read it for what each feature does.
- `dev/SAVE_FORMAT.md`: the save-format freeze. Registry IDs, data components, blockstate values, save keys,
  tags and data maps that must never be renamed. Every new ID is added there.
- `dev/TODO.md`: test checklists for untested releases (top), plus planned and deferred ideas.
- `git log`: one commit per release since 0.20.5 (76 commits), with descriptive messages. `git show <hash>` is the
  quickest way to see how a feature was built.

## What it is
Tarbound is a NeoForge **1.21.1** mod (Java 21, package `com.tarbound`, mod id `tarbound`, MIT, author
**Kragnoth**). It's a low-tech, spooky counterpart to Refined Storage and AE2. Tar pools and machine spirits
are the theme. It's meant to feel like its own mod, not AE2 with a skin. Released on CurseForge since 0.24.3.
The 1.21.1 line is the main work; a 26.1.2 port exists as an on-hold toy.

Core loop:
- **Tar** links touching storage into one pool. Tar-soaked logs/planks are conduits.
- The **Scrying Font** (a haunted radio) is the pool's screen: grid, crafting, fluids, jobs, orders.
- **Tar Antennas** (1-3 high, 3/6/9 reach, Commune/Scry/Tithe modes, priority) link storage without tar.
  **Transient Antennas** push or pull items, fluid and FE.
- **Reliquaries** are tuned to a radio and bind the machine they face. They hold Spirit Contracts, feed FE, Spirit
  Fire (fuel-free furnaces) and, since 0.57, NeoVitae Vitae. The **Ossuary** shows every Reliquary's contracts.
- **Spirit Contracts** are written at the **Scribing Lectern** (a pattern terminal with JEI/EMI ghost slots).
  Contract kinds: jobs, crafting stations (a Reliquary on a crafting table) and standing (tag) contracts.
- **Orders** plan recursively (`CraftPlanner`), get a **Shade** (a soul vessel with ranks 1-5 from Votive Skulls)
  for hard reservations, and run sub-crafts as jobs.
- **Spirit** is the automation currency. It's held by each radio and fed with candles (200) or Weakened Souls
  (100), set by the data map `tarbound:spirit_offerings`. Storage access is always free.
- **Votive Racks** (0.54) near a radio raise its job, plan-depth and plan-node limits.
- **Weeping Seep** (0.56) makes cheap filler for jobs and orders only, paid in Spirit (data map
  `tarbound:seep_yields`). The **Bottomless Seep** (0.55) is the creative test source. Create's creative crate is
  safe as a source too.
- Worldgen lore: tar seeps, the researcher's, trapper and burned cabins, and (0.60) wayside shrines, drowned radios
  and charnel crypts. (The reinforced villages of 0.51-0.52
  were removed in 0.58: off-theme, never released.)

## Source map (src/main/java/com/tarbound, ~25k lines)
- `network/TarNetwork.java`: the pool. `scan()` flood-fills tar and antennas into `BoundInventory`s: storage,
  machines, tanks and roles. It handles counts (`countContents`, `countWithFills`, `countForJobs`), `extract`,
  `extractForJob` (Weeping Seep), `insert` and fluids. Counts are cached per tick (`freshTick()`).
- `network/NetworkChanges.java`: radios keep their scan until a world change count moves, plus watchers and a
  rescan every `networkRescanSeconds`.
- `network/BoundInventory.java` (probing, roles, `bottomless()`), `MachineAccess.java` (reflection adapters for
  tech mods: Mekanism, IE and so on), `MachineLoader.java` (loads contract inputs into a machine from a
  `Source`; `pool()` / `forJobs()`), `SpiritFire.java`, `VitaeFeed.java` (NeoVitae, by reflection),
  `AntiMagnet.java`, the registries (`RadioRegistry`, `ReliquaryRegistry`, ...).
- `block/ScryingFontBlockEntity.java` (1.7k lines): the radio's brain. Spirit store (`pay`, `canPay`, `spirit`),
  battery, jobs and orders, the drain ledger (`loadedInto`, saved as `loaded`: since 0.50 it drains only what it
  loaded), Shade lending, Votive Racks (`tendRacks`, `jobLimit`, `planDepth`), job notices.
- `block/ReliquaryBlockEntity.java`: contract slots (candles unlock up to 18), targets, power, Spirit Fire,
  Vitae feed, FE and Vitae credit.
- `contract/`: `ContractData` (the record plus codec; `returned` = inputs every craft hands back, such as Blood
  Orbs), `ContractJob`, `CraftPlanner` (plans, sources, idle-aware tie-break, limits), `CraftingContracts`
  (station crafts), `StandingOrder`, `ContractDraft`.
- `menu/ScryingFontMenu.java` + `client/ScryingFontScreen.java` (2.5k lines): the radio's UI. `net/TrayPayload`
  syncs machines and jobs to the client. **Bump `VERSION` in `net/TarboundPayloads.java` whenever a payload's wire
  format changes** (currently "20").
- `compat/`: JEI, EMI, Jade, Ponder (9 scenes; generators in `dev/`), Crafting Tweaks, SearchSync. Create recipes
  are data-only, gated with `neoforge:conditions`.
- `worldgen/`: `DryLandStructure`. The structure NBTs come from
  `dev/make_*.py`.
- `config/TarboundConfig.java`: every server and client constant. Add new knobs here, with comments.

## Conventions Kragnoth works by
- **Every change is a release**: bump `mod_version` in `gradle.properties` (currently `0.67.0-alpha`; the
  `-alpha` line started at 0.50, and git tag `v0.44.1` is the stable fallback), update README, SAVE_FORMAT
  (additions) and TODO (a short in-game test list), commit, then zip the repo **without** `build/`, `.gradle/`,
  `run/` and `.git/` as `tarbound-<version>.zip` and send it. Kragnoth builds and tests it himself and reports back.
  - zip: `cd <parent> && zip -qr tarbound-X.zip tarbound -x "tarbound/build/*" "tarbound/.gradle/*" "tarbound/run/*" "tarbound/.git/*"`
- **Never rename save-facing IDs.** See `dev/SAVE_FORMAT.md`. New contract fields must be optional with defaults.
- Validate all JSON after editing resources (python `json.load` over `src/main/resources/**/*.json`).
- Code that's no longer used is deleted (since 0.59; git history keeps it). The old commented-out rule is retired.
- Optional mods are **soft**: reflection or class-name checks, data gated by `mod_loaded` or `item_exists`
  conditions, `"required": false` tag entries. Tarbound must build and run without any of them.
- Kragnoth's style: direct, technical, high signal. He likes the reasoning behind a recommendation and Occam's-razor /
  KISS checks ("is there a crystal clear path we skipped?"). Ask before big design calls; once he says "go with
  your suggestion", just build it. Player-facing text (lang, README) is plain gamer speak that doesn't sound like an
  LLM. He types fast; read typos generously ("voltive" = votive).
- Balance instincts he's stated: don't negate whole parts of the game (the Weeping Seep list stops at
  spider eyes, sugar cane, stone and the like); things that make stuff cost Spirit; no filters ("if they want
  filters, they'll push into tech"); depth and size caps so a weak server can't be locked.

## Building and playtesting (0.64.6+)
Since 2026-10-26 the sandbox can reach Gradle, NeoForge's maven, BlameJared (JEI), Modrinth and CreateMod; only
TerraformersMC (EMI) is blocked. So real builds work:
`./gradlew --no-daemon -Pemi_from_modrinth=true -Pdev_recipe_viewer=none -Pdev_jade=false build` (first run ~4 min to
set up Minecraft), and `... runGameTestServer` runs the headless playtests (with Create: jar in `run/mods`,
`-Pneo_version=21.1.265`) (`com.tarbound.gametest.TarboundPlaytests`,
20 tests, ~1 min). Delete `run/world` and `run/gametestworld` between server runs, and don't `pkill -f` a pattern that
matches your own shell command. Game tests place their template one block above the origin: the playground's floor is
test y 1-2 (tar trench at y 2, machines at y 3).

## Compile check without Gradle (fallback)
If Gradle and Maven aren't reachable from the sandbox, releases are checked with plain `javac` against
decompiled Minecraft, NeoForge source, JEI/Jade/Ponder APIs and a few hand stubs. The handoff zip has
`compile-kit/` with `setup.sh` (unpacks the deps) and `check.sh` (runs javac and prints only errors in Tarbound
files). Thousands of errors inside the dependency sources are normal (missing gson, DFU etc.); only lines that
start with the Tarbound source path matter. Stubs for DFU (`Codec`, `RecordCodecBuilder` with `optionalFieldOf`
and 6-field groups) live in `compile-kit/stubs`; extend them when a new API is missing.

## Recent releases (newest first; full list in git log)
- **0.67.0-alpha**: the Steward (`entity/Steward`, stationary PathfinderMob, MISC category, invulnerable except creative
  players and bypass-invulnerability damage). Implements `OrderVessel`, so multi-step crafts run as an order it holds
  itself (`CraftOrder.vesselEntity` = its UUID; the font finds it with `ServerLevel.getEntity` and waits for
  `areEntitiesLoaded` before calling it gone). `keepStock()` every 20 ticks: counts, skips items any job/order already
  makes, plans at most one thing per round (ops halved until the plan completes), single step -> `addJob`, more ->
  `startOrder(null, this, ...)`; failed targets back off 100 ticks. Item `StewardItem` (tuning + `steward_targets`
  component). Menu `StewardMenu` + `StewardScreen` (rows are not slots; edits go by `StewardEditPayload`, state by
  `StewardStatePayload`; payload VERSION 21). JEI ghost handler + clickable ingredients, EMI drag-drop + stack provider.
  Model `client/StewardModel` (64x64, every cube a root child, `dev/make_steward_texture.py` + `dev/steward_uv_guide.png`),
  glow layer = `RenderType.eyes`. Playtests: `stewardKeepsStock`, `stewardWatchesItsOrder`, `stewardPickedUp`.
- **0.66.1-alpha**: `ContractDraft.fitInputs` folds JEI/EMI ghosts that are the same item (and same alternatives) into
  counted inputs, only when a recipe has more than `INPUTS` (9) of them; null (transfer refused, lang
  `gui.tarbound.lectern.too_many_inputs`) if still more than 9. Before, mechanical crafting patterns over 9 cells were
  cut off at 9. Also groundwork for the Steward: `contract/OrderVessel` (orders can be held by a Shade or an entity,
  `CraftOrder.vesselEntity`), `ScryingFontBlockEntity.planSources`, `entity/StockTarget` (unused yet).
- **0.66.0-alpha**: Create Mechanical Crafter grids (`compat/create/CrafterGrid`, all reflection): walks the crafter chain,
  lays the grid out in Create's frame (x = opposite of a left-pointing crafter's target, y up), fits the recipe pattern
  (mirrored only if `acceptsMirrored`), fills, then `checkCompletedRecipe(true)`. Drain skips crafters. Playtests use
  NeoForge FakePlayers (`requester()`) now: mock players in the level break mods like Create that sync to every player.
- **0.65.0-alpha**: stations beyond the crafting table (`contract/StationContracts`): stonecutter (matches a one-input
  contract to a stonecutting recipe, no recipe id) and composter (standing tags or `compost_fodder`). Jobs note what they
  wait for (`ContractJob.waitItem/waitFluid/waitFeeder/refusal`, sent as `TrayPayload.Wait`; payload VERSION 20).
- **0.64.0-alpha**: tar that swallows (`fluid/TarSwallow`): items in a radio's tar sink `tarSwallowTicks` then
  `TarNetwork.insert`; leftovers spat up with a retry; skips hoard-pinned (`PreventRemoteMovement`) and pickup-delayed
  items. Sounds `block.tar.gulp`/`spit` reuse vanilla events via sounds.json `type: event`.
- **0.63.0-alpha**: the Weakened Blaze's own model (`WeakenedBlazeModel`, texture `textures/entity/weakened_blaze.png`,
  `dev/make_weakened_blaze_texture.py` + UV guide), merged from Kragnoth's 0.42.0 side branch (which was 0.41.0 plus this
  model only). Replaces the tinted vanilla blaze.
- **0.62.0-alpha**: Weeping Seep fixes from Kragnoth's test. (1) Multi-step orders failed to start ("not enough") when the
  plan counted on seep items: `startOrder` set aside only real storage, so seep-made items now don't count as short
  (`TarNetwork.seepMakes`). (2) The per-pass budget lived on the scan and could never cover one big load (64/op):
  it's now a reserve on the radio (`seepReserve`, refilled in `refillSeeps` each pass, banks 4 passes or a stack),
  and `extractForJob` makes the whole shortfall or nothing, so no paid seep items leak into storage.
- **0.61.0-alpha**: antennas (Tar and Transient) use Tarbound's own worn cast-iron textures (`antenna_iron`,
  `antenna_iron_dark` from `dev/make_antenna_textures.py`) instead of vanilla iron block and anvil. Temporary art.
- **0.60.0-alpha**: lore structures, all `dry_land` + one-piece jigsaw, built by `dev/make_lore_structures.py`:
  wayside shrine (plains/meadow/forest/taiga, start height 0, beard_thin), drowned radio (swamps, start height -2 so the
  tar pool is flush with the ground, no terrain adaptation), charnel crypt (same biomes as the shrine, start height
  -10, ladder shaft at the footprint centre so the hatch lands where the jigsaw measures the ground). Each has a loot
  table with a lore book and an advancement under researcher_cabin. No radio is placed in the world (no ticking
  worldgen radios); the drowned set is vanilla blocks plus a real broadcast mast.
- **0.59.1-alpha**: removed the 8-planks + tar bucket recipe (gave 8 where 8 logs give 32 planks: a tar trap);
  Tar Antenna recipe adds a Weakened Soul (` R / WA / IBI`), the soul does the listening.
- **0.59.0-alpha**: release fixes. Antenna settings only for the antenna the server saw the player open (so claim
  mods' click checks apply) plus `mayInteract`; Reliquary item pass-through is the machine's own handler for the face
  it covers (no longer every slot); Vitae pool doubling for altar crafts only (stations use pool Vitae 1:1); Spirit
  Fire through an access transformer (`META-INF/accesstransformer.cfg`, declared in mods.toml; the compile kit applies
  it too); per-player packet maps cleared on logout; orphaned commented code and the dead tarBlocks/truncated scan
  fields deleted.
- **0.58.1-alpha**: hoard radius default 3 → 4 (`antiMagnetRadius`, 1-16), wide enough for a Create fan run.
- **0.58.0-alpha**: the anti-magnet ward is now "the spirits' hoard" (greedy spirits): toggled with a gold nugget
  (spent when it starts), inward soul wisps and a gold glint; save key `anti_magnet` and config keys unchanged.
  Reinforced villages removed outright (never released; off-theme). Tarbound no longer references Rust & Rebar.
- **0.57.0-alpha**: Reliquaries feed Essentia Vitae to a NeoVitae Ara Vitae while it crafts (what the craft still
  needs, into the main tank via `addSacrificeEV`, up to 500/s, never orbs). They also top up `tarbound:vitae_fed`
  stations (the Athanor) to 1,000 mB. 10 Vitae per Spirit; pool Vitae is matched 1:1 by bought Vitae, so it
  doubles. Known exploit: pumping the Athanor's input tank back into the pool loops the doubling (left in;
  the fix would be pool doubling for altar crafts only).
- **0.56.0-alpha**: Weeping Seep. Detected in the scan next to tar or an antenna; counts are added only through
  `countForJobs` / `extractForJob` / `MachineLoader.forJobs` (jobs, orders, stations, order planning, the
  client preview through `TrayPayload.weepingSeep`). Never for players, standing contracts or the board. Skips
  anything a contract on that radio makes; `weepingSeepPerPass` items per seep per pass.
- **0.55**: Bottomless Seep (creative, BE with endless item and fluid handlers for raw inputs) and Create creative
  crates bound as Scry so they're never inserted into.
- **0.54**: Votive Rack (lit with flint; skull raises reach 3→5; nearest-radio rule; Spirit per pass while
  working).
- **0.53**: job-done notices, grouped orders, idle-aware planning, side fluids to the Shade.
- **0.52/0.51**: trapper and burned cabins (plus reinforced villages, removed in 0.58).
- **0.50.x**: ledger-first drain; guide-book (Modonomicon component) fix; handed-back inputs (Blood Orb) counted
  once; lectern JEI/EMI R/U on ghost slots, back history, contract marks.
- **0.42-0.44**: machine compat runs (Mekanism, IE, EnderIO, Oritech, Modern Dynamics, Pipez, PowerfulJS,
  Entangled), scan reuse, outlines, comparators.

## Open / next ideas (details in dev/TODO.md)
- Untested: 0.56, 0.57 and 0.58 (checklists at the top of TODO).
- Release-readiness still open: NeoForge range floor (Kragnoth: not yet), the blaze-burner soul loop (design call),
  Essence/battery and Weakened Blaze decisions. The rest of the 0.57 review list was done in 0.59.
- Contract machine-type stamping; Reliquary candles guttering when blocked; a `reliquary_ignores` allow-list mode;
  remote Reliquaries; Mekanism chemicals.
- Create: souls from soul sand as a Spirit offering.
- The pre-Nether Weakened Blaze ritual (paused). (Its own model landed in 0.63.)
- Lore extras (lone masts, failing lights) were offered; Kragnoth called worldbuilding "advanced enough" for now.
- Older deferred exploits: pipes on a Reliquary reach every machine slot; the antenna settings packet ignores claim
  protection; Weakened Blaze spawner → Blaze Burner → crusher gives unlimited souls.

## Kragnoth's related mods (same author, sometimes cross-referenced)
Rust & Rebar (`rustandrebar`, decorative rusted industrial), plus others not tied to Tarbound. Since 0.58 Tarbound
doesn't reference R&R at all.
