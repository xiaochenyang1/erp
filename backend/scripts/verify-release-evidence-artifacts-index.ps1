param(
    [string]$ArtifactsIndexPath,
    [string]$EvidenceDirectory
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "sha256-helpers.ps1")

$artifactIndexChecks = [System.Collections.Generic.List[object]]::new()
$artifactIndexFailureCount = 0

function Add-ReleaseEvidenceArtifactsIndexCheck {
    param(
        [string]$Name,
        [ValidateSet("PASSED", "FAILED")]
        [string]$Status,
        [string]$Detail
    )

    $script:artifactIndexChecks.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
    [System.Console]::WriteLine("[release-evidence-artifacts-index-verify] $Status $Name - $Detail")
    if ($Status -eq "FAILED") {
        $script:artifactIndexFailureCount++
    }
}

function Get-ReleaseEvidenceArtifactsIndexObjectProperty {
    param(
        [object]$Object,
        [string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }

    $property = $Object.PSObject.Properties |
        Where-Object { $_.Name -ieq $Name } |
        Select-Object -First 1
    if ($null -eq $property) {
        return $null
    }

    return $property.Value
}

function Get-ReleaseEvidenceRequiredArtifactRoles {
    return @(
        "bundle",
        "bundleSha256",
        "summaryMarkdown",
        "summaryJson",
        "verificationReportJson",
        "verificationReportMarkdown",
        "preprodAcceptanceGateJson",
        "preprodAcceptanceGateVerificationJson",
        "preprodAcceptanceGateVerificationMarkdown"
    )
}

function Format-ReleaseEvidenceArtifactsIndexMarkdownValue {
    param([object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return "UNKNOWN"
    }
    if ($Value -is [datetime]) {
        return $Value.ToString("o")
    }

    return ([string]$Value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Format-ReleaseEvidenceArtifactsIndexVerificationReportValue {
    param([object]$Value)

    return (Format-ReleaseEvidenceArtifactsIndexMarkdownValue $Value)
}

function Resolve-ReleaseEvidenceArtifactsIndexPath {
    if (-not [string]::IsNullOrWhiteSpace($ArtifactsIndexPath)) {
        return [System.IO.Path]::GetFullPath($ArtifactsIndexPath)
    }

    if (-not [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        if (-not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) {
            throw "EvidenceDirectory does not exist: $EvidenceDirectory"
        }
        return [System.IO.Path]::GetFullPath((Join-Path $EvidenceDirectory "release-evidence-artifacts-index.json"))
    }

    throw "Provide -ArtifactsIndexPath or -EvidenceDirectory."
}

function Resolve-ReleaseEvidenceArtifactPath {
    param(
        [string]$PathValue,
        [string]$IndexDirectory
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }

    $indexRelativePath = Join-Path $IndexDirectory $PathValue
    if (Test-Path -LiteralPath $indexRelativePath -PathType Leaf) {
        return $indexRelativePath
    }

    return [System.IO.Path]::GetFullPath($PathValue)
}

function Save-ReleaseEvidenceArtifactsIndexVerificationReport {
    param(
        [string]$ReportJsonPath,
        [string]$ReportMarkdownPath,
        [string]$ArtifactsIndexPath,
        [string]$Status,
        [string]$FailureReason
    )

    $report = [ordered]@{
        schemaVersion = 1
        generatedAt = Get-Date -Format "o"
        artifactsIndexPath = $ArtifactsIndexPath
        status = $Status
        failureCount = $artifactIndexFailureCount
        failureReason = $FailureReason
        checks = @($artifactIndexChecks)
    }

    $reportDirectory = Split-Path -Path $ReportJsonPath -Parent
    if ($reportDirectory -and -not (Test-Path -LiteralPath $reportDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ReportJsonPath -Encoding UTF8

    $markdownLines = [System.Collections.Generic.List[string]]::new()
    $markdownLines.Add("# Release evidence artifacts index verification report")
    $markdownLines.Add("")
    $markdownLines.Add("| Field | Value |")
    $markdownLines.Add("| --- | --- |")
    $markdownLines.Add("| Status | $(Format-ReleaseEvidenceArtifactsIndexVerificationReportValue $Status) |")
    $markdownLines.Add("| Artifacts index | $(Format-ReleaseEvidenceArtifactsIndexVerificationReportValue $ArtifactsIndexPath) |")
    $markdownLines.Add("| Failure count | $artifactIndexFailureCount |")
    $markdownLines.Add("| Failure reason | $(Format-ReleaseEvidenceArtifactsIndexVerificationReportValue $FailureReason) |")
    $markdownLines.Add("")
    $markdownLines.Add("| Check | Status | Detail |")
    $markdownLines.Add("| --- | --- | --- |")
    foreach ($check in @($artifactIndexChecks)) {
        $markdownLines.Add("| $(Format-ReleaseEvidenceArtifactsIndexVerificationReportValue $check.Name) | $(Format-ReleaseEvidenceArtifactsIndexVerificationReportValue $check.Status) | $(Format-ReleaseEvidenceArtifactsIndexVerificationReportValue $check.Detail) |")
    }
    $markdownLines | Set-Content -LiteralPath $ReportMarkdownPath -Encoding UTF8

    [System.Console]::WriteLine("[release-evidence-artifacts-index-verify] Verification report JSON: $ReportJsonPath")
    [System.Console]::WriteLine("[release-evidence-artifacts-index-verify] Verification report Markdown: $ReportMarkdownPath")
}

function Assert-ReleaseEvidenceArtifactsIndexRequiredRoles {
    param([object[]]$Artifacts)

    $roles = @($Artifacts | ForEach-Object {
            [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $_ -Name "role")
        } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

    foreach ($requiredRole in Get-ReleaseEvidenceRequiredArtifactRoles) {
        if ($roles -contains $requiredRole) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Required artifact role $requiredRole" "PASSED" "Required artifact role is listed."
        }
        else {
            Add-ReleaseEvidenceArtifactsIndexCheck "Required artifact role $requiredRole" "FAILED" "Required artifact role is missing from release-evidence-artifacts-index.json."
        }
    }
}

function Assert-ReleaseEvidenceArtifactsIndexDuplicateRoles {
    param([object[]]$Artifacts)

    $roleCounts = @{}
    $roleNames = @{}
    foreach ($artifact in @($Artifacts)) {
        $role = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "role")
        if ([string]::IsNullOrWhiteSpace($role)) {
            continue
        }

        $normalizedRole = $role.ToUpperInvariant()
        if ($roleCounts.ContainsKey($normalizedRole)) {
            $roleCounts[$normalizedRole] = [int]$roleCounts[$normalizedRole] + 1
        }
        else {
            $roleCounts[$normalizedRole] = 1
            $roleNames[$normalizedRole] = $role
        }
    }

    $duplicateCount = 0
    foreach ($normalizedRole in $roleCounts.Keys) {
        $role = [string]$roleNames[$normalizedRole]
        $count = [int]$roleCounts[$normalizedRole]
        if ($count -gt 1) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Duplicate artifact role $role" "FAILED" "Artifact role appears $count times in release-evidence-artifacts-index.json."
            $duplicateCount++
        }
    }

    if ($duplicateCount -eq 0) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Duplicate artifact roles" "PASSED" "No duplicate artifact roles were found."
    }
}

function Get-ReleaseEvidenceArtifactsIndexArtifactByRole {
    param(
        [object[]]$Artifacts,
        [string]$Role
    )

    foreach ($artifact in @($Artifacts)) {
        $artifactRole = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "role")
        if ($artifactRole -eq $Role) {
            return $artifact
        }
    }

    return $null
}

function Assert-ReleaseEvidenceArtifactsIndexRoleStatus {
    param(
        [object[]]$Artifacts,
        [string]$Role,
        [string]$ExpectedStatus
    )

    $artifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role $Role
    if ($null -eq $artifact) {
        return
    }

    if ([string]::IsNullOrWhiteSpace($ExpectedStatus)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role $Role status" "FAILED" "Expected status for artifact role $Role is missing from release-evidence-artifacts-index.json."
        return
    }

    $actualStatus = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "status")
    if ([string]::IsNullOrWhiteSpace($actualStatus)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role $Role status" "FAILED" "Artifact role status is missing."
        return
    }

    if ($actualStatus.ToUpperInvariant() -eq $ExpectedStatus.ToUpperInvariant()) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role $Role status" "PASSED" "Status is $actualStatus."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role $Role status" "FAILED" "Status is '$actualStatus', expected '$ExpectedStatus'."
}

function Assert-ReleaseEvidenceArtifactsIndexRoleStatuses {
    param(
        [object]$Index,
        [object[]]$Artifacts
    )

    if ($null -eq $Index) {
        return
    }

    $bundleStatus = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "bundleStatus")
    $verificationStatus = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "verificationStatus")

    Assert-ReleaseEvidenceArtifactsIndexRoleStatus -Artifacts $Artifacts -Role "bundle" -ExpectedStatus $bundleStatus
    Assert-ReleaseEvidenceArtifactsIndexRoleStatus -Artifacts $Artifacts -Role "bundleSha256" -ExpectedStatus "PRESENT"
    Assert-ReleaseEvidenceArtifactsIndexRoleStatus -Artifacts $Artifacts -Role "summaryMarkdown" -ExpectedStatus "PRESENT"
    Assert-ReleaseEvidenceArtifactsIndexRoleStatus -Artifacts $Artifacts -Role "summaryJson" -ExpectedStatus "PRESENT"
    Assert-ReleaseEvidenceArtifactsIndexRoleStatus -Artifacts $Artifacts -Role "verificationReportJson" -ExpectedStatus $verificationStatus
    Assert-ReleaseEvidenceArtifactsIndexRoleStatus -Artifacts $Artifacts -Role "verificationReportMarkdown" -ExpectedStatus $verificationStatus
    Assert-ReleaseEvidenceArtifactsIndexRoleStatus -Artifacts $Artifacts -Role "preprodAcceptanceGateJson" -ExpectedStatus "PRESENT"
    Assert-ReleaseEvidenceArtifactsIndexRoleStatus -Artifacts $Artifacts -Role "preprodAcceptanceGateVerificationJson" -ExpectedStatus "PASSED"
    Assert-ReleaseEvidenceArtifactsIndexRoleStatus -Artifacts $Artifacts -Role "preprodAcceptanceGateVerificationMarkdown" -ExpectedStatus "PASSED"
}

