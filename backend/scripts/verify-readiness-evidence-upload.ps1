param(
    [string[]]$ManifestPath,
    [string]$ManifestDirectory,
    [string]$BaseUrl,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [long]$ReadinessRunId
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "readiness-evidence.ps1")

$uploadHeadersByBaseUrl = @{}
$runDetailByKey = @{}
$uploadChecks = [System.Collections.Generic.List[object]]::new()
$failureCount = 0

function Add-ReadinessUploadCheck {
    param(
        [string]$Name,
        [ValidateSet("PASSED", "FAILED")]
        [string]$Status,
        [string]$Detail
    )

    $script:uploadChecks.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
    [System.Console]::WriteLine("[readiness-upload-verify] $Status $Name - $Detail")
    if ($Status -eq "FAILED") {
        $script:failureCount++
    }
}

function Get-ReadinessUploadManifestPaths {
    $paths = [System.Collections.Generic.List[string]]::new()

    foreach ($path in @($ManifestPath)) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
                throw "ManifestPath does not exist: $path"
            }
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

function Get-ReadinessUploadHeaders {
    param([string]$EffectiveBaseUrl)

    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        return @{
            Authorization = "Bearer $AccessToken"
        }
    }

    if ($script:uploadHeadersByBaseUrl.ContainsKey($EffectiveBaseUrl)) {
        return $script:uploadHeadersByBaseUrl[$EffectiveBaseUrl]
    }

    if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw "Provide -AccessToken or both -Username and -Password for readiness upload verification."
    }

    $loginBody = @{
        username = $Username
        password = $Password
    } | ConvertTo-Json

    [System.Console]::WriteLine("[readiness-upload-verify] POST /api/auth/login")
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
    $script:uploadHeadersByBaseUrl[$EffectiveBaseUrl] = $headers
    return $headers
}

function Get-RequiredUploadField {
    param(
        [object]$Manifest,
        [string]$Name,
        [string]$ManifestPath
    )

    $value = $Manifest.$Name
    if ($null -eq $value -or [string]::IsNullOrWhiteSpace([string]$value)) {
        Add-ReadinessUploadCheck "$ManifestPath::$Name" "FAILED" "Required manifest field is missing."
        return $null
    }
    Add-ReadinessUploadCheck "$ManifestPath::$Name" "PASSED" "Value: $value"
    return $value
}

function Get-EffectiveUploadBaseUrl {
    param(
        [object]$Manifest,
        [string]$ManifestPath
    )

    $effectiveBaseUrl = $BaseUrl
    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        $effectiveBaseUrl = [string]$Manifest.replayBaseUrl
    }
    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        $effectiveBaseUrl = [string]$Manifest.baseUrl
    }
    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        Add-ReadinessUploadCheck "$ManifestPath::baseUrl" "FAILED" "Manifest does not contain baseUrl or replayBaseUrl; provide -BaseUrl."
        return $null
    }
    return $effectiveBaseUrl.TrimEnd("/")
}

function Assert-UploadedReadinessManifest {
    param([string]$Path)

    [System.Console]::WriteLine("[readiness-upload-verify] Readiness evidence upload verification: $Path")
    try {
        $manifest = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        Add-ReadinessUploadCheck "$Path::json" "PASSED" "Manifest JSON parsed."
    }
    catch {
        Add-ReadinessUploadCheck "$Path::json" "FAILED" "Manifest cannot be parsed. $(($_ | Out-String).Trim())"
        return $null
    }

    $effectiveBaseUrl = Get-EffectiveUploadBaseUrl -Manifest $manifest -ManifestPath $Path
    $manifestRunId = Get-RequiredUploadField -Manifest $manifest -Name "readinessRunId" -ManifestPath $Path
    $itemCode = Get-RequiredUploadField -Manifest $manifest -Name "itemCode" -ManifestPath $Path
    $uploadStatus = Get-RequiredUploadField -Manifest $manifest -Name "uploadStatus" -ManifestPath $Path
    $uploadedItemId = Get-RequiredUploadField -Manifest $manifest -Name "uploadedItemId" -ManifestPath $Path
    $uploadedEvidenceId = Get-RequiredUploadField -Manifest $manifest -Name "uploadedEvidenceId" -ManifestPath $Path
    $uploadedAttachmentId = Get-RequiredUploadField -Manifest $manifest -Name "uploadedAttachmentId" -ManifestPath $Path
    $uploadedReadinessStatus = Get-RequiredUploadField -Manifest $manifest -Name "uploadedReadinessStatus" -ManifestPath $Path

    if ([string]::IsNullOrWhiteSpace($uploadStatus) -or $uploadStatus.ToUpperInvariant() -ne "UPLOADED") {
        Add-ReadinessUploadCheck "$Path::uploadStatus" "FAILED" "Expected uploadStatus=UPLOADED before system verification."
        return $null
    }
    Add-ReadinessUploadCheck "$Path::uploadStatus" "PASSED" "Manifest uploadStatus is UPLOADED."

    if ($ReadinessRunId -gt 0 -and [long]$manifestRunId -ne $ReadinessRunId) {
        Add-ReadinessUploadCheck "$Path::ReadinessRunId" "FAILED" "Manifest readinessRunId $manifestRunId does not match requested ReadinessRunId $ReadinessRunId."
        return $null
    }

    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        return $null
    }

    return [pscustomobject]@{
        Path = $Path
        Manifest = $manifest
        BaseUrl = $effectiveBaseUrl
        ReadinessRunId = [long]$manifestRunId
        ItemCode = ([string]$itemCode).ToUpperInvariant()
        UploadedItemId = [long]$uploadedItemId
        UploadedEvidenceId = [long]$uploadedEvidenceId
        UploadedAttachmentId = [long]$uploadedAttachmentId
        UploadedReadinessStatus = ([string]$uploadedReadinessStatus).ToUpperInvariant()
    }
}

