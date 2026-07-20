param(
    [string]$EnvFile = ".env.prod",
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$OutputPath,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [long]$ReadinessRunId,
    [switch]$CreateReadinessRun,
    [string]$ReadinessReleaseVersion,
    [string]$ReadinessEnvironment = "preprod",
    [string]$ReadinessDatabaseInstance,
    [string]$ReadinessRedisInstance,
    [string]$ReadinessDockerProfile = "core",
    [switch]$SkipReleaseCheck,
    [switch]$SkipComposeUp
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "readiness-evidence.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not $OutputPath) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputPath = Join-Path $RepoRoot "target\preprod-acceptance-$timestamp.md"
}

$sections = [System.Collections.Generic.List[string]]::new()
$failure = $null
$readinessRegistrationFailure = $null
$headers = $null
$BaseUrl = $BaseUrl.TrimEnd("/")
$readinessItems = [ordered]@{
    RELEASE_GATE = [ordered]@{
        ItemName = "发布门禁"
        Category = "RELEASE"
        Priority = "P0"
        ExpectedResult = "release-check、jar 和 SBOM 全部通过"
        Status = $null
        ActualResult = $null
        FailureReason = $null
        EvidenceSummary = $null
        BusinessType = "RELEASE_GATE"
        BusinessNo = "RELEASE-GATE"
    }
    DOCKER_COMPOSE_HEALTH = [ordered]@{
        ItemName = "Docker Compose 启动健康检查"
        Category = "DEPLOYMENT"
        Priority = "P0"
        ExpectedResult = "MySQL、Redis、后端服务健康检查全部通过"
        Status = $null
        ActualResult = $null
        FailureReason = $null
        EvidenceSummary = $null
        BusinessType = "DOCKER_COMPOSE_HEALTH"
        BusinessNo = "DOCKER-COMPOSE-HEALTH"
    }
    AUTH_SMOKE = [ordered]@{
        ItemName = "登录与受保护接口冒烟"
        Category = "AUTH"
        Priority = "P0"
        ExpectedResult = "登录、401、403 和 /api/system/profile 验证通过"
        Status = $null
        ActualResult = $null
        FailureReason = $null
        EvidenceSummary = $null
        BusinessType = "AUTH_SMOKE"
        BusinessNo = "AUTH-SMOKE"
    }
    PREPROD_ACCEPTANCE = [ordered]@{
        ItemName = "预生产基础验收"
        Category = "DEPLOYMENT"
        Priority = "P0"
        ExpectedResult = "发布门禁、Docker Compose、健康检查、登录和受保护接口冒烟通过"
        Status = $null
        ActualResult = $null
        FailureReason = $null
        EvidenceSummary = $null
        BusinessType = "PREPROD_ACCEPTANCE"
        BusinessNo = "PREPROD-ACCEPTANCE"
    }
}

function Add-Section {
    param(
        [string]$Title,
        [string]$Body
    )

    $sections.Add("")
    $sections.Add("## $Title")
    $sections.Add("")
    $sections.Add($Body.TrimEnd())
}

function Set-TrackedReadinessItemState {
    param(
        [string]$ItemCode,
        [ValidateSet("PASSED", "FAILED", "BLOCKED")]
        [string]$Status,
        [string]$ActualResult,
        [string]$FailureReason
    )

    $item = $script:readinessItems[$ItemCode]
    if ($null -eq $item) {
        throw "Unknown tracked readiness item: $ItemCode"
    }

    $item.Status = $Status
    $item.ActualResult = $ActualResult
    $item.FailureReason = $FailureReason
    $item.EvidenceSummary = "$($item.ItemName)脚本验收：$Status"
}

function Ensure-TrackedReadinessItemState {
    param(
        [string]$ItemCode,
        [string]$ActualResult,
        [string]$FailureReason
    )

    $item = $script:readinessItems[$ItemCode]
    if ($null -eq $item) {
        throw "Unknown tracked readiness item: $ItemCode"
    }

    if ([string]::IsNullOrWhiteSpace([string]$item.Status)) {
        Set-TrackedReadinessItemState `
            -ItemCode $ItemCode `
            -Status "BLOCKED" `
            -ActualResult $ActualResult `
            -FailureReason $FailureReason
    }
}