function Assert-ReleaseEvidenceArtifactsIndexArtifactJsonProperty {
    param(
        [object[]]$Artifacts,
        [string]$Role,
        [string]$IndexDirectory,
        [string]$PropertyName,
        [string]$ExpectedValue
    )

    $artifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role $Role
    if ($null -eq $artifact) {
        return
    }

    if ([string]::IsNullOrWhiteSpace($ExpectedValue)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role $Role JSON $PropertyName" "FAILED" "Expected JSON property value for artifact role $Role is missing from release-evidence-artifacts-index.json."
        return
    }

    $pathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "path")
    $artifactPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $pathValue -IndexDirectory $IndexDirectory
    if ([string]::IsNullOrWhiteSpace($artifactPath) -or -not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        return
    }

    try {
        $json = Get-Content -LiteralPath $artifactPath -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role $Role JSON $PropertyName" "FAILED" "Cannot parse JSON artifact $pathValue`: $(($_ | Out-String).Trim())"
        return
    }

    $actualValue = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $json -Name $PropertyName
    if ($null -eq $actualValue) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role $Role JSON $PropertyName" "FAILED" "JSON property '$PropertyName' is missing."
        return
    }

    $actualText = [string]$actualValue
    if ($actualText.ToUpperInvariant() -eq $ExpectedValue.ToUpperInvariant()) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role $Role JSON $PropertyName" "PASSED" "JSON property '$PropertyName' is '$actualText'."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role $Role JSON $PropertyName" "FAILED" "JSON property '$PropertyName' is '$actualText', expected '$ExpectedValue'."
}

function Assert-ReleaseEvidenceArtifactsIndexSummaryJsonValue {
    param(
        [string]$PropertyName,
        [object]$ActualValue,
        [string]$ExpectedValue
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedValue)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role summaryJson JSON $PropertyName" "FAILED" "Expected summary JSON property value is missing from release-evidence-artifacts-index.json."
        return
    }

    if ($null -eq $ActualValue) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role summaryJson JSON $PropertyName" "FAILED" "Summary JSON property '$PropertyName' is missing."
        return
    }

    $actualText = [string]$ActualValue
    if ($actualText.ToUpperInvariant() -eq $ExpectedValue.ToUpperInvariant()) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role summaryJson JSON $PropertyName" "PASSED" "Summary JSON property '$PropertyName' is '$actualText'."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role summaryJson JSON $PropertyName" "FAILED" "Summary JSON property '$PropertyName' is '$actualText', expected '$ExpectedValue'."
}

function Assert-ReleaseEvidenceArtifactsIndexSummaryJsonSemantics {
    param(
        [object]$Index,
        [object[]]$Artifacts,
        [string]$IndexDirectory
    )

    if ($null -eq $Index) {
        return
    }

    $summaryArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "summaryJson"
    if ($null -eq $summaryArtifact) {
        return
    }

    $pathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $summaryArtifact -Name "path")
    $artifactPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $pathValue -IndexDirectory $IndexDirectory
    if ([string]::IsNullOrWhiteSpace($artifactPath) -or -not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        return
    }

    try {
        $summaryJson = Get-Content -LiteralPath $artifactPath -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role summaryJson JSON semantics" "FAILED" "Cannot parse JSON artifact $pathValue`: $(($_ | Out-String).Trim())"
        return
    }

    $bundleArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "bundle"
    $expectedBundleSha256 = $null
    if ($null -ne $bundleArtifact) {
        $expectedBundleSha256 = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $bundleArtifact -Name "sha256")
    }
    Assert-ReleaseEvidenceArtifactsIndexSummaryJsonValue -PropertyName "bundleSha256" -ActualValue (Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $summaryJson -Name "bundleSha256") -ExpectedValue $expectedBundleSha256

    $indexReleaseCheck = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "releaseCheck"
    $summaryReleaseCheck = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $summaryJson -Name "releaseCheck"
    Assert-ReleaseEvidenceArtifactsIndexSummaryJsonValue -PropertyName "releaseCheck.status" -ActualValue (Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $summaryReleaseCheck -Name "status") -ExpectedValue ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $indexReleaseCheck -Name "status"))
    Assert-ReleaseEvidenceArtifactsIndexSummaryJsonValue -PropertyName "releaseCheck.releaseCandidateCommit" -ActualValue (Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $summaryReleaseCheck -Name "releaseCandidateCommit") -ExpectedValue ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $indexReleaseCheck -Name "releaseCandidateCommit"))
    Assert-ReleaseEvidenceArtifactsIndexSummaryJsonValue -PropertyName "releaseCheck.allowDirtyWorktree" -ActualValue (Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $summaryReleaseCheck -Name "allowDirtyWorktree") -ExpectedValue ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $indexReleaseCheck -Name "allowDirtyWorktree"))
}

