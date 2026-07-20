param(
    [string]$EvidenceIndexPath,
    [string]$EvidenceDirectory,
    [switch]$RequireUploadedFallback
)

$ErrorActionPreference = "Stop"

$checks = [System.Collections.Generic.List[object]]::new()
$failureCount = 0

function Add-EvidenceIndexCheck {
    param(
        [string]$Name,
        [ValidateSet("PASSED", "FAILED")]
        [string]$Status,
        [string]$Detail
    )

    $script:checks.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
    [System.Console]::WriteLine("[evidence-index] $Status $Name - $Detail")
    if ($Status -eq "FAILED") {
        $script:failureCount++
    }
}

function Format-EvidenceIndexVerificationReportValue {
    param([object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return ""
    }

    return ([string]$Value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Save-EvidenceIndexVerificationReport {
    param(
        [string]$ReportJsonPath,
        [string]$ReportMarkdownPath,
        [string]$ResolvedEvidenceIndexPath,
        [string]$Status,
        [string]$FailureReason
    )

    $report = [ordered]@{
        schemaVersion = 1
        generatedAt = Get-Date -Format "o"
        evidenceIndexPath = $ResolvedEvidenceIndexPath
        status = $Status
        requireUploadedFallback = $RequireUploadedFallback.IsPresent
        failureCount = $failureCount
        failureReason = $FailureReason
        checks = @($checks)
    }

    $reportDirectory = Split-Path -Path $ReportJsonPath -Parent
    if ($reportDirectory -and -not (Test-Path -LiteralPath $reportDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ReportJsonPath -Encoding UTF8

    $markdownLines = [System.Collections.Generic.List[string]]::new()
    $markdownLines.Add("# Evidence index verification report")
    $markdownLines.Add("")
    $markdownLines.Add("| Field | Value |")
    $markdownLines.Add("| --- | --- |")
    $markdownLines.Add("| Status | $(Format-EvidenceIndexVerificationReportValue $Status) |")
    $markdownLines.Add("| Evidence index | $(Format-EvidenceIndexVerificationReportValue $ResolvedEvidenceIndexPath) |")
    $markdownLines.Add("| Require uploaded fallback | $($RequireUploadedFallback.IsPresent) |")
    $markdownLines.Add("| Failure count | $failureCount |")
    $markdownLines.Add("| Failure reason | $(Format-EvidenceIndexVerificationReportValue $FailureReason) |")
    $markdownLines.Add("")
    $markdownLines.Add("| Check | Status | Detail |")
    $markdownLines.Add("| --- | --- | --- |")
    foreach ($check in @($checks)) {
        $markdownLines.Add("| $(Format-EvidenceIndexVerificationReportValue $check.Name) | $(Format-EvidenceIndexVerificationReportValue $check.Status) | $(Format-EvidenceIndexVerificationReportValue $check.Detail) |")
    }
    $markdownLines | Set-Content -LiteralPath $ReportMarkdownPath -Encoding UTF8

    [System.Console]::WriteLine("[evidence-index] Verification report JSON: $ReportJsonPath")
    [System.Console]::WriteLine("[evidence-index] Verification report Markdown: $ReportMarkdownPath")
}

function Get-PreprodEvidenceIndexPath {
    if (-not [string]::IsNullOrWhiteSpace($EvidenceIndexPath)) {
        if (-not (Test-Path -LiteralPath $EvidenceIndexPath -PathType Leaf)) {
            throw "EvidenceIndexPath does not exist: $EvidenceIndexPath"
        }
        return (Resolve-Path -LiteralPath $EvidenceIndexPath).Path
    }

    if (-not [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        if (-not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) {
            throw "EvidenceDirectory does not exist: $EvidenceDirectory"
        }
        $path = Join-Path $EvidenceDirectory "evidence-index.json"
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "EvidenceDirectory does not contain evidence-index.json: $EvidenceDirectory"
        }
        return (Resolve-Path -LiteralPath $path).Path
    }

    throw "Provide -EvidenceIndexPath or -EvidenceDirectory."
}

function Get-ObjectPropertyValue {
    param(
        [object]$Object,
        [string]$FieldName
    )

    if ($null -eq $Object) {
        return $null
    }

    $property = $Object.PSObject.Properties |
        Where-Object { $_.Name -ieq $FieldName } |
        Select-Object -First 1
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Assert-EvidenceIndexRequiredField {
    param(
        [object]$Object,
        [string]$FieldName,
        [string]$Context
    )

    $value = Get-ObjectPropertyValue -Object $Object -FieldName $FieldName
    if ($null -eq $value -or [string]::IsNullOrWhiteSpace([string]$value)) {
        Add-EvidenceIndexCheck "$Context.$FieldName" "FAILED" "Required field is missing."
        return $null
    }

    Add-EvidenceIndexCheck "$Context.$FieldName" "PASSED" "Value: $value"
    return $value
}

function Resolve-EvidencePath {
    param(
        [string]$PathValue,
        [string]$IndexDirectory,
        [string]$Repository
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }
    if (-not [string]::IsNullOrWhiteSpace($Repository)) {
        return (Join-Path $Repository $PathValue)
    }
    return (Join-Path $IndexDirectory $PathValue)
}

function Assert-EvidenceFileExists {
    param(
        [string]$Name,
        [string]$PathValue,
        [string]$IndexDirectory,
        [string]$Repository
    )

    $resolvedPath = Resolve-EvidencePath -PathValue $PathValue -IndexDirectory $IndexDirectory -Repository $Repository
    if ([string]::IsNullOrWhiteSpace($resolvedPath)) {
        Add-EvidenceIndexCheck $Name "FAILED" "Path is missing."
        return [pscustomobject]@{
            Path = $null
            Exists = $false
        }
    }

    $exists = Test-Path -LiteralPath $resolvedPath -PathType Leaf
    if ($exists) {
        Add-EvidenceIndexCheck $Name "PASSED" "File exists: $resolvedPath"
    }
    else {
        Add-EvidenceIndexCheck $Name "FAILED" "File does not exist: $resolvedPath"
    }

    return [pscustomobject]@{
        Path = $resolvedPath
        Exists = $exists
    }
}

function Test-AllowedEvidenceValue {
    param(
        [string]$Name,
        [string]$Value,
        [string[]]$AllowedValues
    )

    if ($AllowedValues -contains $Value) {
        Add-EvidenceIndexCheck $Name "PASSED" "Value: $Value"
        return
    }

    Add-EvidenceIndexCheck $Name "FAILED" "Unexpected value '$Value'. Allowed values: $($AllowedValues -join ', ')."
}

function Test-DeclaredExistsFlag {
    param(
        [object]$Object,
        [string]$Name,
        [bool]$ActualExists
    )

    $declaredValue = Get-ObjectPropertyValue -Object $Object -FieldName "exists"
    if ($null -eq $declaredValue) {
        return
    }

    $declaredExists = [System.Convert]::ToBoolean($declaredValue)
    if ($declaredExists -eq $ActualExists) {
        Add-EvidenceIndexCheck "$Name.exists" "PASSED" "Declared exists flag matches local file state."
        return
    }

    Add-EvidenceIndexCheck "$Name.exists" "FAILED" "Declared exists=$declaredExists but local exists=$ActualExists."
}

function Assert-FallbackPackage {
    param(
        [object]$Package,
        [string]$IndexDirectory,
        [string]$Repository
    )

    $manifestPathValue = Assert-EvidenceIndexRequiredField -Object $Package -FieldName "manifestPath" -Context "fallbackPackages"
    $manifestFile = Assert-EvidenceFileExists -Name "Fallback manifest" -PathValue $manifestPathValue -IndexDirectory $IndexDirectory -Repository $Repository
    if (-not $manifestFile.Exists) {
        return
    }

    try {
        $manifest = Get-Content -LiteralPath $manifestFile.Path -Raw | ConvertFrom-Json
        Add-EvidenceIndexCheck "Fallback manifest JSON" "PASSED" "Parsed manifest: $($manifestFile.Path)"
    }
    catch {
        Add-EvidenceIndexCheck "Fallback manifest JSON" "FAILED" "Manifest cannot be parsed: $($manifestFile.Path). $(($_ | Out-String).Trim())"
        return
    }

    Assert-EvidenceIndexRequiredField -Object $manifest -FieldName "itemCode" -Context "fallbackManifest" | Out-Null
    $detailFile = Assert-EvidenceIndexRequiredField -Object $manifest -FieldName "evidenceDetailFile" -Context "fallbackManifest"
    if (-not [string]::IsNullOrWhiteSpace([string]$detailFile)) {
        $detailPath = [string]$detailFile
        if (-not [System.IO.Path]::IsPathRooted($detailPath)) {
            $detailPath = Join-Path (Split-Path -Path $manifestFile.Path -Parent) $detailPath
        }
        Assert-EvidenceFileExists -Name "Fallback Markdown evidence" -PathValue $detailPath -IndexDirectory $IndexDirectory -Repository $Repository | Out-Null
    }

    $uploadStatus = [string](Get-ObjectPropertyValue -Object $manifest -FieldName "uploadStatus")
    if ([string]::IsNullOrWhiteSpace($uploadStatus)) {
        $uploadStatus = [string](Get-ObjectPropertyValue -Object $Package -FieldName "uploadStatus")
    }
    if ([string]::IsNullOrWhiteSpace($uploadStatus)) {
        $uploadStatus = "PENDING"
    }
    $uploadStatus = $uploadStatus.ToUpperInvariant()

    Test-AllowedEvidenceValue "Fallback uploadStatus" $uploadStatus @("PENDING", "UPLOADED")
    if ($RequireUploadedFallback -and $uploadStatus -ne "UPLOADED") {
        Add-EvidenceIndexCheck "Fallback uploadStatus requirement" "FAILED" "RequireUploadedFallback was specified but uploadStatus is $uploadStatus."
    }
    elseif ($RequireUploadedFallback) {
        Add-EvidenceIndexCheck "Fallback uploadStatus requirement" "PASSED" "uploadStatus is UPLOADED."
    }

    if ($uploadStatus -eq "UPLOADED") {
        Assert-EvidenceIndexRequiredField -Object $manifest -FieldName "uploadedAt" -Context "fallbackManifest" | Out-Null
        Assert-EvidenceIndexRequiredField -Object $manifest -FieldName "uploadedEvidenceId" -Context "fallbackManifest" | Out-Null
        Assert-EvidenceIndexRequiredField -Object $manifest -FieldName "uploadedAttachmentId" -Context "fallbackManifest" | Out-Null
    }
}

$indexPath = Get-PreprodEvidenceIndexPath
$indexDirectory = Split-Path -Path $indexPath -Parent
$verificationReportJsonPath = Join-Path $indexDirectory "evidence-index.verify-report.json"
$verificationReportMarkdownPath = Join-Path $indexDirectory "evidence-index.verify-report.md"
$verificationStatus = "FAILED"
$verificationFailureReason = $null
[System.Console]::WriteLine("[evidence-index] Verifying evidence index: $indexPath")

try {
    try {
        $index = Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json
        Add-EvidenceIndexCheck "evidence-index.json" "PASSED" "JSON parsed successfully."
    }
    catch {
        Add-EvidenceIndexCheck "evidence-index.json" "FAILED" "JSON cannot be parsed. $(($_ | Out-String).Trim())"
        throw "Preproduction evidence index verification failed with $failureCount failed check(s)."
    }

    $repository = [string](Get-ObjectPropertyValue -Object $index -FieldName "repository")

    $schemaVersion = Assert-EvidenceIndexRequiredField -Object $index -FieldName "schemaVersion" -Context "index"
    if ($null -ne $schemaVersion) {
        if ([int]$schemaVersion -eq 1) {
            Add-EvidenceIndexCheck "schemaVersion" "PASSED" "Schema version 1 is supported."
        }
        else {
            Add-EvidenceIndexCheck "schemaVersion" "FAILED" "Unsupported schema version: $schemaVersion."
        }
    }

    $readinessRunId = Assert-EvidenceIndexRequiredField -Object $index -FieldName "ReadinessRunId" -Context "index"
    if ($null -ne $readinessRunId) {
        if ([long]$readinessRunId -gt 0) {
            Add-EvidenceIndexCheck "ReadinessRunId" "PASSED" "Readiness run id is positive."
        }
        else {
            Add-EvidenceIndexCheck "ReadinessRunId" "FAILED" "Readiness run id must be positive before production approval."
        }
    }

    $goNoGoVerdict = [string](Assert-EvidenceIndexRequiredField -Object $index -FieldName "goNoGoVerdict" -Context "index")
    if (-not [string]::IsNullOrWhiteSpace($goNoGoVerdict)) {
        $goNoGoVerdict = $goNoGoVerdict.ToUpperInvariant()
        Test-AllowedEvidenceValue "goNoGoVerdict" $goNoGoVerdict @("GO", "NO-GO")
        if ($goNoGoVerdict -eq "GO") {
            Add-EvidenceIndexCheck "Go / No-Go verdict" "PASSED" "Verdict is GO."
        }
        else {
            Add-EvidenceIndexCheck "Go / No-Go verdict" "FAILED" "Verdict is NO-GO."
        }
    }

    $summaryPath = Assert-EvidenceIndexRequiredField -Object $index -FieldName "summaryPath" -Context "index"
    $summaryFile = Assert-EvidenceFileExists -Name "Summary report" -PathValue $summaryPath -IndexDirectory $indexDirectory -Repository $repository
    if ($summaryFile.Exists) {
        $summary = Get-Content -LiteralPath $summaryFile.Path -Raw
        foreach ($sectionName in @("Failure triage index", "Go / No-Go", "Evidence index")) {
            if ($summary.Contains($sectionName)) {
                Add-EvidenceIndexCheck "Summary section $sectionName" "PASSED" "Summary contains required section."
            }
            else {
                Add-EvidenceIndexCheck "Summary section $sectionName" "FAILED" "Summary is missing required section."
            }
        }
    }

    $reports = @($index.reports | Where-Object { $null -ne $_ })
    if ($reports.Count -eq 0) {
        Add-EvidenceIndexCheck "reports" "FAILED" "No reports were indexed."
    }
    else {
        Add-EvidenceIndexCheck "reports" "PASSED" "Indexed report count: $($reports.Count)."
        foreach ($report in $reports) {
            $reportName = [string](Get-ObjectPropertyValue -Object $report -FieldName "name")
            if ([string]::IsNullOrWhiteSpace($reportName)) {
                $reportName = "Unnamed report"
            }
            Assert-EvidenceIndexRequiredField -Object $report -FieldName "type" -Context $reportName | Out-Null
            $reportPath = Assert-EvidenceIndexRequiredField -Object $report -FieldName "path" -Context $reportName
            $reportFile = Assert-EvidenceFileExists -Name "Report $reportName" -PathValue $reportPath -IndexDirectory $indexDirectory -Repository $repository
            Test-DeclaredExistsFlag -Object $report -Name "Report $reportName" -ActualExists $reportFile.Exists
        }
    }

    $stepResults = @($index.stepResults | Where-Object { $null -ne $_ })
    if ($stepResults.Count -eq 0) {
        Add-EvidenceIndexCheck "stepResults" "FAILED" "No step results were indexed."
    }
    else {
        Add-EvidenceIndexCheck "stepResults" "PASSED" "Indexed step count: $($stepResults.Count)."
        foreach ($step in $stepResults) {
            $stepName = [string](Get-ObjectPropertyValue -Object $step -FieldName "name")
            if ([string]::IsNullOrWhiteSpace($stepName)) {
                $stepName = "Unnamed step"
            }
            $stepStatus = [string](Assert-EvidenceIndexRequiredField -Object $step -FieldName "status" -Context $stepName)
            if (-not [string]::IsNullOrWhiteSpace($stepStatus)) {
                $stepStatus = $stepStatus.ToUpperInvariant()
                Test-AllowedEvidenceValue "Step $stepName status" $stepStatus @("PASSED", "FAILED", "SKIPPED")
                if ($stepStatus -eq "PASSED") {
                    Add-EvidenceIndexCheck "Step $stepName approval status" "PASSED" "Step passed."
                }
                else {
                    Add-EvidenceIndexCheck "Step $stepName approval status" "FAILED" "Step status is $stepStatus."
                }
            }
            $evidencePath = Assert-EvidenceIndexRequiredField -Object $step -FieldName "evidence" -Context $stepName
            Assert-EvidenceFileExists -Name "Step $stepName evidence" -PathValue $evidencePath -IndexDirectory $indexDirectory -Repository $repository | Out-Null
        }
    }

    $fallbackPackages = @($index.fallbackPackages | Where-Object { $null -ne $_ })
    if ($fallbackPackages.Count -eq 0) {
        Add-EvidenceIndexCheck "fallbackPackages" "PASSED" "No offline fallback packages were indexed."
    }
    else {
        Add-EvidenceIndexCheck "fallbackPackages" "PASSED" "Indexed fallback package count: $($fallbackPackages.Count)."
        foreach ($package in $fallbackPackages) {
            Assert-FallbackPackage -Package $package -IndexDirectory $indexDirectory -Repository $repository
        }
    }

    if ($failureCount -gt 0) {
        [System.Console]::WriteLine("[evidence-index] FAILED checks=$($checks.Count) failed=$failureCount")
        throw "Preproduction evidence index verification failed with $failureCount failed check(s)."
    }

    $verificationStatus = "PASSED"
    [System.Console]::WriteLine("[evidence-index] PASSED checks=$($checks.Count) failed=0")
}
catch {
    $verificationStatus = "FAILED"
    $verificationFailureReason = $_.Exception.Message
    throw
}
finally {
    Save-EvidenceIndexVerificationReport -ReportJsonPath $verificationReportJsonPath -ReportMarkdownPath $verificationReportMarkdownPath -ResolvedEvidenceIndexPath $indexPath -Status $verificationStatus -FailureReason $verificationFailureReason
}
