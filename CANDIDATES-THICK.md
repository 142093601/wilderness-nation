# 候选 mod 清单（按功能位分组）

> 数据源：Modrinth 搜索接口，筛选条件 **`1.21.1` + `neoforge` + `project_type:mod`**，按下载量排序，每类取前 40。

> 核查方式：`tools/survey_mods.py`（可重跑；候选会随 mod 生态变化）。

> **这不是最终选型**——候选只回答「市场上有哪些」，选不选还要过设计要求（对照 `DESIGN.md` 的六个时代与反目标）与端侧/依赖检查。


## 汇总

| 功能位 | 候选数 | 已装 | 该类最高下载 |
|---|---|---|---|
| 建造与装饰（建材 / 家具 / 灯饰） | 186 | 2 | Iris Shaders（175,299,236） |
| 冒险（结构 / 地牢 / 维度 / 战利品） | 101 | 0 | JourneyMap（14,309,848） |
| 食物与农业 | 82 | 0 | AppleSkin（88,901,200） |
| 装备与工具 | 57 | 0 | Nature's Compass（25,400,559） |
| 生活质量与信息 | 56 | 4 | FerriteCore（150,285,993） |
| 玩法机制（谨慎：改动大） | 40 | 0 | Client Tweaks（10,050,776） |
| 存储与物流 | 39 | 0 | Carry On（25,392,803） |
| 交通 | 29 | 0 | Waystones（25,030,337） |
| 关键词补充 | 16 | 0 | Polytone（16,962,726） |
| 前置库（自动随依赖装） | 13 | 1 | Moonlight Lib（39,993,333） |
| 猎奇（本包不需要） | 13 | 0 | Diagonal Windows（4,033,378） |
| 管理与治理 | 10 | 0 | Bookshelf Inspector（1,926,181） |
| 社交与联机 | 5 | 0 | Sequoia Deprecated（4,130） |
| 性能（已单独成组） | 4 | 0 | Faster Iris Shadow Mapper [FISM]（570,458） |
| 生物与威胁 | 3 | 0 | Cobblemon Spawn Notification Discord Integration（12,044） |
| 经济（本包暂不需要货币） | 2 | 0 | Farming for Blockheads（505,140） |
| 科技与自动化 | 2 | 0 | Create Train Parts（471,658） |
| 魔法/特殊系统（谨慎） | 2 | 0 | Ars Additions（363,693） |
| 世界与地理（群系 / 地形 / 结构） | 2 | 0 | Dense Trees（13,989） |
| **合计** | **662** | 7 | — |

