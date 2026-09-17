# win_ctl.ps1 -- drive the real Minecraft window (screenshot + keyboard) from the CLI.
# ASCII ONLY on purpose: this repo learned the hard way that .ps1 files with Chinese
# text break the PowerShell parser (unterminated string). Keep it English.
#
# Design notes (learned by failing):
#   * CopyFromScreen grabs whatever is on the SCREEN at those coordinates. The first
#     version did that and happily captured the DSH chat window that was covering the
#     game. PrintWindow(PW_RENDERFULLCONTENT) grabs the window itself, occluded or not.
#   * SetForegroundWindow is refused unless the caller already owns the foreground, so
#     focusing reliably is a fight. PostMessage needs no focus at all: GLFW's message
#     loop translates posted key messages exactly like real ones, and Minecraft keeps
#     running unfocused as long as options.txt has pauseOnLostFocus:false.
#
#   -Action list                        list windows with a title
#   -Action shot   -Out shot.png        screenshot (PrintWindow, falls back to screen)
#   -Action focus                       best-effort bring-to-front
#   -Action tap    -Vk 84               press+release a virtual key (84 = T, 13 = Enter)
#   -Action chars  -Value "/spark tps"  post WM_CHAR per character (text entry)
#   -Action cmd    -Value "/spark tps"  tap T, type, tap Enter  (run a chat command)
#   -Action paste  -Value "..."         clipboard + Ctrl+V (needs focus; non-ASCII)
#
# Exit codes: 0 ok, 2 no matching window, 3 action failed.

param(
  [string]$Action = 'list',
  [string]$Out = '',
  [string]$Match = 'Minecraft',
  [string]$Value = '',
  [int]$Vk = 0,
  [int]$Vk2 = 0,
  [int]$SettleMs = 400,
  [int]$StepMs = 60
)

$ErrorActionPreference = 'Stop'