function Assert-ReleaseEvidenceArtifactsIndexBundleSha256SidecarSemantics {
    param(
        [object[]]$Artifacts,
        [string]$IndexDirectory
    )

    $bundleArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "bundle"
    $sha256Artifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "bundleSha256"
    if ($null -eq $bundleArtifact -or $null -eq $sha256Artifact) {
        return
    }

    $expectedHash = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $bundleArtifact -Name "sha256")
    if ([string]::IsNullOrWhiteSpace($expectedHash)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role bundleSha256 content" "FAILED" "Expected bundle SHA-256 is missing from release-evidence-artifacts-index.json."
        return
    }

    $pathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $sha256Artifact -Name "path")
    $artifactPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $pathValue -IndexDirectory $IndexDirectory
    if ([string]::IsNullOrWhiteSpace($artifactPath) -or -not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        return
    }

    $sidecarContent = (Get-Content -LiteralPath $artifactPath -Raw).Trim()
    $sha256Match = [regex]::Match($sidecarContent, "^(?<hash>[A-Fa-f0-9]{64})(\s+(?<fileName>.+))?$")
    if (-not $sha256Match.Success) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role bundleSha256 content" "FAILED" "SHA-256 sidecar content format is invalid: $pathValue"
        return
    }

    $expectedBundleFileName = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $bundleArtifact -Name "fileName")
    if ([string]::IsNullOrWhiteSpace($expectedBundleFileName)) {
        $bundlePathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $bundleArtifact -Name "path")
        $expectedBundleFileName = Split-Path -Path $bundlePathValue -Leaf
    }
    $sidecarBundleFileName = $sha256Match.Groups["fileName"].Value.Trim()
    if (-not [string]::IsNullOrWhiteSpace($sidecarBundleFileName)) {
        $sidecarBundleFileName = [System.IO.Path]::GetFileName($sidecarBundleFileName)
        if ($sidecarBundleFileName -eq $expectedBundleFileName) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role bundleSha256 file name" "PASSED" "Sidecar names the bundle artifact '$expectedBundleFileName'."
        }
        else {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role bundleSha256 file name" "FAILED" "Sidecar names '$sidecarBundleFileName', expected '$expectedBundleFileName'."
        }
    }

    $actualHash = $sha256Match.Groups["hash"].Value.ToUpperInvariant()
    if ($actualHash -eq $expectedHash.ToUpperInvariant()) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role bundleSha256 content" "PASSED" "Sidecar content matches bundle artifact SHA-256."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role bundleSha256 content" "FAILED" "Sidecar hash is '$actualHash', expected '$expectedHash'."
}

function Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue {
    param(
        [string]$Markdown,
        [string]$Field
    )

    foreach ($line in @($Markdown -split "`r?`n")) {
        $trimmedLine = $line.Trim()
        if (-not $trimmedLine.StartsWith("|")) {
            continue
        }

        $columns = @($trimmedLine.Trim("|").Split("|") | ForEach-Object { $_.Trim() })
        if ($columns.Count -lt 2) {
            continue
        }

        if ($columns[0] -eq $Field) {
            return $columns[1]
        }
    }

    return $null
}

function Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownValue {
    param(
        [string]$Field,
        [object]$ActualValue,
        [string]$ExpectedValue
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedValue)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role summaryMarkdown Markdown $Field" "FAILED" "Expected summary Markdown field value is missing from release-evidence-artifacts-index.json."
        return
    }

    if ($null -eq $ActualValue -or [string]::IsNullOrWhiteSpace([string]$ActualValue)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role summaryMarkdown Markdown $Field" "FAILED" "Summary Markdown field '$Field' is missing."
        return
    }

    $actualText = [string]$ActualValue
    if ($actualText.ToUpperInvariant() -eq $ExpectedValue.ToUpperInvariant()) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role summaryMarkdown Markdown $Field" "PASSED" "Summary Markdown field '$Field' is '$actualText'."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role summaryMarkdown Markdown $Field" "FAILED" "Summary Markdown field '$Field' is '$actualText', expected '$ExpectedValue'."
}

function Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownSemantics {
    param(
        [object]$Index,
        [object[]]$Artifacts,
        [string]$IndexDirectory
    )

    if ($null -eq $Index) {
        return
    }

    $summaryArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "summaryMarkdown"
    if ($null -eq $summaryArtifact) {
        return
    }

    $pathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $summaryArtifact -Name "path")
    $artifactPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $pathValue -IndexDirectory $IndexDirectory
    if ([string]::IsNullOrWhiteSpace($artifactPath) -or -not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        return
    }

    $summaryMarkdown = Get-Content -LiteralPath $artifactPath -Raw

    $bundleArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "bundle"
    $expectedBundleSha256 = $null
    if ($null -ne $bundleArtifact) {
        $expectedBundleSha256 = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $bundleArtifact -Name "sha256")
    }

    $releaseCheck = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "releaseCheck"
    Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownValue -Field "Bundle status" -ActualValue (Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue -Markdown $summaryMarkdown -Field "Bundle status") -ExpectedValue ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "bundleStatus"))
    Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownValue -Field "Bundle SHA-256" -ActualValue (Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue -Markdown $summaryMarkdown -Field "Bundle SHA-256") -ExpectedValue $expectedBundleSha256
    Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownValue -Field "Release check status" -ActualValue (Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue -Markdown $summaryMarkdown -Field "Release check status") -ExpectedValue ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $releaseCheck -Name "status"))
    Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownValue -Field "Release candidate commit" -ActualValue (Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue -Markdown $summaryMarkdown -Field "Release candidate commit") -ExpectedValue ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $releaseCheck -Name "releaseCandidateCommit"))
    Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownValue -Field "Release check allow dirty worktree" -ActualValue (Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue -Markdown $summaryMarkdown -Field "Release check allow dirty worktree") -ExpectedValue ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $releaseCheck -Name "allowDirtyWorktree"))
}

function Assert-ReleaseEvidenceArtifactsIndexVerificationReportMarkdownValue {
    param(
        [string]$Field,
        [object]$ActualValue,
        [string]$ExpectedValue
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedValue)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role verificationReportMarkdown Markdown $Field" "FAILED" "Expected verification report Markdown field value is missing."
        return
    }

    if ($null -eq $ActualValue -or [string]::IsNullOrWhiteSpace([string]$ActualValue)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role verificationReportMarkdown Markdown $Field" "FAILED" "Verification report Markdown field '$Field' is missing."
        return
    }

    $actualText = [string]$ActualValue
    if ($actualText.ToUpperInvariant() -eq $ExpectedValue.ToUpperInvariant()) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role verificationReportMarkdown Markdown $Field" "PASSED" "Verification report Markdown field '$Field' is '$actualText'."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role verificationReportMarkdown Markdown $Field" "FAILED" "Verification report Markdown field '$Field' is '$actualText', expected '$ExpectedValue'."
}

function Assert-ReleaseEvidenceArtifactsIndexVerificationReportMarkdownPath {
    param(
        [string]$Field,
        [object]$ActualValue,
        [string]$ExpectedPath,
        [string]$IndexDirectory
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedPath)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role verificationReportMarkdown Markdown $Field" "FAILED" "Expected verification report Markdown path value is missing from release-evidence-artifacts-index.json."
        return
    }

    if ($null -eq $ActualValue -or [string]::IsNullOrWhiteSpace([string]$ActualValue)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role verificationReportMarkdown Markdown $Field" "FAILED" "Verification report Markdown field '$Field' is missing."
        return
    }

    $actualPath = Resolve-ReleaseEvidenceArtifactPath -PathValue ([string]$ActualValue) -IndexDirectory $IndexDirectory
    $expectedResolvedPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $ExpectedPath -IndexDirectory $IndexDirectory
    $actualFullPath = [System.IO.Path]::GetFullPath($actualPath)
    $expectedFullPath = [System.IO.Path]::GetFullPath($expectedResolvedPath)
    if ($actualFullPath -eq $expectedFullPath) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role verificationReportMarkdown Markdown $Field" "PASSED" "Verification report Markdown path field '$Field' matches artifact path."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role verificationReportMarkdown Markdown $Field" "FAILED" "Verification report Markdown field '$Field' is '$actualFullPath', expected '$expectedFullPath'."
}

function Assert-ReleaseEvidenceArtifactsIndexVerificationReportMarkdownSemantics {
    param(
        [object[]]$Artifacts,
        [string]$IndexDirectory
    )

    $markdownArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "verificationReportMarkdown"
    $jsonArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "verificationReportJson"
    if ($null -eq $markdownArtifact -or $null -eq $jsonArtifact) {
        return
    }

    $markdownPathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $markdownArtifact -Name "path")
    $markdownPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $markdownPathValue -IndexDirectory $IndexDirectory
    if ([string]::IsNullOrWhiteSpace($markdownPath) -or -not (Test-Path -LiteralPath $markdownPath -PathType Leaf)) {
        return
    }

    $jsonPathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $jsonArtifact -Name "path")
    $jsonPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $jsonPathValue -IndexDirectory $IndexDirectory
    if ([string]::IsNullOrWhiteSpace($jsonPath) -or -not (Test-Path -LiteralPath $jsonPath -PathType Leaf)) {
        return
    }

    try {
        $verificationJson = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json
    }
    catch {
        return
    }

    $verificationMarkdown = Get-Content -LiteralPath $markdownPath -Raw
    Assert-ReleaseEvidenceArtifactsIndexVerificationReportMarkdownValue -Field "Status" -ActualValue (Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue -Markdown $verificationMarkdown -Field "Status") -ExpectedValue ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $verificationJson -Name "status"))

    $bundleArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "bundle"
    if ($null -ne $bundleArtifact) {
        Assert-ReleaseEvidenceArtifactsIndexVerificationReportMarkdownPath -Field "Bundle path" -ActualValue (Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue -Markdown $verificationMarkdown -Field "Bundle path") -ExpectedPath ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $bundleArtifact -Name "path")) -IndexDirectory $IndexDirectory
    }

    $sha256Artifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "bundleSha256"
    if ($null -ne $sha256Artifact) {
        Assert-ReleaseEvidenceArtifactsIndexVerificationReportMarkdownPath -Field "SHA-256 file" -ActualValue (Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue -Markdown $verificationMarkdown -Field "SHA-256 file") -ExpectedPath ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $sha256Artifact -Name "path")) -IndexDirectory $IndexDirectory
    }
}

function Assert-ReleaseEvidenceArtifactsIndexPreprodAcceptanceGateVerificationMarkdownValue {
    param(
        [string]$Field,
        [object]$ActualValue,
        [string]$ExpectedValue
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedValue)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role preprodAcceptanceGateVerificationMarkdown Markdown $Field" "FAILED" "Expected preproduction gate verification Markdown field value is missing."
        return
    }

    if ($null -eq $ActualValue -or [string]::IsNullOrWhiteSpace([string]$ActualValue)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role preprodAcceptanceGateVerificationMarkdown Markdown $Field" "FAILED" "Preproduction gate verification Markdown field '$Field' is missing."
        return
    }

    $actualText = [string]$ActualValue
    if ($actualText.ToUpperInvariant() -eq $ExpectedValue.ToUpperInvariant()) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role preprodAcceptanceGateVerificationMarkdown Markdown $Field" "PASSED" "Preproduction gate verification Markdown field '$Field' is '$actualText'."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact role preprodAcceptanceGateVerificationMarkdown Markdown $Field" "FAILED" "Preproduction gate verification Markdown field '$Field' is '$actualText', expected '$ExpectedValue'."
}

