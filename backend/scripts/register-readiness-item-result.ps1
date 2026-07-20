param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long]$ReadinessRunId,
    [string]$ItemCode,
    [string]$Status,
    [string]$EvidenceSummary,
    [string]$EvidenceRequestUri,
    [string]$EvidenceDetailPath,
    [string]$EvidenceDetail,
    [string]$ActualResult,
    [string]$FailureReason,
    [string]$BusinessType,
    [string]$BusinessNo,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "readiness-evidence.ps1")

$supportedItemCodes = @(
    "FINANCE_LEDGER",
    "PERIOD_LOCK",
    "INVENTORY_FINANCE_RECONCILIATION",
    "INITIAL_IMPORT",
    "BACKUP_ROLLBACK"
)
$allowedStatuses = @("PASSED", "FAILED", "BLOCKED", "SKIPPED")

function Write-Step {
    param([string]$Message)

    [System.Console]::WriteLine("[readiness-item] $Message")
}

function Get-RegistrationHeaders {
    param([string]$EffectiveBaseUrl)

    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        return @{
            Authorization = "Bearer $AccessToken"
        }
    }

    if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw "Provide -AccessToken or both -Username and -Password."
    }

    Write-Step "POST /api/auth/login"
    $loginResponse = Invoke-RestMethod -Method Post -Uri "$EffectiveBaseUrl/api/auth/login" `
        -Body (@{
            username = $Username
            password = $Password
        } | ConvertTo-Json) `
        -ContentType "application/json" `
        -TimeoutSec 30

    $token = $loginResponse.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login response did not contain data.accessToken."
    }

    return @{
        Authorization = "Bearer $token"
    }
}

function Get-EvidenceDetailText {
    if (-not [string]::IsNullOrWhiteSpace($EvidenceDetailPath)) {
        $resolvedPath = (Resolve-Path -LiteralPath $EvidenceDetailPath).Path
        $content = Get-Content -LiteralPath $resolvedPath -Raw
        if ([string]::IsNullOrWhiteSpace($content)) {
            throw "EvidenceDetailPath points to an empty file: $resolvedPath"
        }
        return [pscustomobject]@{
            Content = $content
            RequestUri = $resolvedPath
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($EvidenceDetail)) {
        return [pscustomobject]@{
            Content = $EvidenceDetail
            RequestUri = $null
        }
    }

    return [pscustomobject]@{
        Content = $EvidenceSummary
        RequestUri = $null
    }
}

function Resolve-ActualResult {
    param(
        [string]$NormalizedItemCode,
        [string]$NormalizedStatus,
        [string]$ResolvedEvidenceRequestUri
    )

    if (-not [string]::IsNullOrWhiteSpace($ActualResult)) {
        return $ActualResult
    }

    $source = if ([string]::IsNullOrWhiteSpace($ResolvedEvidenceRequestUri)) {
        "inline evidence"
    }
    else {
        $ResolvedEvidenceRequestUri
    }
    return "$NormalizedItemCode recorded as $NormalizedStatus; evidence: $source"
}

function Resolve-FailureReason {
    param([string]$NormalizedStatus)

    if (-not [string]::IsNullOrWhiteSpace($FailureReason)) {
        return $FailureReason
    }
    if ($NormalizedStatus -eq "PASSED") {
        return $null
    }
    return "See readiness evidence detail for $ItemCode."
}

$effectiveBaseUrl = $BaseUrl.TrimEnd("/")
if ($ReadinessRunId -le 0) {
    throw "ReadinessRunId must be a positive number."
}
if ([string]::IsNullOrWhiteSpace($ItemCode)) {
    throw "ItemCode is required."
}
if ([string]::IsNullOrWhiteSpace($Status)) {
    throw "Status is required."
}
if ([string]::IsNullOrWhiteSpace($EvidenceSummary)) {
    throw "EvidenceSummary is required."
}

$normalizedItemCode = $ItemCode.Trim().ToUpperInvariant()
if (-not $supportedItemCodes.Contains($normalizedItemCode)) {
    throw "Unsupported ItemCode '$ItemCode'. Supported values: $($supportedItemCodes -join ', ')."
}

$normalizedStatus = $Status.Trim().ToUpperInvariant()
if (-not $allowedStatuses.Contains($normalizedStatus)) {
    throw "Unsupported Status '$Status'. Supported values: $($allowedStatuses -join ', ')."
}

$detail = Get-EvidenceDetailText
$resolvedEvidenceRequestUri = $EvidenceRequestUri
if ([string]::IsNullOrWhiteSpace($resolvedEvidenceRequestUri)) {
    $resolvedEvidenceRequestUri = $detail.RequestUri
}
if ([string]::IsNullOrWhiteSpace($resolvedEvidenceRequestUri)) {
    $resolvedEvidenceRequestUri = "scripts/register-readiness-item-result.ps1::$normalizedItemCode"
}

$resolvedBusinessType = if ([string]::IsNullOrWhiteSpace($BusinessType)) {
    $normalizedItemCode
}
else {
    $BusinessType.Trim().ToUpperInvariant()
}
$resolvedBusinessNo = if ([string]::IsNullOrWhiteSpace($BusinessNo)) {
    "READINESS-$ReadinessRunId-$normalizedItemCode"
}
else {
    $BusinessNo.Trim()
}

Write-Step "Readiness item evidence registration"
$headers = Get-RegistrationHeaders -EffectiveBaseUrl $effectiveBaseUrl
$item = Get-ReadinessItemByCode `
    -BaseUrl $effectiveBaseUrl `
    -Headers $headers `
    -ReadinessRunId $ReadinessRunId `
    -ItemCode $normalizedItemCode

if ($null -eq $item) {
    throw "Readiness item '$normalizedItemCode' does not exist in run $ReadinessRunId. Create or inspect the run before backfilling evidence."
}

$registration = Register-ReadinessEvidenceWithOfflineFallback `
    -BaseUrl $effectiveBaseUrl `
    -Headers $headers `
    -ReadinessRunId $ReadinessRunId `
    -ItemCode $normalizedItemCode `
    -ItemName ([string]$item.itemName) `
    -Category ([string]$item.category) `
    -Priority ([string]$item.priority) `
    -ExpectedResult ([string]$item.expectedResult) `
    -Status $normalizedStatus `
    -ActualResult (Resolve-ActualResult -NormalizedItemCode $normalizedItemCode -NormalizedStatus $normalizedStatus -ResolvedEvidenceRequestUri $resolvedEvidenceRequestUri) `
    -FailureReason (Resolve-FailureReason -NormalizedStatus $normalizedStatus) `
    -EvidenceSummary $EvidenceSummary `
    -EvidenceDetail $detail.Content `
    -EvidenceRequestUri $resolvedEvidenceRequestUri `
    -BusinessType $resolvedBusinessType `
    -BusinessNo $resolvedBusinessNo

[pscustomobject]@{
    readinessRunId = $ReadinessRunId
    itemCode = $normalizedItemCode
    readinessItemId = $registration.ItemId
    readinessEvidenceId = $registration.EvidenceId
    readinessAttachmentId = $registration.AttachmentId
    readinessStatus = $registration.Status
    evidenceRequestUri = $resolvedEvidenceRequestUri
} | ConvertTo-Json -Depth 10