## 建造与装饰（建材 / 家具 / 灯饰）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Iris Shaders | `iris` | 175,299,236 | required/unsupported | 2026-09-13 |  |
| [ETF] Entity Texture Features | `entitytexturefeatures` | 100,236,772 | required/unsupported | 2026-09-02 | 也在 utility |
| [EMF] Entity Model Features | `entity-model-features` | 95,105,207 | required/unsupported | 2026-09-03 | 也在 utility |
| Not Enough Animations | `not-enough-animations` | 85,768,802 | required/unsupported | 2026-06-18 |  |
| 3D Skin Layers | `3dskinlayers` | 75,908,380 | required/unsupported | 2026-06-18 |  |
| Continuity | `continuity` | 72,629,981 | required/unsupported | 2026-06-16 | 也在 utility |
| LambDynamicLights - Dynamic Lights | `lambdynamiclights` | 57,022,670 | required/unsupported | 2026-08-31 | 也在 utility |
| Chat Heads | `chat-heads` | 48,313,487 | required/unsupported | 2026-08-13 |  |
| AmbientSounds | `ambientsounds` | 38,216,294 | required/unsupported | 2026-06-22 |  |
| Biomes O' Plenty | `biomes-o-plenty` | 35,422,936 | required/required | 2026-09-07 |  |
| BetterF3 | `betterf3` | 33,699,404 | required/unsupported | 2026-07-09 | 也在 utility |
| Supplementaries | `supplementaries` | 27,045,908 | required/required | 2026-09-10 | 也在 storage/查询:decoration |
| Create | `create` | 25,933,802 | required/required | 2026-04-21 | **已装** |
| Handcrafted | `handcrafted` | 25,749,648 | required/required | 2025-03-22 |  |
| Farmer's Delight | `farmers-delight` | 23,581,558 | required/required | 2026-08-29 | 也在 food/equipment/查询:cooking/查询:farming |
| Physics Mod | `physicsmod` | 23,236,582 | required/unsupported | 2026-09-13 |  |
| CustomSkinLoader | `customskinloader` | 23,141,915 | required/unsupported | 2026-06-21 |  |
| Athena | `athena-ctm` | 22,935,906 | required/unsupported | 2026-05-09 |  |
| Sodium Shadowy Path Blocks (SSPB) | `sodium-shadowy-path-blocks` | 22,550,324 | required/unsupported | 2026-05-25 |  |
| Comforts | `comforts` | 22,431,356 | required/required | 2026-06-22 |  |
| Chipped | `chipped` | 22,392,618 | required/required | 2024-10-15 |  |
| YUNG's Better Nether Fortresses | `yungs-better-nether-fortresses` | 22,357,909 | unsupported/required | 2026-09-10 |  |
| Chat Animation [Smooth Chat] | `chatanimation` | 21,829,831 | required/unsupported | 2026-07-05 |  |
| YUNG's Better Ocean Monuments | `yungs-better-ocean-monuments` | 21,490,694 | unsupported/required | 2026-09-10 |  |
| YUNG's Better Dungeons | `yungs-better-dungeons` | 21,481,263 | unsupported/required | 2026-09-10 | **已装** |
| Particle Rain | `particle-rain` | 20,372,598 | required/unsupported | 2026-08-24 |  |
| Traveler's Backpack | `travelersbackpack` | 20,223,162 | required/required | 2026-09-01 | 也在 storage/equipment/查询:backpack |
| YUNG's Better Mineshafts | `yungs-better-mineshafts` | 19,882,207 | unsupported/required | 2026-09-10 |  |
| YUNG's Better Jungle Temples | `yungs-better-jungle-temples` | 19,817,884 | unsupported/required | 2026-09-10 |  |
| YUNG's Better End Island | `yungs-better-end-island` | 19,337,318 | unsupported/required | 2026-09-10 |  |
| EnhancedVisuals | `enhancedvisuals` | 18,696,195 | required/required | 2026-08-17 |  |
| Sodium Dynamic Lights | `sodium-dynamic-lights` | 18,392,067 | required/unsupported | 2025-04-10 |  |
| YUNG's Better Strongholds | `yungs-better-strongholds` | 17,897,031 | unsupported/required | 2026-09-10 |  |
| Prism | `prism-lib` | 17,529,482 | required/unsupported | 2026-06-30 |  |
| YUNG's Better Witch Huts | `yungs-better-witch-huts` | 17,333,204 | unsupported/required | 2026-09-10 |  |
| Wavey Capes | `wavey-capes` | 17,170,694 | required/unsupported | 2026-09-06 |  |
| YUNG's Better Desert Temples | `yungs-better-desert-temples` | 16,285,484 | unsupported/required | 2026-09-10 |  |
| Another Furniture | `another-furniture` | 15,439,658 | required/required | 2026-04-16 | 也在 查询:furniture |
| YUNG's Bridges | `yungs-bridges` | 15,435,832 | unsupported/required | 2026-06-24 | 也在 查询:bridges |
| Yes Steve Model | `yes-steve-model` | 14,678,094 | optional/optional | 2026-05-02 |  |
| MmmMmmMmmMmm | `mmmmmmmmmmmm` | 12,178,495 | required/required | 2026-09-10 |  |
| Macaw's Windows | `macaws-windows` | 10,847,308 | required/required | 2026-06-20 |  |
| Macaw's Bridges | `macaws-bridges` | 9,069,735 | required/required | 2026-06-20 |  |
| Rechiseled | `rechiseled` | 8,146,927 | required/required | 2026-09-13 |  |
| Macaw's Roofs | `macaws-roofs` | 7,642,690 | required/required | 2026-06-20 |  |
| Cluttered | `cluttered` | 7,437,914 | required/required | 2025-12-27 |  |
| Create Deco | `create-deco` | 7,365,438 | required/required | 2026-04-18 |  |
| Macaw's Holidays | `macaws-holidays` | 4,177,210 | required/required | 2026-06-20 |  |
| Armor Statues | `armor-statues` | 3,463,940 | required/optional | 2026-06-18 |  |
| Dusty Decorations | `dusty-decorations` | 3,258,225 | required/required | 2026-04-12 |  |
| Create Goggles | `create-goggles` | 3,167,171 | required/required | 2026-05-11 |  |
| KaleidoscopeChineseFood | `kaleidoscopechinesefood` | 2,435,546 | optional/optional | 2026-09-13 |  |
| 3D Placeable Food | `3d-placeable-food` | 2,144,917 | required/required | 2026-06-28 | 也在 查询:decoration |
| Clayworks | `clayworks` | 2,140,877 | required/required | 2026-05-07 |  |
| Woodworks | `woodworks` | 2,127,606 | required/required | 2025-10-17 |  |
| FramedBlocks | `framedblocks` | 2,113,291 | required/required | 2026-08-27 |  |
| Armor Stand Arms | `armor-stand-arms` | 2,066,473 | optional/required | 2026-09-13 |  |
| [Let's Do] Camping | `lets-do-camping` | 1,963,189 | required/required | 2026-03-14 |  |
| Valhelsia Furniture | `valhelsia-furniture` | 1,952,481 | required/required | 2025-10-28 |  |
| Immersive Armor HUD | `immersive-armor-hud` | 1,757,908 | required/unsupported | 2025-04-03 |  |
| Paladin's Furniture Mod | `paladins-furniture` | 1,717,782 | required/required | 2026-08-14 |  |
| Trail&Tales Delight | `trailtales-delight` | 1,684,552 | required/required | 2026-08-05 |  |
| Chisel Reborn | `chisel-reborn` | 1,427,193 | required/required | 2026-08-25 |  |
| Display Delight | `display-delight` | 1,372,379 | required/required | 2026-06-13 |  |
| Decocraft | `decocraft` | 1,358,926 | required/required | 2026-04-09 |  |
| Thaumon | `thaumon` | 1,336,556 | required/required | 2024-07-01 |  |
| Whimsy Deco | `whimsy-deco` | 1,331,549 | required/required | 2026-08-05 |  |
| Overweight Farming | `overweight-farming` | 1,284,119 | required/required | 2025-11-17 |  |
| Fishermen's Trap [Neo/Fabric] | `fishermens-trap` | 1,274,268 | required/required | 2025-04-13 |  |
| Ultramarine | `ultramarine` | 1,244,266 | required/required | 2026-04-10 | 也在 查询:decoration |
| Cooking for Blockheads | `cooking-for-blockheads` | 1,133,819 | required/required | 2026-09-08 |  |
| Vanilla Cookbook | `vanillacookbook` | 1,100,505 | required/required | 2026-06-14 |  |
| MapFrontiers | `mapfrontiers` | 1,028,485 | optional/optional | 2026-08-21 |  |
| Jaden's Nether Expansion Delight | `jadens-nether-expansion-delight` | 969,809 | required/required | 2026-06-11 |  |
| Armor Trim Item Fix | `armor-trim-item-fix` | 969,486 | required/unsupported | 2026-07-31 |  |
| Ender IO | `enderio` | 933,343 | required/required | 2026-09-07 |  |
| Shutters | `shutters` | 774,788 | required/required | 2026-07-29 | 也在 查询:decoration |
| Builder's Delight | `builders-delight` | 767,236 | required/required | 2026-07-27 |  |
| Veggies Delight (A Farmer's Delight Add-on) | `veggies-delight` | 557,980 | required/required | 2026-07-22 |  |
| 3D Armor | `armor-3d` | 529,475 | required/unsupported | 2026-07-13 |  |
| Furnection | `lyivxs-furniture` | 460,587 | required/required | 2025-08-26 |  |
| FurniCraft | `ketkets-furnicraft` | 428,173 | optional/required | 2026-07-04 |  |
| Fright's Delight | `frights-delight` | 421,004 | required/required | 2026-05-08 |  |
| Ornamental Plants | `more-ornamental-plants` | 390,972 | required/required | 2026-09-06 |  |
| Brick & Mortar | `brick-and-mortar` | 367,685 | required/required | 2026-07-08 |  |
| Dark Window Bar | `dark-window-bar` | 312,813 | required/unsupported | 2025-07-01 |  |
| Rechiseled: Chipped | `rechiseled-chipped` | 311,546 | required/required | 2026-01-25 |  |
| Create Train Utilities (Create Train Doors) | `create-trainutilities` | 308,135 | required/required | 2026-08-16 |  |
| Furnish | `furnish-furniture` | 284,659 | required/required | 2026-04-15 |  |
| XK's Decoration | `xks-decoration` | 279,413 | required/required | 2026-08-13 |  |
| Firmalife | `firmalife` | 259,991 | required/required | 2026-08-29 | 也在 查询:farming |
| Laser Bridges & Doors | `laser-bridges-and-doors` | 225,314 | required/required | 2026-08-08 |  |
| Unusual Furniture | `unusual-furniture` | 223,420 | required/required | 2025-12-14 |  |
| AntiBlocksReChiseled | `antiblocksrechiseled` | 212,712 | required/required | 2026-08-14 |  |
| Titlebar Changer | `titlebar-changer` | 205,075 | required/unsupported | 2025-08-21 |  |
| Furniture Expanded | `furniture-expanded` | 183,261 | required/required | 2025-03-26 |  |
| Useful Backpacks | `useful-backpacks` | 165,315 | required/required | 2025-05-22 |  |
| Furnitury - Vanilla Styled Furniture | `furnitury` | 146,424 | optional/optional | 2026-09-15 |  |
| squaremap | `squaremap` | 142,335 | unsupported/required | 2026-07-29 |  |
| MDM (formerly Modern Decorations Mod) | `modern-decorations-mod` | 133,375 | required/required | 2026-09-03 | 也在 查询:decoration |
| Skniro's Furniture | `skniros-furniture` | 127,375 | required/required | 2026-08-08 |  |
| Bibliocraft Legacy | `bibliocraft-legacy` | 125,168 | required/required | 2026-05-31 |  |
| Roads and Roofs TFC | `roads-and-roofs-tfc` | 123,660 | required/required | 2026-08-04 |  |
| City Craft | `rexs-city-craft` | 118,091 | required/required | 2026-01-12 |  |
| NhatJS's Furniture Mod | `nhatjs-furniture-mod` | 111,539 | required/unsupported | 2026-04-19 |  |
| Silent's Gems | `silents-gems` | 111,118 | required/required | 2026-05-17 |  |
| MrCrayfish's Furniture Mod Tools: Refurbished | `mrcrayfishs-furniture-mod-tools-refurbished` | 102,806 | required/required | 2026-07-14 |  |
| Modern Furniture | `modern-furniture` | 98,067 | required/unsupported | 2024-12-22 | 也在 查询:decoration |
| ModernDeco | `moderndeco` | 91,404 | required/required | 2025-12-09 |  |
| Urban Decor | `urban-decor` | 89,265 | required/required | 2026-06-15 | 也在 查询:decoration |
| EmbellishCraft | `embellishcraft` | 88,339 | required/required | 2025-06-06 |  |
| Backpacked: World of Color | `backpacked-world-of-color` | 86,194 | unsupported/required | 2026-05-21 |  |
| Elegant Countryside | `enchanted-wilderness-elegant-countryside.` | 79,256 | required/required | 2025-12-09 |  |
| Platforms | `platforms` | 79,178 | required/required | 2025-08-22 |  |
| PixelTweaks | `pixeltweaks` | 70,827 | optional/optional | 2026-03-26 |  |
| Catenary | `catenary` | 69,387 | optional/required | 2026-09-12 |  |
| Gaming Furniture | `gaming-furniture` | 62,471 | required/unsupported | 2024-12-22 |  |
| Pretty In Pink | `pretty-in-pink` | 57,714 | required/required | 2026-03-03 |  |
| Macaw's Quark | `macaws-quark` | 53,364 | required/required | 2026-06-23 | 也在 查询:bridges |
| Create: Mixed Casing | `create-mixed-casing` | 51,376 | required/required | 2026-04-10 |  |
| Spectral Decorations | `spectral-decorations` | 51,270 | required/required | 2026-08-24 |  |
| The Block Box | `the-block-box` | 50,394 | required/required | 2026-05-14 | 也在 查询:building blocks/查询:decoration |
| More Crafting Tables! | `more-crafting-tables` | 48,855 | required/required | 2025-07-07 |  |
| Mythrais | `mythrais` | 48,630 | required/required | 2026-08-22 | 也在 查询:windows/查询:decoration |
| SI: Refined Obsidian | `si-refined-obsidian` | 46,995 | required/required | 2025-08-01 |  |
| Mech Trowel | `mech-trowel` | 45,578 | required/required | 2026-05-22 |  |
| Furniture Plan | `furniture-plan` | 43,711 | required/required | 2025-02-11 |  |
| Pottery | `pottery` | 42,876 | required/required | 2026-09-11 |  |
| Wilderness | `wilderness_` | 41,516 | optional/required | 2025-06-19 |  |
| Furniture with create and sable compat | `lets-do-furniture-another-furniture-handcrafted-create-sable` | 39,211 | required/required | 2026-09-03 |  |
| Block Pack | `block-pack` | 38,133 | required/required | 2026-08-26 |  |
| Better Fishtanks | `better-fishtanks` | 37,666 | required/required | 2026-07-20 |  |
| Pyrite | `pyrite` | 35,659 | required/required | 2026-06-30 |  |
| Backpacked: Shells | `backpacked-shells` | 33,883 | required/required | 2026-04-30 |  |
| Farmhouse Decorations | `farmhouse-decorations` | 33,816 | required/required | 2026-09-05 | 也在 查询:decoration |
| Furnies | `furnies` | 33,158 | required/required | 2025-02-17 |  |
| Thingamajigs | `thingamajigs` | 30,496 | required/required | 2026-09-09 |  |
| Chiseled Bookshelves Add Enchantment Power [PurpurPack] | `purpurpacks-chiseled-bookshelves-add-enchantment-power` | 30,067 | optional/required | 2026-06-24 |  |
| Aesthetic Storage | `aesthetic-storage` | 29,203 | required/required | 2025-11-03 |  |
| NhatJS's NGMC Furniture Project | `nhatjs-ngmc-furniture-project` | 27,034 | required/optional | 2026-01-08 |  |
| AcryliCraft | `acrylicraft` | 26,261 | required/unsupported | 2026-07-04 |  |
| Ytones | `ytones` | 25,822 | required/required | 2025-10-08 |  |
| Mel's DeCo | `mels-deco` | 20,244 | required/required | 2025-12-03 |  |
| Zrikon's Furniture : Medieval | `zrikons-furniture-medieval` | 16,303 | required/required | 2026-03-01 |  |
| Solar Cooker | `solar-cooker` | 16,073 | required/required | 2026-09-05 |  |
| Wet Backapacks | `wet-backapacks` | 15,241 | optional/required | 2026-04-21 |  |
| Spelunker's Palette | `spelunkers-palette` | 12,492 | required/required | 2026-05-18 |  |
| Sundries & Decor | `sundriesanddecor` | 11,196 | required/required | 2026-09-07 | 也在 查询:building blocks |
| Verdance | `verdance` | 11,158 | required/required | 2026-05-28 |  |
| Redecorate | `redecorate` | 10,200 | required/required | 2026-06-03 |  |
| Aesthetic Windows | `aesthetic-windows` | 9,941 | required/required | 2025-11-28 |  |
| Iden's Decor | `idens-decor` | 9,762 | required/required | 2026-09-07 |  |
| Name Tag Upgrade | `name-tag-upgrade` | 9,139 | required/required | 2026-09-06 |  |
| Bedrock Crafter | `bedrock-crafter` | 8,575 | required/required | 2026-08-31 |  |
| More Colorful | `morecolorful` | 5,839 | required/required | 2026-06-08 |  |
| Chiseled | `hyper-chiseled` | 5,691 | required/required | 2025-12-21 | 也在 查询:chisel |
| Carpentry & Chisels | `carpentry-and-chisels` | 5,586 | required/required | 2025-07-04 | 也在 查询:chisel |
| Create Train Tank Mod | `createtraintankmod` | 5,502 | required/required | 2026-05-07 |  |
| Better Chiseled Bookshelf | `enchantlib` | 4,169 | required/required | 2026-05-15 |  |
| Patchwork | Building Blocks | `patchwork-blocks` | 3,987 | required/required | 2026-09-01 |  |
| Shroomscape | `shroomscape` | 3,758 | required/required | 2025-03-29 |  |
| Ancient Gems: Reforged | `ancient-gems-reforged` | 3,271 | required/required | 2026-06-11 |  |
| Blocks Abound | `blocks-abound` | 2,721 | required/required | 2026-08-20 |  |
| Chiseler | `chiseler` | 2,712 | required/required | 2025-07-08 |  |
| Arcane Armor Trims | `arcane-armor-trims` | 2,460 | required/required | 2026-03-22 |  |
| Box3Blocks（神岛材质包） | `box3-blocks` | 2,366 | required/required | 2026-06-23 |  |
| Bountiful Pears | `bountiful-pears` | 2,314 | required/required | 2026-07-20 |  |
| Silverwood Trees | `silverwood-trees` | 2,229 | required/required | 2025-08-13 |  |
| Xerca's Building Blocks | `xerca-blocks` | 1,985 | required/required | 2026-07-27 |  |
| Whisperleaf Trees | `whisperleaf-trees` | 1,774 | required/required | 2025-08-17 |  |
| Tinted  Planks | `tinted-planks` | 1,736 | required/required | 2025-08-17 |  |
| Mosaics | `mosaics` | 1,674 | required/required | 2026-06-29 |  |
| owen233666's windows | `owen233666s-windows` | 743 | required/required | 2026-01-21 |  |
| UrbanForma-neo | `urbanforma-neo` | 712 | required/required | 2026-08-04 |  |
| Gilded Blackstone Blocks | `gilded-blackstone-blocks` | 685 | required/required | 2025-09-27 |  |
| Wicker | `wicker` | 560 | required/required | 2025-03-01 |  |
| Dyeable Wood | `dyeablewood` | 399 | required/required | 2024-10-10 |  |
| Andro's Palette | `andros-palette` | 317 | required/required | 2025-11-14 |  |
| Polished Planks | `polished-planks` | 246 | required/required | 2026-03-22 |  |
| Quark: Quilts n' Stains | `quark-quilts-n-stains` | 180 | required/required | 2026-06-18 |  |
| FarmerChaos | `farmerchaos` | 86 | required/required | 2026-09-13 |  |
| Bear Necessities | `bearnecessities` | 86 | required/required | 2026-09-09 |  |
| More Metal Blocks | `more-metal-blocks` | 50 | required/required | 2026-08-12 |  |
| Moss everywhere | `moss-everywhere` | 45 | unsupported/required | 2026-09-09 |  |
| Octachisel | `octachisel` | 40 | required/required | 2026-08-21 |  |
| Epiphany Journal | `epiphany-journal` | 11 | required/required | 2026-08-19 |  |

## 冒险（结构 / 地牢 / 维度 / 战利品）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| JourneyMap | `journeymap` | 14,309,848 | optional/optional | 2026-09-14 |  |
| Ars Nouveau's Flavors & Delight | `arsdelight` | 3,246,932 | required/required | 2026-06-22 |  |
| Abridged | `abridged` | 3,204,038 | unsupported/required | 2026-06-27 |  |
| Antique Atlas 4 | `antique-atlas-4` | 2,807,101 | required/unsupported | 2026-01-05 |  |
| Rustic Delight | `rustic-delight` | 2,802,249 | required/required | 2026-08-19 |  |
| More Armor Trims | `more-armor-trims` | 2,629,998 | required/required | 2026-08-20 |  |
| Youkai's Homecoming | `youkaishomecoming` | 2,607,755 | required/required | 2026-04-28 |  |
| Enderite Mod | `enderite-mod` | 2,093,711 | required/required | 2026-08-30 |  |
| Fruits Delight | `fruits-delight` | 2,062,649 | required/required | 2026-06-23 |  |
| Dungeon's Delight | `dungeons_delight` | 2,061,883 | required/required | 2026-09-13 |  |
| Storage Delight | `storage-delight` | 1,984,200 | required/required | 2026-07-08 |  |
| Naturally Trimmed | `naturally-trimmed` | 1,969,683 | unsupported/required | 2026-07-01 |  |
| Aether Addon: Protect Your Moa | `aether-protect-your-moa` | 1,816,452 | required/required | 2025-02-27 |  |
| Magic Vibe Decorations | `magic-vibe-decorations` | 1,756,727 | required/required | 2026-08-14 |  |
| Additional Additions: Vanilla+ QoL, Sniffers, Music, Food | `addadd` | 1,732,251 | required/required | 2026-08-30 | 也在 查询:farming |
| Cultural Delights | `cultural-delights` | 1,587,904 | required/required | 2026-09-14 |  |
| Epic Knights: Shields Armor and Weapons | `epic-knights-shields-armor-and-weapons` | 1,566,439 | required/required | 2026-09-14 |  |
| Twilight's Flavor & Delight | `twilight-delight` | 1,561,859 | required/required | 2026-06-07 |  |
| Simple Netherite Horse Armor | `simple-netherite-horse-armor` | 1,510,130 | required/required | 2025-09-30 |  |
| Armor of the Ages | `armor-of-the-ages` | 1,395,117 | required/required | 2026-05-30 |  |
| VoxelMap-Updated | `voxelmap-updated` | 1,324,449 | required/unsupported | 2026-09-15 |  |
| Backpacks! | `vanilla-backpacks` | 1,189,690 | optional/required | 2026-06-20 |  |
| SkyBlock - Standard | `standard-skyblock` | 1,170,215 | optional/required | 2026-09-13 |  |
| Dungeons and Taverns Nether Fortress Overhaul | `dungeons-and-taverns-nether-fortress-overhaul` | 762,960 | optional/required | 2026-08-31 |  |
| Fetzi's Asian Decoration | `fetzis-asian-decoration` | 658,505 | required/required | 2026-08-01 | 也在 查询:decoration |
| Useful Slime | `useful-slime` | 655,317 | required/required | 2025-03-09 |  |
| Spooky Foods | `spooky-foods` | 633,372 | required/required | 2026-05-31 |  |
| Fantasy Armor (Medieval Series) | `fantasy_armor` | 625,632 | required/required | 2026-03-31 |  |
| Animal Armor Trims - Horse & Wolf | `animal-armor-trims` | 624,838 | required/required | 2026-05-16 |  |
| Golden Foods! | `golden-foods` | 604,177 | required/required | 2026-08-23 |  |
| Samurai Dynasty | `epic-samurais` | 593,548 | required/required | 2025-12-29 |  |
| EnderPack | `enderpack` | 585,109 | required/required | 2026-05-21 |  |
| DivineRPG | `divinerpg` | 584,880 | required/required | 2026-09-13 |  |
| Knight Quest | `knight-quest` | 536,154 | required/required | 2026-06-15 |  |
| Forbidden and Arcanus  | `forbidden-arcanus` | 506,427 | required/required | 2026-07-27 |  |
| DarkSmelting | `darksmelting` | 492,599 | required/required | 2026-04-30 |  |
| BlueMap | `bluemap` | 485,122 | unsupported/required | 2026-09-10 |  |
| Arts & Crafts | `artsandcrafts` | 476,709 | required/required | 2026-05-27 |  |
| Map Link  (formerly Remote Player Waypoints for Xaero's Map) | `maplink` | 397,274 | required/unsupported | 2026-08-11 |  |
| Surveystones | `surveystones` | 351,743 | required/required | 2026-02-03 |  |
| Order to cook下单了！ | `order-to-cook` | 344,450 | required/required | 2026-08-12 |  |
| Luminous: Nether | `luminous-nether` | 318,605 | required/required | 2026-07-12 |  |
| yyz's backpack | `yyzs-backpack` | 295,521 | required/required | 2026-09-13 |  |
| Jake's Build Tools | `jakes-build-tools` | 288,904 | optional/required | 2026-06-17 |  |
| Immersive Winds | `immersive-winds` | 248,682 | required/required | 2026-08-05 |  |
| JourneyMap  Web Map | `journeymap-web-map` | 203,550 | required/unsupported | 2026-09-09 |  |
| JourneyMap Teams | `journeymap-teams` | 191,485 | optional/optional | 2026-07-09 |  |
| Resource Backpack's | `resource-backpacks` | 160,023 | required/required | 2026-08-17 |  |
| Cheaper Maps | `cheaper-maps` | 124,745 | optional/required | 2024-07-12 |  |
| Tiny Mob Farm Remastered | `tiny-mob-farm-remastered` | 121,772 | required/required | 2026-07-30 |  |
| Lios Hobbit Hill Village | `hobbit-hill-village` | 109,094 | unsupported/required | 2026-03-26 |  |
| Kelka backpack's | `backpacks` | 92,276 | required/optional | 2026-07-19 |  |
| (Discontinued) FNaF's: Build & Decor (Java Edition) | `fnafs-decorations-java` | 80,787 | optional/optional | 2025-08-11 |  |
| Chestpack Backpacks | `chestpack-backpacks` | 65,581 | optional/required | 2025-04-18 |  |
| Rubinated Nether | `rubinated-nether` | 56,893 | required/required | 2025-12-10 |  |
| Beans Backpacks | `beans-backpacks-3` | 55,766 | required/required | 2026-08-25 |  |
| Vehicle Upgrade | `vehicle-upgrade` | 48,156 | required/required | 2026-06-18 |  |
| OmniCut: Better Wood, Copper, and Stone Cutter | `omnicut-better-wood-stone-cutter` | 42,943 | optional/required | 2026-06-28 |  |
| Create: Movable Tracks | `movable-tracks` | 38,490 | required/required | 2026-04-07 |  |
| Mochila | `mochila` | 38,090 | required/required | 2026-06-25 |  |
| Maple | `maple` | 36,560 | required/required | 2026-06-30 |  |
| Hoofprint | `hoofprint` | 31,777 | required/unsupported | 2026-03-30 |  |
| FA: Player Extension Compat | `fa-player-extension-compat` | 30,544 | required/unsupported | 2026-03-29 |  |
| PTS-Deco | `pts-deco` | 29,401 | required/required | 2026-09-11 |  |
| Backpacks for Dummies | `backpacks-for-dummies` | 28,526 | required/required | 2026-05-14 |  |
| Yukami's Sophisticated Backpack Tab | `yukamis-sophisticated-backpack-tab` | 28,265 | required/unsupported | 2026-09-13 | 也在 查询:backpack |
| [🎄] Seasonal Decorations | `seasonal-decorations` | 26,697 | required/required | 2025-12-27 |  |
| Skelun's XP Reward System | `xp-reward-system` | 24,640 | optional/required | 2026-07-29 |  |
| Japanese Castle - Structure ✅ | `japanese-castle` | 22,736 | optional/required | 2025-10-03 |  |
| Ancient Jungle Ring - Ruins Structure ✅ | `ancient-jungle-ring` | 14,497 | optional/required | 2025-10-03 |  |
| Mining Drills & Tools NeoForge | `mining-drills-tools` | 13,803 | unsupported/required | 2026-07-19 |  |
| OmniBlast: Blast Furnace and Smoker recipe list completed | `omniblast` | 12,826 | optional/required | 2026-06-28 | 也在 查询:quality of life |
| Fabulous Furniture (Java) | `fabulous-furniture-java` | 10,635 | required/optional | 2026-08-01 |  |
| Simple Backpacks | `simple-backpacks-by-jupresson` | 10,493 | required/required | 2026-09-04 |  |
| Overpacked | `overpacked` | 10,307 | required/required | 2026-09-10 |  |
| RPG Backpacks | `rpg-backpacks` | 9,343 | required/required | 2026-05-31 |  |
| Crafting QOL | `crafting-qol` | 8,626 | optional/required | 2026-06-12 |  |
| Keystone Expansion Pufferfish's Skills | `keystone-expansion-pufferfishs-skills` | 5,743 | required/required | 2026-01-20 |  |
| Curiosities! | `curiosities-syndicate` | 5,729 | required/required | 2026-04-08 |  |
| The Block of Angel | `angel-block-mod` | 5,712 | required/required | 2026-08-10 |  |
| Whole Cloth | `whole-cloth` | 5,261 | required/required | 2026-05-20 |  |
| Better Bamboo Wood | `better-bamboo-wood` | 4,061 | optional/required | 2024-10-23 |  |
| Chef's Workbench | `chefs-workbench` | 3,134 | required/required | 2026-09-01 |  |
| Building Blocks | `ccbb` | 3,008 | required/required | 2026-09-11 |  |
| Concoction! | `concoction!` | 2,948 | required/required | 2026-01-13 |  |
| Seaworthy Boats | `seaworthy-boats` | 2,505 | required/required | 2026-07-03 |  |
| Teyvat's Delight | `teyvats-delight` | 1,610 | required/required | 2026-09-05 |  |
| Angel Extra Utilities | `angel-utilities` | 1,603 | required/required | 2025-09-08 |  |
| More Essentials | `more-essentials` | 1,260 | required/required | 2026-07-01 |  |
| Useful Tools | `usefultools` | 1,142 | required/required | 2026-04-16 |  |
| Bridge Eggs by Juix | `bridge-eggs-by-juix` | 1,071 | optional/required | 2026-06-17 |  |
| Square's Addons | `squares-addons` | 1,015 | required/required | 2025-05-29 |  |
| LlamaBlocks | `llamablocks` | 922 | required/required | 2026-08-15 |  |
| Phase Pulse | `phase-pulse` | 776 | required/unsupported | 2026-03-02 |  |
| Biospheres Renewed | `yatta-biospheres` | 677 | optional/required | 2026-08-08 |  |
| The Missing Blocks Mod | `missing-blocks` | 591 | required/required | 2025-03-13 |  |
| Just add Craft! | `just-add-craft!` | 183 | required/required | 2024-11-03 |  |
| system95.exe | `system95.exe` | 102 | required/required | 2026-09-08 |  |
| Improved Drops | `improved-drops` | 101 | required/required | 2026-06-06 |  |
| BN's Chiseled Enchanting | `bns-chiseled-enchanting` | 93 | required/required | 2026-08-19 |  |
| A Chef's Dream | `a-chefs-dream` | 33 | required/required | 2026-08-01 |  |

## 食物与农业

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| AppleSkin | `appleskin` | 88,901,200 | optional/optional | 2026-06-18 | 也在 utility/查询:food |
| Smarter Farmers (farmers replant) | `smarter-farmers-farmers-replant` | 12,118,125 | unsupported/required | 2025-10-18 |  |
| RightClickHarvest | `rightclickharvest` | 12,081,396 | unsupported/required | 2026-06-16 |  |
| [Let's Do] Vinery | `lets-do-vinery` | 11,899,152 | required/required | 2026-01-28 |  |
| Oh The Biomes We've Gone | `oh-the-biomes-weve-gone` | 11,235,975 | required/required | 2026-06-16 |  |
| Create Crafts & Additions | `createaddition` | 10,445,056 | required/required | 2026-08-16 | 也在 storage/transportation |
| Aquamirae | `aquamirae` | 10,369,385 | required/required | 2026-09-08 | 也在 equipment |
| Nature's Spirit | `natures-spirit` | 10,185,248 | required/required | 2025-09-04 |  |
| The Aether | `aether` | 9,258,233 | required/required | 2025-10-03 | 也在 equipment/transportation |
| End's Delight | `ends-delight` | 9,146,978 | required/required | 2026-08-18 |  |
| Chef's Delight - Farmer's Delight Villagers | `chefs-delight` | 8,696,732 | required/required | 2026-04-06 |  |
| Ocean's Delight | `oceans-delight` | 8,627,003 | required/required | 2026-04-17 |  |
| [Let's Do] HerbalBrews | `lets-do-herbalbrews` | 8,350,503 | required/required | 2026-02-21 |  |
| [Let's Do] Beachparty | `lets-do-beachparty` | 8,150,900 | required/required | 2026-02-24 | 也在 equipment |
| Spawn | `spawn-mod` | 7,467,526 | required/required | 2026-09-14 |  |
| [Let's Do] Meadow | `lets-do-meadow` | 7,451,486 | required/required | 2026-02-24 | 也在 equipment |
| [Let's Do] Farm & Charm | `lets-do-farm-charm` | 7,445,821 | required/required | 2026-07-30 | 也在 transportation/查询:cooking |
| Create Slice & Dice | `slice-and-dice` | 7,205,098 | required/required | 2026-08-02 |  |
| [Let's Do] Candlelight - Farm&Charm compat | `lets-do-candlelight-farmcharm-compat` | 7,085,239 | required/required | 2026-03-13 |  |
| Serene Seasons | `serene-seasons` | 6,925,629 | required/required | 2026-09-05 |  |
| [Let's Do] Bakery - Farm&Charm Compat | `lets-do-bakery-farmcharm-compat` | 6,455,214 | required/required | 2026-02-15 |  |
| Expanded Delight | `expanded-delight` | 6,433,502 | required/required | 2026-01-10 |  |
| Kaleidoscope Cookery | `kaleidoscope-cookery` | 6,151,978 | required/required | 2026-06-17 | 也在 查询:food |
| Ad Astra | `ad-astra` | 5,604,304 | required/required | 2026-09-13 | 也在 transportation |
| Ecologics | `ecologics` | 5,445,916 | required/required | 2026-07-11 |  |
| Ender's Delight | `enders-delight` | 5,435,383 | required/required | 2026-08-03 |  |
| Create: Central Kitchen | `create-central-kitchen` | 5,165,193 | required/required | 2026-08-29 | 也在 查询:food |
| Adorable Hamster Pets | `adorable-hamster-pets` | 4,809,251 | required/required | 2026-09-08 | 也在 storage |
| More Delight (for Farmer's Delight) | `more-delight` | 4,795,096 | required/required | 2026-06-24 | 也在 storage |
| My Nether's Delight | `my-nethers-delight` | 4,793,603 | required/required | 2026-09-11 |  |
| Hybrid Aquatic | `hybrid-aquatic` | 4,671,007 | required/required | 2026-09-13 |  |
| [Let's Do] WilderNature | `lets-do-wildernature` | 4,571,459 | required/required | 2026-07-30 |  |
| YUNG's Cave Biomes | `yungs-cave-biomes` | 4,167,979 | required/required | 2026-08-19 |  |
| Gensokyo Delight ~ Youkais' Feasts | `gensokyo-delight-youkais-feasts` | 4,069,359 | required/required | 2026-04-28 | 也在 查询:food/查询:cooking |
| Harvest with ease | `harvest-with-ease` | 4,026,797 | unsupported/required | 2026-04-29 |  |
| Universal Bone Meal | `universal-bone-meal` | 4,019,912 | required/required | 2026-06-19 |  |
| Jaden's Nether Expansion | `jadens-nether-expansion` | 3,860,221 | required/required | 2026-08-22 |  |
| Ube's Delight | `ubes-delight` | 3,704,438 | required/required | 2026-06-20 | 也在 查询:food |
| Kaleidoscope World Liquor | `kaleidoscope-world-liquor` | 3,702,788 | required/required | 2026-09-06 |  |
| Autochef's Delight | `autochefs-delight` | 3,667,730 | required/required | 2026-04-30 |  |
| Crabber's Delight | `crabbers-delight` | 3,290,216 | required/required | 2026-09-03 |  |
| Leave My Bars Alone | `leave-my-bars-alone` | 3,144,831 | required/unsupported | 2026-06-19 |  |
| Food Effect Tooltips [Fabric / NeoForge] | `foodeffecttooltips` | 1,782,413 | required/unsupported | 2026-07-15 |  |
| Food Effect Tooltips (Forge) | `food-effect-tooltips-forge` | 1,326,226 | required/unsupported | 2026-06-27 |  |
| Seed Delight | `seed-delight` | 1,257,104 | required/required | 2024-09-20 |  |
| Create: Factory | `create-factory` | 1,171,477 | required/required | 2026-08-15 |  |
| Create: Food | `create-food` | 1,031,905 | required/required | 2026-08-08 |  |
| Midas Hunger | `midas-hunger` | 943,994 | required/required | 2024-12-20 |  |
| Refined Cooking | `refined-cooking` | 928,533 | required/required | 2026-07-29 |  |
| Create: Garnished | `create-garnished` | 778,530 | required/required | 2026-07-28 |  |
| Vegan Delight | `vegan-delight` | 609,963 | required/required | 2025-11-07 |  |
| Maid Restaurant | `maid-restaurant` | 603,397 | required/required | 2026-07-13 |  |
| TofuCraftReload | `tofucraftreload` | 530,442 | required/required | 2026-09-14 |  |
| Create Ratatouille | `create-ratatouille` | 391,364 | required/required | 2026-07-10 |  |
| Actually Harvest | `actually-harvest` | 252,299 | required/required | 2026-06-23 |  |
| Better Farming Right Click  + Right Click Harvest | `better-farming-right-click` | 75,725 | required/unsupported | 2025-11-16 |  |
| Applied Cooking | `applied-cooking` | 71,822 | required/required | 2026-07-28 |  |
| Tweaks Delight | `tweaks-delight` | 41,572 | required/unsupported | 2026-06-25 |  |
| Agritech: Evolved (ATE) | `agritech-evolved` | 40,107 | required/required | 2026-09-12 |  |
| Straw Golem Updated | `straw-golem-rebaled-updated` | 34,089 | required/required | 2026-08-01 |  |
| Agritech (AT2) | `agritech` | 23,071 | required/required | 2026-09-12 |  |
| Enhanced Farming | `enhanced-farming` | 22,626 | required/required | 2026-08-05 |  |
| Mama's Herbs and Harvest | `mamas-herbs-and-harvest` | 12,034 | required/required | 2026-07-02 |  |
| XP Farming | `xp-farming` | 10,112 | required/required | 2025-03-14 |  |
| Crops 'n' Corpses | `crops-n-corpses` | 7,100 | required/required | 2024-11-16 |  |
| Auto Replant Crops | `auto-replant-crops` | 5,835 | optional/required | 2026-02-10 |  |
| Better Farming with Hoes | `better-farming-with-hoes` | 5,028 | optional/required | 2026-02-01 |  |
| DarkRooms | `darkrooms` | 4,548 | unsupported/required | 2025-10-10 |  |
| Immersive Cooking & Farming | `immersive-cooking-adoon` | 3,729 | required/required | 2026-06-30 | 也在 查询:farming |
| Better Farming with Hoes - Farmers Delight Compatibility | `better-farming-with-hoes-farmers-delight-compatibility` | 3,459 | optional/required | 2026-02-01 |  |
| Pantrywork | `pantrywork` | 3,430 | unsupported/required | 2026-09-14 |  |
| Watering Overlay | `watering-overlay` | 2,977 | required/unsupported | 2025-06-01 |  |
| Customized | `customized` | 2,566 | required/required | 2025-11-11 |  |
| Nearby Delight | `nearby-delight` | 2,425 | required/required | 2026-08-19 |  |
| Fancy Food | `fancy-food` | 1,905 | required/required | 2026-06-20 |  |
| Puffish Cooking Experience | `puffish-cooking-experience` | 999 | required/required | 2026-01-26 |  |
| Alex's Caves Continued Delight | `alexs-caves-continued-delight` | 638 | required/required | 2026-08-21 |  |
| Farmer's Attributes | `farmers_attributes` | 597 | required/required | 2026-03-01 |  |
| Occult Cooking | `occult-cooking` | 256 | required/required | 2026-07-31 |  |
| FarmToTable | `farmtotable` | 209 | required/required | 2026-02-08 |  |
| Dice & Delish | `diceanddelish` | 157 | required/required | 2026-09-12 |  |
| DoctorSid's Food Expansion | `doctorsids-food-expansion` | 140 | required/required | 2026-08-29 |  |

## 装备与工具

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Nature's Compass | `natures-compass` | 25,400,559 | required/required | 2026-06-17 |  |
| Dungeons and Taverns | `dungeons-and-taverns` | 20,970,275 | optional/required | 2026-08-31 |  |
| L_Ender's Cataclysm | `l_enders-cataclysm` | 17,931,326 | required/required | 2026-08-22 |  |
| Cut Through | `cut-through` | 17,321,293 | required/unsupported | 2026-06-17 |  |
| VeinMiner Enchantment | `veinminer-enchantment` | 16,902,712 | optional/required | 2026-07-26 |  |
| Item Highlighter | `item-highlighter` | 16,709,917 | required/unsupported | 2026-06-30 |  |
| Better Combat | `better-combat` | 16,307,720 | required/required | 2026-07-22 |  |
| Easy Anvils | `easy-anvils` | 16,193,458 | required/required | 2026-08-31 |  |
| Deeper and Darker | `deeperdarker` | 15,359,457 | required/required | 2026-07-11 | 也在 查询:armor |
| First-person Model | `first-person-model` | 13,982,106 | required/unsupported | 2026-06-21 |  |
| Elytra Slot | `elytra-slot` | 13,595,687 | required/required | 2025-07-23 | 也在 transportation/查询:armor |
| Accessories | `accessories` | 13,412,698 | required/required | 2026-02-06 |  |
| Boat Item View | `boat-item-view` | 13,263,719 | required/unsupported | 2026-06-19 | 也在 transportation |
| Cobblemon: Mega Showdown | `cobblemon-mega-showdown` | 12,096,382 | required/required | 2026-09-13 |  |
| Explorer's Compass | `explorers-compass` | 11,393,967 | required/required | 2026-06-17 |  |
| Do a Barrel Roll | `do-a-barrel-roll` | 10,937,088 | required/optional | 2026-06-18 | 也在 transportation |
| Advanced Netherite | `advanced-netherite` | 10,807,530 | required/required | 2026-06-21 |  |
| Legendary Tooltips | `legendary-tooltips` | 10,264,960 | required/unsupported | 2026-08-18 |  |
| Cobblemon PokeNav | `cobblemon-pokenav` | 9,904,172 | required/required | 2026-09-08 |  |
| Simply Swords | `simply-swords` | 9,556,169 | required/required | 2026-08-27 |  |
| Vanilla Backport | `vanillabackport` | 9,362,858 | required/required | 2026-06-11 |  |
| Charm of Undying | `charm-of-undying` | 8,765,521 | required/required | 2024-10-26 |  |
| Immersive Melodies | `immersive-melodies` | 8,646,732 | required/required | 2026-09-09 |  |
| uku's Armor HUD | `ukus-armor-hud` | 8,514,897 | required/unsupported | 2026-06-16 | 也在 查询:armor |
| Item Borders | `item-borders` | 8,384,693 | required/unsupported | 2026-06-30 |  |
| Enderman Overhaul | `enderman-overhaul` | 7,903,815 | required/required | 2026-02-22 |  |
| Legendary Monsters | `legendary-monsters` | 7,602,115 | required/required | 2026-08-23 |  |
| Dynamic Lights | `dynamic-lights` | 7,461,148 | optional/required | 2026-06-24 |  |
| Create Big Cannons | `create-big-cannons` | 7,447,044 | required/required | 2026-06-22 |  |
| Immersive Armors | `immersive-armors` | 7,376,784 | required/required | 2026-07-28 | 也在 查询:armor |
| GD656Killicon | `gd656killicon` | 7,310,668 | required/required | 2026-08-12 |  |
| Better Than Mending | `better-than-mending` | 3,997,978 | optional/required | 2026-04-12 |  |
| Detail Armor Bar Reconstructed | `detail-armor-bar-reconstructed` | 3,677,631 | required/unsupported | 2026-07-02 |  |
| Mekanism Tools | `mekanism-tools` | 1,664,192 | required/required | 2026-04-10 |  |
| Armortip | `armortip` | 1,506,037 | required/unsupported | 2026-03-24 |  |
| Distracting Trims | `distracting-trims` | 1,115,724 | required/required | 2026-07-12 |  |
| Alloyed | `create-alloyed` | 1,068,312 | required/required | 2026-06-25 |  |
| Armor Durability HUD | `armor-durability-hud` | 771,890 | required/unsupported | 2026-09-12 |  |
| Cobblemon: Armory | `cobblemon-armory` | 556,260 | required/required | 2026-06-01 |  |
| Super Tools Reload | `super-tools-reload` | 468,074 | required/required | 2025-05-03 | 也在 查询:armor |
| Silent Gear | `silent-gear` | 464,597 | required/required | 2026-08-26 |  |
| Elytra/Chestplate Swapper | `elytra-chestplate-swapper` | 220,922 | required/optional | 2026-09-04 |  |
| Fracture Point | `fracturepoint` | 79,229 | required/required | 2026-08-17 |  |
| OmniTools | `omnitools` | 73,709 | required/required | 2026-06-25 |  |
| Polaroid Camera  | `polaroidcamera` | 60,133 | required/required | 2026-07-26 |  |
| Sophisticated Backpacks easier upgrade | `sophisticated-backpacks-easier-upgrade` | 28,096 | required/unsupported | 2026-04-12 |  |
| Cobblemon: Trainers Backpack | `cobblemon-trainers-backpack` | 15,575 | required/required | 2026-07-19 |  |
| Adventurer's BackPack | `adventurers-backpack` | 10,781 | required/required | 2026-08-15 |  |
| Better Farming ++ | `better-farming-plus` | 4,957 | required/required | 2026-08-26 | 也在 查询:quality of life |
| Immersive Armor | `immersive-armor` | 3,682 | optional/required | 2026-08-04 |  |
| CulinaryCraft: A Better Food Experience | `culinarycraft-a-better-food-experience` | 3,083 | required/required | 2026-07-14 |  |
| Precooked by Juix | `precooked-by-juix` | 1,966 | optional/required | 2026-06-17 |  |
| Common Sense: Saws | `common-sense-saws` | 462 | required/required | 2026-02-07 |  |
| Wooden Tools Variety | `wooden-tools-variety` | 191 | required/required | 2026-02-19 |  |
| More Backpack Upgrades Neoforge | `more-backpack-upgrades-neoforge` | 181 | required/required | 2026-08-02 |  |
| Miku's Leek Ryouri | `mikus-leek-ryouri` | 89 | required/required | 2026-08-30 |  |
| Copper Overthrow | `copper-overthrow` | 88 | required/required | 2026-09-09 |  |

## 生活质量与信息

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| FerriteCore | `ferrite-core` | 150,285,993 | optional/optional | 2026-03-24 | **已装** |
| YetAnotherConfigLib (YACL) | `yacl` | 121,849,302 | optional/optional | 2026-07-19 |  |
| Xaero's Minimap | `xaeros-minimap` | 109,589,218 | required/optional | 2026-09-15 | 也在 transportation/查询:map |
| Xaero's World Map | `xaeros-world-map` | 95,947,604 | required/optional | 2026-09-15 | 也在 transportation/查询:map |
| Sodium Extra | `sodium-extra` | 95,669,681 | required/unsupported | 2026-07-10 |  |
| VeinMiner | `veinminer` | 82,840,493 | optional/required | 2026-08-22 | 也在 equipment |
| Reese's Sodium Options | `reeses-sodium-options` | 80,132,109 | required/unsupported | 2026-07-11 |  |
| ModernFix | `modernfix` | 77,229,035 | optional/optional | 2026-08-31 | **已装** |
| Just Enough Items (JEI) | `jei` | 76,561,011 | optional/optional | 2026-09-15 |  |
| Geckolib | `geckolib` | 69,110,705 | required/optional | 2026-09-06 | 也在 查询:armor |
| Simple Voice Chat | `simple-voice-chat` | 68,454,128 | optional/optional | 2026-09-15 |  |
| Jade 🔍 | `jade` | 67,387,222 | optional/optional | 2026-09-11 |  |
| More Culling | `moreculling` | 65,185,320 | required/required | 2026-09-07 | **已装** |
| Dynamic FPS | `dynamic-fps` | 65,087,398 | required/unsupported | 2026-06-16 |  |
| FancyMenu | `fancymenu` | 64,905,562 | required/optional | 2026-08-28 |  |
| Mouse Tweaks | `mouse-tweaks` | 57,861,867 | required/unsupported | 2026-06-18 | 也在 storage |
| VeinMiner Hotkey | `veinminer-client` | 57,293,064 | required/unsupported | 2026-08-23 | 也在 equipment |
| No Chat Reports | `no-chat-reports` | 57,171,063 | optional/optional | 2026-08-18 |  |
| Sound Physics Remastered | `sound-physics-remastered` | 52,339,680 | required/optional | 2026-06-18 |  |
| Bookshelf | `bookshelf-lib` | 45,361,021 | required/required | 2026-08-01 |  |
| Essential Mod | `essential` | 43,421,522 | required/unsupported | 2026-07-30 |  |
| Inventory Profiles Next | `inventory-profiles-next` | 39,110,754 | required/unsupported | 2026-08-28 | 也在 storage |
| Fzzy Config | `fzzy-config` | 38,753,045 | required/required | 2026-09-15 |  |
| Searchables | `searchables` | 38,472,520 | required/unsupported | 2026-06-17 |  |
| Controlling | `controlling` | 38,325,232 | required/unsupported | 2026-06-17 |  |
| Clumps | `clumps` | 38,048,254 | optional/optional | 2026-06-17 | **已装** · 也在 storage |
| Shulker Box Tooltip | `shulkerboxtooltip` | 36,966,421 | required/optional | 2026-07-06 | 也在 storage |
| Enchantment Descriptions | `enchantment-descriptions` | 36,871,209 | required/optional | 2026-08-20 |  |
| Patchouli | `patchouli` | 34,468,449 | required/required | 2026-07-10 |  |
| Distant Horizons | `distanthorizons` | 34,001,346 | optional/optional | 2026-07-07 |  |
| Model Gap Fix | `modelfix` | 32,123,389 | required/unsupported | 2025-10-14 |  |
| Cherished Worlds | `cherished-worlds` | 31,413,778 | required/unsupported | 2026-07-20 |  |
| Curios API | `curios` | 30,825,660 | required/required | 2026-07-21 | 也在 equipment |
| Packet Fixer | `packet-fixer` | 28,397,196 | optional/optional | 2026-07-16 |  |
| Block Runner | `block-runner` | 2,526,281 | optional/required | 2026-06-18 |  |
| JourneyMap Integration | `journeymap-integration` | 921,008 | required/unsupported | 2025-05-08 |  |
| Chat Plus | `chat-plus` | 881,453 | required/unsupported | 2026-08-01 |  |
| Dyed | `dyed` | 668,249 | required/unsupported | 2025-10-04 |  |
| Map Tooltip | `map-tooltip` | 589,749 | required/unsupported | 2026-05-02 |  |
| Create: Sophisticated Backpacks Compat | `create-sophisticated-backpacks-compat` | 389,533 | required/required | 2025-08-03 |  |
| Xaero's Maps x Waystones | `xaeros-maps-x-waystones` | 303,891 | required/unsupported | 2026-08-30 |  |
| Better "Add Server" | `betteraddserver` | 164,144 | required/unsupported | 2026-01-31 |  |
| Maptip | `maptip` | 97,010 | required/unsupported | 2026-07-21 |  |
| Cobblemon: Better Campfire Pot | `cobblemon-better-campfire-pot` | 85,592 | unknown/unknown | 2026-05-30 |  |
| Xaero Train Map | `xaero-train-map` | 79,493 | required/unsupported | 2025-06-27 |  |
| Frequency Create | `frequency-create` | 12,759 | required/required | 2026-07-25 |  |
| Indicatia | `indicatia` | 10,896 | required/unsupported | 2026-07-10 |  |
| Stop Minimizing on Focus Loss | `stop-minimizing-on-focus-loss` | 4,403 | required/unsupported | 2026-09-07 |  |
| Accessible Nether Roof | `accessible-nether-roof` | 2,949 | required/required | 2024-12-18 |  |
| |  TWILIGHT RESONANCE | | `-resonance-` | 2,610 | required/required | 2026-08-12 |  |
| JEI++-- | `jei++-` | 2,271 | required/unsupported | 2026-05-16 |  |
| Better Building Recipes | `better-building-recipes` | 2,262 | optional/required | 2026-07-17 |  |
| Easy Exchange（简易替换） | `easy-exchange` | 730 | required/required | 2026-02-14 |  |
| Anti StickyK | `anti-stickyk` | 211 | required/unsupported | 2026-08-26 |  |
| Sticks To Planks | `stickstoplanks` | 201 | optional/required | 2025-07-20 |  |
| The Sequel Parrot | `the-sequel-parrot` | 12 | unsupported/required | 2026-08-16 |  |

## 玩法机制（谨慎：改动大）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Client Tweaks | `client-tweaks` | 10,050,776 | required/unsupported | 2026-08-21 |  |
| Overflowing Bars | `overflowing-bars` | 9,707,377 | required/unsupported | 2026-06-19 |  |
| Xaero's Minimap & World Map - Waystones Compatibility | `xaeros-minimap-world-map-waystones-compatibility-forge` | 1,234,851 | required/required | 2026-04-24 |  |
| OneKeyMiner | `onekeyminer_nf` | 1,218,687 | required/required | 2026-08-20 |  |
| Armor Hud | `armor-hud` | 1,181,519 | required/unsupported | 2026-08-30 |  |
| No Animal Tempt Delay | `no-animal-tempt-delay` | 1,156,179 | unsupported/required | 2026-06-18 |  |
| Armor Poser | `armor-poser` | 752,592 | required/required | 2026-08-10 |  |
| Create: Integrated Farming | `create-integrated-farming` | 413,588 | required/required | 2026-09-10 |  |
| Sable Beyond | `sable_beyond` | 404,252 | required/required | 2026-07-03 |  |
| Crying Ghasts | `crying-ghasts` | 232,128 | unsupported/required | 2026-06-18 |  |
| Reach Behind | `reach-behind` | 231,606 | optional/optional | 2026-09-10 |  |
| Create: Quality of Life | `create-qol` | 163,228 | required/required | 2026-06-17 |  |
| PMW Extra! | `pmwextra` | 125,823 | unknown/unknown | 2026-08-21 |  |
| Sophisticated Backpack/Storage Emerald Upgrade | `sophisticated-backpackstorage-emerald-upgrade` | 93,164 | required/required | 2026-08-08 |  |
| TNT Breaks Bedrock | `tnt-breaks-bedrock` | 88,369 | unsupported/required | 2026-06-18 |  |
| Better Craftables | `better-craftables` | 82,515 | optional/required | 2026-09-15 |  |
| Crop Critters | `crop-critters` | 81,584 | required/required | 2026-09-02 |  |
| Serilum's QoL Bundle | `serilums-qol-bundle` | 49,683 | required/required | 2026-06-21 |  |
| Farming Experience Core | `farming-experience-core` | 48,354 | required/required | 2026-08-12 |  |
| Project MMO: Farmer's Delight Compat | `project-mmo-farmers-delight` | 37,933 | unsupported/required | 2026-08-15 |  |
| Reliable Backpacks | `reliable-backpacks` | 37,236 | unknown/unknown | 2026-09-07 |  |
| Meccha Crafteleon | `meccha-crafteleon` | 33,313 | required/required | 2026-08-14 |  |
| Instant Smelt | `auto-ore-smelting` | 33,298 | optional/required | 2026-05-24 |  |
| Create: Few More Recipes | `create-few-more-recipes` | 32,609 | optional/required | 2026-04-21 |  |
| Cactus Mod | `cactus` | 31,547 | required/unsupported | 2026-08-03 |  |
| Vexxed | `vexxed` | 23,737 | optional/required | 2026-08-06 |  |
| Backpack Plus: Sophisticated Backpacks Addon | `backpackplus` | 19,027 | required/required | 2025-05-21 |  |
| Ars 'n Spells | `ars-n-spells` | 16,296 | required/required | 2026-09-04 |  |
| Modular Backpacks | `modular-backpacks` | 15,889 | required/required | 2026-09-03 |  |
| Saddlebag – Wolf Bag, Pet Backpack & Companion Utility | `saddlebag` | 14,724 | required/required | 2026-08-05 |  |
| Modulation | `modulation` | 9,085 | required/required | 2026-09-12 |  |
| Don't Punch Trees | `dont-punch-trees` | 8,928 | optional/required | 2024-12-16 |  |
| Effortless Building: Sophisticated | `effortless-building-sophisticated` | 7,833 | required/required | 2026-04-08 |  |
| Easy Recipes | `easy-recipes` | 6,272 | optional/required | 2025-10-25 |  |
| CobbleQualities | `cobblequalities` | 3,624 | required/required | 2025-11-24 |  |
| Croparium: Create | `croparium-create` | 3,247 | required/required | 2026-08-29 |  |
| Create: Craft 'n Extras | `create-craft-n-extras` | 473 | required/required | 2026-06-26 |  |
| Windowed GUIs | `windowed-guis` | 410 | required/required | 2026-09-11 |  |
| Canalize | `canalize` | 337 | required/required | 2026-03-02 |  |
| Solo Bridges | `solo-bridges` | 333 | required/required | 2025-12-10 |  |

## 存储与物流

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Carry On | `carry-on` | 25,392,803 | required/required | 2026-08-04 | 也在 transportation |
| Tom's Simple Storage Mod | `toms-storage` | 19,529,193 | required/required | 2026-08-30 |  |
| Sophisticated Backpacks | `sophisticated-backpacks` | 18,370,050 | required/required | 2026-09-09 | 也在 查询:backpack |
| Sophisticated Core | `sophisticated-core` | 18,257,311 | required/required | 2026-09-09 |  |
| Prickle | `prickle` | 12,631,632 | required/required | 2026-07-08 |  |
| Iron Furnaces | `iron-furnaces` | 9,397,935 | required/required | 2026-06-25 |  |
| InventoryHUD+ | `inventoryhudplus` | 9,350,845 | required/unsupported | 2026-08-30 |  |
| Twigs | `twigs` | 7,710,589 | required/required | 2026-06-03 | 也在 查询:building blocks |
| CobbleFurnies | `cobblefurnies` | 7,570,111 | required/required | 2026-06-29 | 也在 查询:furniture |
| Macaw's Furniture | `macaws-furniture` | 6,818,960 | required/required | 2026-06-20 | 也在 查询:furniture |
| Nether Chested | `nether-chested` | 6,574,048 | required/required | 2026-06-18 |  |
| Applied Energistics 2 | `ae2` | 5,880,866 | required/required | 2026-08-25 |  |
| Create: Enchantment Industry | `create-enchantment-industry` | 5,559,572 | required/required | 2026-08-29 |  |
| Client Sort | `clientsort` | 5,515,798 | required/optional | 2026-09-12 |  |
| Small Ships | `small-ships` | 5,374,951 | required/required | 2025-05-16 | 也在 transportation |
| Easy Shulker Boxes | `easy-shulker-boxes` | 5,246,778 | required/required | 2026-08-12 |  |
| Sophisticated Storage | `sophisticated-storage` | 4,985,431 | required/required | 2026-09-15 |  |
| Man of Many Planes | `man-of-many-planes` | 4,495,370 | required/required | 2026-09-09 | 也在 transportation |
| You're in Grave Danger | `yigd` | 4,469,869 | required/required | 2025-06-22 |  |
| Create: Design n' Decor | `create-design-n-decor` | 4,456,487 | required/required | 2026-05-28 |  |
| Corpse | `corpse` | 4,341,213 | required/required | 2026-08-16 |  |
| Carved Wood | `carved-wood` | 4,035,435 | required/required | 2026-09-09 |  |
| Storage Drawers | `storagedrawers` | 3,802,820 | required/required | 2026-09-12 |  |
| Mekanism | `mekanism` | 3,628,756 | required/required | 2026-04-10 |  |
| Inventory Management | `inventory-management` | 3,594,175 | required/required | 2026-06-21 |  |
| Bountiful Fares | `bountiful-fares` | 3,564,250 | required/required | 2026-08-19 | 也在 查询:food/查询:farming |
| Inventory Sorting | `inventory-sorting` | 3,400,089 | optional/required | 2026-06-24 |  |
| Incubation | `incubation` | 3,359,101 | required/required | 2026-03-04 |  |
| [Let's Do] Furniture | `lets-do-furniture` | 3,084,472 | required/required | 2026-03-13 | 也在 查询:furniture |
| RSInfinityBooster | `rsinfinitybooster` | 3,045,005 | required/required | 2025-05-02 |  |
| Barbeque's Delight [Forge/NeoForge] | `barbeques-delight-forge` | 3,024,560 | required/required | 2026-04-28 |  |
| Create: Storage [Neo/Forge] | `create-storage-neo-forge` | 753,302 | required/required | 2026-08-23 |  |
| Packed Up (Backpacks) | `packed-up-backpacks` | 376,520 | required/required | 2026-09-10 |  |
| Bag Of Holding | `bag-of-holding` | 143,445 | required/required | 2026-08-12 |  |
| L2 Backpack | `l2backpack` | 128,663 | required/required | 2026-07-30 |  |
| Sophisticated JEI Index | `sophisticated-jei-index` | 126,333 | required/required | 2026-09-10 |  |
| Hysk-LongHotbar | `hysk-longhotbar` | 31,883 | required/unsupported | 2026-09-06 |  |
| Backpack Side GUI | `backpack-side-gui` | 25,376 | required/required | 2026-06-20 |  |
| SophisticatedFix | `sophisticatedfix` | 7,763 | required/unsupported | 2025-05-31 |  |

## 交通

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Waystones | `waystones` | 25,030,337 | required/required | 2026-09-13 |  |
| InvMove | `invmove` | 16,460,356 | required/unsupported | 2026-06-17 |  |
| Immersive Aircraft | `immersive-aircraft` | 14,633,663 | required/required | 2026-09-15 |  |
| XaeroPlus | `xaeroplus` | 11,515,828 | required/unsupported | 2026-09-12 |  |
| Xaero Zoomout | `xaero-zoomout` | 8,789,776 | required/unsupported | 2026-04-21 | 也在 查询:map |
| Create Aeronautics | `create-aeronautics` | 7,982,976 | required/required | 2026-08-29 |  |
| Caelus API | `caelus` | 7,725,068 | required/required | 2025-07-23 |  |
| Snowy Spirit | `snowy-spirit` | 5,517,893 | required/required | 2026-08-03 |  |
| Automobility | `automobility` | 5,281,059 | required/required | 2025-04-24 |  |
| Icarus | `icarus` | 4,806,605 | required/required | 2026-08-28 |  |
| Void Totem | `voidtotem` | 4,719,740 | required/required | 2025-03-06 |  |
| Create: Bells & Whistles | `bellsandwhistles` | 4,662,124 | required/required | 2025-03-13 |  |
| Create: Interiors | `interiors` | 4,493,771 | required/required | 2026-01-29 | 也在 查询:furniture |
| Towers of the Wild Modded | `totw-modded` | 4,212,347 | required/required | 2026-01-17 |  |
| Better Climbing | `better-climbing` | 3,271,351 | required/unsupported | 2026-05-20 |  |
| AstikorCarts Redux | `astikorcarts-redux` | 2,948,268 | required/required | 2026-08-16 |  |
| ParCool! | `parcool` | 2,939,989 | required/required | 2026-08-30 |  |
| Paragliders | `paragliders` | 2,722,612 | required/required | 2026-09-08 |  |
| Boatload | `boatload` | 2,642,387 | required/required | 2025-10-17 |  |
| Create Stuff 'N Additions | `create-stuff-additions` | 2,578,133 | required/required | 2026-08-17 |  |
| [NoCube's] Undergarden Delight | `undergarden-delight` | 2,482,619 | required/required | 2026-07-09 |  |
| Refined Storage | `refined-storage` | 2,446,743 | required/required | 2026-06-07 |  |
| Cobblemon Journey Mounts | `cobblemon-journey-mounts` | 2,428,291 | optional/required | 2026-01-24 |  |
| Hellion's Sniffer+ | `hellions-sniffer+` | 2,385,916 | required/required | 2026-09-05 |  |
| Estrogen | `estrogen` | 2,226,232 | required/required | 2026-09-03 |  |
| Steam 'n' Rails Neoforge | `create-steam-n-rails-1.21.1` | 2,205,750 | required/required | 2026-07-27 |  |
| Easy Elytra Takeoff | `easy-elytra-takeoff` | 2,188,258 | unsupported/required | 2026-06-18 |  |
| ElevatorMod | `elevatormod` | 2,166,040 | required/required | 2026-06-20 |  |
| Create: Track Map (UNOFFICIAL FORK) | `create-track-map-(unofficial-fork)` | 208,694 | unsupported/required | 2026-03-08 |  |

## 关键词补充

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Polytone | `polytone` | 16,962,726 | required/unsupported | 2026-09-11 |  |
| Maidsoul Kitchen | `maidsoul-kitchen` | 1,465,296 | required/required | 2026-03-13 | 也在 查询:farming |
| Sophisticated Backpacks Create Integration | `sophisticated-backpacks-create-integration` | 899,929 | required/required | 2026-09-07 |  |
| Redden's Stone Lanterns | `reddens-stone-lanterns` | 833,323 | required/required | 2025-07-29 |  |
| Map Atlases [Forge] | `map-atlases-forge` | 788,603 | required/required | 2026-09-01 |  |
| Create Aeronautics: Throwable Rope Connector | `create-aeronautics-throwable-rope-connector` | 126,838 | required/required | 2026-08-10 |  |
| Iris Veil Compat | `iris-veil-compat` | 93,827 | required/unsupported | 2026-06-19 |  |
| Create Aeronautics: Claims | `aeronautics-claims` | 86,570 | required/required | 2026-09-08 |  |
| Create Ars Nouveau | `create-ars-nouveau` | 10,421 | required/required | 2026-08-10 |  |
| Create: Cobblemon Factories | `create-cobblemon-factories` | 8,639 | required/required | 2025-12-17 |  |
| Stable Farm | `stable-farm` | 1,584 | required/required | 2025-02-06 |  |
| Recycle Door | `recycle-door` | 567 | optional/required | 2025-03-21 |  |
| SasquatchMike's Builder's Delight | `sasquatchmikes-builders-delight` | 550 | required/required | 2026-07-18 |  |
| Frog's Roof Mod | `frogs-roof-mod` | 492 | required/required | 2025-11-29 |  |
| MushWood | `mushwood` | 90 | required/required | 2026-01-13 |  |
| Window Title Bar Information | `window-title-bar-information` | 67 | required/unsupported | 2026-06-28 |  |

## 前置库（自动随依赖装）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Moonlight Lib | `moonlight` | 39,993,333 | required/required | 2026-09-10 |  |
| Open Parties and Claims | `open-parties-and-claims` | 22,054,241 | optional/required | 2026-09-15 | **已装** |
| WorldEdit | `worldedit` | 10,585,402 | unsupported/required | 2026-08-09 |  |
| Every Compat (Wood Good) | `every-compat` | 6,585,143 | required/required | 2026-09-14 |  |
| AzureLib Armor | `azurelib-armor` | 5,791,972 | required/required | 2026-04-21 |  |
| Surveyor Map Framework | `surveyor` | 2,371,101 | optional/optional | 2026-05-10 |  |
| AddonsLib | `addonslib` | 1,491,749 | required/required | 2026-07-14 | 也在 查询:bridges |
| ProjectE Integration | `projecte-integration` | 133,586 | required/required | 2026-04-13 |  |
| Mapper Base | `mapper-base` | 83,993 | required/required | 2025-06-06 |  |
| CCSecureBoot | `ccsecureboot` | 15,583 | unsupported/required | 2025-12-05 |  |
| CNPCs BBS Addon | `cnpcs-bbs-addon` | 6,570 | required/required | 2026-03-07 |  |
| KubeJS Create Automation | `kubejs-create-automation` | 1,960 | required/required | 2026-06-05 |  |
| Pane | `pane` | 185 | required/optional | 2026-08-25 |  |

## 猎奇（本包不需要）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Diagonal Windows | `diagonal-windows` | 4,033,378 | required/required | 2026-06-18 |  |
| Milky Way | `milky-way` | 1,794,208 | required/required | 2025-09-27 |  |
| Map Distance Fix | `map-distance-fix` | 740,558 | required/optional | 2026-07-25 |  |
| Immersive Furniture | `immersive-furniture` | 673,935 | required/required | 2026-08-02 |  |
| ExtraDelight | `extradelight` | 245,278 | required/required | 2026-06-16 |  |
| Diversity | `faewufs-diversity` | 155,131 | optional/optional | 2026-08-20 |  |
| Shadered | `shadered` | 36,632 | required/required | 2026-08-02 |  |
| SnowyGUI | `snowygui` | 2,913 | required/unsupported | 2026-07-04 |  |
| LazyKeys | `lazykeys` | 2,290 | required/unsupported | 2026-08-23 |  |
| The Rock Mod | `the_rock_mod` | 824 | required/required | 2025-03-15 |  |
| Spare Planks | `spare-planks` | 239 | required/required | 2025-10-02 |  |
| MinePaperEngine | `minepaper-engine` | 179 | required/unsupported | 2026-05-08 |  |
| The Better Food And Cooking Mod | `the-better-food-and-cooking-mod` | 133 | required/required | 2026-01-18 |  |

## 管理与治理

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Bookshelf Inspector | `bookshelf-inspector` | 1,926,181 | required/required | 2026-09-15 |  |
| Chiseled Bookshelf Visualizer | `chiseled-bookshelf-visualizer` | 1,399,844 | required/required | 2026-08-21 |  |
| Effect Insights | `effect-insights` | 871,157 | required/unsupported | 2026-06-27 |  |
| AdvancedAE | `advancedae` | 619,068 | required/required | 2026-08-08 |  |
| Xaero's Map Server Utils | `xaeros-map-server-utils` | 422,011 | optional/required | 2026-05-16 |  |
| I Want That Back | `iwtb` | 394,398 | optional/required | 2026-04-30 |  |
| Mica | `mica` | 127,157 | required/unsupported | 2026-08-19 |  |
| Discord-Linker | `discord-linker` | 11,552 | unknown/unknown | 2026-08-14 |  |
| Create: Schematic i18n | `create-schematic-i18n` | 5,517 | required/unsupported | 2026-03-10 |  |
| MC Matrix Bridge | `mc-matrix-bridge` | 546 | unsupported/required | 2026-05-31 |  |

## 社交与联机

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Sequoia Deprecated | `sequoia-deprecated` | 4,130 | required/unsupported | 2025-03-27 |  |
| StreamCraft Live | `streamcraft-live` | 3,363 | required/required | 2026-09-14 |  |
| Zulip Bridge | `zulip-bridge` | 988 | unsupported/required | 2026-06-17 |  |
| JavAlert | `javalert` | 512 | required/required | 2026-09-14 |  |
| MistyRadio mc | `mistyradio-mc` | 302 | required/unsupported | 2026-08-17 |  |

## 性能（已单独成组）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Faster Iris Shadow Mapper [FISM] | `fism` | 570,458 | required/unsupported | 2026-06-21 |  |
| WindowFix | `window-fix` | 24,078 | required/unsupported | 2026-08-14 |  |
| Chiselmon - Cobblemon QOL | `chiselmon` | 12,337 | required/unsupported | 2026-09-13 |  |
| NeoCocoa | `neococoa` | 367 | unknown/unknown | 2026-06-19 |  |

## 生物与威胁

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Cobblemon Spawn Notification Discord Integration | `cobblemon-spawn-notification-for-discord` | 12,044 | unsupported/required | 2026-02-22 |  |
| Mob Flow Utilities | `mob-flow-utilities` | 8,624 | required/required | 2026-09-10 |  |
| NeoVillagers-Lumberjack | `neovillagers-lumberjack` | 1,271 | required/required | 2026-05-03 |  |

## 经济（本包暂不需要货币）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Farming for Blockheads | `farming-for-blockheads` | 505,140 | required/required | 2026-08-28 |  |
| Honey Sticky Piston | `honey-sticky-piston` | 7,133 | optional/required | 2025-11-16 |  |

## 科技与自动化

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Create Train Parts | `create-train-parts` | 471,658 | required/required | 2026-07-21 |  |
| O.F.Device (Ore Farming Device) | `o-f-device` | 5,619 | required/required | 2026-06-29 |  |

## 魔法/特殊系统（谨慎）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Ars Additions | `ars-additions` | 363,693 | required/required | 2026-02-18 |  |
| Ars Mekanica | `ars-mekanica` | 1,037 | optional/required | 2026-07-27 |  |

## 世界与地理（群系 / 地形 / 结构）

| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |
|---|---|---|---|---|---|
| Dense Trees | `dense-trees` | 13,989 | required/required | 2026-04-14 |  |
| brasil mod | `brasil-mod` | 168 | required/required | 2026-06-03 |  |