function Assert-ReleaseEvidenceArtifactsIndexPreprodAcceptanceGateVerificationMarkdownSemantics {
    param(
        [object[]]$Artifacts,
        [string]$IndexDirectory
    )

    $markdownArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "preprodAcceptanceGateVerificationMarkdown"
    $jsonArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "preprodAcceptanceGateVerificationJson"
    if ($null -eq $markdownArtifact -or $null -eq $jsonArtifact) {
        return
    }

    $markdownPathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $markdownArtifact -Name "path")
    $markdownPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $markdownPathValue -IndexDirectory $IndexDirectory
    if ([string]::IsNullOrWhiteSpace($markdownPath) -or -not (Test-Path -LiteralPath $markdownPath -PathType Leaf)) {
        return
    }

    $jsonPathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $jsonArtifact -Name "path")
    $jsonPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $jsonPathValue -IndexDirectory $IndexDirectory
    if ([string]::IsNullOrWhiteSpace($jsonPath) -or -not (Test-Path -LiteralPath $jsonPath -PathType Leaf)) {
        return
    }

    try {
        $gateVerificationJson = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json
    }
    catch {
        return
    }

    $gateVerificationMarkdown = Get-Content -LiteralPath $markdownPath -Raw
    Assert-ReleaseEvidenceArtifactsIndexPreprodAcceptanceGateVerificationMarkdownValue -Field "Status" -ActualValue (Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue -Markdown $gateVerificationMarkdown -Field "Status") -ExpectedValue ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $gateVerificationJson -Name "status"))
}

function Assert-ReleaseEvidenceArtifactsIndexEvidenceIndexCandidateCommit {
    param(
        [object]$Index,
        [string]$IndexDirectory
    )

    $evidenceIndexPath = Join-Path $IndexDirectory "evidence-index.json"
    if (-not (Test-Path -LiteralPath $evidenceIndexPath -PathType Leaf)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Evidence index candidate binding" "PASSED" "evidence-index.json is absent; candidate consistency check was skipped for a legacy evidence directory."
        return
    }

    try {
        $evidenceIndex = Get-Content -LiteralPath $evidenceIndexPath -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceArtifactsIndexCheck "Evidence index candidate binding" "FAILED" "Cannot parse evidence-index.json: $(($_ | Out-String).Trim())"
        return
    }

    $evidenceReleaseCheck = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $evidenceIndex -Name "releaseCheck"
    if ($null -eq $evidenceReleaseCheck) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Evidence index candidate binding" "PASSED" "evidence-index.json has no releaseCheck binding; legacy candidate check was skipped."
        return
    }

    $artifactsReleaseCheck = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "releaseCheck"
    $evidenceCandidate = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $evidenceReleaseCheck -Name "releaseCandidateCommit"
    $artifactsCandidate = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifactsReleaseCheck -Name "releaseCandidateCommit"
    if ([string]::IsNullOrWhiteSpace([string]$evidenceCandidate) -or [string]::IsNullOrWhiteSpace([string]$artifactsCandidate)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Evidence index candidate binding" "FAILED" "Evidence index and artifacts index must both declare releaseCheck.releaseCandidateCommit."
        return
    }

    $evidenceCandidate = ([string]$evidenceCandidate).Trim()
    $artifactsCandidate = ([string]$artifactsCandidate).Trim()
    if ($evidenceCandidate.Equals($artifactsCandidate, [System.StringComparison]::OrdinalIgnoreCase)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Evidence index candidate binding" "PASSED" "Evidence index candidate matches artifacts index candidate $artifactsCandidate."
    }
    else {
        Add-ReleaseEvidenceArtifactsIndexCheck "Evidence index candidate binding" "FAILED" "Evidence index candidate $evidenceCandidate does not match artifacts index candidate $artifactsCandidate."
    }
}

function Assert-ReleaseEvidenceArtifactsIndexJsonSemantics {
    param(
        [object]$Index,
        [object[]]$Artifacts,
        [string]$IndexDirectory
    )

    if ($null -eq $Index) {
        return
    }

    $bundleStatus = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "bundleStatus")
    $verificationStatus = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "verificationStatus")

    Assert-ReleaseEvidenceArtifactsIndexEvidenceIndexCandidateCommit -Index $Index -IndexDirectory $IndexDirectory
    Assert-ReleaseEvidenceArtifactsIndexArtifactJsonProperty -Artifacts $Artifacts -Role "summaryJson" -IndexDirectory $IndexDirectory -PropertyName "bundleStatus" -ExpectedValue $bundleStatus
    Assert-ReleaseEvidenceArtifactsIndexSummaryJsonSemantics -Index $Index -Artifacts $Artifacts -IndexDirectory $IndexDirectory
    Assert-ReleaseEvidenceArtifactsIndexBundleSha256SidecarSemantics -Artifacts $Artifacts -IndexDirectory $IndexDirectory
    Assert-ReleaseEvidenceArtifactsIndexArtifactJsonProperty -Artifacts $Artifacts -Role "verificationReportJson" -IndexDirectory $IndexDirectory -PropertyName "status" -ExpectedValue $verificationStatus
    Assert-ReleaseEvidenceArtifactsIndexVerificationReportMarkdownSemantics -Artifacts $Artifacts -IndexDirectory $IndexDirectory
    Assert-ReleaseEvidenceArtifactsIndexArtifactJsonProperty -Artifacts $Artifacts -Role "preprodAcceptanceGateJson" -IndexDirectory $IndexDirectory -PropertyName "verdict" -ExpectedValue "READY_FOR_APPROVAL"
    Assert-ReleaseEvidenceArtifactsIndexArtifactJsonProperty -Artifacts $Artifacts -Role "preprodAcceptanceGateVerificationJson" -IndexDirectory $IndexDirectory -PropertyName "status" -ExpectedValue "PASSED"
    Assert-ReleaseEvidenceArtifactsIndexPreprodAcceptanceGateVerificationMarkdownSemantics -Artifacts $Artifacts -IndexDirectory $IndexDirectory
}

function Assert-ReleaseEvidenceArtifactsIndexEvidenceDirectory {
    param(
        [object]$Index,
        [string]$IndexDirectory
    )

    $declaredEvidenceDirectory = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "evidenceDirectory")
    if ([string]::IsNullOrWhiteSpace($declaredEvidenceDirectory)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index evidenceDirectory" "FAILED" "evidenceDirectory is missing."
        return
    }

    try {
        $declaredFullPath = [System.IO.Path]::GetFullPath($declaredEvidenceDirectory)
    }
    catch {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index evidenceDirectory" "FAILED" "evidenceDirectory is not a valid path: $declaredEvidenceDirectory"
        return
    }

    $expectedFullPath = [System.IO.Path]::GetFullPath($IndexDirectory)
    if ($declaredFullPath -eq $expectedFullPath) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index evidenceDirectory" "PASSED" "evidenceDirectory matches the artifacts index directory."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index evidenceDirectory" "FAILED" "evidenceDirectory is '$declaredEvidenceDirectory', expected '$expectedFullPath'."
}

