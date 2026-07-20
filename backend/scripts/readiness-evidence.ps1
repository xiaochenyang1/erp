# Windows PowerShell 5.1 does not auto-load System.Net.Http; PowerShell 7 does.
if (-not ("System.Net.Http.HttpClient" -as [type])) {
    try {
        Add-Type -AssemblyName System.Net.Http
    }
    catch {
        throw "Unable to load System.Net.Http for readiness attachment upload. Use PowerShell 7 (pwsh) or ensure System.Net.Http is available. $_"
    }
}

function ConvertTo-ReadinessField {
    param(
        [string]$Text,
        [int]$MaxLength
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }

    $normalized = $Text.Trim()
    if ($normalized.Length -le $MaxLength) {
        return $normalized
    }

    if ($MaxLength -le 20) {
        return $normalized.Substring(0, $MaxLength)
    }

    $suffix = "... truncated"
    return "$($normalized.Substring(0, $MaxLength - $suffix.Length))$suffix"
}

function Invoke-ReadinessJson {
    param(
        [string]$BaseUrl,
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers,
        [object]$Body
    )

    $invokeArgs = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $Headers
        UseBasicParsing = $true
        TimeoutSec = 30
    }

    if ($null -ne $Body) {
        $invokeArgs["Body"] = $Body | ConvertTo-Json -Depth 20
        $invokeArgs["ContentType"] = "application/json"
    }

    return Invoke-RestMethod @invokeArgs
}

function Get-ReadinessItemByCode {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [long]$ReadinessRunId,
        [string]$ItemCode
    )

    $runPath = "/api/system/readiness/runs/$ReadinessRunId"
    $detail = Invoke-ReadinessJson -BaseUrl $BaseUrl -Method "GET" -Path $runPath -Headers $Headers -Body $null
    return @($detail.data.items) | Where-Object { $_.itemCode -eq $ItemCode } | Select-Object -First 1
}

function New-ReadinessRun {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [string]$ReleaseCommit,
        [string]$ReleaseVersion,
        [string]$Environment,
        [string]$DatabaseInstance,
        [string]$RedisInstance,
        [string]$DockerProfile,
        [string]$Remark
    )

    if ($null -eq $Headers) {
        throw "Readiness run creation requires Authorization headers."
    }

    $runsPath = "/api/system/readiness/runs"
    $response = Invoke-ReadinessJson -BaseUrl $BaseUrl -Method "POST" -Path $runsPath -Headers $Headers -Body @{
        releaseCommit = $ReleaseCommit
        releaseVersion = ConvertTo-ReadinessField -Text $ReleaseVersion -MaxLength 128
        environment = $Environment
        databaseInstance = ConvertTo-ReadinessField -Text $DatabaseInstance -MaxLength 256
        redisInstance = ConvertTo-ReadinessField -Text $RedisInstance -MaxLength 256
        dockerProfile = ConvertTo-ReadinessField -Text $DockerProfile -MaxLength 128
        generateDefaultItems = $true
        recordPreflightEvidence = $true
        remark = ConvertTo-ReadinessField -Text $Remark -MaxLength 512
    }
    return $response.data
}

function New-ReadinessItem {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [long]$ReadinessRunId,
        [string]$ItemCode,
        [string]$ItemName,
        [string]$Category,
        [string]$Priority,
        [string]$ExpectedResult
    )

    $itemsPath = "/api/system/readiness/runs/$ReadinessRunId/items"
    $response = Invoke-ReadinessJson -BaseUrl $BaseUrl -Method "POST" -Path $itemsPath -Headers $Headers -Body @{
        itemCode = $ItemCode
        itemName = $ItemName
        category = $Category
        priority = $Priority
        expectedResult = ConvertTo-ReadinessField -Text $ExpectedResult -MaxLength 512
    }
    return $response.data
}

function New-ReadinessAttachment {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [long]$ReadinessRunId,
        [string]$BusinessNo,
        [string]$FileName,
        [string]$Content
    )

    if ($null -eq $Headers) {
        throw "Readiness attachment upload requires Authorization headers."
    }
    if ([string]::IsNullOrWhiteSpace($Content)) {
        throw "Readiness attachment upload requires non-empty Markdown content."
    }

    $safeFileName = if ([string]::IsNullOrWhiteSpace($FileName)) {
        "readiness-evidence-$ReadinessRunId.md"
    }
    else {
        [System.IO.Path]::GetFileName($FileName)
    }

    $client = [System.Net.Http.HttpClient]::new()
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    try {
        foreach ($headerName in $Headers.Keys) {
            $client.DefaultRequestHeaders.TryAddWithoutValidation([string]$headerName, [string]$Headers[$headerName]) | Out-Null
        }

        $multipart.Add([System.Net.Http.StringContent]::new("READINESS_RUN"), "businessType")
        $multipart.Add([System.Net.Http.StringContent]::new("$ReadinessRunId"), "businessId")
        if (-not [string]::IsNullOrWhiteSpace($BusinessNo)) {
            $multipart.Add([System.Net.Http.StringContent]::new($BusinessNo), "businessNo")
        }

        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Content)
        $fileContent = [System.Net.Http.ByteArrayContent]::new($bytes)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("text/markdown")
        $multipart.Add($fileContent, "file", $safeFileName)

        $uploadPath = "/api/system/attachments"
        $response = $client.PostAsync("$BaseUrl$uploadPath", $multipart).GetAwaiter().GetResult()
        $responseText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Readiness attachment upload failed with status $([int]$response.StatusCode): $responseText"
        }
        if ([string]::IsNullOrWhiteSpace($responseText)) {
            throw "Readiness attachment upload returned an empty response."
        }
        return ($responseText | ConvertFrom-Json).data
    }
    finally {
        $multipart.Dispose()
        $client.Dispose()
    }
}