$cs = @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public class McCtl {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr wp, IntPtr lp);
    [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint mapType);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();

    // Without DPI awareness GetClientRect reports VIRTUALIZED pixels: on a 125% display a
    // 1280x720 game window reports 1024x576, and PrintWindow then renders the real frame
    // clipped to that smaller bitmap -- we silently lost the bottom and right edges, which
    // is exactly where Minecraft draws the newest chat line.
    public static string MakeDpiAware() {
        try { return SetProcessDPIAware() ? "DPI_AWARE=1" : "DPI_AWARE=0"; }
        catch { return "DPI_AWARE=err"; }
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int L, T, R, B; }

    [StructLayout(LayoutKind.Sequential)]
    public struct POINT { public int X, Y; }

    const uint WM_KEYDOWN = 0x0100, WM_KEYUP = 0x0101, WM_CHAR = 0x0102;
    const uint PW_CLIENTONLY = 0x00000001, PW_RENDERFULLCONTENT = 0x00000002;

    public static void Down(byte vk) { keybd_event(vk, 0, 0, UIntPtr.Zero); }
    public static void Up(byte vk) { keybd_event(vk, 0, 2, UIntPtr.Zero); }

    public static void Tap(byte vk) {
        Down(vk); Thread.Sleep(45); Up(vk); Thread.Sleep(45);
    }

    public static void Combo(byte mod, byte key) {
        Down(mod); Thread.Sleep(60); Tap(key); Up(mod); Thread.Sleep(60);
    }

    public static string Focus(IntPtr h) {
        ShowWindow(h, 9); // SW_RESTORE
        Thread.Sleep(200);
        if (GetForegroundWindow() != h) {
            Tap(0x12); // ALT: satisfies "process received last input" so the request is granted
            SetForegroundWindow(h);
            Thread.Sleep(250);
        }
        return GetForegroundWindow() == h ? "FOCUSED=1" : "FOCUSED=0";
    }

    static IntPtr Lparam(byte vk, bool up) {
        uint scan = MapVirtualKey(vk, 0);
        long lp = 1L | ((long)scan << 16);
        if (up) lp |= 0xC0000000L;
        return (IntPtr)lp;
    }

    // Post key messages straight into the window's queue. No focus required.
    public static void PostTap(byte vk) {
        PostMessage(hLast, WM_KEYDOWN, (IntPtr)vk, Lparam(vk, false));
        Thread.Sleep(30);
        PostMessage(hLast, WM_KEYUP, (IntPtr)vk, Lparam(vk, true));
        Thread.Sleep(30);
    }

    public static void PostChars(string s) {
        foreach (char c in s) {
            PostMessage(hLast, WM_CHAR, (IntPtr)c, (IntPtr)1);
            Thread.Sleep(12);
        }
    }

    // MC's chat scrolls its history with the mouse wheel, so long command output that has
    // been pushed out of the visible box can still be read -- without this, /help output
    // is only readable in its last ~10 lines.
    public static void Wheel(int notches) {
        int delta = (notches >= 0 ? 120 : -120);
        for (int i = 0; i < Math.Abs(notches); i++) {
            int wp = (delta << 16) & unchecked((int)0xFFFF0000);
            int lp = (300 << 16) | 300;
            PostMessage(hLast, 0x020A, (IntPtr)wp, (IntPtr)lp);   // WM_MOUSEWHEEL
            Thread.Sleep(60);
        }
    }

    [ThreadStatic] public static IntPtr hLast;

    public static double Brightness(Bitmap bmp) {
        long sum = 0; int n = 0;
        for (int y = 0; y < bmp.Height; y += 8) {
            for (int x = 0; x < bmp.Width; x += 8) {
                Color c = bmp.GetPixel(x, y);
                sum += (c.R + c.G + c.B) / 3;
                n++;
            }
        }
        return n == 0 ? 0.0 : (double)sum / n;
    }

    public static string ShotPrint(IntPtr h, string path) {
        RECT r;
        if (!GetWindowRect(h, out r)) return "ERR GetWindowRect";
        int w = r.R - r.L, ht = r.B - r.T;
        if (w <= 0 || ht <= 0) return "ERR bad rect " + w + "x" + ht;
        double bright;
        using (Bitmap bmp = new Bitmap(w, ht)) {
            using (Graphics g = Graphics.FromImage(bmp)) {
                IntPtr hdc = g.GetHdc();
                PrintWindow(h, hdc, PW_RENDERFULLCONTENT);
                g.ReleaseHdc(hdc);
            }
            bright = Brightness(bmp);
            bmp.Save(path, ImageFormat.Png);
        }
        return string.Format("OK print {0}x{1} bright={2:F1}", w, ht, bright);
    }

    // Client area only. Capturing the whole window rect shifts the game content down by
    // the title bar height and clips the same amount off the bottom -- which silently ate
    // the last line of command output (the subcommand list we actually needed).
    public static string ShotPrintClient(IntPtr h, string path) {
        RECT cr;
        if (!GetClientRect(h, out cr)) return "ERR GetClientRect";
        int w = cr.R - cr.L, ht = cr.B - cr.T;
        if (w <= 0 || ht <= 0) return "ERR bad client rect " + w + "x" + ht;
        double bright;
        using (Bitmap bmp = new Bitmap(w, ht)) {
            using (Graphics g = Graphics.FromImage(bmp)) {
                IntPtr hdc = g.GetHdc();
                PrintWindow(h, hdc, PW_CLIENTONLY | PW_RENDERFULLCONTENT);
                g.ReleaseHdc(hdc);
            }
            bright = Brightness(bmp);
            bmp.Save(path, ImageFormat.Png);
        }
        return string.Format("OK printclient {0}x{1} bright={2:F1}", w, ht, bright);
    }

    public static string ShotScreenClient(IntPtr h, string path) {
        RECT cr;
        if (!GetClientRect(h, out cr)) return "ERR GetClientRect";
        int w = cr.R - cr.L, ht = cr.B - cr.T;
        POINT o = new POINT(); o.X = 0; o.Y = 0;
        if (!ClientToScreen(h, ref o)) return "ERR ClientToScreen";
        double bright;
        using (Bitmap bmp = new Bitmap(w, ht)) {
            using (Graphics g = Graphics.FromImage(bmp)) {
                g.CopyFromScreen(o.X, o.Y, 0, 0, new Size(w, ht));
            }
            bright = Brightness(bmp);
            bmp.Save(path, ImageFormat.Png);
        }
        return string.Format("OK screenclient {0}x{1} bright={2:F1}", w, ht, bright);
    }

    public static string ShotScreen(IntPtr h, string path) {
        RECT r;
        if (!GetWindowRect(h, out r)) return "ERR GetWindowRect";
        int w = r.R - r.L, ht = r.B - r.T;
        double bright;
        using (Bitmap bmp = new Bitmap(w, ht)) {
            using (Graphics g = Graphics.FromImage(bmp)) {
                g.CopyFromScreen(r.L, r.T, 0, 0, new Size(w, ht));
            }
            bright = Brightness(bmp);
            bmp.Save(path, ImageFormat.Png);
        }
        return string.Format("OK screen {0}x{1} bright={2:F1}", w, ht, bright);
    }
}
'@

