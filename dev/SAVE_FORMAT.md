# Tarbound: save format freeze

Tarbound is released (first public build 0.24.3). Worlds, player inventories and dropped items carry the names
below. **Never rename or remove any of them.** Adding new ones is fine; so is changing display names (lang), models,
textures and recipes. If something truly must change, keep reading the old name and convert it on load (see the
shims at the end), and say so in the changelog.

Checked against the full history from 0.20.5 to 0.41.0: nothing listed here has ever been renamed or removed.

## Registry IDs (namespace `tarbound`)

| Kind | IDs |
|---|---|
| Blocks | `tar`, `scrying_font`, `reliquary`, `tar_antenna`, `transient_antenna`, `scribing_lectern`, `essence_of_the_void`, `shade`, `ossuary`, `tar_soaked_log`, `tar_soaked_planks`, `pitch_cask`, `votive_skull`, `votive_wall_skull`, `votive_rack` (0.54), `bottomless_seep` (0.55, creative), `weeping_seep` (0.56, no properties, no block entity) |
| Items | the block items above (except the fluids and `votive_wall_skull`), plus `tar_bucket`, `essence_of_the_void_bucket`, `tallow`, `plant_wax`, `spirit_board`, `blank_contract`, `spirit_contract`, `weakened_soul`, `weakened_blaze_spawn_egg`, `steward` (0.67) |
| Fluids | `tar`, `flowing_tar`, `essence_of_the_void`, `flowing_essence_of_the_void` (fluid types: `tar`, `essence_of_the_void`) |
| Block entities | `bottomless_seep` (0.55, saves nothing), `scrying_font`, `reliquary`, `scribing_lectern`, `tar_antenna`, `transient_antenna`, `shade`, `ossuary`, `pitch_cask` (Votive Skulls use vanilla's `minecraft:skull`) |
| Entities | `weakened_blaze`, `steward` (0.67) |
| Menus | `scrying_font`, `reliquary`, `scribing_lectern`, `ossuary`, `steward` (0.67) |
| Recipe serializers | `shapeless_tool`, `clone_contract` |
| Structure types | `dry_land` (structures `researcher_cabin`; templates `trapper_cabin`, `burned_cabin`; 0.60: structures and templates `wayside_shrine`, `drowned_radio`, `charnel_crypt`) |

## Data components (on items in inventories and chests)

`contract`, `standing`, `crafting_recipe`, `exact_items`, `radio_link`, `radio_id`, `spirit`, `energy`,
`contract_slots`, `cask_fluid`, `shade_rank`, `steward_targets` (0.67: a list of `{item, keep}`).

The **contract** component (`ContractData.CODEC`) has fields `inputs`, `outputs`, `tags`, `fluid_inputs`,
`fluid_outputs`, `returned` (0.50.3: indices of inputs every craft hands back; absent = not recorded). New fields must be optional with a default, as `tags` and the fluid fields are.
The **standing** component (`StandingOrder.CODEC`) has `tag` and `count`.

## Blockstate properties

Property names *and* their values are saved in the world.

| Block | Properties (values) |
|---|---|
| `tar_antenna` | `facing`, `stage` (`single`, `base2`, `top2`, `base3`, `mid3`, `top3`), `mode` (`commune`, `scry`, `tithe`) |
| `transient_antenna` | `facing`, `mode` (`idle`, `pull`, `push`) |
| `reliquary` | `facing`, `book` (0-4), `cover` (0-4), `attach` (`down`, `up`, `north`, `south`, `west`, `east`, `all`) |
| `votive_skull` / `votive_wall_skull` | vanilla skull properties, plus `lit` |
| `tar_soaked_log` | `axis` |
| `votive_rack` | `facing`, `lit`, `skull` (0.54) |

The broadcast mast (0.38.2) kept `base3`/`mid3`/`top3` on purpose: only the models changed.

## Block entity save keys

| Block entity | Keys |
|---|---|
| `scrying_font` | `radio_id`, `spirit`, `energy`, `burn_from_pool`, `hungry`, `jobs`, `nextJob`, `orders`, `next_order`, `loaded` (0.50: per machine `pos` and `items` of `item` + `count`, what the radio loaded and hasn't seen worked; absent before) |
| `reliquary` | `contracts`, `unlocked`, `paused`, `anti_magnet`, `link`, `link_id` (plus its custom name) |
| `shade` | `link`, `link_id`, `order`, `rank`, `held`, `held_fluids` (`shown` is client-only) |
| `tar_antenna` | `link_id`, `priority` (and its link) |
| `transient_antenna` | `link`, `link_id`, `filter` |
| `ossuary` | `link`, `link_id` |
| `scribing_lectern` | `pages` |
| `pitch_cask` | `Fluid` |
| job (`ContractJob`) | `id`, `total`, `pushed`, `received`, `contract`, `order`, `to_shade`, `extra`, `requester` (0.53) |
| order (`CraftOrder`) | `id`, `shade`, `requester`, `output`, `fluid`, `amount`, `vessel_entity` (0.66.1, optional UUID) |

- **Steward entity** (0.67, beside the vanilla mob keys): `link` (GlobalPos codec), `link_id`, `targets` (same codec as the
  `steward_targets` component), `order`, `held`, `held_fluids` (same shape as a Shade's).

## Other saved or shared IDs

- **Commands** (0.64.3): `/tarbound trace` (op). Config `debug.traceLoading`.
- **Sound events** (0.64): `block.tar.gulp`, `block.tar.spit` (alongside `block.scrying_font.static`, `block.scrying_font.tune`).
- **Item entity persistent data** (0.64, tar swallowing): `tarbound:sink` (ticks sunk so far), `tarbound:spat` (game time of the next try).

- **Advancements** (0.67 adds `steward`) keep their IDs even when the wording changes (`cell_tower` is the broadcast-mast advancement).
- **Loot tables** other packs may override: `tarbound:entities/weakened_blaze`, `entities/cow_tallow`, `entities/pig_tallow`,
  and the lore chests `chests/researcher_cabin`, `trapper_cabin`, `burned_cabin`, `wayside_shrine`, `drowned_radio`, `charnel_crypt`.
- **Advancements** (lore branch, 0.60): `wayside_shrine`, `drowned_radio`, `charnel_crypt`.
- **Tags and data maps** pack makers rely on: `never_pooled`, `reliquary_ignores`, `work_surface`, `tar_conduits`, `cabin_rots`,
  `has_tar_seeps`, `has_desert_tar_seeps`, `has_researcher_cabin`, `has_wayside_shrine`, `has_drowned_radio`, `has_charnel_crypt` (0.60), `pool_fuel`, `candle_wax`, `stays_in_machine`,
  the `vitae_fed` (0.57), `cutting_stations` and `composting_stations` (0.65) block tags, the `compost_fodder` (0.65) item tag, and the `spirit_offerings` and `seep_yields` (0.56) data maps.
- **Network payload IDs** only need to match between client and server of the same version; they can change freely.

## Shims that must stay (worlds from released versions depend on them)

| Where | What it keeps working |
|---|---|
| `ReliquaryBlockEntity.loadAdditional` | Pre-0.29 Reliquaries saved 9 contract slots: read into an 18-slot scratch handler and copy across, so no contract is lost and the inventory isn't shrunk. |
| `ReliquaryBlockEntity.loadAdditional` (`unlocked`) | Pre-0.26 Reliquaries saved no slot count: they keep 9 open (`OLD_OPEN_SLOTS`). |
| `ReliquaryBlock` default `attach=down` | Pre-0.25 Reliquaries (always on top of their machine) still face it. |
| `link_id` optional everywhere | Links made before 0.19.1 have no radio ID and still resolve by position. |
| `ContractData` optional codec fields | Contracts from before tags or fluids existed still load. |
| `ShadeBlockEntity` `rank` default 1 | Shades placed before 0.39. |

## Removed before release

Never in a public build, so removed outright with no shim: the reinforced villages (0.51-0.57 alpha; structure type
`walled_village`, piece type `village_wall`, structures `reinforced_village*`, advancement `reinforced_village`), removed in 0.58.