function Add-CommandSection {
    param(
        [string]$Title,
        [string]$CommandText,
        [scriptblock]$Command
    )

    [System.Console]::WriteLine("[preprod] $Title")
    $output = ""
    $exitCode = 0
    try {
        $output = (& $Command 2>&1 | Out-String).TrimEnd()
        if ($LASTEXITCODE -is [int]) {
            $exitCode = $LASTEXITCODE
        }
    }
    catch {
        $output = $_ | Out-String
        $exitCode = 1
    }

    Add-Section $Title @"
Command:

````powershell
$CommandText
````

Exit code: $exitCode

Output:

````
$output
````
"@

    if ($exitCode -ne 0) {
        throw "$Title failed with exit code $exitCode"
    }
}

function Add-WebSection {
    param(
        [string]$Title,
        [string]$Path,
        [hashtable]$Headers
    )

    $url = "$BaseUrl$Path"
    [System.Console]::WriteLine("[preprod] $Title $url")
    try {
        $response = Invoke-WebRequest -Uri $url -Headers $Headers -UseBasicParsing -TimeoutSec 20
        Add-Section $Title @"
URL: $url

Status: $($response.StatusCode)

Body:

````
$($response.Content)
````
"@
    }
    catch {
        Add-Section $Title @"
URL: $url

Failure:

````
$($_ | Out-String)
````
"@
        throw
    }
}

function Get-NonSecretEnvSummary {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Environment file not found: $Path"
    }

    $secretPattern = "(PASSWORD|SECRET|TOKEN|KEY)"
    $rows = foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) {
            continue
        }
        $parts = $trimmed.Split("=", 2)
        $name = $parts[0]
        $value = $parts[1]
        if ($name -match $secretPattern) {
            "| `$name` | `<redacted>` |"
        }
        else {
            "| `$name` | `$value` |"
        }
    }

    return @("| Variable | Value |", "|---|---|") + $rows
}

function Get-AccessToken {
    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        Add-Section "Authentication" "Using bearer token provided through -AccessToken. Token value is intentionally not recorded."
        return $AccessToken
    }

    if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw "Provide -AccessToken or both -Username and -Password."
    }

    $loginBody = @{
        username = $Username
        password = $Password
    } | ConvertTo-Json

    [System.Console]::WriteLine("[preprod] POST /api/auth/login")
    $loginResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
        -Body $loginBody `
        -ContentType "application/json" `
        -TimeoutSec 20

    $token = $loginResponse.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login response did not contain data.accessToken"
    }

    Add-Section "Login smoke" "POST $BaseUrl/api/auth/login returned a bearer token for user `$Username`. Token value is intentionally not recorded."
    return $token
}

function Register-TrackedReadinessEvidence {
    param(
        [string]$Evidence,
        [hashtable]$Headers
    )

    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("| Item code | Status | Readiness item ID | Readiness evidence ID | Attachment ID |")
    $rows.Add("|---|---|---|---|---|")

    foreach ($itemCode in @("RELEASE_GATE", "DOCKER_COMPOSE_HEALTH", "AUTH_SMOKE", "PREPROD_ACCEPTANCE")) {
        $item = $script:readinessItems[$itemCode]
        if ([string]::IsNullOrWhiteSpace([string]$item.Status)) {
            throw "Tracked readiness item state was not finalized: $itemCode"
        }

        $registration = Register-ReadinessEvidenceWithOfflineFallback `
            -BaseUrl $BaseUrl `
            -Headers $Headers `
            -ReadinessRunId $ReadinessRunId `
            -ItemCode $itemCode `
            -ItemName $item.ItemName `
            -Category $item.Category `
            -Priority $item.Priority `
            -ExpectedResult $item.ExpectedResult `
            -Status $item.Status `
            -ActualResult $item.ActualResult `
            -FailureReason $item.FailureReason `
            -EvidenceSummary $item.EvidenceSummary `
            -EvidenceDetail $Evidence `
            -EvidenceRequestUri $OutputPath `
            -BusinessType $item.BusinessType `
            -BusinessNo $item.BusinessNo

        $rows.Add("| $itemCode | $($registration.Status) | $($registration.ItemId) | $($registration.EvidenceId) | $($registration.AttachmentId) |")
    }

    Add-Section "Readiness evidence registration" ($rows -join [Environment]::NewLine)
}