function Get-ReadinessRunDetail {
    param(
        [string]$EffectiveBaseUrl,
        [long]$RunId
    )

    $key = "$EffectiveBaseUrl|$RunId"
    if ($script:runDetailByKey.ContainsKey($key)) {
        return $script:runDetailByKey[$key]
    }

    $headers = Get-ReadinessUploadHeaders -EffectiveBaseUrl $EffectiveBaseUrl
    $detail = Invoke-ReadinessJson -BaseUrl $EffectiveBaseUrl -Method "GET" -Path "/api/system/readiness/runs/$RunId" -Headers $headers -Body $null
    $script:runDetailByKey[$key] = $detail.data
    Add-ReadinessUploadCheck "GET /api/system/readiness/runs/$RunId" "PASSED" "Readiness run detail loaded from $EffectiveBaseUrl."
    return $detail.data
}

function Find-ReadinessItem {
    param(
        [object]$RunDetail,
        [long]$UploadedItemId,
        [string]$ItemCode,
        [string]$ManifestPath
    )

    $item = @($RunDetail.items) | Where-Object { [long]$_.id -eq $UploadedItemId } | Select-Object -First 1
    if ($null -eq $item) {
        Add-ReadinessUploadCheck "$ManifestPath::uploadedItemId" "FAILED" "Readiness item id $UploadedItemId was not found in system run."
        return $null
    }
    Add-ReadinessUploadCheck "$ManifestPath::uploadedItemId" "PASSED" "Readiness item id $UploadedItemId exists."

    if ([string]$item.itemCode -eq $ItemCode) {
        Add-ReadinessUploadCheck "$ManifestPath::itemCode" "PASSED" "System itemCode matches $ItemCode."
    }
    else {
        Add-ReadinessUploadCheck "$ManifestPath::itemCode" "FAILED" "System itemCode is $($item.itemCode), expected $ItemCode."
    }

    return $item
}

function Find-ReadinessEvidence {
    param(
        [object]$Item,
        [long]$UploadedEvidenceId,
        [string]$ManifestPath
    )

    $evidence = @($Item.evidence) | Where-Object { [long]$_.id -eq $UploadedEvidenceId } | Select-Object -First 1
    if ($null -eq $evidence) {
        Add-ReadinessUploadCheck "$ManifestPath::uploadedEvidenceId" "FAILED" "Readiness evidence id $UploadedEvidenceId was not found under item $($Item.id)."
        return $null
    }

    Add-ReadinessUploadCheck "$ManifestPath::uploadedEvidenceId" "PASSED" "Readiness evidence id $UploadedEvidenceId exists."
    return $evidence
}

