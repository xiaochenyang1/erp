param(
    [string]$EvidenceDirectory,
    [string]$EvidenceIndexPath,
    [string]$BaseUrl,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [long]$ReadinessRunId,
    [string]$OutputPath,
    [string]$DecisionComment,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "readiness-evidence.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$decisionChecks = [System.Collections.Generic.List[object]]::new()
$decisionSections = [System.Collections.Generic.List[string]]::new()
$decisionFailureCount = 0
$decisionHeaders = $null
$decisionEvidenceIndex = $null

function Add-ReadinessDecisionCheck {
    param(
        [string]$Name,
        [ValidateSet("PASSED", "FAILED")]
        [string]$Status,
        [string]$Detail
    )

    $script:decisionChecks.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
    [System.Console]::WriteLine("[readiness-decision] $Status $Name - $Detail")
    if ($Status -eq "FAILED") {
        $script:decisionFailureCount++
    }
}

function Add-ReadinessDecisionReportSection {
    param(
        [string]$Title,
        [string]$Body
    )

    $decisionSections.Add("")
    $decisionSections.Add("## $Title")
    $decisionSections.Add("")
    $decisionSections.Add($Body.TrimEnd())
}

function Get-ReadinessDecisionEvidenceDirectory {
    if (-not [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        if (-not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) {
            throw "EvidenceDirectory does not exist: $EvidenceDirectory"
        }
        return (Resolve-Path -LiteralPath $EvidenceDirectory).Path
    }

    if (-not [string]::IsNullOrWhiteSpace($EvidenceIndexPath)) {
        if (-not (Test-Path -LiteralPath $EvidenceIndexPath -PathType Leaf)) {
            throw "EvidenceIndexPath does not exist: $EvidenceIndexPath"
        }
        return (Split-Path -Path (Resolve-Path -LiteralPath $EvidenceIndexPath).Path -Parent)
    }

    if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
        $outputDirectory = Split-Path -Path $OutputPath -Parent
        if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
            return $outputDirectory
        }
    }

    return (Get-Location).Path
}

function Get-ReadinessDecisionIndexPath {
    param([string]$EffectiveEvidenceDirectory)

    if (-not [string]::IsNullOrWhiteSpace($EvidenceIndexPath)) {
        return (Resolve-Path -LiteralPath $EvidenceIndexPath).Path
    }

    $candidate = Join-Path $EffectiveEvidenceDirectory "evidence-index.json"
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        return (Resolve-Path -LiteralPath $candidate).Path
    }

    return $null
}

function Get-ReadinessDecisionEvidenceIndex {
    param([string]$IndexPath)

    if ($null -ne $script:decisionEvidenceIndex) {
        return $script:decisionEvidenceIndex
    }
    if ([string]::IsNullOrWhiteSpace($IndexPath) -or -not (Test-Path -LiteralPath $IndexPath -PathType Leaf)) {
        return $null
    }

    $script:decisionEvidenceIndex = Get-Content -LiteralPath $IndexPath -Raw | ConvertFrom-Json
    return $script:decisionEvidenceIndex
}

function Get-ReadinessDecisionRunId {
    param([string]$IndexPath)

    if ($ReadinessRunId -gt 0) {
        return $ReadinessRunId
    }

    $index = Get-ReadinessDecisionEvidenceIndex -IndexPath $IndexPath
    if ($null -ne $index -and $null -ne $index.ReadinessRunId -and -not [string]::IsNullOrWhiteSpace([string]$index.ReadinessRunId)) {
        return [long]$index.ReadinessRunId
    }

    throw "ReadinessRunId is required. Provide -ReadinessRunId or an evidence-index.json with ReadinessRunId."
}

function Get-ReadinessDecisionBaseUrl {
    param([string]$IndexPath)

    $effectiveBaseUrl = $BaseUrl
    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        $index = Get-ReadinessDecisionEvidenceIndex -IndexPath $IndexPath
        if ($null -ne $index) {
            $effectiveBaseUrl = [string]$index.baseUrl
        }
    }
    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        throw "Provide -BaseUrl or an evidence-index.json with baseUrl."
    }

    return $effectiveBaseUrl.TrimEnd("/")
}

