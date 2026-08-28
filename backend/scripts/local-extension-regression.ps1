param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [switch]$SkipDataScope,
    [switch]$SkipExtension,
    [switch]$SkipBudget,
    [switch]$SkipContract
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$env:BASE_URL = $BaseUrl.TrimEnd("/")

function Write-Step([string]$Message) {
    [System.Console]::WriteLine("[extension-regression] $Message")
}

Write-Step "BaseUrl=$($env:BASE_URL)"
$failed = 0

if (-not $SkipExtension) {
    Write-Step "node scripts/extension-features-api-smoke.cjs"
    Push-Location $RepoRoot
    try {
        node (Join-Path $PSScriptRoot "extension-features-api-smoke.cjs")
        if ($LASTEXITCODE -ne 0) { $failed++ }
    } finally {
        Pop-Location
    }
}

if (-not $SkipDataScope) {
    Write-Step "node scripts/data-scope-api-smoke.cjs"
    Push-Location $RepoRoot
    try {
        node (Join-Path $PSScriptRoot "data-scope-api-smoke.cjs")
        if ($LASTEXITCODE -ne 0) { $failed++ }
    } finally {
        Pop-Location
    }
}

if (-not $SkipBudget) {
    Write-Step "node scripts/budget-api-smoke.cjs"
    Push-Location $RepoRoot
    try {
        node (Join-Path $PSScriptRoot "budget-api-smoke.cjs")
        if ($LASTEXITCODE -ne 0) { $failed++ }
    } finally {
        Pop-Location
    }
}

if (-not $SkipContract) {
    Write-Step "node scripts/contract-api-smoke.cjs"
    Push-Location $RepoRoot
    try {
        node (Join-Path $PSScriptRoot "contract-api-smoke.cjs")
        if ($LASTEXITCODE -ne 0) { $failed++ }
    } finally {
        Pop-Location
    }
}

if ($failed -gt 0) {
    Write-Step "FAILED suites=$failed"
    exit 1
}

Write-Step "ALL extension regression suites passed"
Write-Step "Reports: target/extension-features-api-smoke/  target/data-scope-api-smoke/  target/budget-api-smoke/  target/contract-api-smoke/"
