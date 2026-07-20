param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$Username = "admin",
    [string]$Password = "LocalAdmin123",
    [long]$WarehouseId = 0,
    [long]$MaterialWarehouseId = 0,
    [long]$FinishedWarehouseId = 0,
    [string]$BusinessDate = (Get-Date -Format "yyyy-MM-dd"),
    [switch]$PreflightOnly,
    [switch]$SkipBusinessChains,
    [switch]$NoRollback
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BaseUrl = $BaseUrl.TrimEnd("/")
$RunStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$EvidenceDirectory = Join-Path $RepoRoot "target\local-runtime-acceptance-$RunStamp"

function Write-Step([string]$Message) {
    [System.Console]::WriteLine("[local-runtime] $Message")
}

function Get-AccessToken {
    $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
    Write-Step "POST $BaseUrl/api/auth/login as $Username"
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
        -Body $loginBody -ContentType "application/json" -TimeoutSec 30
    $token = $login.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login did not return data.accessToken. Is the backend up on $BaseUrl and using erp_codex_runtime?"
    }
    return $token
}

function Get-Json([string]$Path, [string]$Token) {
    $headers = @{ Authorization = "Bearer $Token" }
    return Invoke-RestMethod -Method Get -Uri "$BaseUrl$Path" -Headers $headers -TimeoutSec 30
}

function Resolve-Warehouses([string]$Token) {
    $script:WarehouseId = $WarehouseId
    $script:MaterialWarehouseId = $MaterialWarehouseId
    $script:FinishedWarehouseId = $FinishedWarehouseId

    $page = Get-Json "/api/masterdata/warehouses?pageNo=1&pageSize=200&status=ACTIVE" $Token
    $rows = @()
    if ($null -ne $page.data) {
        if ($null -ne $page.data.records) { $rows = @($page.data.records) }
        elseif ($null -ne $page.data.list) { $rows = @($page.data.list) }
        elseif ($page.data -is [System.Array]) { $rows = @($page.data) }
    }
    if ($rows.Count -eq 0) {
        throw "No ACTIVE warehouses returned. Seed at least MAIN_WH and a material/finished pair."
    }

    if ($script:WarehouseId -le 0) {
        $main = $rows | Where-Object { [string]$_.warehouseCode -eq "MAIN_WH" } | Select-Object -First 1
        if ($null -eq $main) { $main = $rows | Select-Object -First 1 }
        $script:WarehouseId = [long]$main.id
    }

    if ($script:MaterialWarehouseId -le 0) {
        $material = $rows | Where-Object {
            ([string]$_.warehouseCode -like "UIMW*") -or ([string]$_.warehouseName -like "*材料*")
        } | Where-Object { [long]$_.id -ne $script:WarehouseId } | Select-Object -First 1
        if ($null -eq $material) {
            $material = $rows | Where-Object { [long]$_.id -ne $script:WarehouseId } | Select-Object -First 1
        }
        if ($null -eq $material) { throw "Could not resolve MaterialWarehouseId." }
        $script:MaterialWarehouseId = [long]$material.id
    }

    if ($script:FinishedWarehouseId -le 0) {
        $finished = $rows | Where-Object {
            ([string]$_.warehouseCode -like "UIFW*") -or ([string]$_.warehouseName -like "*成品*")
        } | Where-Object {
            [long]$_.id -ne $script:WarehouseId -and [long]$_.id -ne $script:MaterialWarehouseId
        } | Select-Object -First 1
        if ($null -eq $finished) {
            $finished = $rows | Where-Object {
                [long]$_.id -ne $script:WarehouseId -and [long]$_.id -ne $script:MaterialWarehouseId
            } | Select-Object -First 1
        }
        if ($null -eq $finished) { throw "Could not resolve FinishedWarehouseId." }
        $script:FinishedWarehouseId = [long]$finished.id
    }

    $script:ResolvedWarehouseId = $script:WarehouseId
    $script:ResolvedMaterialWarehouseId = $script:MaterialWarehouseId
    $script:ResolvedFinishedWarehouseId = $script:FinishedWarehouseId
    Write-Step "WarehouseId=$($script:ResolvedWarehouseId) Material=$($script:ResolvedMaterialWarehouseId) Finished=$($script:ResolvedFinishedWarehouseId)"
}

Write-Step "Evidence directory: $EvidenceDirectory"
New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null

try {
    $null = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
}
catch {
    throw @"
Backend is not healthy at $BaseUrl.
Start with local profile against erp_codex_runtime first, for example:

  java -jar target\erp-server-1.0.0.jar --spring.profiles.active=local

See docs/local-runtime-integration.md
"@
}

$token = Get-AccessToken
Resolve-Warehouses $token
$WarehouseId = $script:ResolvedWarehouseId
$MaterialWarehouseId = $script:ResolvedMaterialWarehouseId
$FinishedWarehouseId = $script:ResolvedFinishedWarehouseId

