"""
快照采集器 —— 在服务器运行期间定期跑，把"那一刻的世界状态"存成一份快照。

数据来源（全部是原版文件，**零 mod、零脚本、不改游戏**）：
  * <server>/world/stats/<uuid>.json      —— 每玩家的原版统计（play_time / 挖掘 / 使用 / 移动 / 死亡 / 击杀 / 敲钟…）
  * <server>/world/advancements/<uuid>.json —— 进度完成情况（任务的近似指标）
  * <server>/world/level.dat               —— 世界天数、MC 版本、DataVersion
  * <server>/usercache.json                —— UUID → 玩家名
  * <server>/logs/latest.log               —— 上线/下线、死亡、启动耗时、WARN/ERROR

**核心机制是"差分"**：单次快照没有意义，定期快照之间的增量才说明"这段时间发生了什么"。
所以只要让它按周期自动跑（见 run_daily.ps1），时间序列会自己长出来。

用法：
  python collect_snapshot.py --world "C:\\srv\\world" [--log "C:\\srv\\logs\\latest.log"] [--out data]

注意：中文只写进文件（UTF-8 正确），控制台只打印 ASCII —— Windows 控制台会把中文变乱码，
但**文件内容是对的**（这个坑在预设的 authoring-this-preset 里记过）。
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

# play_time 在不同版本里键名不同：新版本 minecraft:play_time，旧世界可能是 minecraft:play_one_minute。
# 两个都试，并把实际命中的键报出来 —— 不靠记忆断言。
PLAY_TIME_KEYS = ("minecraft:play_time", "minecraft:play_one_minute")

# 值得单独拎出来的自定义统计（其余归入 custom_all 的总计数）
NOTABLE_CUSTOM = {
    "minecraft:deaths": "deaths",
    "minecraft:mob_kills": "mob_kills",
    "minecraft:player_kills": "player_kills",
    "minecraft:damage_taken": "damage_taken",
    "minecraft:damage_dealt": "damage_dealt",
    "minecraft:bell_ring": "bell_ring",              # 我们的"敲钟集结"
    "minecraft:sleep_in_bed": "sleep_in_bed",
    "minecraft:raid_trigger": "raid_trigger",
    "minecraft:raid_win": "raid_win",
    "minecraft:enchant_item": "enchant_item",
    "minecraft:traded_with_villager": "traded_with_villager",
    "minecraft:animals_bred": "animals_bred",
    "minecraft:fish_caught": "fish_caught",
    "minecraft:open_chest": "open_chest",
    "minecraft:open_barrel": "open_barrel",
    "minecraft:open_shulker_box": "open_shulker_box",
    "minecraft:play_noteblock": "play_noteblock",
    "minecraft:target_hit": "target_hit",
}


def read_json(path: Path) -> Any | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def load_usercache(server_root: Path) -> dict[str, str]:
    """UUID → 玩家名。"""
    data = read_json(server_root / "usercache.json")
    out: dict[str, str] = {}
    if isinstance(data, list):
        for item in data:
            if isinstance(item, dict) and "uuid" in item and "name" in item:
                out[str(item["uuid"]).replace("-", "").lower()] = str(item["name"])
    return out


def read_level_dat(world: Path) -> dict[str, Any]:
    """世界天数 / MC 版本 / DataVersion（level.dat 是 gzip NBT）。

    踩过的坑：`LevelName` 是**字符串**，早先对每个键都无脑 `int()` 会让整段读取失败，
    结果连 day_time / mc_version 一起丢掉（合成样例里没有 level.dat，所以一直没暴露）。
    现在按类型分别处理，且任何单项失败都不影响其余字段。
    """
    info: dict[str, Any] = {}
    try:
        import nbtlib  # 局部导入：没有这个库时其余功能仍然可用

        f = nbtlib.load(str(world / "level.dat"))
        data = f["Data"]
        for key, name in (("DayTime", "day_time"), ("Time", "total_time")):
            if key in data:
                try:
                    info[name] = int(data[key])
                except (TypeError, ValueError):
                    pass
        if "LevelName" in data:
            info["level_name"] = str(data["LevelName"])
        if "DataVersion" in data:
            try:
                info["data_version"] = int(data["DataVersion"])
            except (TypeError, ValueError):
                pass
        version = data.get("Version")
        if version is not None and "Name" in version:
            info["mc_version"] = str(version["Name"])
    except Exception as exc:  # 读不到不影响采集其余部分
        info["level_dat_error"] = f"{type(exc).__name__}: {exc}"
    return info


def collect_player(stats_path: Path, names: dict[str, str], adv_dir: Path) -> dict[str, Any]:
    raw = read_json(stats_path) or {}
    stats = raw.get("stats", {}) if isinstance(raw, dict) else {}
    custom = stats.get("minecraft:custom", {}) if isinstance(stats, dict) else {}

    play_time = None
    play_time_key = None
    for key in PLAY_TIME_KEYS:
        if key in custom:
            play_time = int(custom[key])
            play_time_key = key
            break

    def total(category: str) -> int:
        block = stats.get(category, {})
        return int(sum(v for v in block.values() if isinstance(v, (int, float)))) if isinstance(block, dict) else 0

    move_cm = sum(int(v) for k, v in custom.items() if k.endswith("_one_cm") and isinstance(v, (int, float)))

    uuid = stats_path.stem.replace("-", "").lower()
    notable = {label: int(custom.get(key, 0)) for key, label in NOTABLE_CUSTOM.items() if key in custom}

    # 进度完成数（任务完成的近似指标）
    done = 0
    adv = read_json(adv_dir / f"{stats_path.stem}.json") if adv_dir.is_dir() else None
    if isinstance(adv, dict):
        done = sum(1 for v in adv.values() if isinstance(v, dict) and v.get("done") is True)

    return {
        "uuid": uuid,
        "name": names.get(uuid, uuid[:8]),
        "play_time_ticks": play_time,
        "play_time_key": play_time_key,
        "play_time_hours": round(play_time / 20 / 3600, 2) if play_time else None,
        "advancements_done": done,
        "mined": total("minecraft:mined"),
        "crafted": total("minecraft:crafted"),
        "used": total("minecraft:used"),        # 注意：原版没有独立"放置"统计，used 是近似
        "broken": total("minecraft:broken"),
        "picked_up": total("minecraft:picked_up"),
        "killed": total("minecraft:killed"),
        "killed_by": total("minecraft:killed_by"),
        "move_cm": move_cm,
        "move_km": round(move_cm / 100000, 2),
        "notable": notable,
    }


LOG_PATTERNS = {
    "join": re.compile(r"\]: ([A-Za-z0-9_]{3,16}) joined the game"),
    "leave": re.compile(r"\]: ([A-Za-z0-9_]{3,16}) left the game"),
    "death": re.compile(r"\]: ([A-Za-z0-9_]{3,16}) (?:was slain by|was killed by|died|drowned|blew up|burned|fell|withered|starved|hit the ground)"),
    "startup": re.compile(r"Done \(([0-9.]+)s\)!"),
    # ModernFix 自己会测启动耗时，而且**每次都会打印** —— 比按日志时间戳推算更准，
    # 也正好当 baseline.csv 的统一口径（见 BOOTSTRAP.md 第四节）。
    "modernfix_game_start": re.compile(r"Game took ([0-9.]+) seconds to start"),
    "modernfix_total_load": re.compile(r"Total time to load game and open world was ([0-9.]+) seconds"),
    "modernfix_menu_to_world": re.compile(r"Time from main menu to in-game was ([0-9.]+) seconds"),
}


def parse_log(log_path: Path, tail_bytes: int = 4_000_000) -> dict[str, Any]:
    """只读日志尾部（大日志不要整份读进内存）。"""
    out: dict[str, Any] = {"path": str(log_path), "joins": [], "leaves": [], "deaths": [],
                           "startup_seconds": None, "errors": 0, "warns": 0, "markers": [],
                           "modernfix": {}}
    if not log_path.is_file():
        out["error"] = "log not found"
        return out
    try:
        with log_path.open("rb") as fh:
            fh.seek(0, 2)
            size = fh.tell()
            fh.seek(max(0, size - tail_bytes))
            text = fh.read().decode("utf-8", errors="replace")
    except Exception as exc:
        out["error"] = f"{type(exc).__name__}: {exc}"
        return out

    for line in text.splitlines():
        m = LOG_PATTERNS["join"].search(line)
        if m:
            out["joins"].append(m.group(1))
        m = LOG_PATTERNS["leave"].search(line)
        if m:
            out["leaves"].append(m.group(1))
        m = LOG_PATTERNS["death"].search(line)
        if m:
            out["deaths"].append(m.group(1))
        m = LOG_PATTERNS["startup"].search(line)
        if m:
            out["startup_seconds"] = float(m.group(1))
        for key in ("modernfix_game_start", "modernfix_total_load", "modernfix_menu_to_world"):
            m = LOG_PATTERNS[key].search(line)
            if m:
                out["modernfix"][key.replace("modernfix_", "")] = float(m.group(1))
        if "/ERROR]" in line or " ERROR]" in line:
            out["errors"] += 1
        if "/WARN]" in line or " WARN]" in line:
            out["warns"] += 1
        # 我们的机制可能在日志里留痕（mod 名 / 关键字），抓到就记一条
        low = line.lower()
        for kw in ("horde", "undeadnights", "roadweaver", "sculk"):
            if kw in low:
                out["markers"].append(line[-200:])
                break
    for key in ("joins", "leaves", "deaths", "markers"):
        out[key] = out[key][-200:]  # 只留最近 200 条，避免快照无限膨胀
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="Minecraft 复盘数据快照采集器")
    ap.add_argument("--world", required=True, help="世界目录（含 stats/、level.dat）")
    ap.add_argument("--log", default=None, help="服务器 latest.log 路径，默认 <world>/../logs/latest.log")
    ap.add_argument("--out", default="data", help="输出目录，默认 ./data")
    args = ap.parse_args()

    world = Path(args.world).expanduser().resolve()
    server_root = world.parent
    log_path = Path(args.log).expanduser().resolve() if args.log else server_root / "logs" / "latest.log"
    out_dir = Path(args.out).expanduser().resolve()
    (out_dir / "snapshots").mkdir(parents=True, exist_ok=True)

    names = load_usercache(server_root)
    players = []
    stats_dir = world / "stats"
    if stats_dir.is_dir():
        for stats_file in sorted(stats_dir.glob("*.json")):
            players.append(collect_player(stats_file, names, world / "advancements"))

    now = datetime.now(timezone.utc)
    snapshot = {
        "collected_at": now.isoformat(timespec="seconds"),
        "world": str(world),
        "level": read_level_dat(world),
        "players": sorted(players, key=lambda p: -(p["play_time_ticks"] or 0)),
        "log": parse_log(log_path),
        "notes": {
            "play_time_key": next((p["play_time_key"] for p in players if p["play_time_key"]), None),
            "placed_stat": "原版无独立放置统计，used 为近似值",
            "quests": "任务书/时代进度不在原版统计里 —— 需要人工写 milestones.csv，或让日历脚本写日志",
        },
    }

    # 文件名要**定宽**且能被字典序正确排序。
    # 踩过的坑：同一秒内跑两次会互相覆盖（自检抓到）；而用 `_2` 后缀补救在 Windows 上是错的——
    # Windows 路径排序大小写不敏感，'_'(0x5F) 会排在 'z'(0x7A) 之前，于是补号反而跑到前面。
    # 所以：把毫秒写进文件名（定宽），冲突兜底用 '~' 后缀（'~' 0x7E > 'z'，排在后面）。
    # 另外分析器始终按 JSON 里的 collected_at 排序，文件名顺序只是给人看的。
    base = now.strftime("%Y%m%dT%H%M%S") + f"{now.microsecond // 1000:03d}"
    snap_name = f"{base}Z.json"
    target = out_dir / "snapshots" / snap_name
    counter = 2
    while target.exists():
        snap_name = f"{base}~{counter}Z.json"
        target = out_dir / "snapshots" / snap_name
        counter += 1
    target.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2), encoding="utf-8")

    # 追加一行紧凑时间线（utf-8-sig 便于 Excel 直接打开）
    csv_path = out_dir / "timeline.csv"
    new_file = not csv_path.exists()
    team_play = sum(p["play_time_ticks"] or 0 for p in players)
    with csv_path.open("a", newline="", encoding="utf-8-sig") as fh:
        w = csv.writer(fh)
        if new_file:
            w.writerow(["collected_at", "players", "team_play_hours_sum", "mined", "used",
                        "crafted", "deaths", "move_km", "advancements", "errors", "warns",
                        "startup_seconds"])
        w.writerow([
            snapshot["collected_at"], len(players), round(team_play / 20 / 3600, 2),
            sum(p["mined"] for p in players), sum(p["used"] for p in players),
            sum(p["crafted"] for p in players),
            sum(p["notable"].get("deaths", 0) for p in players),
            round(sum(p["move_cm"] for p in players) / 100000, 2),
            sum(p["advancements_done"] for p in players),
            snapshot["log"]["errors"], snapshot["log"]["warns"],
            snapshot["log"]["startup_seconds"],
        ])

    # 控制台只打印 ASCII（中文写进文件）
    print(f"snapshot: {snap_name}")
    print(f"players : {len(players)}  team_play_hours_sum={round(team_play/20/3600, 2)}")
    print(f"play_time key found: {snapshot['notes']['play_time_key']}")
    if "level_dat_error" in snapshot["level"]:
        print(f"level.dat read failed: {snapshot['level']['level_dat_error']}")
    print(f"out     : {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
