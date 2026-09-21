#!/usr/bin/env python3
"""给 `80-civilization`（文明 · 时代 5）追加任务 —— 只追加，不动已有 18 条。

为什么只追加：id = sha256("quest|civilization|名字")，是纯函数；
改已有任务的名字会换 id，丢 lang 文案、断依赖（见 INCIDENTS.md 2026-09-22）。

判据用 id 的安全策略：这一章大量是"建筑/市政"类，**只用已核验过的 vanilla 物品 id**
以及确定存在的 Create 物品，不凭空造 mod id（凭空 id 会被服务端判 Unknown registry key）。

新增六段：
  城市与交通升级 / 市政与公益 / 艺术与纪念 / 知识与记录 / 边域与外交 / 终局工程
"""

from __future__ import annotations

import pathlib
import re
import sys

TARGET = pathlib.Path(r"D:\project\nation-pack\tools\questbook\chapters\80-civilization.toml")

NEW = [
    # ================= 城市与交通升级 =================
    ("rail_network", "轨道成网", "create:track", ["second_city"],
     [("item", "create:track", 256), ("item", "create:track_station", 2)],
     ["两座城之间要有固定班次，而不是想起才走一趟。",
      "轨道成网之后，「距离」这个成本才第一次真正下降。"]),

    ("aqueduct", "引水渠", "minecraft:water_bucket", ["avenues"],
     [("item", "minecraft:water_bucket", 8), ("item", "minecraft:stone_bricks", 64)],
     ["把水从远处引到城里，而不是城里迁就水源。",
      "有引水渠，城市选址才不再被水绑住。"]),

    ("central_plaza", "中央广场", "minecraft:bell", ["avenues"],
     [("item", "minecraft:bell", 2)],
     ["全城的人能聚在一处。集合、公告、庆典都需要一个中心。"]),

    ("public_transport", "城内公共交通", "minecraft:rail", ["central_plaza"],
     [("item", "minecraft:rail", 128), ("item", "minecraft:minecart", 4)],
     ["城里大到走路嫌远时，就该有代步。这一条决定城市还能不能继续扩。"]),

    ("harbor", "码头", "minecraft:oak_boat", ["aqueduct"],
     [("item", "minecraft:oak_boat", 2), ("item", "minecraft:barrel", 8)],
     ["临水的城市，水运比陆运便宜。码头是另一条交通线。"]),

    # ================= 市政与公益 =================
    ("town_hall", "市政厅", "minecraft:lectern", ["great_hall"],
     [("item", "minecraft:lectern", 4), ("item", "minecraft:bookshelf", 8)],
     ["议事、存档、发公告固定在一处。国策与档案要有实体落点。"]),

    ("post_office", "邮驿", "minecraft:barrel", ["signposts"],
     [("item", "minecraft:barrel", 8)],
     ["信件与包裹有固定收发的点。人不在时东西也不会丢在路边。"]),

    ("fire_brigade", "消防站", "minecraft:water_bucket", ["districts"],
     [("item", "minecraft:water_bucket", 16), ("item", "minecraft:cauldron", 4)],
     ["木构城市最大的风险是火。常备水源与取水点，比事后重建便宜。"]),

    ("clinic", "医馆", "minecraft:brewing_stand", ["districts"],
     [("item", "minecraft:brewing_stand", 1), ("item", "minecraft:cauldron", 2)],
     ["药水与急救集中在一处。多人一起玩时，这一条省的是所有人的死亡成本。"]),

    ("census", "户籍与登记", "minecraft:writable_book", ["town_hall"],
     [("item", "minecraft:writable_book", 4), ("item", "minecraft:book", 16)],
     ["谁住在哪、负责什么，写下来。人一多，口头分工就开始失效。"]),

    ("public_kitchen", "公共食堂升级", "minecraft:campfire", ["districts"],
     [("item", "minecraft:campfire", 8), ("item", "minecraft:smoker", 4)],
     ["从「能吃饱」到「有选择」。这一条影响的是长期留人。"]),

    # ================= 艺术与纪念 =================
    ("statues", "雕像与纪念碑", "minecraft:chiseled_stone_bricks", ["avenues"],
     [("item", "minecraft:chiseled_stone_bricks", 32)],
     ["把重要的人与事做成看得见的东西。城市与营地的区别在这里。"]),

    ("theater", "剧场", "minecraft:note_block", ["central_plaza"],
     [("item", "minecraft:note_block", 16), ("item", "minecraft:jukebox", 1)],
     ["有个专门给娱乐的地方。这一条是「有余力之后」的第一个信号。"]),

    ("arena", "竞技场", "minecraft:stone_brick_stairs", ["central_plaza"],
     [("item", "minecraft:stone_brick_stairs", 64)],
     ["比试与演练的场地。军事训练与节日活动可以共用同一处。"]),

    ("fountain", "喷泉与园林", "minecraft:prismarine_bricks", ["central_plaza"],
     [("item", "minecraft:prismarine_bricks", 32), ("item", "minecraft:lantern", 16)],
     ["纯装饰，但它决定城市给人的第一印象。"]),

    ("banners", "旗帜与徽记", "minecraft:white_banner", ["statues"],
     [("item", "minecraft:white_banner", 8), ("item", "minecraft:white_dye", 16)],
     ["统一的旗帜与配色，让队伍在远处也认得出自己人。"]),

    # ================= 知识与记录 =================
    ("school", "学堂", "minecraft:bookshelf", ["library"],
     [("item", "minecraft:bookshelf", 16), ("item", "minecraft:lectern", 4)],
     ["把已知的东西教给后来的人。新人上手快慢直接取决于这一条。"]),

    ("observatory", "观星台", "minecraft:spyglass", ["library"],
     [("item", "minecraft:spyglass", 1), ("item", "minecraft:daylight_detector", 2)],
     ["观测昼夜与天象，也是全城最高的视野点。"]),

    ("archive", "档案库", "minecraft:chest", ["national_history"],
     [("item", "minecraft:chest", 16), ("item", "minecraft:barrel", 8)],
     ["国史、条约、图纸分门别类存放。找得到，才算留下来。"]),

    ("map_room", "舆图室", "minecraft:cartography_table", ["signposts"],
     [("item", "minecraft:cartography_table", 2), ("item", "minecraft:filled_map", 8)],
     ["地图集中管理并持续更新。远征与外交都从这里取资料。"]),

    ("printing", "印刷", "minecraft:paper", ["school"],
     [("item", "minecraft:paper", 64), ("item", "minecraft:book", 16)],
     ["让书与告示能成批出。知识传播的成本降下来，城市才算有文化。"]),

    # ================= 边域与外交 =================
    ("wall_upgrade", "城墙升级", "minecraft:stone_bricks", ["districts"],
     [("item", "minecraft:stone_bricks", 256)],
     ["主城墙体换成石质并加高。文明时代的城市不该再用土墙。"]),

    ("watch_towers", "烽燧体系", "minecraft:campfire", ["wall_upgrade"],
     [("item", "minecraft:campfire", 8), ("item", "minecraft:scaffolding", 16)],
     ["边域到主城的视觉信号链。发现敌情到全城反应之间的时间靠它压缩。"]),

    ("armory", "武库", "minecraft:barrel", ["wall_upgrade"],
     [("item", "minecraft:barrel", 16), ("item", "minecraft:anvil", 2)],
     ["装备集中存放与维护。临时找装备是最常见的失手原因。"]),

    ("embassy", "使馆", "minecraft:oak_sign", ["town_hall"],
     [("item", "minecraft:oak_sign", 8), ("item", "minecraft:lectern", 2)],
     ["专门接待外邦的地方。外交要有实体场所，才不像临时串门。"]),

    ("border_markers", "界碑", "minecraft:chiseled_stone_bricks", ["embassy"],
     [("item", "minecraft:chiseled_stone_bricks", 16)],
     ["国土边界要有可见的标记。争议往往起源于「这里算谁的」不清楚。"]),

    # ================= 终局工程 =================
    ("wonder", "奇观", "minecraft:beacon", ["grand_lighting", "gardens"],
     [("item", "minecraft:beacon", 1), ("item", "minecraft:nether_star", 1)],
     ["全城最费工的那一件。它不解决任何生产问题，只回答「我们做到了什么」。",
      "文明时代的意义在这里——不是为了活下去而建。"]),

    ("air_dock", "空港", "create:track_station", ["rail_network"],
     [("item", "create:track_station", 2), ("item", "minecraft:scaffolding", 32)],
     ["垂直方向的交通枢纽：接飞行器、飞艇或高空轨道。",
      "城市往上长之后，这一层迟早要有。"]),

    ("central_exchange", "总仓与调配中心", "create:item_vault", ["full_auto_storage"],
     [("item", "create:item_vault", 16), ("item", "create:stock_ticker", 1)],
     ["全城物资的总入口与总出口，产能与消耗在这里对账。"]),

    ("night_city", "不夜城", "minecraft:lantern", ["grand_lighting"],
     [("item", "minecraft:lantern", 64), ("item", "minecraft:sea_lantern", 32)],
     ["夜间全城无死角照明。它是「文明」最直观的样子——",
      "从远处看，一座亮着的城和一片营地完全不同。"]),

    ("monorail_cross", "城际干线", "create:track", ["rail_network", "air_dock"],
     [("item", "create:track", 256), ("item", "create:track_signal", 4)],
     ["多城之间的主干线，固定班次、双向运行。",
      "到这一步，「国家」在交通上才真的连成一体。"]),
]


