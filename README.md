# Tarbound

Low-tech storage and autocrafting for **NeoForge 1.21.1**. Pour tar, tune a haunted radio, and let bound spirits run your machines.

By Kragnoth. MIT licensed (see `LICENSE`).

---

## Quick start

1. Craft a **Bucket of Tar** and pour it so it touches a few chests or barrels.
2. Place a **Scrying Font** (the radio) within 3 blocks of the tar.
3. Open the radio. Everything in those chests is now in one searchable list.
4. Put a crafting table next to the tar to unlock the radio's crafting grid.
5. Feed the radio a few **candles** so its spirits can run your machines later.

Got Create installed? Hold **W** over the radio and Ponder will walk you through the rest.

---

## Tar that swallows
Drop something into tar that a radio holds and the tar takes it: the item slows down, sinks for about two seconds, and with a gulp it's in that radio's storage. It's storage, so it's free.
- **Mob farms:** kill mobs over (or in) a radio's tar and their drops swallow themselves, and the deaths feed the radio Spirit as well.
- **Full storage:** whatever doesn't fit gets spat back up, and the tar tries again a few seconds later. Nothing is ever deleted (items still despawn after 5 minutes like anywhere else).
- **Left alone:** items a Reliquary's hoard is guarding (so a fan line can cross tar), items another mod has pinned, and items that can't be picked up yet (a thrown item waits its usual moment first).
- **Wild tar** (seeps no radio is linked to) only drags items down.
- Config: `tar.tarSwallows`, `tarSwallowTicks` (40), `tarSwallowRetryTicks` (100); client: `tarSwallowParticles`, `tarGulpSound`.

## Out in the world