if (-not ('McCtl' -as [type])) {
    try {
        Add-Type -TypeDefinition $cs -ReferencedAssemblies 'System.Drawing.Common', 'System.Windows.Forms'
    } catch {
        Add-Type -TypeDefinition $cs -ReferencedAssemblies 'System.Drawing', 'System.Windows.Forms'
    }
}

function Get-TargetWindow {
    $procs = Get-Process | Where-Object { $_.MainWindowTitle -like "*$Match*" }
    if (-not $procs) { return $null }
    return ($procs | Select-Object -First 1)
}

# Must happen before any window geometry is queried in this process.
Write-Output ([McCtl]::MakeDpiAware())

if ($Action -eq 'list') {
    $all = Get-Process | Where-Object { $_.MainWindowTitle } |
        Select-Object Id, MainWindowHandle, MainWindowTitle
    if (-not $all) { Write-Output 'NO_TITLED_WINDOWS' } else { $all | Format-Table -AutoSize | Out-String -Width 220 }
    exit 0
}

$p = Get-TargetWindow
if (-not $p) {
    Write-Output "NO_WINDOW match='$Match'"
    exit 2
}
$h = [IntPtr]$p.MainWindowHandle
[McCtl]::hLast = $h
Write-Output ("WINDOW pid={0} hwnd={1} title='{2}' iconic={3}" -f $p.Id, $h, $p.MainWindowTitle, [McCtl]::IsIconic($h))

switch ($Action) {
    'focus' {
        Write-Output ([McCtl]::Focus($h))
        exit 0
    }
    'shot' {
        if (-not $Out) { Write-Output 'ERR -Out required'; exit 3 }
        $res = [McCtl]::ShotPrintClient($h, $Out)
        # PrintWindow returns black for some GL surfaces; fall back to a real screen grab.
        if ($res -match 'bright=(\d+\.?\d*)' -and [double]$Matches[1] -lt 5.0) {
            Write-Output "  (print capture looked black, falling back to screen)"
            [McCtl]::Focus($h) | Out-Null
            Start-Sleep -Milliseconds $SettleMs
            $res = [McCtl]::ShotScreenClient($h, $Out)
        }
        Write-Output $res
        if ($res -like 'OK*') { exit 0 } else { exit 3 }
    }
    'tap' {
        if ($Vk -le 0) { Write-Output 'ERR -Vk required'; exit 3 }
        [McCtl]::PostTap([byte]$Vk)
        Write-Output ("TAPPED(vk={0})" -f $Vk)
        exit 0
    }
    'chars' {
        if (-not $Value) { Write-Output 'ERR -Value required'; exit 3 }
        [McCtl]::PostChars($Value)
        Write-Output ("CHARS len={0}" -f $Value.Length)
        exit 0
    }
    'wheel' {
        # -Vk = notches, positive scrolls chat history UP (older lines)
        if ($Vk -eq 0) { Write-Output 'ERR -Vk <notches> required'; exit 3 }
        [McCtl]::Wheel($Vk)
        Write-Output ("WHEEL notches={0}" -f $Vk)
        exit 0
    }
    'cmd' {
        if (-not $Value) { Write-Output 'ERR -Value required'; exit 3 }
        [McCtl]::PostTap(0x54)                      # T opens chat
        Start-Sleep -Milliseconds 500
        [McCtl]::PostChars($Value)
        Start-Sleep -Milliseconds 250
        [McCtl]::PostTap(0x0D)                      # Enter submits
        Write-Output ("RAN cmd len={0}" -f $Value.Length)
        exit 0
    }
    'combo' {
        if ($Vk -le 0 -or $Vk2 -le 0) { Write-Output 'ERR -Vk and -Vk2 required'; exit 3 }
        [McCtl]::Focus($h) | Out-Null
        [McCtl]::Combo([byte]$Vk, [byte]$Vk2)
        Write-Output ("COMBO mod={0} key={1}" -f $Vk, $Vk2)
        exit 0
    }
    'paste' {
        if (-not $Value) { Write-Output 'ERR -Value required'; exit 3 }
        Set-Clipboard -Value $Value
        [McCtl]::Focus($h) | Out-Null
        [McCtl]::Combo(0x11, 0x56)   # CTRL+V
        Write-Output ("PASTED len={0}" -f $Value.Length)
        exit 0
    }
    default {
        Write-Output "ERR unknown action '$Action'"
        exit 3
    }
}