def task_line(t) -> str:
    if t[0] == "adv":
        return f'\t{{ type = "advancement", advancement = "{t[1]}" }},'
    if t[0] == "checkmark":
        return '\t{ type = "checkmark" },'
    return f'\t{{ type = "item", item = "{t[1]}", count = {t[2]} }},'


def main() -> int:
    text = TARGET.read_text(encoding="utf-8")
    existing = re.findall(r'^name = "(.*?)"', text, re.M)
    known = set(existing)

    new_names = [q[0] for q in NEW]
    dup = [n for n in new_names if n in known]
    if dup:
        print(f"[FAIL] 与已有任务重名：{dup}", file=sys.stderr)
        return 2
    dup2 = {n for n in new_names if new_names.count(n) > 1}
    if dup2:
        print(f"[FAIL] 新任务内部重名：{dup2}", file=sys.stderr)
        return 2

    allnames = known | set(new_names)
    for q in NEW:
        for d in q[3]:
            if d not in allnames:
                print(f"[FAIL] {q[0]} 的依赖 {d!r} 不存在", file=sys.stderr)
                return 2

    body = text.rstrip("\n") + "\n\n"
    body += "# ================= 追加：公共建筑与市政（2026-09-22）=================\n"
    body += "# 原章讲的是「时代里程碑」（大工程/国史/毕业/分区/图书馆/第二城/终局碑），\n"
    body += "# 这次补的是它们之间的公共设施与社会性内容。\n"
    body += "# 判据只用已核验过的 vanilla 物品与确定存在的 Create 物品 —— 不凭空造 mod id。\n\n"
    for (name, title, icon, deps, tasks, desc) in NEW:
        body += "[[quest]]\n"
        body += f'name = "{name}"\n'
        body += f'title = "{title}"\n'
        body += "desc = [\n"
        for d in desc:
            body += f'\t"{d}",\n'
        body += "]\n"
        body += f'icon = "{icon}"\n'
        body += 'shape = "circle"\n'
        body += "size = 1.2\n"
        if deps:
            body += "deps = [" + ", ".join(f'"{d}"' for d in deps) + "]\n"
        body += "tasks = [\n"
        for t in tasks:
            body += task_line(t) + "\n"
        body += "]\n\n"

    TARGET.write_text(body, encoding="utf-8", newline="\n")
    print(f"追加 {len(NEW)} 条 -> {TARGET.name} 现共 {len(existing) + len(NEW)} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