$fullScript = Join-Path $PSScriptRoot "preprod-full-acceptance.ps1"
$arguments = [System.Collections.Generic.List[string]]::new()
$arguments.Add("-BaseUrl"); $arguments.Add($BaseUrl)
$arguments.Add("-Username"); $arguments.Add($Username)
$arguments.Add("-Password"); $arguments.Add($Password)
$arguments.Add("-WarehouseId"); $arguments.Add("$WarehouseId")
$arguments.Add("-MaterialWarehouseId"); $arguments.Add("$MaterialWarehouseId")
$arguments.Add("-FinishedWarehouseId"); $arguments.Add("$FinishedWarehouseId")
$arguments.Add("-BusinessDate"); $arguments.Add($BusinessDate)
$arguments.Add("-EvidenceDirectory"); $arguments.Add($EvidenceDirectory)
$arguments.Add("-SkipReleaseCheck")
$arguments.Add("-SkipComposeUp")
if ($PreflightOnly) { $arguments.Add("-PreflightOnly") }
# Default keeps RollbackAfterSuccess for real preprod parity; compensation may fail when
# production consumes stock. Prefer -NoRollback for a happy-path technical GO on local.
if (-not $NoRollback) { $arguments.Add("-RollbackAfterSuccess") }

# EnvFile is required by preprod-full for reporting; .env.prod may be absent on local.
$envFile = Join-Path $RepoRoot ".env.prod"
if (-not (Test-Path -LiteralPath $envFile)) {
    $envFile = Join-Path $RepoRoot ".env.prod.example"
}
$arguments.Add("-EnvFile"); $arguments.Add($envFile)

Write-Step "Invoking preprod-full-acceptance.ps1 (SkipReleaseCheck + SkipComposeUp)"
$powerShellExe = $null
$pwshCmd = Get-Command pwsh -ErrorAction SilentlyContinue
if ($null -ne $pwshCmd) {
    $powerShellExe = $pwshCmd.Source
    Write-Step "Using PowerShell 7: $powerShellExe"
}
else {
    $powerShellExe = (Get-Process -Id $PID).Path
    if ([string]::IsNullOrWhiteSpace($powerShellExe)) { $powerShellExe = "powershell.exe" }
    Write-Step "Using Windows PowerShell: $powerShellExe"
}

$processArguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $fullScript) + $arguments.ToArray()
& $powerShellExe @processArguments
$exitCode = 0
if ($LASTEXITCODE -is [int]) { $exitCode = $LASTEXITCODE }

$summaryPath = Join-Path $EvidenceDirectory "summary.md"
$checklistSeed = Join-Path $EvidenceDirectory "finance-qc-signoff-seed.md"
@"
# 财务/质检签字种子（本机 local-runtime 跑完后填写）

- 生成时间: $(Get-Date -Format "o")
- BaseUrl: $BaseUrl
- 证据目录: $EvidenceDirectory
- 汇总报告: $summaryPath
- 联调库约定: erp_codex_runtime（见 docs/local-runtime-integration.md）

> 本机脚本 GO 只证明技术链路；预生产签字仍按 docs/finance-qc-acceptance-runbook.md F1–F12 / Q1–Q5。

## 从本次证据摘抄样例单号后勾选

| # | 核对项 | 样例单号 | 通过 |
|---|--------|----------|------|
| F1 | 费用过账分录借贷平衡 | | ☐ |
| F5 | 销售发货生成应收 | | ☐ |
| F6 | 收款核销应收 SETTLED | | ☐ |
| F8 | 采购入库→付款核销应付 | | ☐ |
| F9 | 采购退货应付冲减 | | ☐ |
| Q1–Q5 | IQC 仅合格入库等 | 见 ui-smoke / 本证据 | ☐ |

签字: ________ 日期: ________
"@ | Set-Content -LiteralPath $checklistSeed -Encoding UTF8

Write-Step "Exit code: $exitCode"
Write-Step "Summary: $summaryPath"
Write-Step "Sign-off seed: $checklistSeed"

if ($exitCode -eq 0) {
    $extensionScript = Join-Path $PSScriptRoot "local-extension-regression.ps1"
    if (Test-Path -LiteralPath $extensionScript) {
        Write-Step "Running Track B extension regression (inquiry/invoice/OQC/credit + data-scope)"
        & $powerShellExe -NoProfile -ExecutionPolicy Bypass -File $extensionScript -BaseUrl $BaseUrl
        if ($LASTEXITCODE -is [int] -and $LASTEXITCODE -ne 0) {
            Write-Step "Extension regression failed with exit code $LASTEXITCODE"
            exit $LASTEXITCODE
        }
    }
}

if ($exitCode -ne 0) {
    exit $exitCode
}
