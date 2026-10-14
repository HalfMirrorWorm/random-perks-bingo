# 端到端自动化测试: 假玩家完成格子 → 发放机会 → 三选一 → 效果生效
#
# 前置条件:
#   1. 开发服务器已启动: ./gradlew runServer
#   2. run/server.properties 已启用 RCON (enable-rcon=true, rcon.password=randomperks_test)
#   3. 依赖 Carpet 假玩家 (build.gradle 中已配置为 modLocalRuntime)
#
# 用法:
#   ./scripts/e2e-test.ps1                 # 等待 55 秒后开始
#   ./scripts/e2e-test.ps1 -SkipStartupWait  # 服务器已就绪时直接开始
param(
    [int]$StartupWaitSeconds = 55,
    [switch]$SkipStartupWait
)

$ErrorActionPreference = 'Stop'
$rconScript = Join-Path $PSScriptRoot 'rcon.ps1'

function Rcon([string[]]$cmds) { & $rconScript -Commands $cmds }
function Step([string]$msg) { Write-Host "== $msg" -ForegroundColor Cyan }

if (-not $SkipStartupWait) {
    Step "waiting ${StartupWaitSeconds}s for server startup..."
    Start-Sleep -Seconds $StartupWaitSeconds
}

Step "1/7 spawn fake player"
Rcon @('player Steve spawn') | Out-Null
Start-Sleep -Seconds 4

Step "2/7 create teams (player is assigned to a random one)"
Rcon @('bingo shuffleteams 2') | Out-Null
Start-Sleep -Seconds 2

# 解析队伍内部名: /team list 显示的是显示名 (如 [Green]), 内部名是 bingo_green
$tl = (Rcon @('team list') | Out-String)
$teamName = 'bingo_green'
if ($tl -match '\[(\w+)\]') { $teamName = 'bingo_' + $Matches[1].ToLower() }
Write-Host "   team internal name: $teamName"

Step "3/7 start the game"
Rcon @('bingo start ignore_warnings') | Out-Null
Start-Sleep -Seconds 3

# 假玩家不会发送移动包, bingo 的 WaitUntilLoadedController 会一直等待;
# 让玩家暂时离队可让该检查通过 (40 秒超时之前), 之后再拉回队伍.
Step "4/7 leave team to pass the loading check"
Rcon @('team leave Steve') | Out-Null
Start-Sleep -Seconds 12

Step "5/7 rejoin team (game is now PLAYING)"
Rcon @("team join $teamName Steve") | Out-Null
Start-Sleep -Seconds 2

Step "6/7 give items to trigger objective capture"
$items = @('oak_log', 'cobblestone', 'coal', 'iron_ingot', 'wheat', 'apple', 'leather', 'string', 'bone')
$cmds = @()
for ($i = 0; $i -lt $items.Count; $i++) {
    $cmds += "item replace entity Steve hotbar.$i with minecraft:$($items[$i]) 64"
}
Rcon $cmds | Out-Null
Start-Sleep -Seconds 4

Step "7/7 verify choices granted"
Rcon @('randomperks status')

Step "pick a perk (index 0) as Steve"
Rcon @('execute as Steve run randomperks pick 0')
Start-Sleep -Seconds 2
Rcon @('randomperks status')

Write-Host ""
Write-Host "验证要点:" -ForegroundColor Green
Write-Host "  - 服务器日志出现: [RandomPerks] team '...' scored '...' → N member(s)   (mixin hook 被触发)"
Write-Host "  - 服务器日志出现: [ScoredItemCheck] ... got ...                       (bingo 正常计分)"
Write-Host "  - randomperks status 显示 owned=<词条>, pending 相应减少"