function Get-ReadinessDecisionHeaders {
    param([string]$EffectiveBaseUrl)

    if ($null -ne $script:decisionHeaders) {
        return $script:decisionHeaders
    }

    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        $script:decisionHeaders = @{
            Authorization = "Bearer $AccessToken"
        }
        return $script:decisionHeaders
    }

    if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw "Provide -AccessToken or both -Username and -Password for readiness release decision."
    }

    $loginBody = @{
        username = $Username
        password = $Password
    } | ConvertTo-Json

    [System.Console]::WriteLine("[readiness-decision] POST /api/auth/login")
    $loginResponse = Invoke-RestMethod -Method Post -Uri "$EffectiveBaseUrl/api/auth/login" `
        -Body $loginBody `
        -ContentType "application/json" `
        -TimeoutSec 30

    $token = $loginResponse.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login response did not contain data.accessToken."
    }

    $script:decisionHeaders = @{
        Authorization = "Bearer $token"
    }
    return $script:decisionHeaders
}

function Get-ReadinessDecisionRunDetail {
    param(
        [string]$EffectiveBaseUrl,
        [hashtable]$Headers,
        [long]$RunId
    )

    $response = Invoke-ReadinessJson -BaseUrl $EffectiveBaseUrl -Method "GET" -Path "/api/system/readiness/runs/$RunId" -Headers $Headers -Body $null
    if ($null -eq $response.data -or $null -eq $response.data.run) {
        throw "Readiness run detail response did not contain data.run."
    }
    return $response.data
}

function Assert-ReadinessDecisionPrerequisites {
    param([object]$RunDetail)

    $items = @($RunDetail.items | Where-Object { $null -ne $_ })
    $p0p1Items = @($items | Where-Object { @("P0", "P1") -contains [string]$_.priority })
    if ($p0p1Items.Count -eq 0) {
        Add-ReadinessDecisionCheck "P0/P1 readiness items" "FAILED" "No P0/P1 readiness items were found in the run."
    }
    else {
        Add-ReadinessDecisionCheck "P0/P1 readiness items" "PASSED" "P0/P1 item count: $($p0p1Items.Count)"
    }

    $blocking = @($p0p1Items | Where-Object { [string]$_.status -ne "PASSED" })
    if ($blocking.Count -eq 0 -and $p0p1Items.Count -gt 0) {
        Add-ReadinessDecisionCheck "P0/P1 readiness item statuses" "PASSED" "All P0/P1 readiness items are PASSED."
    }
    else {
        $detail = ($blocking | ForEach-Object { "$($_.itemCode)=$($_.status)" }) -join ", "
        if ([string]::IsNullOrWhiteSpace($detail)) {
            $detail = "P0/P1 readiness item status check could not be completed."
        }
        Add-ReadinessDecisionCheck "P0/P1 readiness item statuses" "FAILED" $detail
    }

    $approvalGate = @($items | Where-Object { [string]$_.itemCode -eq "PREPROD_APPROVAL_GATE" } | Select-Object -First 1)
    if ($null -eq $approvalGate) {
        Add-ReadinessDecisionCheck "PREPROD_APPROVAL_GATE item" "FAILED" "Approval gate readiness item is missing."
        return
    }

    Add-ReadinessDecisionCheck "PREPROD_APPROVAL_GATE item" "PASSED" "Approval gate item id: $($approvalGate.id)"
    if ([string]$approvalGate.status -eq "PASSED") {
        Add-ReadinessDecisionCheck "PREPROD_APPROVAL_GATE status" "PASSED" "Approval gate status is PASSED."
    }
    else {
        Add-ReadinessDecisionCheck "PREPROD_APPROVAL_GATE status" "FAILED" "Approval gate status is $($approvalGate.status), expected PASSED."
    }

    $approvalEvidence = @($approvalGate.evidence | Where-Object {
            [string]$_.attachmentBusinessType -eq "SYSTEM_ATTACHMENT" -and
            $null -ne $_.attachmentBusinessId -and
            [long]$_.attachmentBusinessId -gt 0
        })
    if ($approvalEvidence.Count -gt 0) {
        Add-ReadinessDecisionCheck "PREPROD_APPROVAL_GATE Markdown attachment" "PASSED" "Approval gate evidence has SYSTEM_ATTACHMENT attachmentBusinessId."
    }
    else {
        Add-ReadinessDecisionCheck "PREPROD_APPROVAL_GATE Markdown attachment" "FAILED" "Approval gate evidence does not contain a SYSTEM_ATTACHMENT Markdown attachment."
    }
}

function Invoke-ReadinessGoDecision {
    param(
        [string]$EffectiveBaseUrl,
        [hashtable]$Headers,
        [long]$RunId
    )

    $comment = $DecisionComment
    if ([string]::IsNullOrWhiteSpace($comment)) {
        $comment = "Final release decision approved by decide-readiness-release.ps1 after PREPROD_APPROVAL_GATE and all P0/P1 readiness items passed."
    }

    return Invoke-ReadinessJson -BaseUrl $EffectiveBaseUrl -Method "POST" -Path "/api/system/readiness/runs/$RunId/decision" -Headers $Headers -Body @{
        decision = "GO"
        status = "PASSED"
        decisionComment = $comment
    }
}

function Save-ReadinessDecisionReport {
    param(
        [string]$EffectiveOutputPath,
        [string]$EffectiveBaseUrl,
        [long]$RunId,
        [string]$DecisionStatus
    )

    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("| Check | Status | Detail |")
    $rows.Add("|---|---|---|")
    foreach ($check in $decisionChecks) {
        $safeDetail = ([string]$check.Detail) -replace "\r?\n", " "
        $safeDetail = $safeDetail -replace "\|", "/"
        $rows.Add("| $($check.Name) | $($check.Status) | $safeDetail |")
    }

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# Readiness release decision")
    $lines.Add("")
    $lines.Add("- Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")")
    $lines.Add("- Repository: $RepoRoot")
    $lines.Add("- Base URL: $EffectiveBaseUrl")
    $lines.Add("- Readiness run ID: $RunId")
    $lines.Add("- Dry run: $($DryRun.IsPresent)")
    $lines.Add("- Decision status: $DecisionStatus")
    $lines.Add("")
    $lines.Add("## Decision checks")
    $lines.Add("")
    $lines.Add($rows -join [Environment]::NewLine)
    foreach ($section in $decisionSections) {
        $lines.Add($section)
    }

    $outputDirectory = Split-Path -Path $EffectiveOutputPath -Parent
    if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }
    $lines -join [Environment]::NewLine | Set-Content -LiteralPath $EffectiveOutputPath -Encoding UTF8
}

$effectiveEvidenceDirectory = Get-ReadinessDecisionEvidenceDirectory
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $effectiveEvidenceDirectory "readiness-release-decision.md"
}
$effectiveIndexPath = Get-ReadinessDecisionIndexPath -EffectiveEvidenceDirectory $effectiveEvidenceDirectory
$effectiveReadinessRunId = 0
$effectiveBaseUrl = $null
$decisionStatus = "BLOCKED"
$decisionFailure = $null

try {
    $effectiveReadinessRunId = Get-ReadinessDecisionRunId -IndexPath $effectiveIndexPath
    $effectiveBaseUrl = Get-ReadinessDecisionBaseUrl -IndexPath $effectiveIndexPath
    $headers = Get-ReadinessDecisionHeaders -EffectiveBaseUrl $effectiveBaseUrl
    $runDetail = Get-ReadinessDecisionRunDetail -EffectiveBaseUrl $effectiveBaseUrl -Headers $headers -RunId $effectiveReadinessRunId
    Add-ReadinessDecisionCheck "Readiness run detail" "PASSED" "Loaded run $effectiveReadinessRunId."

    Assert-ReadinessDecisionPrerequisites -RunDetail $runDetail

    if ($decisionFailureCount -eq 0) {
        if ($DryRun) {
            $decisionStatus = "READY_TO_DECIDE"
            Add-ReadinessDecisionReportSection "Dry run" "Readiness run is ready for final GO/PASSED decision. No decision API call was made because -DryRun was specified."
        }
        else {
            $decisionResponse = Invoke-ReadinessGoDecision -EffectiveBaseUrl $effectiveBaseUrl -Headers $headers -RunId $effectiveReadinessRunId
            $decisionStatus = "DECIDED_GO"
            Add-ReadinessDecisionReportSection "Decision API result" @"
Decision API status: $($decisionResponse.data.decision)/$($decisionResponse.data.status)
Decision comment: $($decisionResponse.data.decisionComment)
"@
        }
    }
    else {
        Add-ReadinessDecisionReportSection "Blocked" "Readiness release decision is blocked. Fix failed checks before posting GO/PASSED."
    }
}
catch {
    $decisionFailure = $_
    Add-ReadinessDecisionCheck "Readiness release decision script" "FAILED" (($decisionFailure | Out-String).Trim())
    $decisionStatus = "BLOCKED"
}
finally {
    Save-ReadinessDecisionReport -EffectiveOutputPath $OutputPath -EffectiveBaseUrl $effectiveBaseUrl -RunId $effectiveReadinessRunId -DecisionStatus $decisionStatus
    [System.Console]::WriteLine("[readiness-decision] Report written to $OutputPath")
    [System.Console]::WriteLine("[readiness-decision] Decision status: $decisionStatus")
}

if ($decisionStatus -eq "BLOCKED") {
    throw "Readiness release decision is BLOCKED. See $OutputPath"
}
