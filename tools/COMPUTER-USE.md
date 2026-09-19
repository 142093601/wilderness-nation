# `dsh-computer-use-win` 怎么用（2026-09-20 装好后实测）

插件：**`dsh-computer-use-win` v0.1.2**（作者 Yu-tao-Li，MIT）
= Windows 的 UIA + 截图 + 输入 + OCR，做成 **MCP stdio server**，
经 DSH 自带的 `@deepseek-ai/dsh-mcp-client` 桥接进来。

| 项 | 值 |
|---|---|
| 安装位置 | `%APPDATA%\dsh-desktop\harness\profiles\web\node_modules\dsh-computer-use-win`（是 **Junction**，真实路径在 `.generations\live\...`）|
| MCP server 名 | `wincu` |
| **模型侧工具名** | **`mcp__wincu__<tool>`** |
| 接线方式 | `cordis.patch.yml`（bundle patch，**启动时**挂载）|
| 工具数 | **22** |
| 后端 | Windows PowerShell 5.1 + UIA，`mode: persistent`，SendInput ✓ PostMessage ✓ 截图 ✓ |

## 一、它必须在**启动时**挂载 ⇒ 装完要重启 DSH

工具表在会话建立时固定。插件是运行中装的，所以**当前会话调不到它**；
重启 DSH（或新开会话）之后才会出现 `mcp__wincu__*`。

## 二、22 个工具，按用途分五组

| 组 | 工具 |
|---|---|
| **看** | `health`（自检）· `snapshot`（截图 + UIA 树）· `accessibility_tree`（只读树）· `list_windows` · `find`（按名字/自动化 id/类名/值搜）· `element_info` · `ocr` · `wait_for` |
| **鼠标** | `move` · `click` · `double_click` · `drag` · `scroll` |
| **键盘** | `type_text`（走剪贴板，Unicode 安全，**用完还原剪贴板**）· `keypress`（SendKeys 组合键，如 `Ctrl+L`、`Alt+F4`）|
| **UIA 结构化** | `focus` · `invoke`（依次试 Invoke→Toggle→SelectionItem→ExpandCollapse，最后可退化为点击中心）· `set_value` · `activate_window` · `close_window` · `move_window` |
| **时序** | `wait` |

## 三、用法要点（决定实战怎么选）

1. **先 `list_windows` 拿 hwnd**，之后每个调用都带 `nativeWindowHandle`（或 `windowTitle`/`processId`）——
   焦点随时会跑，靠"当前活动窗口"很脆。需要时带 `activate: true`。
2. **`snapshot` 会把截图当真正的图片返回给模型**（`server.mjs:574` push `type:"image"`），
   所以模型能**直接看到画面**，不必先落盘再读文件。
3. **`find` 对原生控件程序很强**（PCL 启动器、Windows 设置、各种对话框）——
   以后"改 PCL 内存/加 JVM 参数"这类事可以自己点，不用再让用户去 GUI 里改。
4. **对 Minecraft 基本用不上 UIA**：MC 是自绘 GL 窗口，UIA 树几乎是空的。
   游戏里只能"截图 + 按坐标点击 + 发按键"——比我们自建的 `win_ctl.ps1` 多出**鼠标点击**这一项。
5. `ocr` 用系统语言包（`OcrEngine.TryCreateFromUserProfileLanguages()`），中文机器可用；
   但 MC 自绘字体未必准，读游戏文字**优先用模型自己的视觉**（`snapshot` 的图片）。
6. `type_text` 走剪贴板并**还原**原内容 —— 不会把用户复制的东西弄丢。

## 四、两个"静默失败"的坑（都实测踩到过，都写进 `tools/wcu_probe.py`）

1. **它不是按行 JSON，而是 LSP 式 `Content-Length` 分帧**
   （`server.mjs:704` 写出、`:715` 解析 `Content-Length: N\r\n\r\n`）。
   按行发过去：**一句话都不回，stderr 也是空的**，看起来像"server 挂了"。
2. **`profiles\web\node_modules\...` 是 Junction**，而 `server.mjs` 末尾的入口守卫是
   `import.meta.url === pathToFileURL(process.argv[1]).href` ——
   用 junction 路径启动时两边不一致，**脚本什么都不做就退出**（无输出）。
   → 必须先用真实路径（`os.path.realpath`）。
   DSH 自己不受影响：patch 用 `!!js` 按 patch 文件所在目录解析，本来就是真实路径。

## 五、自检怎么做

```powershell
python tools\wcu_probe.py        # 握手 + 列 22 个工具 + 调一次 health
```