- **Tar seeps** form in swamps, badlands and deserts.
- **Abandoned researcher's cabins** turn up rarely in forests: a mossy, half-collapsed spruce shack with a two-stage antenna on the roof, a little antenna on the barrel by the bed, and a desk. The barrel and chest hold paper, blank contracts, candles, tar-soaked planks and maybe the researcher's field notes. Biome tag `tarbound:has_researcher_cabin` (forests); remove or tune it with a datapack (`worldgen/structure_set/researcher_cabin.json` sets how rare). The build is made by `dev/make_researcher_cabin.py`. Cabins only go down on dry ground (never over lakes, so no cabins on ice): `worldgen/structure/researcher_cabin.json` is a `tarbound:dry_land` structure wrapping the jigsaw in `researcher_cabin_layout.json`.
- **More cabins** share the researcher's spot (same rarity, same biomes): a **trapper's cabin** (R.'s neighbour, with a drying rack, snares by a small seep and the *Trap Ledger*), and a **burned cabin** around the tar crater someone tried to set alight (the *Scorched Pages*).
- **Wayside shrines** stand by the roads in plains, meadows, forests and taiga: a little roofed niche where a Reliquary still minds a cold furnace, with a Votive Skull, an unlit Votive Rack and an offering barrel. Leave a coin; the *Wayside Notice* explains. (Biome tag `tarbound:has_wayside_shrine`.)
- **Drowned radios** sink in swamp tar seeps: an old set gone under to its dials, a broadcast mast rising out of the tar, a half-sunk boardwalk, and the operator's chest on the bank with the *Last Broadcast*. (`tarbound:has_drowned_radio`: swamps and mangrove swamps.)
- **Charnel crypts** lie under a mossy hatch ringed by candle posts, in the same biomes as the shrines but rarer. Climb down the ladder into a bone chapel: Votive Racks along the walls, an Ossuary keeping count, and the sexton's chest with the *Charnel Register*. (`tarbound:has_charnel_crypt`.)
- Rarity for all of them is in `worldgen/structure_set/<name>.json`; the builds are made by `dev/make_lore_structures.py`, and every one only goes down on dry ground (`tarbound:dry_land`).

## Classic Cubes look

The mod ships a built-in resource pack, **Tarbound: Classic Cubes** (off by default; turn it on in Options → Resource Packs). It swaps in a full-cube radio, simple post-and-tip antennas and flatter item icons. Its files live in `src/main/resources/resourcepacks/classic_cubes`.

## Advancements

Tarbound has its own advancement tab that walks you through the mod: from your first Weakened Soul to tar, the radio, antennas, Reliquaries, contracts, your first order and a Reliquary with sixteen candles. Stuck on what to do next? Check the tab.

## Blocks and items

| Item | What it's for | Recipe |
|---|---|---|
| **Bucket of Tar** | The black goo that links your storage. Slow and heavy, you can't swim in it, and it spreads like lava (faster in the Nether). Mobs that wade in are poisoned and hurt; players aren't. A mob that dies in a radio's tar feeds that radio Spirit (1 per point of its max health). Items that land in a radio's tar sink and get swallowed into its storage, so a tar-floored mob farm collects its own drops. You can also find it: tar seeps form in swamps, badlands and deserts. | Bucket + 4 Weakened Souls (shapeless) |
| **Tar-Soaked Log / Planks** | Solid wood that carries tar like the fluid does. Run links under floors and through walls; anything touching it counts as touching the tar. Burns twice as long as wood. | 8 logs around a bucket of tar makes 8 (bucket comes back). A log makes 4 planks. |
| **Pitch Cask** | A squat oak cask sealed with tar, holding 8 buckets of any fluid. Fill and empty it with buckets or any fluid container, or with pipes. Next to tar or an antenna it joins the pool like any tank, so a row of casks is one big tank in the radio. Breaking it keeps what's inside (the item shows how much). A cask of tar weeps a drop now and then. | `PIP / P P / PIP`: tar-soaked planks, iron ingot |
| **Scrying Font** | The radio. Your storage screen, crafting grid and job board. | `INI / ATA / III`: iron, note block, amethyst shard, bucket of tar (bucket comes back) |
| **Tar Antenna** | Links storage to the radio without tar. Stacks up to 3 high. | ` R / WA / IBI`: lightning rod, Weakened Soul, amethyst shard, iron, polished blackstone |
| **Reliquary** | Goes on top of a machine so the radio can run it. | Bone block + book + any candle + soul sand or soul soil + bucket of tar (shapeless, bucket comes back) |
| **Scribing Lectern** | A pattern terminal for Spirit Contracts: lay a recipe out in ghost slots (crafting grid, or machine inputs and outputs) and write it. | Lectern + feather + ink sac + bucket of tar (shapeless) |
| **Blank Contract** | Paper for the lectern. A written one crafted alone wipes back to blank. | 3 paper + ink sac + soul sand or soul soil, makes 3 |
| **Spirit Board** | Opens your radio from far away. | ` A / IBI / PPP`: amethyst shard, ink sac, bone, tar-soaked planks |
| **Weakened Soul** | Makes tar, and the radio will eat it (100 Spirit). Dropped by Weakened Blazes. | 8 soul sand + flint and steel (shapeless, the flint and steel comes back a little more worn) |
| **Tallow** | Candle wax. Cows drop 0-1, pigs 0-2, plus Looting. | — |
| **Plant Wax** | Vegan candle wax. Only made with Create: heated mixing of 4 seeds. | — |
| **Ossuary** | A little flight of bone steps covered in candles. Tune it to a radio and it shows every Reliquary on that radio, three to a page, with their contract slots: hand out and take back contracts without walking to each one. Click a name to light the machine up in the world. | `C C / BAB / BBB`: any candle, amethyst shard, bone block |
| **Votive Skull** | A candle burning on a skull. Feed it to a Shade to raise its rank by one (up to rank 5), or set it down as decoration: on the floor (turns 16 ways) or on a wall, drawn with the game's own skeleton skull and candle so resource packs restyle it. Lit it gives a candle's light and flame; an empty hand snuffs it, flint and steel or a fire charge relights it. | Any mob head + a plain candle (shapeless) |
| **Steward** | A spirit bound into a husk of tar, with an observer for a head, that keeps a list of things in stock (see [Stewards](#stewards)). | Observer / 2 Ender Pearls + Shade / 2 Weakened Souls + Tar Bucket (shaped: ` O `, `EHE`, `STS`) |
| **Shade** | A spirit that watches over orders with sub-crafts. Tune it to a radio (shift-right-click), then set it down anywhere loaded. One order at a time; its **rank** sets how big (see below). | Any mob head + Weakened Soul + any candle (shapeless) |
| **Candles** | String over tallow or plant wax makes a candle, same as vanilla's honeycomb candle. | — |

### Getting Weakened Souls
- **Weakened Blazes** drop 3-7 each (Looting adds up to 1). They're blazes worn thin: half the health, half the damage, half the XP.
- **Make a Weakened Blaze spawner:** use flint and steel on any spawner with a blaze in it, like the ones in Nether fortresses. From then on it spawns Weakened Blazes. It costs one point of durability.
- **With Create:**
  - crush or mill a Blaze Burner that has a blaze in it: you get the empty burner back plus 3-7 souls;
  - crush soul sand: 0-3 souls, about 1 on average.
  - an empty Blaze Burner can capture a Weakened Blaze, or fill up from a Weakened Blaze spawner, just like a normal blaze. You get a regular filled Blaze Burner.
- **Without Create or a spawner:** 8 soul sand and a flint and steel make 1 soul.

---

## The radio

### Binding storage
- Any storage block touching the tar joins the pool: chests, barrels, shulker boxes, drawers, Create vaults and so on.
- The radio counts as touching the tar if tar is within 3 blocks of it, diagonals included.
- Connected tar counts as one body, flowing or still, up to 8192 blocks.
- Chutes, belts, funnels and depots from Create never join the pool, so the radio won't grab items mid-flight. Neither do Create's fluid pipes, pumps and valves. (Block tag `tarbound:never_pooled`.)
- Big multiblocks like Create item vaults and Sophisticated Storage controllers only count once, not once per block.
- There's no limit on how much you bind.

### Screen controls

| Where | Do this | Result |
|---|---|---|
| Pool | Left click | Take a stack |
| Pool | Right click | Take half |
| Pool | Shift + click | Send a stack to your inventory. While you hold Shift the list stops shuffling, so you can keep clicking the same spot to empty out one item. |
| Pool, holding an item | Left / right click | Put it all in / put one in |
| Crafting result | Left click | Craft once. Empty grid slots refill from storage. |
| Number on the result slot | Click | Shows up when several recipes match the same ingredients. Click it and pick which one to make; your pick sticks while it still matches (like Polymorph). |
| Crafting result | Right click | Craft up to a full stack onto your cursor |
| Crafting result | Shift + click | Craft up to a stack into your inventory (server config `shiftCraftStacks`) |
| `×` button | Click | Put the grid back into storage |
| Search box | Type | Search by name. Start with `@` to search by mod. Right-click to clear. |
| Sort button | Click | Sort by count, name or mod |
| ◆ button | Click | Show craftable items first |
| ⇄ button | Click | Keep the search box in sync with JEI or EMI (only shows if you have one) |
| View button (left of the grid) | Click | Show items, fluids, or both |
| Candle button | Click | Let the radio burn candles from storage when it runs low |

The crafting grid only works if a crafting table touches the tar or an antenna.

### Fluids
No tank mod? The **Pitch Cask** is Tarbound's own. Tanks work like chests: any tank touching the tar or an antenna joins the pool, with the same modes and priority. Fluids show up in the radio with a shorthand amount (`250` mB, `1k` is one bucket, `1.5k`, `64k`, `2.1M`). Hover one for the exact amount.

| Do this | Result |
|---|---|
| Click a fluid | Fill a bucket and take it. Uses the empty bucket in your hand, or one from storage. |
| Shift + click a fluid | Same, but the bucket goes to your inventory |
| Click the pool holding a filled bucket | Pour it into the tanks (not in items-only view) |

Fluids come first in the list. Tanks on a machine under a Reliquary belong to the machine, not the pool.

### Moving a radio
Break it and place it again in the same dimension. It keeps its candles, and everything tuned to it follows it to the new spot. A freshly crafted radio is a new radio, though: things tuned to the old one will need re-tuning.

---


### Searching
The search box understands the recipe viewers' prefixes. With JEI installed, JEI itself decides what matches (keep the ⇄ sync on), so it works exactly like JEI's box: `@mod`, `#tooltip`, `$tag`, `^color`, `&id`, `-` to exclude, `|` for "or". Without JEI the radio does the same itself; with only EMI installed, `#` means tag and `$` tooltip, as in EMI.
## Antennas

Antennas let the radio reach storage where running tar is a pain.

- **Tune:** shift-right-click the radio with antennas in hand. The whole stack gets tuned.
- **Place:** on any side of any solid block. Whatever touches the antenna counts as touching the tar: chests join the pool, a crafting table unlocks crafting, tar joins the tar body.
- **Stack:** click an antenna with another to grow it, up to 3 pieces:
  - 1 piece: rabbit ears, 3 blocks of reach
  - 2 pieces: rooftop aerial, 6 blocks
  - 3 pieces: broadcast mast (a taller roof mast with a stacked aerial), 9 blocks
- **Range:** an antenna works if the radio can reach it, either directly or through other antennas tuned to the same radio. Two antennas link if they're within the bigger of their two reaches. Distance is measured from the mounted end.
- Antennas on bare ground are fine. They just relay.
- If some tuned antennas can't be reached, the radio says so in red.

### Modes and priority
Right-click an antenna with an empty hand to switch modes:

| Mode | Radio can take from it | Radio puts things in it | Use it for |
|---|---|---|---|
| **Commune** (red) | Yes | Yes | Normal storage |
| **Scry** (gold) | Yes | Never | Output chests you still want to craft from |
| **Tithe** (violet) | No, it's hidden | Yes | Feed chests and void chests |

Shift-right-click with an empty hand to open the settings and set a **priority** from -99 to 99. Higher priority fills first and empties last. At the same priority, Tithe chests only get what the normal chests can't hold. There are no item filters, on purpose.

---

## Transient Antennas

A Transient Antenna (a Tar Antenna and an eye of ender) moves things between the pool and one block, from anywhere in the radio's dimension. Use it to finish autocrafts on machines out of the pool's reach, or to feed something far away.

- **Tune** it like a Reliquary: shift-right-click the radio with it. Then mount it on any face of the block.
- **Glowstone dust** cycles the mode (one dust each time). Any wrench in the `c:tools/wrench` tag (Create's, IE's and so on) does the same for free:
  - **Idle:** does nothing.
  - **Pull:** brings what the block has finished, fluids and power into the pool. On a machine it only takes finished output, never inputs or fuel.
  - **Push:** sends its filter item into the block, plus power. Shift-right-click the antenna with an item to set the filter, or with an empty hand to clear it. A filled bucket (or any filled fluid container) as the filter pushes that fluid from the pool's tanks instead, and the antenna tells you so ("Pushes: Water (fluid...)"). It won't push into a tank that's already part of the pool (one touching the tar or an antenna).
- Right-click with an empty hand to see its mode and filter. If a pushing antenna is stuck, it also says why in chat: no tank or slots on that side, the target is already part of the pool, none of the filter in the pool, or the target won't take it.
- **Direct links:** whatever a pulling antenna takes goes first to push antennas on the same radio whose filter matches, then into the pool. A pull on an altar and a push (LP bucket filter) on a tank pipe it straight across, no pool tank needed. Power goes to every push antenna this way.
- Otherwise fluids need a tank in the pool to go into (or come out of).

## Energy and Essence of the Void

The radio has a built-in battery, 1,000 FE by default (`batteryCapacity`). Cables can connect straight to the radio.

- Stored energy shows in the pool as **Essence of the Void**, a fluid at 1 FE = 1 mB. Bucket it out, pour it back in, or keep it in tanks.
- Essence in your pool's tanks is spare energy: the radio tops its battery back up from it. Bigger tanks, more storage.
- Reliquaries power their machines from the battery first, and only spend Spirit on power once it's empty.

---

## Reliquaries and machines

Machines don't join the pool just by touching tar, so their slots don't get mixed up with your storage. Use a Reliquary instead.

- **Tune** it to the radio (shift-right-click the radio with it), then place it against the machine. It works the block you clicked on to place it (the book lies against it), on any side: top, bottom or any face.
- **Wrench** (anything in `c:tools/wrench`): right-click to turn it to the next side. After the six sides comes **all sides**: a ring of four books around a dark heart with red eyes, working every machine it touches. All-sides works every neighbour with slots, tanks or power (and crafting tables), except storage: chests, barrels, shulker boxes, hoppers and anything in the `tarbound:reliquary_ignores` block tag (add your drawers or crates there). Jobs share out across its machines in turn, like several Reliquaries holding the same contract.
- Placing it against something you don't want bound? Turn it with a wrench, or place it from a different side.
- A tuned Reliquary works from **anywhere in the radio's dimension**, as long as both are loaded.
- The machine shows up in the radio's **machine window** (the tab on the right side of the screen), slot by slot.
  - Click a machine to select it. Shift-clicking items from your inventory then sends them there.
  - Shift-right-click a machine's name to outline it in the world for a few seconds.
- **Finished items drain back** into storage once a second: anything in an output slot, plus anything a contract on that Reliquary makes, wherever it sits in the machine (for mods whose output slots don't look like outputs). Fluids a contract makes are pumped into your tanks too.
- The machine window shows the machine's tanks under its slots (look only, no clicking).
- **Machines without slots:** a Create spout has no inventory of its own, so a Reliquary on it uses the depot it works on as its slots. Set it up the normal Create way: spout two blocks above the depot, with a gap between. Finished items on the depot (a filled bucket) drain back to storage. Machines with only tanks still bind and show their tanks. (Block tag `tarbound:work_surface`.)
- **Spirit Fire:** a furnace, smoker or blast furnace under a Reliquary stays lit without fuel (costs a little Spirit).
- **Machine power:** a Reliquary also feeds FE to a machine that takes it (costs Spirit, can be turned off in the config).
- **Vitae (NeoVitae):** a Reliquary on an Ara Vitae pours Essentia Vitae into it while it crafts: exactly what the craft still needs, up to 500 a second, never for orbs. It also keeps stations in the `tarbound:vitae_fed` tag (the Athanor) topped up to 1,000 mB. One Spirit buys 10 Vitae (a blank slate is about 100 Spirit). For altar crafts, Vitae stored in the pool gets amplified: each mB taken from the pool is matched by one bought with Spirit, so pool Vitae arrives doubled and each Spirit goes twice as far. Tagged stations use pool Vitae one for one first and buy only the rest. Config: `vitaeFeed`, `vitaePerSpirit`, `vitaePerSecond`, `vitaeTopUp`.
- **The spirits' hoard:** spirits are greedy with their work. Toss a Reliquary a gold nugget and they hoard everything that lands within 4 blocks (server config `antiMagnetRadius`, up to 16): item magnets can't pull it away (hoppers and other machines still can). Right-click with a nugget again to make them let go (that one's free). Works with any magnet that respects the usual "don't pull this" flags.
- **Contract slots:** a new Reliquary has 1 contract slot open. Right-click it with any candle to open another, up to 18 (the candle is used up). Reliquaries placed before 0.26 start with 9 open. Each open slot shows as a lit candle floating in a ring around it, and a broken Reliquary keeps its count. Shift-right-click with an empty hand to take the last candle back; the contract in that slot pops out with it.
- **Names:** name a Reliquary in an anvil or in the box at the top of its screen. The radio shows that name instead of "Furnace".
- Machines touching the tar without a Reliquary show up as a `!` count in the machine window.
- **See what's bound:** crouch and look at a radio and everything it has bound is outlined through walls: storage in gold, machines in cyan, tanks in blue, and machines touching the tar without a Reliquary in red. Crouch and look at a Reliquary to outline the machines it works.
- **Comparators:** a comparator against a Reliquary reads the machine it faces, as if it were against the machine.
- **Pipes and hoppers** on a Reliquary reach the machine it faces exactly as they would on the face the Reliquary covers, nothing more.

**Create crushing wheels:** put the Reliquary on a chute above the wheels, and route what comes out the bottom into storage. The millstone works with a Reliquary directly on top.

**Create Mechanical Crafters:** build the grid the normal Create way (crafters facing the same way, pointing into each other, turning), put a tuned Reliquary on any one crafter, and give it a contract written from the mechanical crafting recipe in JEI/EMI. Tarbound finds a spot on the grid where the recipe's pattern fits, puts one item in each crafter it needs (gaps in the pattern can sit over empty crafters; it starts the grid itself), and Create crafts it. Mirrored spots are only used when the recipe allows them. The result comes out of the last crafter the usual Create way, so put a chest the radio sees (over the tar, or on an antenna) or a belt in front of it. A job that can't load says why when you hover it: the grid isn't turning, is still busy, has no spot big enough for the pattern, or the crafters aren't one connected grid. Big patterns fit in one contract: when a recipe has more than 9 cells (a crushing wheel has 21), the lectern folds them into one input per ingredient with a count (16 andesite, 4 planks, 1 stone) and the Reliquary lays them back out cell by cell. A pattern with more than 9 *different* ingredients can't be written; JEI/EMI says so.

**Tech mods:** the Reliquary works a machine the way its own screen does, whatever the machine's side settings say. Finished outputs (and fluid outputs a contract makes) drain back to storage.
- **Mekanism** (and addons built on its inventory API): full slot and fluid tank access. Chemical tanks (gases, slurries) aren't moved.
- **EnderIO, Oritech:** put the Reliquary on any face of the machine (or any block of an Oritech multiblock).
- **Immersive Engineering:** for the coke oven, alloy kiln and blast furnaces, any block of the multiblock will do. Machines that process items in place (metal press, crusher, sawmill, arc furnace and so on) take the Reliquary on their input block. They push what they make out of their output face, so put storage touching the tar (or a Transient Antenna) there. Leave the mold in the metal press.
- **Modern Dynamics:** a Machine Extender between the Reliquary and the machine passes everything through. Modern Dynamics, Mekanism, EnderIO and Pipez pipes never join the pool.

---

### Votive Racks
A tar-soaked rack of candles, set up round the radio like a shrine. While it burns, the radio may run more work at once and plan deeper orders: each lit rack adds 4 to the job limit, 2 levels of sub-crafts and 128 plan lines (up to 4 racks; all in the config). A rack counts if it's within 3 blocks of the radio sideways and from 1 below to 2 above, however the radio's storage is bound (tar, antennas, one huge vault). Set a Votive Skull on it and it reaches 5 blocks. Each rack counts for one radio only, the nearest.
- Light it with flint and steel or a fire charge. Shift-right-click with an empty hand to snuff it, and again to take the skull back.
- While jobs or orders run, each lit rack burns 1 Spirit per radio pass. A radio that can't pay snuffs its racks.
- Recipe: 3 candles over tar-soaked plank, iron ingot, tar-soaked plank, with two tar-soaked planks as legs.
- Jade on the radio shows how many racks it has and its job limit.

### Weeping Seep
A survival, lesser Bottomless Seep (crafted from tar-soaked logs, a Weakened Soul, candles, a tar bucket and a Votive Skull). Set it touching a radio's tar or one of its antennas and, when an order or job runs short of cheap filler, it weeps the rest in, for a little Spirit each:

- **What it makes** is the `tarbound:seep_yields` item data map (item → Spirit per item). The default list is filler you'd farm in minutes anyway: cobblestone, cobbled deepslate, stone, dirt, netherrack, sand, red sand, gravel, andesite, diorite, granite, clay balls, kelp, seeds, sugar cane, bone meal, flint, rotten flesh, string, bones and spider eyes (a candle is 200 Spirit; cobblestone is 2, a spider eye 20). Packs can add or replace entries.
- **Only for jobs and orders.** It never puts anything into storage, never hands anything to players, the board or standing contracts, and never makes something a contract on that radio's machines makes (that gets crafted).
- **Rate:** each seep adds `weepingSeepPerPass` (16) items per radio pass to what it may make, and saves up to four passes' worth (at least a stack), so a machine that loads 64 at a time still gets fed. It makes a load's whole shortfall or none of it, and with no Spirit it makes nothing.
- **Orders with sub-crafts** count on the seep too: what storage doesn't have isn't set aside in the Shade at the start, and the seep makes it as the jobs load.
- Order previews and the job buttons count on it, so an order for torches with no cobblestone in storage still plans.

### Creative sources
- **Bottomless Seep** (creative tab only): set it touching the tar and it hands its radio endless amounts of every raw material its contracts need: anything no contract on that radio's Reliquaries makes, items and fluids. Everything a contract makes still has to be crafted, so an order runs its whole tree for real. It's the test bench for deep orders; it takes nothing in.
- **Create's creative crate** works too: the radio counts its item as plenty and only takes from it (anything put into a creative crate vanishes, so the radio never puts anything in).

## Spirit

Storage is always free. Automation runs on **Spirit**, which each radio holds and you top up with candles.

- **The meter** sits at the right of the crafting area. Hover it for the numbers. Jade shows it too, and a comparator next to the radio reads how full it is.
- **What costs Spirit:**
  - loading a job into a machine
  - a craft at a crafting station
  - a standing contract operation
  - keeping a furnace lit with Spirit Fire
  - machine power
  - Vitae for NeoVitae stations (10 per Spirit; 20 for altar crafts with Vitae in the pool)
  - holding a Spirit Board screen open
- **Free:** taking, inserting, searching, the crafting grid and the spirits' hoard (it costs a gold nugget to start).
- **Out of Spirit:** jobs pause with "The spirits are hungry", furnaces go out, power stops and Spirit Boards go quiet. Feed it and everything picks up again.
- **Feeding it:**
  1. Right-click the radio with candles.
  2. Hopper or pipe candles into it. It won't take anything else and you can't pull anything out.
  3. Light the candle button in the radio's screen and it'll burn candles from storage whenever it drops below half. Turn it off if you want to keep your candles.
- One candle is worth 200 Spirit, a Weakened Soul 100, and the radio holds 4000. Packs can change what counts as an offering (see the pack makers section).

---

## Contracts and autocrafting

A **Spirit Contract** is a note for a spirit: "put these in, this comes out". It doesn't name a machine. Whatever machine the Reliquary is sitting on is the one that runs it.

Need the same contract twice? Put a written contract and some blank ones in a crafting grid: each blank becomes a copy, and the original comes back.

1. **Write it:** put blank contracts in the Scribing Lectern's left slot. Lay the recipe out in the ghost slots (nothing real goes in them), then press **Write**; the written contract pops out on the right.
   - **Building a chain:** with JEI or EMI, hover any ghost ingredient (or the crafting result) and press R for its recipes or U for its uses. Press + to lay that recipe out, write it, then press **↶** (top right) to go back to the recipe you came from. A small corner mark on each ingredient shows whether a contract you carry already makes it (green) or not yet (amber).
   - Ingredients a recipe hands back after every craft (a Blood Orb in a rune recipe) are counted once, not per craft. The lectern asks the recipe itself when it writes a crafting contract.
   - **Crafting** mode is a 3x3 grid with its result shown (for crafting stations); **Machine** mode has 9 inputs and 6 outputs, items or fluids, for any machine. The button on the left switches.
   - Fill it by pressing **+** on a recipe in JEI or EMI (it picks the mode for you), by dragging items and fluids from JEI/EMI onto the slots, or by clicking a slot with an item on your cursor (shift-click with a bucket or tank for its fluid). Scroll on a machine slot to change the amount (shift for bigger steps); right-click clears a slot.
   - **Subs: On / Off** decides how strict the contract is. On, an input may be anything its recipe accepts (any planks, any log; a small cyan mark shows an input that has substitutes). Off, it takes only the exact items laid out, and crafting stations keep to them too.
2. **Hand it over:** right-click a Reliquary and drop the contract in one of its open slots (feed it candles to open more, up to 18). Put the same contract in several Reliquaries and they share the work.
3. **Order:** in the radio, anything you can make has a cyan corner mark. Click a dimmed item to order it, or Ctrl-click (or middle-click) something you already have. Pick an amount and hit **Start**.
4. **Watch it:** the second tab on the radio lists your jobs. Click × to cancel one, or use the red button under the tab to cancel them all. Jobs keep running with the screen closed and save with the world.

Good to know:
- **Orders plan their own ingredients.** The order panel shows the whole plan: what comes out of storage (✓), what gets crafted first by other contracts (+), and anything missing (red, with no contract that makes it). Start queues every sub-craft as its own job ahead of the one it feeds. Hover a line for where it's made. Plans are limited in depth and size (server config `maxPlanDepth`, `maxPlanNodes`) so a huge tree can't stall the server.
- **Shades.** An order with sub-crafts needs a free Shade tuned to the radio. When it starts, everything the plan takes from storage moves into the Shade, so other jobs and standing contracts can't grab it, and the sub-crafts hand their results to the Shade too. Machines load the order's ingredients straight from the Shade, so nothing set aside passes back through storage, where a Tithe chest, a hopper or a standing contract could take it. Ghostly copies of what it holds circle its head. When the last job finishes, the Shade gives back anything left over and you get a chat message. Cancel any job of an order (or break the Shade) and the whole order stops and the stock goes back to storage. Single-step orders don't need a Shade. If a sub-craft comes up short (chance outputs), it runs a few extra times before giving up.
- **Shade ranks.** A Shade starts at rank 1 and can watch an order of up to 4 steps (each sub-craft is a step, and so is the thing you ordered). Right-click it with a **Votive Skull** (any mob head and a candle) to raise it a rank: 8, 16, 32, then 64 steps at rank 5. An AE2 256k storage cell from scratch is about 14 steps, so it wants a rank 3 Shade. Orders pick the weakest free Shade that's big enough, keeping the strong ones free; a Shade keeps its rank when picked up. The order panel says which rank an order needs.
- **Job limits.** An order counts once against `maxJobs`, however many sub-crafts it runs (like an AE2 crafting CPU); the Shades you have limit how many big orders run at once.
- A job counts its results as they show up in storage, however they get there.
- If outputs stop arriving for 2 minutes after everything's been loaded (random drops, someone grabbing them), the job closes itself.
- Contracts remember tags, so a recipe that takes "any log" will use whatever logs you have.
- Used tools count. A recipe that wants a flint and steel or an Immersive Engineering hammer takes a damaged one from storage, here and in the crafting grid fill. Enchanted, renamed or filled items (a shulker box with stuff in it) are only used when there's no plain one.
- **Fluids:** contracts written from recipes with fluids (Create mixing, for example) pump the fluid straight from your tanks into the machine. If a recipe wants a filled bucket instead, the radio fills an empty one from storage. Crafting stations and the crafting grid do the same, so milk for a cake can come out of a tank.
- **Ordering fluids:** a contract that only makes fluid can be ordered too. Fluids it makes show in the fluid part of the list with the same cyan mark (as a ghost tank if you have none yet); click one to order it in whole buckets (+1/+5/+10, or scroll; shift-scroll for 10). The order rounds up to whole operations, so a bucket of something made 250 mB at a time runs 4. The job counts the fluid as it arrives in your tanks; if they're full, it waits ("No tank room") instead of giving up.
- Sub-crafts that make a fluid an order needs hand it to the order's Shade, the same as items.

### Presses, molds and dies
Some machines keep part of a recipe: AE2's inscriber keeps its press. Items in the `tarbound:stays_in_machine` tag (AE2's presses by default) are handled that way: a contract's press is loaded once and left in the machine, the planner never asks for one per operation, and the press is never drained back. If an inscriber holds a different contract's press, it's swapped out (back into storage) the next time the inscriber is completely empty, so one inscriber can run every inscriber contract; one per press is still faster. Add other mods' molds and dies to the tag with a datapack.

### Crafting stations
Put a tuned Reliquary on a **crafting table** and give it a contract written from a crafting recipe. It crafts straight from storage, with no inventory of its own. Add more stations with the same contract and the job is split between them. If storage is full, the station waits instead of dropping items.

- **Stonecutter stations:** a tuned Reliquary on a **stonecutter** cuts straight from storage too. Give it any one-input contract a stonecutting recipe makes (write it at the lectern from the stonecutter's recipe in JEI/EMI: stone to 2 slabs, stone to bricks). Orders use it like any machine. Block tag `tarbound:cutting_stations`.
- **Composter stations:** a tuned Reliquary on a **composter** feeds it junk from storage, one item at a time with the composter's usual chance, and puts the bone meal back in storage. With no contract it takes anything in the item tag `tarbound:compost_fodder` (seeds, saplings, leaves, grass, kelp, vines...); give it standing contracts to pick your own tags instead. Each item costs the standing-contract Spirit (1). Block tag `tarbound:composting_stations`.
- **What a job is waiting for:** hover a job in the radio's jobs tab. A job short of an input says which one and how many it needs for its next operation, and either names the job that's making it or says nothing here makes it. A machine that refused a load gives its reason ("the Basin won't take...").

### Stewards
A **Steward** keeps things in stock, the way an AE2 level emitter and crafting card would, or a Refined Storage requester. Shift-right-click a Scrying Font with it to tune it, right-click a block to set it down (it faces you), then right-click it for its list.

- **The list:** up to 18 items, each with how many to keep in storage. Put an item on it by clicking something the radio can make (the list at the bottom, with a search box), clicking an empty row with an item on your cursor, shift-clicking an item in your inventory, or dragging one from JEI or EMI. Select a row and type an amount, use the -64 to +64 buttons (shift for ten times as much), or scroll over the row. Right-click a row to take it off.
- **What it does:** every second it counts what storage has of each item. When one is under its amount, it asks the radio's contracts for the difference (as much as storage has the ingredients for; the rest next time). A one-step craft becomes an ordinary job. A craft with sub-crafts becomes an order the Steward watches itself, holding its stock the way a Shade does, so it needs no Shade (it can watch an order as big as a rank 5 Shade can) but it watches one such order at a time. It starts one thing per second, never doubles up on something a job is already making (yours or its own), and uses the radio's usual job slots.
- **The line under each row** says what it's doing: green stocked, cyan being made, amber waiting (no free job slot, or already watching an order), red stuck (storage is short, no contract makes it, or it's too big to plan). Hover a row for the words and the count.
- It never moves and takes no harm (only a creative player or `/kill`; `/kill` drops it as an item). Tar doesn't hurt it. Shift-right-click it with an empty hand to pick it up; its list and tuning come along. Picking it up mid-order cancels the order and gives the stock back to storage.

### Standing contracts
For "smelt every raw ore" or "wash every crushed ore":
1. Write a normal contract for one example recipe with a single input.
2. Leave it in the lectern's right slot. A tag picker shows up.
3. Pick a tag with `<` and `>` and press **Generalize**.

Give it to a Reliquary and it'll keep that machine topped up with anything in the tag, a few at a time. Pause or resume it from the bottom half of the radio's jobs tab. Watch out for loops: if the output is also in the tag, it'll go right back in.

---

## Spirit Board

Shift-right-click your radio with the board to tune it, then right-click anywhere in the same dimension to open that radio. The radio's chunk has to be loaded, so use a chunk loader if it's far away. The board costs a little Spirit for every second it's open.

---

## Works with (all optional)

- **Crafting Tweaks:** rotate and balance buttons next to the radio's crafting grid (and its hotkeys). Its clear button is turned off; the radio's own × clears the grid into storage. Set up by `craftingtweaks/grids/tarbound.json`, no code.
- **JEI / EMI:** fill the crafting grid or a machine from a recipe, write contracts, R/U lookups on pool items, and search sync.
- **Create:** plant wax, Weakened Souls from crushing soul sand and blaze burners, chute and belt handling, item vault support, and Ponder scenes for everything.
- **Ponder:** hold W over the radio for the full tutorial, or over any other Tarbound item for its part. All scenes are also under "Tarbound" in the Ponder index.
- **Jade:** shows what a radio has bound, its Spirit and jobs, what a Reliquary is tuned to, and whether an antenna is in range.

REI isn't supported.

---

## For pack makers

### Tags
| Tag | What it does |
|---|---|
| `tarbound:never_pooled` (block) | Blocks that never join the pool. Comes with Create's chutes, belts, funnels, depots, ejectors, fluid pipes, pumps and valves, and the item and fluid pipes of Modern Dynamics, Mekanism, EnderIO and Pipez. |
| `tarbound:reliquary_ignores` (block) | Storage an all-sides Reliquary leaves alone. Comes with chests, barrels, shulker boxes, hoppers and Create vaults and tanks. |
| `tarbound:work_surface` (block) | What a slotless machine (like a spout) works on from above. A Reliquary on the machine uses the block below as its slots. Comes with Create's depot. |
| `tarbound:tar_conduits` (block) | Solid blocks that carry tar: links run through them as if they were tar. Comes with tar-soaked logs and planks. |
| `tarbound:has_tar_seeps` (biome) | Where tar seeps generate (1 chunk in 12 tries). Comes with swamps and badlands (plus `c:is_swamp`, `c:is_badlands`). Empty it with a datapack to turn seeps off. |
| `tarbound:has_desert_tar_seeps` (biome) | The same, a bit rarer (1 in 20). Comes with deserts (plus `c:is_desert`). |
| `tarbound:stays_in_machine` (item) | Recipe inputs a machine keeps rather than uses (presses, molds, dies): loaded once, left in the machine, never planned per operation. Comes with AE2's `ae2:inscriber_presses`. |
| `tarbound:pool_fuel` (item) | The only items jobs will burn as furnace fuel: coal, charcoal, coal blocks, blaze rods, dried kelp blocks, lava buckets, plus coke if a mod adds it. Stops jobs from burning your wooden tools and chests. |
| `c:tallow` (item) | Tallow, shared with other mods. The tallow candle recipe takes anything in it. |
| `c:wax` (item) | Includes `c:tallow` and plant wax. |
| `tarbound:candle_wax` (item) | Extra waxes that make candles. |

### Spirit offerings
What you can feed the radio is a data map. Default: any candle is worth 200, a Weakened Soul 100.

`data/<namespace>/data_maps/item/spirit_offerings.json`
```json
{ "values": { "#minecraft:candles": 200, "tarbound:weakened_soul": 100, "minecraft:soul_lantern": 50 } }
```

### Loot
Weakened Blaze drops are in `tarbound:entities/weakened_blaze`.

Tallow drops are added to the cow and pig loot tables with a NeoForge loot modifier, so other mods' changes to those tables still work. Override `tarbound:entities/cow_tallow` and `tarbound:entities/pig_tallow` to change the drops.

### Config
**Client** (`config/tarbound-client.toml`): radio and Reliquary sounds, all particles, craftables-first and search sync.

**Server** (`serverconfig/tarbound-server.toml`, per world):

| Section | Settings (defaults) |
|---|---|
| network | `radioReach` 3, `maxTarBlocks` 8192, `antennaReachPerPiece` 3 |
| jobs | `maxPlanDepth` 16, `maxPlanNodes` 512, `maxJobs` 16 (single jobs plus orders; an order counts once), `maxOpsPerJob` 4096, `opsPerPass` 8, `jobInterval` 5 ticks, `idleInterval` 20 ticks, `networkRescanSeconds` 5 (radios keep their network scan until something next to the tar, a Reliquary or an antenna changes; 0 rescans every pass), `standingBufferOps` 16, `stationCraftsPerPass` 2, `jobStallSeconds` 120 |
| reliquary | `freeFePerTick` 4096, `spiritFire` true, `antiMagnet` true, `antiMagnetRadius` 4 |
| spirit_board | `spiritBoardRange` 0 (anywhere in the dimension) |
| crafting_grid | `shiftCraftStacks` 1 |
| spirit | `spiritCosts` true, `spiritCapacity` 4000, `jobOpCost` 1, `stationCraftCost` 1, `standingOpCost` 1, `spiritFireCost` 4, `boardCostPerSecond` 1, `machinePower` true, `fePerSpirit` 20000 |
| energy | `batteryCapacity` 1000 |
| transient_antenna | `transientInterval` 10 ticks, `transientItemsPerOp` 64, `transientFluidPerOp` 1000, `transientFePerOp` 10000 |

Set `spiritCosts` to false to make all automation free.

---

## Reporting a bug: the trace log
If loading machines, fluids or Transient Antennas misbehave, an op can run **`/tarbound trace`** (or set `debug.traceLoading = true` in the world's `serverconfig/tarbound-server.toml`). Tarbound then writes what it tries and why things are refused to **`logs/tarbound-debug.log`** in the game folder: every machine load (items, fluids, the machine's tanks and what they say they'll take), pool tank drains, the pool's tanks after each scan, jobs waiting for inputs or without a machine, and every Transient Antenna push and pull. Repeats are written once every 10 seconds. Reproduce the problem for a minute, run `/tarbound trace` again to stop, and upload the file.

## For developers

- **Build:** `./gradlew build`. The jar lands in `build/libs/`. If TerraformersMC's maven (EMI) is unreachable, add `-Pemi_from_modrinth=true` to take EMI from Modrinth's maven instead.
- **Playtests:** `./gradlew runGameTestServer` boots a headless server and plays through the mod: 20 game tests in `com.tarbound.gametest` build little bases (tar, chests, the radio, tuned Reliquaries on vanilla furnaces and crafting tables, Shades, antennas, seeps) and check that jobs, orders and standing contracts finish by themselves without losing or duplicating items, plus Spirit running out, storage filling up, a Reliquary broken mid-job, the hoard, tar swallowing, saving and loading, and that every survival item has a recipe path from vanilla. Results are in the console (`All N required tests passed`); `[playtest]` lines narrate. The tests and their template (`dev/make_gametest_template.py`) stay out of the jar.
- `dev_recipe_viewer` in `gradle.properties` picks what `runClient` loads: `jei`, `emi` or `none`. `dev_jade` adds Jade.
- Ponder is compile-only. Drop Create into the run's `mods` folder to see the scenes.
- **Playtests with Create:** put a Create jar in `run/mods` and run `./gradlew runGameTestServer -Pneo_version=21.1.265` (Create 6.0.11 needs NeoForge 21.1.219 or newer); the crafter grid tests run instead of skipping. They use a dev-only recipe (`data/tarbound/recipe/gametest/`) that stays out of the jar.
- **Ponder tools** (in `dev/`):
  - `make_ponder_nbt.py` builds every scene's schematic. Each scene's comment in `TarboundScenes.java` lists the block positions it expects.
  - `gen_ponder_lang.py` rebuilds the Ponder lang keys from the text in the scene code. Edit the English there, then run it.
  - Ponder only draws blocks that are in the schematic from the start, so scenes reveal and change blocks rather than placing new ones.
- **Textures:** tar-soaked planks, log and log top and the Pitch Cask are drawn by `dev/make_wood_textures.py`, the radio by `dev/make_radio_textures.py` (small palettes, long runs of one tone, no per-pixel noise). Rerun after changing a palette.
- **Weakened Blaze:** its own model (`client/WeakenedBlazeModel`, Blockbench-style flat part list on a 64x32 texture). The texture is drawn by `dev/make_weakened_blaze_texture.py`; `dev/weakened_blaze_uv_guide.png` shows where each part lives, so it can be repainted without touching code.
- **Antenna models:** `dev/make_antenna_models.py` writes all six stage models and the blockstate. The metal is Tarbound's own worn cast iron (`antenna_iron`, `antenna_iron_dark`, drawn by `dev/make_antenna_textures.py`, shared with the Transient Antenna); the base and mode lamps are vanilla textures. Hitboxes live in `TarAntennaBlock.BOXES_UP`.
- **Sounds:** the radio sounds are toned down for headphones. The originals and the script that processes them are in `dev/`.
- **Old code:** things no longer used are deleted; git history keeps them. Unused lang lines live in `dev/orphaned_lang.json`.
- **Access transformer:** `META-INF/accesstransformer.cfg` opens the furnace burn timer for Spirit Fire (declared in `neoforge.mods.toml`).
- Plans for later are in `dev/TODO.md`.
- **Save format:** `dev/SAVE_FORMAT.md` lists every ID, component, blockstate value and save key worlds depend on. Never rename those.

### Under the hood
- One network scan per radio per tick, shared by the radio and everyone looking at its screen.
- Item counts are kept running between scans instead of re-counting every slot.
- The pool screen sends changes only, in pages small enough to never hit the packet limit.
- Anything broken since the last scan is skipped, so items are never pushed into a block that's gone.
- Crafting from the radio reuses the current recipe instead of searching every recipe after each craft.
- Fluids ride the same scan and contents packets as items (`FluidStore`, `FluidKey`). Filled buckets a recipe asks for are made on demand from an empty bucket and pool fluid, inside `TarNetwork.extract`.

---

Minecraft is a trademark of Mojang AB. Tarbound is not affiliated with Mojang or Microsoft.
