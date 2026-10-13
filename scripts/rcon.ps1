# 简易 RCON 客户端 (用于开发环境自动化测试)
# 用法: ./rcon.ps1 -Commands @('roguebingo status', 'list')
param(
    [string]$Host_ = '127.0.0.1',
    [int]$Port = 25575,
    [string]$Password = 'roguebingo_test',
    [string[]]$Commands = @('roguebingo status')
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($Host_, $Port)
$stream = $client.GetStream()
$stream.ReadTimeout = 8000

function Write-Packet([System.IO.Stream]$s, [int]$id, [int]$type, [string]$payload) {
    $p = [System.Text.Encoding]::UTF8.GetBytes($payload)
    $len = 4 + 4 + $p.Length + 2
    $buf = New-Object byte[] (4 + $len)
    [BitConverter]::GetBytes([int]$len).CopyTo($buf, 0)
    [BitConverter]::GetBytes([int]$id).CopyTo($buf, 4)
    [BitConverter]::GetBytes([int]$type).CopyTo($buf, 8)
    $p.CopyTo($buf, 12)
    $s.Write($buf, 0, $buf.Length)
    $s.Flush()
}

function Read-Packet([System.IO.Stream]$s) {
    $lenBuf = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $r = $s.Read($lenBuf, $read, 4 - $read)
        if ($r -le 0) { return $null }
        $read += $r
    }
    $len = [BitConverter]::ToInt32($lenBuf, 0)
    $data = New-Object byte[] $len
    $read = 0
    while ($read -lt $len) {
        $r = $s.Read($data, $read, $len - $read)
        if ($r -le 0) { return $null }
        $read += $r
    }
    return @{
        Id      = [BitConverter]::ToInt32($data, 0)
        Type    = [BitConverter]::ToInt32($data, 4)
        Payload = [System.Text.Encoding]::UTF8.GetString($data, 8, $len - 10)
    }
}

Write-Packet $stream 1 3 $Password
$auth = Read-Packet $stream
if ($null -eq $auth -or $auth.Id -eq -1) {
    Write-Output 'AUTH FAILED'
    $client.Close()
    exit 1
}

foreach ($c in $Commands) {
    Write-Packet $stream 100 2 $c
    $resp = Read-Packet $stream
    if ($resp -and $resp.Payload) { Write-Output $resp.Payload }
}

$client.Close()