Push-Location $RepoRoot
try {
    $sections.Add("# Preproduction acceptance evidence")
    $sections.Add("")
    $sections.Add("- Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")")
    $sections.Add("- Repository: $RepoRoot")
    $sections.Add("- Environment file: $EnvFile")
    $sections.Add("- Base URL: $BaseUrl")

    Add-CommandSection "Git commit" "git rev-parse --short HEAD" {
        & git rev-parse --short HEAD
    }
    Add-CommandSection "Git worktree status" "git status --short --branch" {
        & git status --short --branch
    }

    Add-Section "Environment summary" ((Get-NonSecretEnvSummary $EnvFile) -join [Environment]::NewLine)

    if ($SkipReleaseCheck) {
        Add-Section "Release gate" "Skipped because -SkipReleaseCheck was specified."
        Set-TrackedReadinessItemState `
            -ItemCode "RELEASE_GATE" `
            -Status "BLOCKED" `
            -ActualResult "发布门禁未执行；证据文件：$OutputPath" `
            -FailureReason "Release gate was skipped because -SkipReleaseCheck was specified."
    }
    else {
        try {
            Add-CommandSection "Release gate with Testcontainers" ".\scripts\release-check.ps1 -IncludeTestcontainers" {
                & .\scripts\release-check.ps1 -IncludeTestcontainers
            }
            Set-TrackedReadinessItemState `
                -ItemCode "RELEASE_GATE" `
                -Status "PASSED" `
                -ActualResult "发布门禁通过；报告：target\release-check-report.json / target\release-check-report.md" `
                -FailureReason $null
        }
        catch {
            Set-TrackedReadinessItemState `
                -ItemCode "RELEASE_GATE" `
                -Status "FAILED" `
                -ActualResult "发布门禁失败；证据文件：$OutputPath" `
                -FailureReason (($_ | Out-String).Trim())
            throw
        }
    }

    if ($SkipComposeUp) {
        # Local/runtime acceptance against an already-running backend (e.g. erp_codex_runtime)
        # must not require Docker CLI presence.
        Add-Section "Docker version" "Skipped because -SkipComposeUp was specified (existing BaseUrl backend)."
        Add-Section "Docker Compose version" "Skipped because -SkipComposeUp was specified."
        Add-Section "Docker Compose startup" "Skipped because -SkipComposeUp was specified."
        Add-Section "Docker Compose status" "Skipped because -SkipComposeUp was specified."
        Add-Section "ERP server logs" "Skipped because -SkipComposeUp was specified; inspect host process logs instead."
        Set-TrackedReadinessItemState `
            -ItemCode "DOCKER_COMPOSE_HEALTH" `
            -Status "BLOCKED" `
            -ActualResult "Docker Compose 启动健康检查未执行；证据文件：$OutputPath" `
            -FailureReason "Docker Compose checks were skipped because -SkipComposeUp was specified."
    }
    else {
        try {
            Add-CommandSection "Docker version" "docker --version" {
                & docker --version
            }
            Add-CommandSection "Docker Compose version" "docker compose version" {
                & docker compose version
            }
            Add-CommandSection "Docker Compose startup" "docker compose --env-file $EnvFile --profile core up -d --build" {
                & docker compose --env-file $EnvFile --profile core up -d --build
            }
            Add-CommandSection "Docker Compose status" "docker compose --env-file $EnvFile ps" {
                & docker compose --env-file $EnvFile ps
            }
            Add-CommandSection "ERP server logs" "docker compose --env-file $EnvFile logs --tail 200 erp-server" {
                & docker compose --env-file $EnvFile logs --tail 200 erp-server
            }
        }
        catch {
            Set-TrackedReadinessItemState `
                -ItemCode "DOCKER_COMPOSE_HEALTH" `
                -Status "FAILED" `
                -ActualResult "Docker Compose 启动或容器状态检查失败；证据文件：$OutputPath" `
                -FailureReason (($_ | Out-String).Trim())
            throw
        }
    }

    try {
        Add-WebSection "Actuator health" "/actuator/health" @{}
        Add-WebSection "API health" "/api/health" @{}
        if (-not $SkipComposeUp) {
            Set-TrackedReadinessItemState `
                -ItemCode "DOCKER_COMPOSE_HEALTH" `
                -Status "PASSED" `
                -ActualResult "Docker Compose 启动、容器状态和健康检查通过；证据文件：$OutputPath" `
                -FailureReason $null
        }
    }
    catch {
        if (-not $SkipComposeUp) {
            Set-TrackedReadinessItemState `
                -ItemCode "DOCKER_COMPOSE_HEALTH" `
                -Status "FAILED" `
                -ActualResult "Docker Compose 启动后健康检查失败；证据文件：$OutputPath" `
                -FailureReason (($_ | Out-String).Trim())
        }
        throw
    }

    if ([string]::IsNullOrWhiteSpace($AccessToken) -and ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password))) {
        Add-Section "Login and profile smoke" "Skipped because neither -AccessToken nor both -Username and -Password were provided."
        Set-TrackedReadinessItemState `
            -ItemCode "AUTH_SMOKE" `
            -Status "BLOCKED" `
            -ActualResult "登录与受保护接口冒烟未执行；证据文件：$OutputPath" `
            -FailureReason "Neither -AccessToken nor both -Username and -Password were provided."
    }
    else {
        try {
            $token = Get-AccessToken
            $headers = @{
                Authorization = "Bearer $token"
            }
            Add-WebSection "Profile smoke" "/api/system/profile" $headers
            Set-TrackedReadinessItemState `
                -ItemCode "AUTH_SMOKE" `
                -Status "PASSED" `
                -ActualResult "登录和 /api/system/profile 冒烟通过；证据文件：$OutputPath" `
                -FailureReason $null
            if ($SkipComposeUp) {
                # local profile typically exposes only /actuator/health; prometheus is a prod observability gate.
                Add-Section "Prometheus metrics" "Skipped because -SkipComposeUp was specified (local/runtime host without full actuator exposure)."
                try {
                    Add-WebSection "Business health summary" "/api/system/observability/business-health" $headers
                }
                catch {
                    Add-Section "Business health summary" "Optional on local runtime; request failed: $(($_ | Out-String).Trim())"
                }
            }
            else {
                Add-WebSection "Prometheus metrics" "/actuator/prometheus" $headers
                Add-WebSection "Business health summary" "/api/system/observability/business-health" $headers
            }
        }
        catch {
            Set-TrackedReadinessItemState `
                -ItemCode "AUTH_SMOKE" `
                -Status "FAILED" `
                -ActualResult "登录与受保护接口冒烟失败；证据文件：$OutputPath" `
                -FailureReason (($_ | Out-String).Trim())
            throw
        }
    }

    if ($CreateReadinessRun) {
        if ($ReadinessRunId -gt 0) {
            Add-Section "Readiness run creation" "Skipped because -ReadinessRunId was already provided: $ReadinessRunId"
        }
        else {
            if ($null -eq $headers) {
                $token = Get-AccessToken
                $headers = @{ Authorization = "Bearer $token" }
            }
            $releaseCommit = (& git rev-parse --short HEAD).Trim()
            $run = New-ReadinessRun `
                -BaseUrl $BaseUrl `
                -Headers $headers `
                -ReleaseCommit $releaseCommit `
                -ReleaseVersion $ReadinessReleaseVersion `
                -Environment $ReadinessEnvironment `
                -DatabaseInstance $ReadinessDatabaseInstance `
                -RedisInstance $ReadinessRedisInstance `
                -DockerProfile $ReadinessDockerProfile `
                -Remark "Created by preprod-acceptance.ps1; evidence file: $OutputPath"
            $ReadinessRunId = [long]$run.id
            Add-Section "Readiness run creation" @"
Readiness run ID: $ReadinessRunId
Readiness run no: $($run.runNo)
Release commit: $($run.releaseCommit)
Environment: $($run.environment)
"@
        }
    }

    Add-Section "Manual business acceptance" "Continue with docs\business-readiness-checklist.md and attach business document numbers, sample data batches, and blocker decisions to this evidence file."
}
catch {
    $failure = $_
    Add-Section "Failure" ($failure | Out-String)
}
finally {
    $outputDirectory = Split-Path -Path $OutputPath -Parent
    if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory | Out-Null
    }
    $evidence = $sections -join [Environment]::NewLine

    if ($ReadinessRunId -gt 0) {
        try {
            if ($null -eq $headers) {
                $token = Get-AccessToken
                $headers = @{ Authorization = "Bearer $token" }
            }

            Ensure-TrackedReadinessItemState `
                -ItemCode "RELEASE_GATE" `
                -ActualResult "发布门禁未完整执行；证据文件：$OutputPath" `
                -FailureReason "Preproduction acceptance ended before the release gate completed."
            Ensure-TrackedReadinessItemState `
                -ItemCode "DOCKER_COMPOSE_HEALTH" `
                -ActualResult "Docker Compose 启动健康检查未完整执行；证据文件：$OutputPath" `
                -FailureReason "Preproduction acceptance ended before Docker Compose startup or service health checks completed."
            Ensure-TrackedReadinessItemState `
                -ItemCode "AUTH_SMOKE" `
                -ActualResult "登录与受保护接口冒烟未完整执行；证据文件：$OutputPath" `
                -FailureReason "Preproduction acceptance ended before login or protected API smoke completed."

            $status = "PASSED"
            $actualResult = "预生产基础验收通过；证据文件：$OutputPath"
            $failureReason = $null
            if ($failure) {
                $status = "FAILED"
                $actualResult = "预生产基础验收失败；证据文件：$OutputPath"
                $failureReason = ($failure | Out-String).Trim()
            }
            elseif (@($readinessItems.GetEnumerator() | Where-Object { $_.Key -ne "PREPROD_ACCEPTANCE" -and $_.Value.Status -eq "BLOCKED" }).Count -gt 0) {
                $status = "BLOCKED"
                $actualResult = "预生产基础验收未完整执行；证据文件：$OutputPath"
                $failureReason = (@($readinessItems.GetEnumerator() |
                        Where-Object { $_.Key -ne "PREPROD_ACCEPTANCE" -and $_.Value.Status -eq "BLOCKED" } |
                        ForEach-Object { "$($_.Key)=$($_.Value.FailureReason)" })) -join "; "
            }

            Set-TrackedReadinessItemState `
                -ItemCode "PREPROD_ACCEPTANCE" `
                -Status $status `
                -ActualResult $actualResult `
                -FailureReason $failureReason

            Register-TrackedReadinessEvidence -Evidence $evidence -Headers $headers
        }
        catch {
            $readinessRegistrationFailure = $_
            Add-Section "Readiness evidence registration failure" ($readinessRegistrationFailure | Out-String)
        }
    }

    $evidence = $sections -join [Environment]::NewLine
    Set-Content -LiteralPath $OutputPath -Value $evidence -Encoding UTF8
    [System.Console]::WriteLine("Preproduction acceptance evidence written to $OutputPath")
    Pop-Location
}

if ($failure) {
    throw $failure
}

if ($readinessRegistrationFailure) {
    throw $readinessRegistrationFailure
}
