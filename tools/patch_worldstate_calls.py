"""给 WorldState 加第 10 个分量 `buildings` 时，机械地把所有构造点补上 `List.of()`。

为什么用脚本：测试里有 20+ 处 `new WorldState(...)`，手改必漏；而用全局字符串替换又不安全
（每个调用的结尾形状不同，还有 `List.of(new Event(...))` 这种嵌套括号）。
所以这里做**括号配对扫描**：找到 `new WorldState(` 之后，一直数到配对的那个 `)`，在它前面插入。
"""

import re
import sys
from pathlib import Path

ROOT = Path(r"D:\project\nation-pack\mods\statecraft\core\src\test")
TARGET = "new WorldState("
INSERT = ", List.of()"


def patch(text: str) -> tuple[str, int]:
    out = []
    i = 0
    count = 0
    while True:
        j = text.find(TARGET, i)
        if j < 0:
            out.append(text[i:])
            break
        out.append(text[i:j + len(TARGET)])
        # 从 '(' 之后开始配对
        depth = 1
        k = j + len(TARGET)
        while k < len(text) and depth > 0:
            ch = text[k]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    break
            k += 1
        if k >= len(text):
            raise SystemExit(f"括号没配对：offset {j}")
        body = text[j + len(TARGET):k]
        if body.rstrip().endswith("List.of()") and ", List.of()" in body[-20:]:
            out.append(body)  # 已经补过了
        else:
            out.append(body + INSERT)
            count += 1
        out.append(")")
        i = k + 1
    return "".join(out), count


def main() -> int:
    total = 0
    for path in sorted(ROOT.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        if TARGET not in text:
            continue
        new_text, n = patch(text)
        if n:
            path.write_text(new_text, encoding="utf-8", newline="")
            print(f"  {path.name}: 补了 {n} 处")
            total += n
    print(f"共 {total} 处")
    return 0


if __name__ == "__main__":
    sys.exit(main())