function Register-ReadinessEvidence {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [long]$ReadinessRunId,
        [string]$ItemCode,
        [string]$ItemName,
        [string]$Category,
        [string]$Priority,
        [string]$ExpectedResult,
        [string]$Status,
        [string]$ActualResult,
        [string]$FailureReason,
        [string]$EvidenceSummary,
        [string]$EvidenceDetail,
        [string]$EvidenceRequestUri,
        [string]$BusinessType,
        [string]$BusinessNo
    )

    if ($ReadinessRunId -le 0) {
        return $null
    }
    if ($null -eq $Headers) {
        throw "Readiness evidence registration requires Authorization headers."
    }

    $normalizedItemCode = $ItemCode.ToUpperInvariant()
    $normalizedStatus = $Status.ToUpperInvariant()
    $item = Get-ReadinessItemByCode -BaseUrl $BaseUrl -Headers $Headers -ReadinessRunId $ReadinessRunId -ItemCode $normalizedItemCode
    if ($null -eq $item) {
        $item = New-ReadinessItem -BaseUrl $BaseUrl -Headers $Headers -ReadinessRunId $ReadinessRunId `
            -ItemCode $normalizedItemCode `
            -ItemName $ItemName `
            -Category $Category `
            -Priority $Priority `
            -ExpectedResult $ExpectedResult
    }

    $itemId = [long]$item.id
    $attachment = New-ReadinessAttachment `
        -BaseUrl $BaseUrl `
        -Headers $Headers `
        -ReadinessRunId $ReadinessRunId `
        -BusinessNo $BusinessNo `
        -FileName $EvidenceRequestUri `
        -Content $EvidenceDetail
    $attachmentId = [long]$attachment.id
    $evidenceType = "ATTACHMENT"

    $evidencePath = "/api/system/readiness/items/$itemId/evidence"
    $evidence = Invoke-ReadinessJson -BaseUrl $BaseUrl -Method "POST" -Path $evidencePath -Headers $Headers -Body @{
        evidenceType = $evidenceType
        requestMethod = "SCRIPT"
        requestUri = ConvertTo-ReadinessField -Text $EvidenceRequestUri -MaxLength 512
        httpStatus = 200
        businessType = $BusinessType
        businessNo = ConvertTo-ReadinessField -Text $BusinessNo -MaxLength 128
        summary = ConvertTo-ReadinessField -Text $EvidenceSummary -MaxLength 256
        detail = ConvertTo-ReadinessField -Text $EvidenceDetail -MaxLength 2048
        attachmentBusinessType = "SYSTEM_ATTACHMENT"
        attachmentBusinessId = $attachmentId
    }

    $resultPath = "/api/system/readiness/items/$itemId/result"
    $result = Invoke-ReadinessJson -BaseUrl $BaseUrl -Method "POST" -Path $resultPath -Headers $Headers -Body @{
        status = $normalizedStatus
        actualResult = ConvertTo-ReadinessField -Text $ActualResult -MaxLength 512
        failureReason = ConvertTo-ReadinessField -Text $FailureReason -MaxLength 512
    }

    return [pscustomobject]@{
        ItemId = $itemId
        EvidenceId = [long]$evidence.data.id
        AttachmentId = $attachmentId
        Status = $result.data.status
    }
}

function Save-ReadinessEvidenceFallbackPackage {
    param(
        [string]$BaseUrl,
        [long]$ReadinessRunId,
        [string]$ItemCode,
        [string]$ItemName,
        [string]$Category,
        [string]$Priority,
        [string]$ExpectedResult,
        [string]$Status,
        [string]$ActualResult,
        [string]$FailureReason,
        [string]$EvidenceSummary,
        [string]$EvidenceDetail,
        [string]$EvidenceRequestUri,
        [string]$BusinessType,
        [string]$BusinessNo,
        [string]$RegistrationFailure
    )

    $fallbackDirectory = Split-Path -Path $EvidenceRequestUri -Parent
    if ([string]::IsNullOrWhiteSpace($fallbackDirectory)) {
        $fallbackDirectory = (Get-Location).Path
    }
    if (-not (Test-Path -LiteralPath $fallbackDirectory)) {
        New-Item -ItemType Directory -Path $fallbackDirectory -Force | Out-Null
    }

    $normalizedItemCode = "READINESS_EVIDENCE"
    if (-not [string]::IsNullOrWhiteSpace($ItemCode)) {
        $normalizedItemCode = $ItemCode.ToUpperInvariant()
    }
    $safeItemCode = [regex]::Replace($normalizedItemCode, "[^A-Za-z0-9_.-]", "_")
    $manifestPath = Join-Path $fallbackDirectory "$safeItemCode-readiness-evidence-pending-upload.json"
    $detailPath = Join-Path $fallbackDirectory "$safeItemCode-readiness-evidence.md"

    $detail = $EvidenceDetail
    if ([string]::IsNullOrWhiteSpace($detail)) {
        $detail = "No readiness evidence detail was captured."
    }
    Set-Content -LiteralPath $detailPath -Value $detail -Encoding UTF8

    $manifest = [ordered]@{
        schemaVersion = 1
        generatedAt = Get-Date -Format "o"
        baseUrl = $BaseUrl
        readinessRunId = $ReadinessRunId
        itemCode = $normalizedItemCode
        itemName = $ItemName
        category = $Category
        priority = $Priority
        expectedResult = $ExpectedResult
        status = $Status
        actualResult = $ActualResult
        failureReason = $FailureReason
        evidenceSummary = $EvidenceSummary
        evidenceRequestUri = $EvidenceRequestUri
        businessType = $BusinessType
        businessNo = $BusinessNo
        registrationFailure = $RegistrationFailure
        evidenceDetailFile = (Split-Path -Path $detailPath -Leaf)
        intendedApis = [ordered]@{
            runDetail = "/api/system/readiness/runs/$ReadinessRunId"
            itemCreate = "/api/system/readiness/runs/$ReadinessRunId/items"
            attachmentUpload = "/api/system/attachments"
            evidenceCreate = "/api/system/readiness/items/<itemId>/evidence"
            resultUpdate = "/api/system/readiness/items/<itemId>/result"
        }
        replayHint = "After fixing readiness API connectivity or permissions, upload the Markdown file and register this manifest into the readiness run."
    }

    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

    return [pscustomobject]@{
        ManifestPath = $manifestPath
        DetailPath = $detailPath
    }
}

function Register-ReadinessEvidenceWithOfflineFallback {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [long]$ReadinessRunId,
        [string]$ItemCode,
        [string]$ItemName,
        [string]$Category,
        [string]$Priority,
        [string]$ExpectedResult,
        [string]$Status,
        [string]$ActualResult,
        [string]$FailureReason,
        [string]$EvidenceSummary,
        [string]$EvidenceDetail,
        [string]$EvidenceRequestUri,
        [string]$BusinessType,
        [string]$BusinessNo
    )

    try {
        return Register-ReadinessEvidence `
            -BaseUrl $BaseUrl `
            -Headers $Headers `
            -ReadinessRunId $ReadinessRunId `
            -ItemCode $ItemCode `
            -ItemName $ItemName `
            -Category $Category `
            -Priority $Priority `
            -ExpectedResult $ExpectedResult `
            -Status $Status `
            -ActualResult $ActualResult `
            -FailureReason $FailureReason `
            -EvidenceSummary $EvidenceSummary `
            -EvidenceDetail $EvidenceDetail `
            -EvidenceRequestUri $EvidenceRequestUri `
            -BusinessType $BusinessType `
            -BusinessNo $BusinessNo
    }
    catch {
        $failureText = ($_ | Out-String).Trim()
        $fallback = Save-ReadinessEvidenceFallbackPackage `
            -BaseUrl $BaseUrl `
            -ReadinessRunId $ReadinessRunId `
            -ItemCode $ItemCode `
            -ItemName $ItemName `
            -Category $Category `
            -Priority $Priority `
            -ExpectedResult $ExpectedResult `
            -Status $Status `
            -ActualResult $ActualResult `
            -FailureReason $FailureReason `
            -EvidenceSummary $EvidenceSummary `
            -EvidenceDetail $EvidenceDetail `
            -EvidenceRequestUri $EvidenceRequestUri `
            -BusinessType $BusinessType `
            -BusinessNo $BusinessNo `
            -RegistrationFailure $failureText

        throw "Readiness evidence registration failed. Offline fallback package written: $($fallback.ManifestPath); Markdown evidence: $($fallback.DetailPath). Original readiness evidence registration failure: $failureText"
    }
}