function Assert-ReadinessEvidenceMatchesManifest {
    param([object]$Upload)

    $detail = Get-ReadinessRunDetail -EffectiveBaseUrl $Upload.BaseUrl -RunId $Upload.ReadinessRunId
    if ($null -eq $detail -or $null -eq $detail.run) {
        Add-ReadinessUploadCheck "$($Upload.Path)::run" "FAILED" "Readiness run detail response did not contain data.run."
        return
    }

    if ([long]$detail.run.id -eq $Upload.ReadinessRunId) {
        Add-ReadinessUploadCheck "$($Upload.Path)::runId" "PASSED" "System run id matches $($Upload.ReadinessRunId)."
    }
    else {
        Add-ReadinessUploadCheck "$($Upload.Path)::runId" "FAILED" "System run id is $($detail.run.id), expected $($Upload.ReadinessRunId)."
    }

    $item = Find-ReadinessItem -RunDetail $detail -UploadedItemId $Upload.UploadedItemId -ItemCode $Upload.ItemCode -ManifestPath $Upload.Path
    if ($null -eq $item) {
        return
    }

    if ([string]$item.status -eq $Upload.UploadedReadinessStatus) {
        Add-ReadinessUploadCheck "$($Upload.Path)::uploadedReadinessStatus" "PASSED" "System item status matches $($Upload.UploadedReadinessStatus)."
    }
    else {
        Add-ReadinessUploadCheck "$($Upload.Path)::uploadedReadinessStatus" "FAILED" "System item status is $($item.status), expected $($Upload.UploadedReadinessStatus)."
    }

    $evidence = Find-ReadinessEvidence -Item $item -UploadedEvidenceId $Upload.UploadedEvidenceId -ManifestPath $Upload.Path
    if ($null -eq $evidence) {
        return
    }

    if ([long]$evidence.attachmentBusinessId -eq $Upload.UploadedAttachmentId) {
        Add-ReadinessUploadCheck "$($Upload.Path)::uploadedAttachmentId" "PASSED" "System evidence attachmentBusinessId matches $($Upload.UploadedAttachmentId)."
    }
    else {
        Add-ReadinessUploadCheck "$($Upload.Path)::uploadedAttachmentId" "FAILED" "System evidence attachmentBusinessId is $($evidence.attachmentBusinessId), expected $($Upload.UploadedAttachmentId)."
    }

    if ([string]$evidence.attachmentBusinessType -eq "SYSTEM_ATTACHMENT") {
        Add-ReadinessUploadCheck "$($Upload.Path)::attachmentBusinessType" "PASSED" "System evidence attachmentBusinessType is SYSTEM_ATTACHMENT."
    }
    else {
        Add-ReadinessUploadCheck "$($Upload.Path)::attachmentBusinessType" "FAILED" "System evidence attachmentBusinessType is $($evidence.attachmentBusinessType), expected SYSTEM_ATTACHMENT."
    }

    $manifest = $Upload.Manifest
    if (-not [string]::IsNullOrWhiteSpace([string]$manifest.evidenceRequestUri)) {
        if ([string]$evidence.requestUri -eq [string]$manifest.evidenceRequestUri) {
            Add-ReadinessUploadCheck "$($Upload.Path)::evidenceRequestUri" "PASSED" "System evidence requestUri matches manifest."
        }
        else {
            Add-ReadinessUploadCheck "$($Upload.Path)::evidenceRequestUri" "FAILED" "System evidence requestUri is $($evidence.requestUri), expected $($manifest.evidenceRequestUri)."
        }
    }

    if (-not [string]::IsNullOrWhiteSpace([string]$manifest.businessType)) {
        if ([string]$evidence.businessType -eq [string]$manifest.businessType) {
            Add-ReadinessUploadCheck "$($Upload.Path)::businessType" "PASSED" "System evidence businessType matches manifest."
        }
        else {
            Add-ReadinessUploadCheck "$($Upload.Path)::businessType" "FAILED" "System evidence businessType is $($evidence.businessType), expected $($manifest.businessType)."
        }
    }
}

$uploads = [System.Collections.Generic.List[object]]::new()
foreach ($path in Get-ReadinessUploadManifestPaths) {
    $upload = Assert-UploadedReadinessManifest -Path $path
    if ($null -ne $upload) {
        $uploads.Add($upload)
    }
}

if ($uploads.Count -eq 0) {
    Add-ReadinessUploadCheck "uploaded manifests" "FAILED" "No uploaded manifest is ready for system verification."
}

foreach ($upload in $uploads) {
    Assert-ReadinessEvidenceMatchesManifest -Upload $upload
}

if ($failureCount -gt 0) {
    [System.Console]::WriteLine("[readiness-upload-verify] FAILED checks=$($uploadChecks.Count) failed=$failureCount")
    throw "Readiness evidence upload verification failed with $failureCount failed check(s)."
}

[System.Console]::WriteLine("[readiness-upload-verify] PASSED checks=$($uploadChecks.Count) failed=0")