function Assert-ReleaseEvidenceArtifactsIndexBundlePath {
    param(
        [object]$Index,
        [object[]]$Artifacts
    )

    $declaredBundlePath = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "bundlePath")
    if ([string]::IsNullOrWhiteSpace($declaredBundlePath)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index bundlePath" "FAILED" "bundlePath is missing."
        return
    }

    $bundleArtifact = Get-ReleaseEvidenceArtifactsIndexArtifactByRole -Artifacts $Artifacts -Role "bundle"
    if ($null -eq $bundleArtifact) {
        return
    }

    $artifactBundlePath = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $bundleArtifact -Name "path")
    if ([string]::IsNullOrWhiteSpace($artifactBundlePath)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index bundlePath" "FAILED" "bundle artifact path is missing."
        return
    }

    if ($declaredBundlePath -eq $artifactBundlePath) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index bundlePath" "PASSED" "bundlePath matches the bundle artifact path."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index bundlePath" "FAILED" "bundlePath is '$declaredBundlePath', expected '$artifactBundlePath'."
}

function Assert-ReleaseEvidenceArtifactsIndexArtifactFileName {
    param(
        [object]$Artifact,
        [string]$role,
        [string]$PathValue
    )

    $declaredFileName = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Artifact -Name "fileName")
    $expectedFileName = if ([string]::IsNullOrWhiteSpace($PathValue)) { "UNKNOWN" } else { Split-Path -Path $PathValue -Leaf }
    if ($declaredFileName -eq $expectedFileName) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role fileName" "PASSED" "fileName matches path leaf."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role fileName" "FAILED" "fileName is '$declaredFileName', expected '$expectedFileName'."
}

function Assert-ReleaseEvidenceArtifactsIndexArtifactLastWriteTimeUtc {
    param(
        [object]$Artifact,
        [string]$role,
        [System.IO.FileInfo]$File
    )

    $declaredLastWriteTimeUtc = Format-ReleaseEvidenceArtifactsIndexMarkdownValue (Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Artifact -Name "lastWriteTimeUtc")
    $expectedLastWriteTimeUtc = $File.LastWriteTimeUtc.ToString("o")
    if ($declaredLastWriteTimeUtc -eq $expectedLastWriteTimeUtc) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role lastWriteTimeUtc" "PASSED" "lastWriteTimeUtc matches file metadata."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role lastWriteTimeUtc" "FAILED" "lastWriteTimeUtc is '$declaredLastWriteTimeUtc', expected '$expectedLastWriteTimeUtc'."
}

function Assert-ReleaseEvidenceArtifactsIndexJson {
    param(
        [string]$ResolvedArtifactsIndexPath,
        [string]$IndexDirectory
    )

    if (-not (Test-Path -LiteralPath $ResolvedArtifactsIndexPath -PathType Leaf)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index JSON" "FAILED" "release-evidence-artifacts-index.json does not exist: $ResolvedArtifactsIndexPath"
        return $null
    }

    try {
        $index = Get-Content -LiteralPath $ResolvedArtifactsIndexPath -Raw | ConvertFrom-Json
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index JSON" "PASSED" "Parsed release-evidence-artifacts-index.json."
    }
    catch {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index JSON" "FAILED" "Cannot parse release-evidence-artifacts-index.json: $(($_ | Out-String).Trim())"
        return $null
    }

    $schemaVersion = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $index -Name "schemaVersion"
    if ([int]$schemaVersion -eq 1) {
        Add-ReleaseEvidenceArtifactsIndexCheck "schemaVersion" "PASSED" "schemaVersion is 1."
    }
    else {
        Add-ReleaseEvidenceArtifactsIndexCheck "schemaVersion" "FAILED" "schemaVersion is '$schemaVersion', expected 1."
    }

    $verificationStatus = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $index -Name "verificationStatus")
    if (-not [string]::IsNullOrWhiteSpace($verificationStatus)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "verificationStatus" "PASSED" "verificationStatus is $verificationStatus."
    }
    else {
        Add-ReleaseEvidenceArtifactsIndexCheck "verificationStatus" "FAILED" "verificationStatus is missing."
    }

    $artifacts = @(Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $index -Name "artifacts" | Where-Object { $null -ne $_ })
    if ($artifacts.Count -gt 0) {
        Add-ReleaseEvidenceArtifactsIndexCheck "artifacts" "PASSED" "Artifacts index lists $($artifacts.Count) artifact(s)."
    }
    else {
        Add-ReleaseEvidenceArtifactsIndexCheck "artifacts" "FAILED" "Artifacts index has no artifacts."
    }

    $artifactCount = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $index -Name "artifactCount"
    if ([int]$artifactCount -eq $artifacts.Count) {
        Add-ReleaseEvidenceArtifactsIndexCheck "artifactCount" "PASSED" "artifactCount matches artifacts array."
    }
    else {
        Add-ReleaseEvidenceArtifactsIndexCheck "artifactCount" "FAILED" "artifactCount is '$artifactCount', expected $($artifacts.Count)."
    }

    Assert-ReleaseEvidenceArtifactsIndexRequiredRoles -Artifacts $artifacts
    Assert-ReleaseEvidenceArtifactsIndexDuplicateRoles -Artifacts $artifacts
    Assert-ReleaseEvidenceArtifactsIndexRoleStatuses -Index $index -Artifacts $artifacts
    Assert-ReleaseEvidenceArtifactsIndexEvidenceDirectory -Index $index -IndexDirectory $IndexDirectory
    Assert-ReleaseEvidenceArtifactsIndexBundlePath -Index $index -Artifacts $artifacts

    $actualMissingCount = 0
    foreach ($artifact in $artifacts) {
        $role = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "role")
        if ([string]::IsNullOrWhiteSpace($role)) {
            $role = "UNKNOWN"
        }

        $pathValue = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "path")
        $artifactPath = Resolve-ReleaseEvidenceArtifactPath -PathValue $pathValue -IndexDirectory $IndexDirectory
        $declaredExistsValue = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "exists"
        $declaredExists = $false
        if ($null -ne $declaredExistsValue) {
            $declaredExists = [System.Convert]::ToBoolean($declaredExistsValue)
        }

        if (-not $declaredExists) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role exists flag" "FAILED" "Artifact is not marked as existing."
            $actualMissingCount++
            continue
        }

        if ([string]::IsNullOrWhiteSpace($artifactPath) -or -not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role file" "FAILED" "Artifact file does not exist: $pathValue"
            $actualMissingCount++
            continue
        }
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role file" "PASSED" "Artifact file exists: $artifactPath"

        $file = Get-Item -LiteralPath $artifactPath
        Assert-ReleaseEvidenceArtifactsIndexArtifactFileName -Artifact $artifact -Role $role -PathValue $pathValue
        Assert-ReleaseEvidenceArtifactsIndexArtifactLastWriteTimeUtc -Artifact $artifact -Role $role -File $file

        $declaredLength = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "length"
        if ([long]$declaredLength -eq $file.Length) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role length" "PASSED" "Length matches $($file.Length)."
        }
        else {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role length" "FAILED" "Length is '$declaredLength', expected $($file.Length)."
        }

        $declaredSha256 = ([string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "sha256")).ToUpperInvariant()
        $actualSha256 = (Get-Sha256Hex -LiteralPath $artifactPath).ToUpperInvariant()
        if ($declaredSha256 -eq $actualSha256) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role SHA-256" "PASSED" "SHA-256 matches index."
        }
        else {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifact $role SHA-256" "FAILED" "Expected $declaredSha256 but found $actualSha256."
        }
    }

    $missingArtifactCount = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $index -Name "missingArtifactCount"
    if ([int]$missingArtifactCount -eq $actualMissingCount) {
        Add-ReleaseEvidenceArtifactsIndexCheck "missingArtifactCount" "PASSED" "missingArtifactCount matches actual missing artifacts."
    }
    else {
        Add-ReleaseEvidenceArtifactsIndexCheck "missingArtifactCount" "FAILED" "missingArtifactCount is '$missingArtifactCount', expected $actualMissingCount."
    }

    Assert-ReleaseEvidenceArtifactsIndexJsonSemantics -Index $index -Artifacts $artifacts -IndexDirectory $IndexDirectory
    Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownSemantics -Index $index -Artifacts $artifacts -IndexDirectory $IndexDirectory

    return $index
}

