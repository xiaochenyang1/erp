param(
    [string[]]$ManifestPath,
    [string]$ManifestDirectory,
    [string]$BaseUrl,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "readiness-evidence.ps1")

$replayHeadersByBaseUrl = @{}

function Get-ReadinessReplayManifestPaths {
    $paths = [System.Collections.Generic.List[string]]::new()

    foreach ($path in @($ManifestPath)) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            $paths.Add((Resolve-Path -LiteralPath $path).Path)
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($ManifestDirectory)) {
        if (-not (Test-Path -LiteralPath $ManifestDirectory -PathType Container)) {
            throw "ManifestDirectory does not exist: $ManifestDirectory"
        }
        $found = Get-ChildItem -Path (Join-Path $ManifestDirectory "*-readiness-evidence-pending-upload.json") -File
        foreach ($file in $found) {
            $paths.Add($file.FullName)
        }
    }

    $uniquePaths = @($paths | Sort-Object -Unique)
    if ($uniquePaths.Count -eq 0) {
        throw "Provide -ManifestPath or -ManifestDirectory containing *-readiness-evidence-pending-upload.json."
    }
    return $uniquePaths
}

function Get-ReadinessReplayHeaders {
    param([string]$EffectiveBaseUrl)

    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        return @{
            Authorization = "Bearer $AccessToken"
        }
    }

    if ($script:replayHeadersByBaseUrl.ContainsKey($EffectiveBaseUrl)) {
        return $script:replayHeadersByBaseUrl[$EffectiveBaseUrl]
    }

    if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw "Provide -AccessToken or both -Username and -Password for replay upload."
    }

    $loginBody = @{
        username = $Username
        password = $Password
    } | ConvertTo-Json

    [System.Console]::WriteLine("[readiness-replay] POST /api/auth/login")
    $loginResponse = Invoke-RestMethod -Method Post -Uri "$EffectiveBaseUrl/api/auth/login" `
        -Body $loginBody `
        -ContentType "application/json" `
        -TimeoutSec 30

    $token = $loginResponse.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login response did not contain data.accessToken."
    }

    $headers = @{
        Authorization = "Bearer $token"
    }
    $script:replayHeadersByBaseUrl[$EffectiveBaseUrl] = $headers
    return $headers
}

function Get-RequiredReplayField {
    param(
        [object]$Manifest,
        [string]$Name,
        [string]$ManifestPath
    )

    $value = $Manifest.$Name
    if ($null -eq $value -or [string]::IsNullOrWhiteSpace([string]$value)) {
        throw "Replay manifest $ManifestPath is missing required field: $Name"
    }
    return [string]$value
}

function Validate-ReadinessReplayManifest {
    param(
        [object]$Manifest,
        [string]$ManifestPath,
        [string]$BaseUrlOverride
    )

    $effectiveBaseUrl = $BaseUrlOverride
    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        $effectiveBaseUrl = $Manifest.baseUrl
    }
    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        throw "Replay manifest $ManifestPath does not contain baseUrl; provide -BaseUrl."
    }
    $effectiveBaseUrl = $effectiveBaseUrl.TrimEnd("/")

    [long](Get-RequiredReplayField $Manifest "readinessRunId" $ManifestPath) | Out-Null
    Get-RequiredReplayField $Manifest "itemCode" $ManifestPath | Out-Null
    Get-RequiredReplayField $Manifest "status" $ManifestPath | Out-Null
    $evidenceDetailFile = Get-RequiredReplayField $Manifest "evidenceDetailFile" $ManifestPath

    $manifestDirectory = Split-Path -Path $ManifestPath -Parent
    $detailPath = $evidenceDetailFile
    if (-not [System.IO.Path]::IsPathRooted($detailPath)) {
        $detailPath = Join-Path $manifestDirectory $evidenceDetailFile
    }
    if (-not (Test-Path -LiteralPath $detailPath -PathType Leaf)) {
        throw "Replay evidence Markdown file does not exist: $detailPath"
    }

    $evidenceDetail = Get-Content -LiteralPath $detailPath -Raw
    if ([string]::IsNullOrWhiteSpace($evidenceDetail)) {
        throw "Replay evidence Markdown file is empty: $detailPath"
    }

    return [pscustomobject]@{
        BaseUrl = $effectiveBaseUrl
        DetailPath = $detailPath
        EvidenceDetail = $evidenceDetail
    }
}

function Set-ReplayUploadStatus {
    param(
        [object]$Manifest,
        [string]$ManifestPath,
        [object]$Registration,
        [string]$EffectiveBaseUrl
    )

    $Manifest | Add-Member -NotePropertyName uploadStatus -NotePropertyValue "UPLOADED" -Force
    $Manifest | Add-Member -NotePropertyName uploadedAt -NotePropertyValue (Get-Date -Format "o") -Force
    $Manifest | Add-Member -NotePropertyName replayBaseUrl -NotePropertyValue $EffectiveBaseUrl -Force
    $Manifest | Add-Member -NotePropertyName uploadedItemId -NotePropertyValue $Registration.ItemId -Force
    $Manifest | Add-Member -NotePropertyName uploadedEvidenceId -NotePropertyValue $Registration.EvidenceId -Force
    $Manifest | Add-Member -NotePropertyName uploadedAttachmentId -NotePropertyValue $Registration.AttachmentId -Force
    $Manifest | Add-Member -NotePropertyName uploadedReadinessStatus -NotePropertyValue $Registration.Status -Force

    $Manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
}

$processed = 0
foreach ($manifestPath in Get-ReadinessReplayManifestPaths) {
    [System.Console]::WriteLine("[readiness-replay] Readiness evidence replay: $manifestPath")
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $validation = Validate-ReadinessReplayManifest -Manifest $manifest -ManifestPath $manifestPath -BaseUrlOverride $BaseUrl

    if ($ValidateOnly) {
        [System.Console]::WriteLine("[readiness-replay] VALIDATED $manifestPath -> $($validation.DetailPath)")
        $processed++
        continue
    }

    $headers = Get-ReadinessReplayHeaders -EffectiveBaseUrl $validation.BaseUrl
    $registration = Register-ReadinessEvidence `
        -BaseUrl $validation.BaseUrl `
        -Headers $headers `
        -ReadinessRunId ([long]$manifest.readinessRunId) `
        -ItemCode $manifest.itemCode `
        -ItemName $manifest.itemName `
        -Category $manifest.category `
        -Priority $manifest.priority `
        -ExpectedResult $manifest.expectedResult `
        -Status $manifest.status `
        -ActualResult $manifest.actualResult `
        -FailureReason $manifest.failureReason `
        -EvidenceSummary $manifest.evidenceSummary `
        -EvidenceDetail $validation.EvidenceDetail `
        -EvidenceRequestUri $manifest.evidenceRequestUri `
        -BusinessType $manifest.businessType `
        -BusinessNo $manifest.businessNo

    Set-ReplayUploadStatus -Manifest $manifest -ManifestPath $manifestPath -Registration $registration -EffectiveBaseUrl $validation.BaseUrl
    [System.Console]::WriteLine("[readiness-replay] UPLOADED $manifestPath evidenceId=$($registration.EvidenceId) attachmentId=$($registration.AttachmentId)")
    $processed++
}

[System.Console]::WriteLine("[readiness-replay] Processed manifest count: $processed")
