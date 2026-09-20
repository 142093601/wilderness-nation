# -*- coding: utf-8 -*-
"""给 events.json 补上国书相关的文本模板（保持既有键不动）。"""
import collections
import io
import json
import os

PATH = r"D:\project\nation-pack\mods\statecraft\mod\src\main\resources\data\statecraft\events.json"

NEW = collections.OrderedDict([
    ("letter_stop_building",
     "「{0}」递来国书：要你们别再往它那边动土——离它 {1} 格内，限期内给个回话"),
    ("letter_joint_war",
     "「{0}」递来国书：邀你们一起打「{1}」，限期内给个回话"),
    ("letter_tribute",
     "「{0}」递来国书：交 {1} 便保你们一段太平，限期内给个回话"),
    ("letter_accepted",
     "你们接了「{0}」的国书，使者在关隘上松了口气"),
    ("letter_refused",
     "你们把「{0}」的国书退了回去，使者脸色发青"),
    ("letter_expired",
     "「{0}」的国书搁在案头没人理，它把这当成回绝"),
    ("letter_kept",
     "你们守住了对「{0}」的承诺，它记下了这份体面"),
    ("letter_breached",
     "你们在「{0}」划下的 {1} 格内动了土，承诺当场作废"),
])

with io.open(PATH, encoding="utf-8") as f:
    data = json.load(f, object_pairs_hook=collections.OrderedDict)

added = []
for k, v in NEW.items():
    if k in data:
        continue
    data[k] = v
    added.append(k)

with io.open(PATH, "w", encoding="utf-8", newline="\n") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
    f.write("\n")

print("added:", added)
print("total keys:", len(data))