function Get-ReleaseEvidenceArtifactsIndexMarkdownSummaryRows {
    param([string]$Markdown)

    $lines = @($Markdown -split "`r?`n")
    $header = "| Field | Value |"
    $separator = "| --- | --- |"
    $headerIndex = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].Trim() -eq $header) {
            $headerIndex = $i
            break
        }
    }

    if ($headerIndex -lt 0) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown summary table" "FAILED" "Markdown summary table header is missing."
        return @()
    }
    Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown summary table" "PASSED" "Found Markdown summary table header."

    $separatorIndex = $headerIndex + 1
    if ($separatorIndex -ge $lines.Count -or $lines[$separatorIndex].Trim() -ne $separator) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown summary table separator" "FAILED" "Markdown summary table separator is missing."
        return @()
    }
    Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown summary table separator" "PASSED" "Found Markdown summary table separator."

    $rows = [System.Collections.Generic.List[string]]::new()
    for ($i = $headerIndex + 2; $i -lt $lines.Count; $i++) {
        $line = $lines[$i].TrimEnd()
        if ([string]::IsNullOrWhiteSpace($line)) {
            break
        }
        if (-not $line.StartsWith("|")) {
            break
        }

        $rows.Add($line)
    }

    return @($rows)
}

function Get-ReleaseEvidenceArtifactsIndexMarkdownSummaryRow {
    param(
        [string]$Field,
        [object]$Value
    )

    return "| $Field | $(Format-ReleaseEvidenceArtifactsIndexMarkdownValue $Value) |"
}

function Assert-ReleaseEvidenceArtifactsIndexMarkdownSummaryRow {
    param(
        [string[]]$ActualRows,
        [string]$Field,
        [object]$ExpectedValue
    )

    $expectedRow = Get-ReleaseEvidenceArtifactsIndexMarkdownSummaryRow -Field $Field -Value $ExpectedValue
    if (@($ActualRows) -ccontains $expectedRow) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown summary field $Field" "PASSED" "Markdown summary field matches release-evidence-artifacts-index.json."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown summary field $Field" "FAILED" "Expected Markdown summary row is missing or differs from release-evidence-artifacts-index.json: $expectedRow"
}

function Assert-ReleaseEvidenceArtifactsIndexMarkdownSummaryRows {
    param(
        [string]$Markdown,
        [object]$Index
    )

    if ($null -eq $Index) {
        return
    }

    $releaseCheck = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "releaseCheck"
    $expectedRows = @(
        [pscustomobject]@{ Field = "Bundle status"; Value = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "bundleStatus" }
        [pscustomobject]@{ Field = "Verification status"; Value = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "verificationStatus" }
        [pscustomobject]@{ Field = "Release check status"; Value = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $releaseCheck -Name "status" }
        [pscustomobject]@{ Field = "Release candidate commit"; Value = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $releaseCheck -Name "releaseCandidateCommit" }
        [pscustomobject]@{ Field = "Release check allow dirty worktree"; Value = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $releaseCheck -Name "allowDirtyWorktree" }
        [pscustomobject]@{ Field = "Artifact count"; Value = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "artifactCount" }
        [pscustomobject]@{ Field = "Missing artifacts"; Value = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "missingArtifactCount" }
        [pscustomobject]@{ Field = "Generated at"; Value = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "generatedAt" }
    )
    $actualRows = @(Get-ReleaseEvidenceArtifactsIndexMarkdownSummaryRows -Markdown $Markdown)

    if ($actualRows.Count -eq $expectedRows.Count) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown summary row count" "PASSED" "Markdown summary row count matches expected fields."
    }
    else {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown summary row count" "FAILED" "Markdown summary row count is $($actualRows.Count), expected $($expectedRows.Count)."
    }

    foreach ($row in $expectedRows) {
        Assert-ReleaseEvidenceArtifactsIndexMarkdownSummaryRow -ActualRows $actualRows -Field $row.Field -ExpectedValue $row.Value
    }
}

function Get-ReleaseEvidenceArtifactsIndexMarkdownArtifactRows {
    param([string]$Markdown)

    $lines = @($Markdown -split "`r?`n")
    $header = "| Artifact | Status | SHA-256 | Bytes | Path |"
    $separator = "| --- | --- | --- | --- | --- |"
    $headerIndex = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].Trim() -eq $header) {
            $headerIndex = $i
            break
        }
    }

    if ($headerIndex -lt 0) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown artifact table" "FAILED" "Markdown artifact table header is missing."
        return @()
    }
    Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown artifact table" "PASSED" "Found Markdown artifact table header."

    $separatorIndex = $headerIndex + 1
    if ($separatorIndex -ge $lines.Count -or $lines[$separatorIndex].Trim() -ne $separator) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown artifact table separator" "FAILED" "Markdown artifact table separator is missing."
        return @()
    }
    Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown artifact table separator" "PASSED" "Found Markdown artifact table separator."

    $rows = [System.Collections.Generic.List[string]]::new()
    for ($i = $headerIndex + 2; $i -lt $lines.Count; $i++) {
        $line = $lines[$i].TrimEnd()
        if ([string]::IsNullOrWhiteSpace($line)) {
            break
        }
        if (-not $line.StartsWith("|")) {
            break
        }

        $rows.Add($line)
    }

    return @($rows)
}

