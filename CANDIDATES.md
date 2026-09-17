# 候选 mod 清单（按功能位分组）

> 数据源：Modrinth 搜索接口，筛选条件 **`1.21.1` + `neoforge` + `project_type:mod`**，按下载量排序，每类取前 60。

> 核查方式：`tools/survey_mods.py`（可重跑；候选会随 mod 生态变化）。

> **这不是最终选型**——候选只回答「市场上有哪些」，选不选还要过设计要求（对照 `DESIGN.md` 的六个时代与反目标）与端侧/依赖检查。


## 汇总

| 功能位 | 候选数 | 已装 | 该类最高下载 |
|---|---|---|---|
| 世界与地理（群系 / 地形 / 结构） | 60 | 3 | TerraBlender（39,044,234） |
| 存储与物流 | 51 | 1 | Mouse Tweaks（57,861,867） |
| 管理与治理 | 49 | 0 | YetAnotherConfigLib (YACL)（121,849,302） |
| 生物与威胁 | 46 | 0 | [EMF] Entity Model Features（95,105,207） |
| 交通 | 42 | 0 | InvMove（16,460,356） |
| 食物与农业 | 41 | 0 | AppleSkin（88,901,200） |
| 社交与联机 | 41 | 0 | Modern UI（21,665,707） |
| 建造与装饰（建材 / 家具 / 灯饰） | 32 | 1 | Iris Shaders（175,299,236） |
| 冒险（结构 / 地牢 / 维度 / 战利品） | 31 | 0 | Xaero's Minimap（109,589,218） |
| 生活质量与信息 | 29 | 3 | FerriteCore（150,285,993） |
| 科技与自动化 | 26 | 0 | Euphoria Patches（27,067,572） |
| 装备与工具 | 24 | 0 | VeinMiner（82,840,493） |
| **合计** | **472** | 8 | — |

## 世界与地理（群系 / 地形 / 结构）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| TerraBlender | `terrablender` | 39,044,234 | required/required | 2026-07-07 |  |
| Biomes O' Plenty | `biomes-o-plenty` | 35,422,936 | required/required | 2026-09-07 | 也在 adventure/decoration |
| Cobblemon | `cobblemon` | 34,597,099 | required/required | 2026-09-12 | 也在 adventure/mobs |
| YUNG's API | `yungs-api` | 34,128,727 | required/required | 2026-09-10 | **已装** |
| Nature's Compass | `natures-compass` | 25,400,559 | required/required | 2026-06-17 | 也在 adventure/equipment/technology/utility |
| Waystones | `waystones` | 25,030,337 | required/required | 2026-09-13 | 也在 adventure/transportation/social |
| Terralith | `terralith` | 22,756,611 | optional/required | 2026-07-08 |  |
| Lithostitched | `lithostitched` | 22,576,015 | unsupported/required | 2026-09-07 |  |
| YUNG's Better Nether Fortresses | `yungs-better-nether-fortresses` | 22,357,909 | unsupported/required | 2026-09-10 | 也在 adventure/decoration |
| YUNG's Better Ocean Monuments | `yungs-better-ocean-monuments` | 21,490,694 | unsupported/required | 2026-09-10 | 也在 adventure/decoration |
| YUNG's Better Dungeons | `yungs-better-dungeons` | 21,481,263 | unsupported/required | 2026-09-10 | **已装** · 也在 adventure/decoration |
| Dungeons and Taverns | `dungeons-and-taverns` | 20,970,275 | optional/required | 2026-08-31 | 也在 adventure/mobs/equipment |
| YUNG's Better Mineshafts | `yungs-better-mineshafts` | 19,882,207 | unsupported/required | 2026-09-10 | 也在 adventure/decoration |
| YUNG's Better Jungle Temples | `yungs-better-jungle-temples` | 19,817,884 | unsupported/required | 2026-09-10 | 也在 adventure/decoration |
| YUNG's Better End Island | `yungs-better-end-island` | 19,337,318 | unsupported/required | 2026-09-10 | 也在 adventure/decoration |
| Yeetus Experimentus | `yeetus-experimentus` | 18,392,676 | required/unsupported | 2026-06-22 |  |
| YUNG's Better Strongholds | `yungs-better-strongholds` | 17,897,031 | unsupported/required | 2026-09-10 | 也在 adventure/decoration |
| Chunky | `chunky` | 17,850,363 | optional/optional | 2026-07-23 | **已装** |
| Cristel Lib | `cristel-lib` | 17,669,073 | optional/required | 2026-07-17 |  |
| Towns and Towers | `towns-and-towers` | 17,388,405 | optional/required | 2026-08-15 | 也在 adventure |
| YUNG's Better Witch Huts | `yungs-better-witch-huts` | 17,333,204 | unsupported/required | 2026-09-10 | 也在 adventure/decoration |
| YUNG's Better Desert Temples | `yungs-better-desert-temples` | 16,285,484 | unsupported/required | 2026-09-10 | 也在 adventure/decoration |
| Tectonic | `tectonic` | 16,232,276 | optional/required | 2026-09-11 | 也在 adventure |
| YUNG's Bridges | `yungs-bridges` | 15,435,832 | unsupported/required | 2026-06-24 | 也在 adventure/decoration |
| Deeper and Darker | `deeperdarker` | 15,359,457 | required/required | 2026-07-11 | 也在 adventure/mobs/equipment |
| Artifacts | `artifacts` | 13,151,513 | required/required | 2026-09-05 | 也在 adventure |
| Dynamic Trees | `dynamictrees` | 12,987,911 | required/required | 2026-07-11 | 也在 adventure/decoration |
| YUNG's Extras | `yungs-extras` | 12,400,099 | unsupported/required | 2026-06-24 | 也在 adventure/decoration |
| Cobblemon: Mega Showdown | `cobblemon-mega-showdown` | 12,096,382 | required/required | 2026-09-13 | 也在 adventure/mobs/equipment |
| Trek | `trek` | 11,564,322 | optional/required | 2026-06-16 | 也在 adventure |
| Explorer's Compass | `explorers-compass` | 11,393,967 | required/required | 2026-06-17 | 也在 adventure/equipment/technology |
| Oh The Biomes We've Gone | `oh-the-biomes-weve-gone` | 11,235,975 | required/required | 2026-06-16 | 也在 adventure/mobs/decoration/food |
| Naturalist | `naturalist` | 10,965,983 | required/required | 2026-08-16 | 也在 adventure/mobs |
| The Lost Cities | `the-lost-cities` | 10,791,949 | unsupported/required | 2026-09-12 | 也在 adventure |
| When Dungeons Arise | `when-dungeons-arise` | 10,739,417 | unsupported/required | 2025-10-26 | 也在 adventure |
| ChoiceTheorem's Overhauled Village | `ct-overhaul-village` | 10,403,589 | unsupported/required | 2026-08-29 | 也在 adventure |
| Aquamirae | `aquamirae` | 10,369,385 | required/required | 2026-09-08 | 也在 mobs/food/equipment |
| Geophilic | `geophilic` | 10,286,202 | optional/required | 2026-06-16 |  |
| Nature's Spirit | `natures-spirit` | 10,185,248 | required/required | 2025-09-04 | 也在 decoration/food |
| MES - Moog's End Structures | `mes-moogs-end-structures` | 10,134,354 | unsupported/required | 2026-09-07 | 也在 decoration |
| Incendium Legacy | `incendium` | 9,913,213 | optional/required | 2026-09-01 |  |
| Regions Unexplored | `regions-unexplored` | 9,487,291 | required/required | 2026-08-06 |  |
| Vanilla Backport | `vanillabackport` | 9,362,858 | required/required | 2026-06-11 | 也在 mobs/equipment |
| The Aether | `aether` | 9,258,233 | required/required | 2025-10-03 | 也在 mobs/food/transportation/equipment |
| Explorify | `explorify` | 9,147,448 | optional/required | 2026-05-13 |  |
| End's Delight | `ends-delight` | 9,146,978 | required/required | 2026-08-18 | 也在 food |
| Chef's Delight - Farmer's Delight Villagers | `chefs-delight` | 8,696,732 | required/required | 2026-04-06 | 也在 mobs/food |
| Dynamic Trees Plus | `dynamictreesplus` | 8,672,685 | required/required | 2026-03-01 |  |
| Structory: Towers | `structory-towers` | 8,616,376 | optional/required | 2026-07-08 |  |
| Dynamic Trees - Biomes O' Plenty | `dynamic-trees-biomes-o-plenty` | 8,593,361 | required/required | 2026-03-15 |  |
| VillagersPlus | `villagersplus` | 8,500,041 | required/required | 2026-08-13 | 也在 mobs |
| Villages&Pillages | `villages-and-pillages` | 8,489,688 | unsupported/required | 2025-06-13 |  |
| [Let's Do] HerbalBrews | `lets-do-herbalbrews` | 8,350,503 | required/required | 2026-02-21 | 也在 food |
| Structory | `structory` | 8,248,122 | optional/required | 2026-07-08 |  |
| Philips Ruins | `philips-ruins` | 8,189,911 | required/required | 2026-01-11 |  |
| Dynamic Trees - Quark | `dynamic-trees-quark` | 8,176,257 | required/required | 2026-05-01 |  |
| [Let's Do] Beachparty | `lets-do-beachparty` | 8,150,900 | required/required | 2026-02-24 | 也在 mobs/food/equipment |
| MVS - Moog's Voyager Structures | `moogs-voyager-structures` | 8,040,961 | unsupported/required | 2026-09-02 |  |
| Ksyxis | `ksyxis` | 7,849,836 | unsupported/required | 2026-04-27 | 也在 management |
| Sparse Structures | `sparsestructures` | 7,847,073 | unsupported/required | 2026-07-11 | 也在 management |

## 存储与物流

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Mouse Tweaks | `mouse-tweaks` | 57,861,867 | required/unsupported | 2026-06-18 | 也在 utility |
| Inventory Profiles Next | `inventory-profiles-next` | 39,110,754 | required/unsupported | 2026-08-28 | 也在 utility |
| Clumps | `clumps` | 38,048,254 | optional/optional | 2026-06-17 | **已装** · 也在 utility |
| Shulker Box Tooltip | `shulkerboxtooltip` | 36,966,421 | required/optional | 2026-07-06 | 也在 utility |
| Carry On | `carry-on` | 25,392,803 | required/required | 2026-08-04 | 也在 transportation/utility |
| Tom's Simple Storage Mod | `toms-storage` | 19,529,193 | required/required | 2026-08-30 |  |
| Sophisticated Backpacks | `sophisticated-backpacks` | 18,370,050 | required/required | 2026-09-09 |  |
| Sophisticated Core | `sophisticated-core` | 18,257,311 | required/required | 2026-09-09 |  |
| Prickle | `prickle` | 12,631,632 | required/required | 2026-07-08 |  |
| Iron Furnaces | `iron-furnaces` | 9,397,935 | required/required | 2026-06-25 | 也在 technology |
| InventoryHUD+ | `inventoryhudplus` | 9,350,845 | required/unsupported | 2026-08-30 | 也在 management |
| Twigs | `twigs` | 7,710,589 | required/required | 2026-06-03 |  |
| CobbleFurnies | `cobblefurnies` | 7,570,111 | required/required | 2026-06-29 |  |
| Macaw's Furniture | `macaws-furniture` | 6,818,960 | required/required | 2026-06-20 |  |
| Nether Chested | `nether-chested` | 6,574,048 | required/required | 2026-06-18 | 也在 management |
| Applied Energistics 2 | `ae2` | 5,880,866 | required/required | 2026-08-25 | 也在 technology |
| Create: Enchantment Industry | `create-enchantment-industry` | 5,559,572 | required/required | 2026-08-29 | 也在 technology |
| Client Sort | `clientsort` | 5,515,798 | required/optional | 2026-09-12 |  |
| Small Ships | `small-ships` | 5,374,951 | required/required | 2025-05-16 | 也在 transportation |
| Easy Shulker Boxes | `easy-shulker-boxes` | 5,246,778 | required/required | 2026-08-12 |  |
| Sophisticated Storage | `sophisticated-storage` | 4,985,431 | required/required | 2026-09-15 |  |
| Man of Many Planes | `man-of-many-planes` | 4,495,370 | required/required | 2026-09-09 | 也在 transportation/technology |
| You're in Grave Danger | `yigd` | 4,469,869 | required/required | 2025-06-22 |  |
| Create: Design n' Decor | `create-design-n-decor` | 4,456,487 | required/required | 2026-05-28 |  |
| Corpse | `corpse` | 4,341,213 | required/required | 2026-08-16 |  |
| Carved Wood | `carved-wood` | 4,035,435 | required/required | 2026-09-09 |  |
| Storage Drawers | `storagedrawers` | 3,802,820 | required/required | 2026-09-12 |  |
| Mekanism | `mekanism` | 3,628,756 | required/required | 2026-04-10 | 也在 technology |
| Inventory Management | `inventory-management` | 3,594,175 | required/required | 2026-06-21 |  |
| Inventory Sorting | `inventory-sorting` | 3,400,089 | optional/required | 2026-06-24 |  |
| [Let's Do] Furniture | `lets-do-furniture` | 3,084,472 | required/required | 2026-03-13 |  |
| RSInfinityBooster | `rsinfinitybooster` | 3,045,005 | required/required | 2025-05-02 |  |
| AstikorCarts Redux | `astikorcarts-redux` | 2,948,268 | required/required | 2026-08-16 | 也在 transportation |
| Underground Worlds | `underground-worlds` | 2,941,672 | required/required | 2026-09-02 |  |
| XP Tome | `xp-tome` | 2,646,965 | required/required | 2026-04-28 | 也在 technology |
| Boatload | `boatload` | 2,642,387 | required/required | 2025-10-17 | 也在 transportation |
| Create Contraption Terminals | `create-contraption-terminals` | 2,521,598 | required/required | 2026-08-12 | 也在 technology |
| [NoCube's] Undergarden Delight | `undergarden-delight` | 2,482,619 | required/required | 2026-07-09 | 也在 transportation/management |
| Refined Storage | `refined-storage` | 2,446,743 | required/required | 2026-06-07 | 也在 transportation/technology |
| Hellion's Sniffer+ | `hellions-sniffer+` | 2,385,916 | required/required | 2026-09-05 | 也在 transportation |
| Powah! | `powah` | 2,374,747 | required/required | 2026-05-02 | 也在 technology |
| Woodworks | `woodworks` | 2,127,606 | required/required | 2025-10-17 |  |
| Adorn | `adorn` | 2,127,010 | required/required | 2026-07-30 |  |
| Echo Chest | `echo-chest` | 2,101,140 | required/required | 2026-06-18 |  |
| Enderite Mod | `enderite-mod` | 2,093,711 | required/required | 2026-08-30 |  |
| Etcetera | `etcetera` | 2,064,464 | required/required | 2026-08-15 | 也在 technology |
| Dungeon's Delight | `dungeons_delight` | 2,061,883 | required/required | 2026-09-13 |  |
| Immortalers Delight | `immortalers-delight` | 2,052,697 | required/required | 2026-09-02 |  |
| ItemLocks | `itemlocks` | 2,051,889 | required/unsupported | 2026-08-01 |  |
| Trash Cans | `trash-cans` | 2,033,106 | required/required | 2026-09-10 | 也在 technology |
| Storage Delight | `storage-delight` | 1,984,200 | required/required | 2026-07-08 |  |

## 管理与治理

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| YetAnotherConfigLib (YACL) | `yacl` | 121,849,302 | optional/optional | 2026-07-19 | 也在 utility |
| Dynamic FPS | `dynamic-fps` | 65,087,398 | required/unsupported | 2026-06-16 | 也在 utility |
| No Chat Reports | `no-chat-reports` | 57,171,063 | optional/optional | 2026-08-18 | 也在 utility/social |
| Essential Mod | `essential` | 43,421,522 | required/unsupported | 2026-07-30 | 也在 utility/social |
| Fzzy Config | `fzzy-config` | 38,753,045 | required/required | 2026-09-15 | 也在 utility |
| Cherished Worlds | `cherished-worlds` | 31,413,778 | required/unsupported | 2026-07-20 | 也在 utility |
| Crash Assistant | `crash-assistant` | 18,105,873 | required/unsupported | 2026-08-13 | 也在 social |
| Default Options | `default-options` | 17,770,179 | required/unsupported | 2026-07-24 |  |
| Particle Core | `particle-core` | 15,924,573 | required/unsupported | 2026-07-08 |  |
| Resourcify | `resourcify` | 12,264,925 | required/unsupported | 2026-09-11 |  |
| Rebind Narrator | `rebind-narrator` | 11,340,453 | required/unsupported | 2026-09-01 |  |
| WorldEdit | `worldedit` | 10,585,402 | unsupported/required | 2026-08-09 |  |
| Paxi | `paxi` | 10,476,284 | optional/optional | 2026-06-24 |  |
| Starter Kit | `starter-kit` | 10,277,490 | optional/required | 2026-06-18 |  |
| Crafting Tweaks | `crafting-tweaks` | 9,821,115 | optional/optional | 2026-08-21 |  |
| Overflowing Bars | `overflowing-bars` | 9,707,377 | required/unsupported | 2026-06-19 |  |
| Better Block Entities | `better-block-entities` | 8,745,002 | required/unsupported | 2026-07-24 |  |
| EMI Loot | `emi-loot` | 8,683,885 | required/required | 2026-01-04 |  |
| EMI Enchanting | `emi-enchanting` | 8,399,866 | required/unsupported | 2024-09-18 |  |
| Radical Cobblemon Trainers API | `rctapi` | 7,483,779 | optional/optional | 2026-09-14 |  |
| Better Compatibility Checker | `better-compatibility-checker` | 6,732,518 | required/required | 2026-06-17 |  |
| Difficulty Lock | `difficulty-lock` | 6,537,063 | optional/required | 2026-06-18 |  |
| Config Manager | `configmanager` | 6,276,925 | unsupported/required | 2026-06-16 |  |
| TrashSlot | `trashslot` | 5,823,498 | required/required | 2026-07-31 |  |
| Krypton Reno | `krypton-fnp` | 5,660,753 | optional/optional | 2026-07-09 |  |
| In-Game Account Switcher | `in-game-account-switcher` | 5,233,845 | required/unsupported | 2026-06-25 | 也在 social |
| Luna | `luna` | 5,050,792 | required/unsupported | 2025-06-03 |  |
| Tiny Item Animations | `tiny-item-animations` | 4,467,166 | required/unsupported | 2025-08-09 |  |
| Advancement Disable | `advancementdisable` | 4,218,387 | unsupported/required | 2026-07-11 |  |
| Pufferfish's Skills | `skills` | 4,100,075 | required/required | 2026-09-02 |  |
| Sounds Be Gone! | `soundsbegone` | 4,002,386 | required/unsupported | 2026-06-24 |  |
| Item Obliterator | `item-obliterator` | 3,853,755 | optional/required | 2025-06-25 |  |
| Superflat World No Slimes | `superflat-world-no-slimes` | 3,544,479 | unsupported/required | 2026-06-18 |  |
| In Control! | `in-control` | 3,490,150 | unsupported/required | 2026-09-02 |  |
| Better ModList | `better-modlist` | 3,460,176 | required/unsupported | 2026-08-10 |  |
| Server Browser | `server-browser` | 3,346,712 | required/unsupported | 2025-04-04 | 也在 social |
| World Play Time | `world-play-time` | 3,257,718 | required/unsupported | 2026-03-27 |  |
| Pufferfish's Attributes | `attributes` | 3,147,847 | required/required | 2026-08-09 |  |
| Structurify - Structure Control | `structurify` | 2,999,568 | required/required | 2026-09-15 |  |
| No Chat Restrictions | `no-chat-restrictions` | 2,851,762 | required/unsupported | 2026-08-22 | 也在 social |
| Delete Worlds To Trash | `delete-worlds-to-trash` | 2,844,826 | required/unsupported | 2026-06-18 |  |
| Cobblemon Catch Rate Display | `catch-rate-display` | 2,773,540 | required/unsupported | 2026-09-15 |  |
| LuckPerms | `luckperms` | 2,692,619 | unsupported/required | 2026-08-06 |  |
| Tom's Trading Network | `toms-trading-network` | 2,597,228 | required/required | 2026-06-17 |  |
| CrashExploitFixer | `crashexploitfixer` | 2,550,050 | unsupported/required | 2026-05-18 |  |
| Packed Packs | `packed-packs` | 2,217,123 | required/unsupported | 2026-09-14 |  |
| Open Parties and Claims PvP Support | `opacpvp` | 2,212,022 | unsupported/required | 2025-02-20 |  |
| KubeJS Additions | `kubejs-additions` | 2,137,507 | required/required | 2026-06-21 |  |
| SeasonHud | `seasonhud` | 2,126,536 | required/optional | 2026-08-31 |  |

## 生物与威胁

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| [EMF] Entity Model Features | `entity-model-features` | 95,105,207 | required/unsupported | 2026-09-03 | 也在 decoration/utility |
| Guard Villagers | `guard-villagers` | 12,842,198 | required/required | 2026-09-12 |  |
| Touhou Little Maid | `touhou-little-maid` | 10,959,518 | required/required | 2026-05-09 | 也在 decoration |
| Creeper Overhaul | `creeper-overhaul` | 10,379,621 | required/required | 2025-01-04 |  |
| Zombie Awareness | `zombie-awareness` | 8,436,509 | required/required | 2024-09-17 |  |
| Radical Cobblemon Trainers | `rctmod` | 8,083,339 | required/required | 2026-09-07 |  |
| Mowzie's Mobs | `mowzies-mobs` | 8,066,583 | required/required | 2026-04-02 |  |
| Animal Feeding Trough | `animal_feeding_trough` | 8,035,547 | required/required | 2026-07-10 |  |
| Just Enough Breeding (JEBr) | `justenoughbreeding` | 7,905,983 | required/unsupported | 2026-08-24 | 也在 management |
| Enderman Overhaul | `enderman-overhaul` | 7,903,815 | required/required | 2026-02-22 | 也在 equipment |
| Illager Invasion | `illager-invasion` | 7,734,024 | required/required | 2026-06-18 |  |
| TslatEntityStatus | `tslatentitystatus` | 7,692,687 | required/optional | 2025-12-16 |  |
| Legendary Monsters | `legendary-monsters` | 7,602,115 | required/required | 2026-08-23 | 也在 equipment |
| Spawn | `spawn-mod` | 7,467,526 | required/required | 2026-09-14 | 也在 food |
| [Let's Do] Meadow | `lets-do-meadow` | 7,451,486 | required/required | 2026-02-24 | 也在 food/equipment |
| Cobblemon Fight or Flight Reborn | `cobblemon-fight-or-flight-reborn` | 7,400,156 | required/required | 2026-09-08 |  |
| Nyf's Spiders | `nyfs-spiders` | 7,227,013 | required/required | 2026-06-02 |  |
| Spawn Animations | `spawn-animations` | 6,906,294 | optional/required | 2026-06-24 |  |
| Bartering Station | `bartering-station` | 6,902,614 | required/required | 2026-06-18 |  |
| Galosphere | `galosphere` | 6,865,179 | required/required | 2026-06-23 | 也在 equipment |
| Particle Effects | `particle-effects` | 6,700,850 | required/unsupported | 2026-06-26 |  |
| Goety - The Dark Arts | `goety` | 6,651,491 | required/required | 2026-09-15 | 也在 equipment |
| You Shall Not Spawn! | `you-shall-not-spawn` | 6,528,022 | unsupported/required | 2024-10-18 | 也在 management |
| SmartBrainLib | `smartbrainlib` | 6,245,171 | unsupported/required | 2026-06-30 |  |
| True Ending - Ender Dragon Overhaul | `true-ending` | 6,180,184 | optional/required | 2025-10-19 |  |
| Critters and Companions | `critters-and-companions` | 5,930,970 | required/required | 2026-08-19 |  |
| Ribbits | `ribbits` | 5,713,729 | required/required | 2025-12-19 |  |
| MissingMons [cobblemon] | `missingmons-cobblemon` | 5,679,115 | optional/required | 2026-07-27 |  |
| Ad Astra | `ad-astra` | 5,604,304 | required/required | 2026-09-13 | 也在 food/transportation/equipment/technology |
| Iron's Spells 'n Spellbooks | `irons-spells-n-spellbooks` | 5,483,233 | required/required | 2026-08-18 | 也在 equipment |
| Friends&Foes (Forge/NeoForge) | `friends-and-foes-forge` | 4,844,090 | required/required | 2026-08-10 |  |
| Adorable Hamster Pets | `adorable-hamster-pets` | 4,809,251 | required/required | 2026-09-08 | 也在 food/storage |
| Mutant Monsters | `mutant-monsters` | 4,758,081 | required/required | 2026-08-31 | 也在 equipment |
| Hybrid Aquatic | `hybrid-aquatic` | 4,671,007 | required/required | 2026-09-13 | 也在 food/equipment |
| [Let's Do] BloomingNature | `lets-do-bloomingnature` | 4,664,595 | required/required | 2026-03-28 |  |
| [Let's Do] WilderNature | `lets-do-wildernature` | 4,571,459 | required/required | 2026-07-30 | 也在 food |
| Frostiful | `frostiful` | 4,448,734 | required/required | 2026-09-02 |  |
| VillagerConfig | `villagerconfig` | 4,389,238 | unsupported/required | 2026-07-02 | 也在 management |
| Cobblemon cafe | `cobble-caf-forms` | 4,363,112 | optional/required | 2026-09-11 |  |
| TDmon | `tdmon` | 4,192,903 | optional/required | 2025-07-04 |  |
| YUNG's Cave Biomes | `yungs-cave-biomes` | 4,167,979 | required/required | 2026-08-19 | 也在 food |
| The Bumblezone - NeoForge/Forge | `the-bumblezone` | 4,068,233 | required/required | 2026-07-31 |  |
| MCA Reborn | `minecraft-comes-alive-reborn` | 4,008,771 | required/required | 2026-08-29 |  |
| Bosses of Mass Destruction Forge | `bosses-of-mass-destruction-forge` | 3,936,536 | required/required | 2026-07-17 |  |
| Jaden's Nether Expansion | `jadens-nether-expansion` | 3,860,221 | required/required | 2026-08-22 | 也在 food |
| Rotten Creatures | `rottencreatures` | 3,853,314 | required/required | 2025-06-21 |  |

## 交通

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| InvMove | `invmove` | 16,460,356 | required/unsupported | 2026-06-17 |  |
| Elytra Slot | `elytra-slot` | 13,595,687 | required/required | 2025-07-23 | 也在 equipment |
| Do a Barrel Roll | `do-a-barrel-roll` | 10,937,088 | required/optional | 2026-06-18 | 也在 equipment |
| Xaero Zoomout | `xaero-zoomout` | 8,789,776 | required/unsupported | 2026-04-21 |  |
| Create Aeronautics | `create-aeronautics` | 7,982,976 | required/required | 2026-08-29 | 也在 technology |
| Caelus API | `caelus` | 7,725,068 | required/required | 2025-07-23 |  |
| Snowy Spirit | `snowy-spirit` | 5,517,893 | required/required | 2026-08-03 |  |
| Automobility | `automobility` | 5,281,059 | required/required | 2025-04-24 | 也在 equipment/technology |
| Icarus | `icarus` | 4,806,605 | required/required | 2026-08-28 | 也在 equipment |
| Void Totem | `voidtotem` | 4,719,740 | required/required | 2025-03-06 | 也在 equipment |
| Create: Bells & Whistles | `bellsandwhistles` | 4,662,124 | required/required | 2025-03-13 | 也在 technology |
| Create: Interiors | `interiors` | 4,493,771 | required/required | 2026-01-29 | 也在 technology |
| Towers of the Wild Modded | `totw-modded` | 4,212,347 | required/required | 2026-01-17 |  |
| Better Climbing | `better-climbing` | 3,271,351 | required/unsupported | 2026-05-20 |  |
| ParCool! | `parcool` | 2,939,989 | required/required | 2026-08-30 |  |
| Paragliders | `paragliders` | 2,722,612 | required/required | 2026-09-08 |  |
| Create Stuff 'N Additions | `create-stuff-additions` | 2,578,133 | required/required | 2026-08-17 |  |
| Cobblemon Journey Mounts | `cobblemon-journey-mounts` | 2,428,291 | optional/required | 2026-01-24 |  |
| Estrogen | `estrogen` | 2,226,232 | required/required | 2026-09-03 | 也在 technology |
| Steam 'n' Rails Neoforge | `create-steam-n-rails-1.21.1` | 2,205,750 | required/required | 2026-07-27 | 也在 technology/management |
| Easy Elytra Takeoff | `easy-elytra-takeoff` | 2,188,258 | unsupported/required | 2026-06-18 |  |
| ElevatorMod | `elevatormod` | 2,166,040 | required/required | 2026-06-20 |  |
| Minecraft Transit Railway | `minecraft-transit-railway` | 2,075,656 | required/required | 2026-07-04 | 也在 technology |
| Cobblemon: Ride On! | `cobblemon-ride-on` | 2,036,299 | required/required | 2025-07-07 |  |
| Create Railways Navigator | `create-railways-navigator` | 2,023,855 | required/required | 2026-05-31 | 也在 technology |
| Horseman | `horseman` | 1,863,900 | required/required | 2026-08-25 |  |
| Aether Addon: Protect Your Moa | `aether-protect-your-moa` | 1,816,452 | required/required | 2025-02-27 |  |
| Immersive Machinery | `immersive-machinery` | 1,800,720 | required/required | 2026-09-09 |  |
| Mob Lassos | `mob-lassos` | 1,790,806 | required/required | 2026-07-23 |  |
| Forgematica | `forgematica` | 1,568,996 | required/unsupported | 2026-07-13 |  |
| Superb Steeds | `superb-steeds` | 1,488,418 | required/required | 2026-07-27 |  |
| Gliders | `gliders` | 1,461,166 | required/required | 2025-12-18 |  |
| Pyrotechnic Elytra | `pyrotechnic-elytra` | 1,407,497 | unsupported/required | 2025-12-31 |  |
| Hang Glider | `hang-glider` | 1,299,456 | required/required | 2026-06-19 |  |
| Xaero's Minimap & World Map - Waystones Compatibility | `xaeros-minimap-world-map-waystones-compatibility-forge` | 1,234,851 | required/required | 2026-04-24 |  |
| Oritech | `oritech` | 1,214,489 | required/required | 2026-08-31 |  |
| Backpacks! | `vanilla-backpacks` | 1,189,690 | optional/required | 2026-06-20 |  |
| Copper Age Backport | `backport-copper-age` | 1,175,488 | required/required | 2025-12-01 |  |
| Factory API | `factory-api` | 1,136,550 | required/required | 2026-08-17 |  |
| Leaf Me Alone | `leaf-me-alone` | 1,133,998 | required/required | 2026-01-04 |  |
| Create: Compatible Storage | `create-compatible-storage` | 1,133,156 | required/required | 2026-08-12 |  |
| Butchery | `butchery` | 1,120,231 | required/required | 2026-07-29 |  |

## 食物与农业

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| AppleSkin | `appleskin` | 88,901,200 | optional/optional | 2026-06-18 | 也在 utility |
| Smarter Farmers (farmers replant) | `smarter-farmers-farmers-replant` | 12,118,125 | unsupported/required | 2025-10-18 |  |
| RightClickHarvest | `rightclickharvest` | 12,081,396 | unsupported/required | 2026-06-16 |  |
| Ocean's Delight | `oceans-delight` | 8,627,003 | required/required | 2026-04-17 |  |
| [Let's Do] Farm & Charm | `lets-do-farm-charm` | 7,445,821 | required/required | 2026-07-30 | 也在 transportation |
| Create Slice & Dice | `slice-and-dice` | 7,205,098 | required/required | 2026-08-02 | 也在 technology |
| [Let's Do] Candlelight - Farm&Charm compat | `lets-do-candlelight-farmcharm-compat` | 7,085,239 | required/required | 2026-03-13 |  |
| Serene Seasons | `serene-seasons` | 6,925,629 | required/required | 2026-09-05 |  |
| [Let's Do] Bakery - Farm&Charm Compat | `lets-do-bakery-farmcharm-compat` | 6,455,214 | required/required | 2026-02-15 |  |
| Expanded Delight | `expanded-delight` | 6,433,502 | required/required | 2026-01-10 |  |
| Kaleidoscope Cookery | `kaleidoscope-cookery` | 6,151,978 | required/required | 2026-06-17 |  |
| Ecologics | `ecologics` | 5,445,916 | required/required | 2026-07-11 |  |
| Ender's Delight | `enders-delight` | 5,435,383 | required/required | 2026-08-03 | 也在 equipment |
| Create: Central Kitchen | `create-central-kitchen` | 5,165,193 | required/required | 2026-08-29 | 也在 technology |
| More Delight (for Farmer's Delight) | `more-delight` | 4,795,096 | required/required | 2026-06-24 | 也在 storage |
| My Nether's Delight | `my-nethers-delight` | 4,793,603 | required/required | 2026-09-11 |  |
| Gensokyo Delight ~ Youkais' Feasts | `gensokyo-delight-youkais-feasts` | 4,069,359 | required/required | 2026-04-28 |  |
| Harvest with ease | `harvest-with-ease` | 4,026,797 | unsupported/required | 2026-04-29 |  |
| Universal Bone Meal | `universal-bone-meal` | 4,019,912 | required/required | 2026-06-19 |  |
| Ube's Delight | `ubes-delight` | 3,704,438 | required/required | 2026-06-20 |  |
| Kaleidoscope World Liquor | `kaleidoscope-world-liquor` | 3,702,788 | required/required | 2026-09-06 |  |
| Crate Delight | `crate-delight` | 3,671,694 | required/required | 2026-07-21 |  |
| Autochef's Delight | `autochefs-delight` | 3,667,730 | required/required | 2026-04-30 |  |
| Brewin' And Chewin' | `brewin-and-chewin` | 3,612,878 | required/required | 2026-06-24 |  |
| Bountiful Fares | `bountiful-fares` | 3,564,250 | required/required | 2026-08-19 | 也在 storage/technology/social |
| Miner's Delight | `miners-delight` | 3,495,515 | required/required | 2026-05-27 |  |
| Incubation | `incubation` | 3,359,101 | required/required | 2026-03-04 | 也在 storage |
| Crabber's Delight | `crabbers-delight` | 3,290,216 | required/required | 2026-09-03 |  |
| Ars Nouveau's Flavors & Delight | `arsdelight` | 3,246,932 | required/required | 2026-06-22 |  |
| Nether Depths Upgrade | `nether-depths-upgrade` | 3,246,384 | required/required | 2026-08-21 |  |
| Leave My Bars Alone | `leave-my-bars-alone` | 3,144,831 | required/unsupported | 2026-06-19 |  |
| Barbeque's Delight [Forge/NeoForge] | `barbeques-delight-forge` | 3,024,560 | required/required | 2026-04-28 | 也在 storage |
| Sunflower Delight | `sunflower-delight` | 2,955,552 | required/required | 2026-08-27 |  |
| Inventory Particles | `inventory-particles` | 2,942,604 | required/unsupported | 2026-09-12 | 也在 storage |
| The Undergarden | `the-undergarden` | 2,908,777 | required/required | 2026-08-24 |  |
| Upgrade Aquatic | `upgrade-aquatic` | 2,823,250 | required/required | 2025-10-17 |  |
| Rustic Delight | `rustic-delight` | 2,802,249 | required/required | 2026-08-19 |  |
| Atmospheric | `atmospheric` | 2,689,857 | required/required | 2025-10-18 |  |
| Autumnity | `autumnity` | 2,661,550 | required/required | 2025-10-17 |  |
| Youkai's Homecoming | `youkaishomecoming` | 2,607,755 | required/required | 2026-04-28 |  |
| Pineapple Delight | `pineapple-delight` | 2,577,744 | required/required | 2026-07-08 |  |

## 社交与联机

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Modern UI | `modern-ui` | 21,665,707 | required/unsupported | 2026-06-14 |  |
| Sounds | `sound` | 17,907,355 | required/unsupported | 2026-09-15 |  |
| What Are They Up To (Watut) | `what-are-they-up-to` | 14,816,178 | required/required | 2025-04-13 |  |
| Ping Wheel | `ping-wheel` | 13,294,763 | required/required | 2026-07-18 |  |
| Hardcore Revival | `hardcore-revival` | 9,413,946 | required/required | 2026-09-08 |  |
| Emotecraft | `emotecraft` | 7,380,998 | required/optional | 2026-09-12 |  |
| Simple Rich Discord Presence | `srdp` | 6,931,972 | required/unsupported | 2026-06-22 |  |
| Immersive Paintings | `immersive-paintings` | 5,042,121 | required/required | 2026-09-13 |  |
| Chat Tools | `chat-tools` | 4,739,476 | required/unsupported | 2026-07-08 |  |
| Longer Chat History | `longer-chat-history` | 4,299,185 | required/unsupported | 2026-03-28 |  |
| Medieval Buildings | `medieval-buildings` | 3,969,684 | required/required | 2026-04-20 |  |
| Plasmo Voice | `plasmo-voice` | 3,918,477 | optional/optional | 2026-08-25 |  |
| Emoji Type | `emoji-type` | 3,763,209 | required/unsupported | 2026-06-25 |  |
| Joy of Painting | `joy-of-painting` | 3,726,641 | required/required | 2026-07-27 |  |
| Vanilla Refresh | `vanilla-refresh` | 3,167,094 | optional/required | 2026-07-23 |  |
| Health Indicators | `health-indicators` | 3,107,882 | required/unsupported | 2026-06-20 |  |
| Tier Tagger | `tiertagger` | 2,966,846 | required/unsupported | 2026-06-16 |  |
| Customizable Player Models | `custom-player-models` | 2,943,754 | required/unsupported | 2026-09-04 |  |
| Antique Atlas 4 | `antique-atlas-4` | 2,807,101 | required/unsupported | 2026-01-05 |  |
| Scribble | `scribble` | 2,784,110 | required/unsupported | 2026-06-16 |  |
| Music Maker | `music-maker-mod` | 2,641,960 | required/required | 2026-07-28 |  |
| PatPat [Mod & Plugin] | `patpat` | 2,625,987 | unknown/unknown | 2026-08-01 |  |
| Selfexpression | `selfexpression` | 2,421,320 | required/required | 2025-08-30 |  |
| CustomNPCs-Unofficial | `customnpcs-unofficial` | 2,032,568 | required/required | 2026-03-01 |  |
| SDM Shop (Legacy) | `sdm-shop` | 2,013,570 | required/required | 2026-04-16 |  |
| Camerapture | `camerapture` | 1,811,128 | required/required | 2026-07-08 |  |
| Improved Pillager Outpost | `improved-pillager-outpost` | 1,800,004 | optional/required | 2025-12-21 |  |
| Dragon Drops Elytra | `dragon-drops-elytra` | 1,785,571 | unsupported/required | 2026-06-18 |  |
| Wynntils | `wynntils` | 1,778,874 | required/unsupported | 2026-09-13 |  |
| Players Drop Heads | `players-drop-heads` | 1,698,873 | optional/required | 2026-06-17 |  |
| Skin Restorer | `skinrestorer` | 1,696,680 | unsupported/required | 2026-09-08 |  |
| Figura | `figura` | 1,648,345 | required/unsupported | 2026-06-19 |  |
| Personality | `personality!` | 1,582,350 | required/required | 2026-05-06 |  |
| More Music Discs | `more-music-discs` | 1,575,474 | required/required | 2026-06-18 |  |
| World Host | `world-host` | 1,489,234 | required/unsupported | 2025-04-05 |  |
| DisguiseHeads | `disguiseheads` | 1,438,737 | required/unsupported | 2026-07-05 |  |
| Custom Crosshair Mod | `custom-crosshair-mod` | 1,413,064 | required/unsupported | 2026-07-03 |  |
| AutoModpack | `automodpack` | 1,412,166 | required/required | 2026-08-09 |  |
| Ears | `ears` | 1,331,699 | required/unsupported | 2026-09-10 |  |
| Just Player Heads | `just-player-heads` | 1,308,584 | unsupported/required | 2026-06-18 |  |
| Emojiful | `emojiful` | 1,293,584 | required/required | 2026-07-15 |  |

## 建造与装饰（建材 / 家具 / 灯饰）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Iris Shaders | `iris` | 175,299,236 | required/unsupported | 2026-09-13 |  |
| [ETF] Entity Texture Features | `entitytexturefeatures` | 100,236,772 | required/unsupported | 2026-09-02 | 也在 utility |
| Continuity | `continuity` | 72,629,981 | required/unsupported | 2026-06-16 | 也在 utility |
| Chat Heads | `chat-heads` | 48,313,487 | required/unsupported | 2026-08-13 | 也在 social |
| AmbientSounds | `ambientsounds` | 38,216,294 | required/unsupported | 2026-06-22 |  |
| BetterF3 | `betterf3` | 33,699,404 | required/unsupported | 2026-07-09 | 也在 utility |
| Supplementaries | `supplementaries` | 27,045,908 | required/required | 2026-09-10 | 也在 storage/utility |
| Create | `create` | 25,933,802 | required/required | 2026-04-21 | **已装** · 也在 technology/utility |
| Handcrafted | `handcrafted` | 25,749,648 | required/required | 2025-03-22 | 也在 utility |
| Farmer's Delight | `farmers-delight` | 23,581,558 | required/required | 2026-08-29 | 也在 food/equipment |
| Physics Mod | `physicsmod` | 23,236,582 | required/unsupported | 2026-09-13 | 也在 utility |
| CustomSkinLoader | `customskinloader` | 23,141,915 | required/unsupported | 2026-06-21 |  |
| Athena | `athena-ctm` | 22,935,906 | required/unsupported | 2026-05-09 |  |
| Sodium Shadowy Path Blocks (SSPB) | `sodium-shadowy-path-blocks` | 22,550,324 | required/unsupported | 2026-05-25 |  |
| Chipped | `chipped` | 22,392,618 | required/required | 2024-10-15 |  |
| Chat Animation [Smooth Chat] | `chatanimation` | 21,829,831 | required/unsupported | 2026-07-05 | 也在 social |
| Particle Rain | `particle-rain` | 20,372,598 | required/unsupported | 2026-08-24 |  |
| Sodium Dynamic Lights | `sodium-dynamic-lights` | 18,392,067 | required/unsupported | 2025-04-10 |  |
| Prism | `prism-lib` | 17,529,482 | required/unsupported | 2026-06-30 |  |
| Wavey Capes | `wavey-capes` | 17,170,694 | required/unsupported | 2026-09-06 |  |
| Another Furniture | `another-furniture` | 15,439,658 | required/required | 2026-04-16 |  |
| Yes Steve Model | `yes-steve-model` | 14,678,094 | optional/optional | 2026-05-02 |  |
| Fusion (Connected Textures) | `fusion-connected-textures` | 14,587,545 | required/unsupported | 2026-09-11 |  |
| First-person Model | `first-person-model` | 13,982,106 | required/unsupported | 2026-06-21 | 也在 equipment/social |
| MmmMmmMmmMmm | `mmmmmmmmmmmm` | 12,178,495 | required/required | 2026-09-10 |  |
| Macaw's Windows | `macaws-windows` | 10,847,308 | required/required | 2026-06-20 |  |
| Diagonal Fences | `diagonal-fences` | 10,759,226 | required/required | 2026-06-18 |  |
| Blur+ | `blur-plus` | 10,686,280 | required/unsupported | 2026-06-29 |  |
| Macaw's Fences and Walls | `macaws-fences-and-walls` | 10,658,433 | required/required | 2026-06-20 |  |
| Highlight | `highlight` | 10,547,798 | required/unsupported | 2026-07-20 |  |
| Create Crafts & Additions | `createaddition` | 10,445,056 | required/required | 2026-08-16 | 也在 food/storage/transportation/technology |
| Legendary Tooltips | `legendary-tooltips` | 10,264,960 | required/unsupported | 2026-08-18 | 也在 equipment |

## 冒险（结构 / 地牢 / 维度 / 战利品）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Xaero's Minimap | `xaeros-minimap` | 109,589,218 | required/optional | 2026-09-15 | 也在 transportation/utility |
| Xaero's World Map | `xaeros-world-map` | 95,947,604 | required/optional | 2026-09-15 | 也在 transportation/utility |
| Not Enough Animations | `not-enough-animations` | 85,768,802 | required/unsupported | 2026-06-18 | 也在 decoration |
| 3D Skin Layers | `3dskinlayers` | 75,908,380 | required/unsupported | 2026-06-18 | 也在 decoration |
| Simple Voice Chat | `simple-voice-chat` | 68,454,128 | optional/optional | 2026-09-15 | 也在 utility/social |
| LambDynamicLights - Dynamic Lights | `lambdynamiclights` | 57,022,670 | required/unsupported | 2026-08-31 | 也在 decoration/utility |
| Sound Physics Remastered | `sound-physics-remastered` | 52,339,680 | required/optional | 2026-06-18 | 也在 utility |
| Curios API | `curios` | 30,825,660 | required/required | 2026-07-21 | 也在 equipment/utility |
| Comforts | `comforts` | 22,431,356 | required/required | 2026-06-22 | 也在 decoration |
| Better Third Person | `better-third-person` | 21,045,973 | required/unsupported | 2025-04-23 |  |
| Traveler's Backpack | `travelersbackpack` | 20,223,162 | required/required | 2026-09-01 | 也在 decoration/storage/equipment/management |
| EnhancedVisuals | `enhancedvisuals` | 18,696,195 | required/required | 2026-08-17 | 也在 decoration |
| L_Ender's Cataclysm | `l_enders-cataclysm` | 17,931,326 | required/required | 2026-08-22 | 也在 mobs/equipment |
| Cut Through | `cut-through` | 17,321,293 | required/unsupported | 2026-06-17 | 也在 equipment |
| Advancement Plaques | `advancement-plaques` | 16,968,488 | required/unsupported | 2026-06-30 |  |
| Item Highlighter | `item-highlighter` | 16,709,917 | required/unsupported | 2026-06-30 | 也在 equipment |
| Better Combat | `better-combat` | 16,307,720 | required/required | 2026-07-22 | 也在 equipment |
| Immersive Aircraft | `immersive-aircraft` | 14,633,663 | required/required | 2026-09-15 | 也在 transportation/technology |
| JourneyMap | `journeymap` | 14,309,848 | optional/optional | 2026-09-14 |  |
| Exposure | `exposure` | 13,934,562 | required/required | 2026-06-09 |  |
| Accessories | `accessories` | 13,412,698 | required/required | 2026-02-06 | 也在 equipment |
| Boat Item View | `boat-item-view` | 13,263,719 | required/unsupported | 2026-06-19 | 也在 decoration/transportation/equipment/management |
| Make Bubbles Pop | `make_bubbles_pop` | 13,093,165 | required/unsupported | 2026-09-05 | 也在 decoration |
| [Let's Do] Vinery | `lets-do-vinery` | 11,899,152 | required/required | 2026-01-28 | 也在 mobs/decoration/food |
| Villager Names | `villager-names-serilum` | 11,746,237 | optional/required | 2026-08-23 | 也在 social |
| Tips | `tips` | 11,642,824 | required/optional | 2026-02-12 |  |
| Shoulder Surfing Reloaded | `shoulder-surfing-reloaded` | 11,551,619 | required/unsupported | 2026-08-27 |  |
| GraveStone Mod | `gravestone-mod` | 11,547,742 | required/required | 2026-08-17 | 也在 technology |
| XaeroPlus | `xaeroplus` | 11,515,828 | required/unsupported | 2026-09-12 | 也在 transportation |
| Subtle Effects | `subtle-effects` | 11,050,018 | required/optional | 2026-06-17 | 也在 decoration |
| Advanced Netherite | `advanced-netherite` | 10,807,530 | required/required | 2026-06-21 | 也在 equipment |

## 生活质量与信息

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| FerriteCore | `ferrite-core` | 150,285,993 | optional/optional | 2026-03-24 | **已装** |
| Sodium Extra | `sodium-extra` | 95,669,681 | required/unsupported | 2026-07-10 |  |
| Reese's Sodium Options | `reeses-sodium-options` | 80,132,109 | required/unsupported | 2026-07-11 |  |
| ModernFix | `modernfix` | 77,229,035 | optional/optional | 2026-08-31 | **已装** |
| Just Enough Items (JEI) | `jei` | 76,561,011 | optional/optional | 2026-09-15 |  |
| Geckolib | `geckolib` | 69,110,705 | required/optional | 2026-09-06 |  |
| Jade 🔍 | `jade` | 67,387,222 | optional/optional | 2026-09-11 |  |
| More Culling | `moreculling` | 65,185,320 | required/required | 2026-09-07 | **已装** |
| FancyMenu | `fancymenu` | 64,905,562 | required/optional | 2026-08-28 |  |
| Bookshelf | `bookshelf-lib` | 45,361,021 | required/required | 2026-08-01 |  |
| Searchables | `searchables` | 38,472,520 | required/unsupported | 2026-06-17 |  |
| Controlling | `controlling` | 38,325,232 | required/unsupported | 2026-06-17 |  |
| Enchantment Descriptions | `enchantment-descriptions` | 36,871,209 | required/optional | 2026-08-20 |  |
| Patchouli | `patchouli` | 34,468,449 | required/required | 2026-07-10 |  |
| Distant Horizons | `distanthorizons` | 34,001,346 | optional/optional | 2026-07-07 |  |
| Model Gap Fix | `modelfix` | 32,123,389 | required/unsupported | 2025-10-14 |  |
| Packet Fixer | `packet-fixer` | 28,397,196 | optional/optional | 2026-07-16 |  |
| EMI | `emi` | 28,341,922 | required/optional | 2026-05-13 |  |
| MidnightLib | `midnightlib` | 27,618,491 | optional/optional | 2026-09-13 |  |
| Cubes Without Borders | `cubes-without-borders` | 27,264,885 | required/unsupported | 2026-06-21 |  |
| Remove Reloading Screen | `rrls` | 25,689,102 | required/unsupported | 2026-07-31 |  |
| Better Advancements | `better-advancements` | 25,443,666 | required/unsupported | 2026-08-03 |  |
| Visual Workbench | `visual-workbench` | 25,314,508 | required/required | 2026-09-02 |  |
| Puzzle | `puzzle` | 25,170,893 | required/unsupported | 2026-08-31 |  |
| Sinytra Connector | `connector` | 24,928,945 | optional/optional | 2026-08-15 |  |
| Polymorph | `polymorph` | 24,550,957 | required/required | 2026-09-11 |  |
| NetherPortalFix | `netherportalfix` | 23,668,045 | unsupported/required | 2026-06-16 | 也在 social |
| e4mc | `e4mc` | 23,396,894 | required/required | 2026-07-28 | 也在 social |
| Controlify (Controller support) | `controlify` | 23,050,537 | required/optional | 2026-09-03 |  |

## 科技与自动化

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Euphoria Patches | `euphoria-patches` | 27,067,572 | required/unsupported | 2026-09-15 | 也在 utility |
| Quark | `quark` | 24,449,527 | required/required | 2026-09-14 | 也在 utility |
| Alternate Current | `alternate-current` | 11,114,334 | unsupported/required | 2026-09-15 |  |
| Create Deco | `create-deco` | 7,365,438 | required/required | 2026-04-18 |  |
| Create: Connected | `create-connected` | 6,607,446 | required/required | 2026-08-31 |  |
| Create: Diesel Generators | `create-diesel-generators` | 5,735,168 | required/required | 2026-07-30 |  |
| Create: New Age | `create-new-age` | 5,304,041 | required/required | 2026-06-08 |  |
| CC: Tweaked | `cc-tweaked` | 5,057,837 | required/required | 2026-08-08 |  |
| Only Bottle Caps | `only-bottle-caps` | 4,096,107 | required/required | 2026-09-06 |  |
| Create Jetpack | `create-jetpack` | 4,001,748 | required/required | 2026-07-31 |  |
| ImmersiveThunder | `immersivethunder` | 3,705,197 | required/unsupported | 2025-06-30 |  |
| Immersive Engineering | `immersiveengineering` | 3,488,363 | required/required | 2025-07-05 |  |
| Create Ore Excavation | `create-ore-excavation` | 3,389,076 | required/required | 2025-11-16 |  |
| Create: Dreams & Desires | `create-dreams-and-desires` | 3,382,494 | required/required | 2026-05-04 |  |
| Applied Energistics 2 Wireless Terminals | `applied-energistics-2-wireless-terminals` | 2,955,723 | required/required | 2026-07-31 |  |
| Just Hammers | `just-hammers` | 2,908,440 | required/required | 2026-06-28 |  |
| Rechiseled: Create | `rechiseled-create` | 2,849,946 | required/required | 2026-06-22 |  |
| Alloy Forgery | `alloy-forgery` | 2,666,266 | required/required | 2026-09-10 |  |
| Create: Framed | `create-framed` | 2,551,203 | required/required | 2026-07-08 |  |
| Create: Power Loader | `create-power-loader` | 2,483,215 | required/required | 2026-05-22 |  |
| Mekanism Generators | `mekanism-generators` | 2,460,868 | required/required | 2026-04-10 |  |
| Ad-Astra: Giselle Addon | `ad-astra-giselle-addon` | 2,346,635 | required/required | 2026-08-30 |  |
| Moving Elevators | `moving-elevators` | 2,075,786 | required/required | 2026-09-10 |  |
| Modern Industrialization | `modern-industrialization` | 1,911,725 | required/required | 2026-09-12 |  |
| Simple Radio | `simple-radio` | 1,906,513 | required/required | 2026-06-19 | 也在 social |
| Create: Pattern Schematics | `create-pattern-schematics` | 1,861,971 | required/required | 2026-04-17 |  |

## 装备与工具

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| VeinMiner | `veinminer` | 82,840,493 | optional/required | 2026-08-22 | 也在 technology/utility |
| VeinMiner Hotkey | `veinminer-client` | 57,293,064 | required/unsupported | 2026-08-23 | 也在 utility |
| VeinMiner Enchantment | `veinminer-enchantment` | 16,902,712 | optional/required | 2026-07-26 |  |
| Easy Anvils | `easy-anvils` | 16,193,458 | required/required | 2026-08-31 |  |
| Cobblemon PokeNav | `cobblemon-pokenav` | 9,904,172 | required/required | 2026-09-08 | 也在 social |
| Simply Swords | `simply-swords` | 9,556,169 | required/required | 2026-08-27 |  |
| Charm of Undying | `charm-of-undying` | 8,765,521 | required/required | 2024-10-26 |  |
| Immersive Melodies | `immersive-melodies` | 8,646,732 | required/required | 2026-09-09 | 也在 social |
| uku's Armor HUD | `ukus-armor-hud` | 8,514,897 | required/unsupported | 2026-06-16 |  |
| Item Borders | `item-borders` | 8,384,693 | required/unsupported | 2026-06-30 |  |
| Dynamic Lights | `dynamic-lights` | 7,461,148 | optional/required | 2026-06-24 |  |
| Create Big Cannons | `create-big-cannons` | 7,447,044 | required/required | 2026-06-22 | 也在 technology |
| Immersive Armors | `immersive-armors` | 7,376,784 | required/required | 2026-07-28 | 也在 technology |
| GD656Killicon | `gd656killicon` | 7,310,668 | required/required | 2026-08-12 | 也在 technology |
| Enchanting Infuser | `enchanting-infuser` | 7,211,447 | required/required | 2026-06-19 |  |
| Better Archeology | `better-archeology` | 6,983,407 | required/required | 2026-08-04 |  |
| Library Ferret | `library-ferret` | 6,138,814 | required/required | 2026-09-06 |  |
| Simple Hats | `simple-hats` | 5,847,212 | required/required | 2026-02-09 | 也在 social |
| Farmer's Knives | `farmers-knives` | 5,591,888 | required/required | 2026-04-16 |  |
| SecurityCraft | `security-craft` | 5,441,042 | required/required | 2026-08-09 | 也在 technology |
| Wizards (RPG Series) | `wizards` | 5,284,002 | required/required | 2026-09-05 |  |
| Blossom Blade | `blossom-blade` | 5,141,348 | optional/required | 2024-10-12 |  |
| Elytra Trims | `elytra-trims` | 4,955,753 | optional/optional | 2026-08-25 |  |
| Ranged Weapon API | `ranged-weapon-api` | 4,806,013 | required/required | 2026-09-05 |  |