function Get-ReleaseEvidenceArtifactsIndexMarkdownArtifactRow {
    param([object]$Artifact)

    $role = Format-ReleaseEvidenceArtifactsIndexMarkdownValue (Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Artifact -Name "role")
    $status = Format-ReleaseEvidenceArtifactsIndexMarkdownValue (Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Artifact -Name "status")
    $sha256 = Format-ReleaseEvidenceArtifactsIndexMarkdownValue (Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Artifact -Name "sha256")
    $length = Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Artifact -Name "length"
    $path = Format-ReleaseEvidenceArtifactsIndexMarkdownValue (Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Artifact -Name "path")

    return "| $role | $status | $sha256 | $length | $path |"
}

function Assert-ReleaseEvidenceArtifactsIndexMarkdownArtifactRow {
    param(
        [string]$Markdown,
        [object]$Artifact,
        [string[]]$ActualRows
    )

    if ($null -eq $ActualRows) {
        $ActualRows = @(Get-ReleaseEvidenceArtifactsIndexMarkdownArtifactRows -Markdown $Markdown)
    }

    $role = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Artifact -Name "role")
    if ([string]::IsNullOrWhiteSpace($role)) {
        $role = "UNKNOWN"
    }

    $expectedRow = Get-ReleaseEvidenceArtifactsIndexMarkdownArtifactRow -Artifact $Artifact
    if (@($ActualRows) -ccontains $expectedRow) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown artifact row $role" "PASSED" "Markdown artifact row matches release-evidence-artifacts-index.json."
        return
    }

    Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown artifact row $role" "FAILED" "Expected Markdown artifact row is missing or differs from release-evidence-artifacts-index.json: $expectedRow"
}

function Assert-ReleaseEvidenceArtifactsIndexMarkdownArtifactRows {
    param(
        [string]$Markdown,
        [object]$Index
    )

    if ($null -eq $Index) {
        return
    }

    $artifacts = @(Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "artifacts" | Where-Object { $null -ne $_ })
    $actualRows = @(Get-ReleaseEvidenceArtifactsIndexMarkdownArtifactRows -Markdown $Markdown)

    if ($actualRows.Count -eq $artifacts.Count) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown artifact row count" "PASSED" "Markdown artifact row count matches artifacts array."
    }
    else {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown artifact row count" "FAILED" "Markdown artifact row count is $($actualRows.Count), expected $($artifacts.Count)."
    }

    foreach ($artifact in $artifacts) {
        Assert-ReleaseEvidenceArtifactsIndexMarkdownArtifactRow -Markdown $Markdown -Artifact $artifact -ActualRows $actualRows
    }
}

function Assert-ReleaseEvidenceArtifactsIndexMarkdown {
    param(
        [string]$ResolvedArtifactsIndexMarkdownPath,
        [object]$Index
    )

    if (-not (Test-Path -LiteralPath $ResolvedArtifactsIndexMarkdownPath -PathType Leaf)) {
        Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown" "FAILED" "release-evidence-artifacts-index.md does not exist: $ResolvedArtifactsIndexMarkdownPath"
        return
    }

    $markdown = Get-Content -LiteralPath $ResolvedArtifactsIndexMarkdownPath -Raw
    Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown" "PASSED" "Found release-evidence-artifacts-index.md."

    foreach ($fragment in @("Release evidence artifacts index", "Artifact count", "Missing artifacts")) {
        if ($markdown.Contains($fragment)) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown $fragment" "PASSED" "Found expected Markdown fragment."
        }
        else {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown $fragment" "FAILED" "Missing expected Markdown fragment."
        }
    }

    if ($null -eq $Index) {
        return
    }

    foreach ($artifact in @(Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $Index -Name "artifacts")) {
        $role = [string](Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $artifact -Name "role")
        if (-not [string]::IsNullOrWhiteSpace($role) -and $markdown.Contains($role)) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown role $role" "PASSED" "Markdown lists artifact role."
        }
        elseif (-not [string]::IsNullOrWhiteSpace($role)) {
            Add-ReleaseEvidenceArtifactsIndexCheck "Artifacts index Markdown role $role" "FAILED" "Markdown does not list artifact role."
        }
    }

    Assert-ReleaseEvidenceArtifactsIndexMarkdownSummaryRows -Markdown $markdown -Index $Index
    Assert-ReleaseEvidenceArtifactsIndexMarkdownArtifactRows -Markdown $markdown -Index $Index
}

$resolvedArtifactsIndexPath = Resolve-ReleaseEvidenceArtifactsIndexPath
$artifactsIndexDirectory = Split-Path -Path $resolvedArtifactsIndexPath -Parent
$resolvedArtifactsIndexMarkdownPath = Join-Path $artifactsIndexDirectory "release-evidence-artifacts-index.md"
$verificationReportJsonPath = Join-Path $artifactsIndexDirectory "release-evidence-artifacts-index.verify-report.json"
$verificationReportMarkdownPath = Join-Path $artifactsIndexDirectory "release-evidence-artifacts-index.verify-report.md"
$verificationStatus = "FAILED"
$verificationFailureReason = $null

try {
    $index = Assert-ReleaseEvidenceArtifactsIndexJson -ResolvedArtifactsIndexPath $resolvedArtifactsIndexPath -IndexDirectory $artifactsIndexDirectory
    Assert-ReleaseEvidenceArtifactsIndexMarkdown -ResolvedArtifactsIndexMarkdownPath $resolvedArtifactsIndexMarkdownPath -Index $index

    if ($artifactIndexFailureCount -gt 0) {
        throw "Release evidence artifacts index verification failed with $artifactIndexFailureCount failed check(s)."
    }

    $verificationStatus = "PASSED"
    [System.Console]::WriteLine("[release-evidence-artifacts-index-verify] Artifacts index verification passed: $resolvedArtifactsIndexPath")
}
catch {
    $verificationStatus = "FAILED"
    $verificationFailureReason = $_.Exception.Message
    throw
}
finally {
    Save-ReleaseEvidenceArtifactsIndexVerificationReport -ReportJsonPath $verificationReportJsonPath -ReportMarkdownPath $verificationReportMarkdownPath -ArtifactsIndexPath $resolvedArtifactsIndexPath -Status $verificationStatus -FailureReason $verificationFailureReason
}
