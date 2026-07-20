param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$OutputPath,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [long]$ReadinessRunId,
    [switch]$AllowFailures
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "readiness-evidence.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not $OutputPath) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputPath = Join-Path $RepoRoot "target\business-smoke-$timestamp.md"
}

$BaseUrl = $BaseUrl.TrimEnd("/")
$sections = [System.Collections.Generic.List[string]]::new()
$results = [System.Collections.Generic.List[object]]::new()
$failure = $null
$readinessRegistrationFailure = $null

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

function Get-BodyPreview {
    param(
        [string]$Body,
        [int]$MaxLength = 2000
    )

    if ([string]::IsNullOrWhiteSpace($Body)) {
        return ""
    }

    $normalized = $Body.Trim()
    if ($normalized.Length -le $MaxLength) {
        return $normalized
    }

    return "$($normalized.Substring(0, $MaxLength))`n... truncated ..."
}

function Get-ResponseContent {
    param([object]$Response)

    if (-not $Response) {
        return ""
    }

    try {
        $stream = $Response.GetResponseStream()
        if (-not $stream) {
            return ""
        }
        $reader = [System.IO.StreamReader]::new($stream)
        return $reader.ReadToEnd()
    }
    catch {
        return ""
    }
}

function Invoke-SmokeRequest {
    param(
        [string]$Name,
        [string]$Path,
        [hashtable]$Headers
    )

    $url = "$BaseUrl$Path"
    [System.Console]::WriteLine("[business-smoke] GET $Path")

    $statusCode = $null
    $statusText = ""
    $bodyPreview = ""
    $success = $false
    $errorText = ""

    try {
        $response = Invoke-WebRequest -Method Get -Uri $url -Headers $Headers -UseBasicParsing -TimeoutSec 20
        $statusCode = [int]$response.StatusCode
        $statusText = $response.StatusDescription
        $bodyPreview = Get-BodyPreview $response.Content
        $success = ($statusCode -ge 200 -and $statusCode -lt 300)
    }
    catch {
        $errorText = ($_ | Out-String).Trim()
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            $statusText = $_.Exception.Response.StatusDescription
            $bodyPreview = Get-BodyPreview (Get-ResponseContent $_.Exception.Response)
        }
    }

    $result = [pscustomobject]@{
        Name = $Name
        Method = "GET"
        Path = $Path
        Status = $statusCode
        Success = $success
    }
    $results.Add($result)

    $statusDisplay = "n/a"
    if ($null -ne $statusCode) {
        $statusDisplay = "$statusCode $statusText".Trim()
    }

    $failureBlock = ""
    if (-not [string]::IsNullOrWhiteSpace($errorText)) {
        $failureBlock = @"

Failure:

````
$errorText
````
"@
    }

    Add-Section $Name @"
Request: GET $url

Status: $statusDisplay

Success: $success

Body preview:

````
$bodyPreview
````
$failureBlock
"@
}

function Add-SummarySection {
    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("| Endpoint | Method | Path | Status | Result |")
    $rows.Add("|---|---|---|---|---|")
    foreach ($result in $results) {
        $status = "n/a"
        if ($null -ne $result.Status) {
            $status = "$($result.Status)"
        }
        $verdict = "FAIL"
        if ($result.Success) {
            $verdict = "PASS"
        }
        $rows.Add("| $($result.Name) | $($result.Method) | `$($result.Path)` | $status | $verdict |")
    }

    Add-Section "Summary" ($rows -join [Environment]::NewLine)
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

    [System.Console]::WriteLine("[business-smoke] POST /api/auth/login")
    $loginResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
        -Body $loginBody `
        -ContentType "application/json" `
        -TimeoutSec 20

    $token = $loginResponse.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login response did not contain data.accessToken."
    }

    Add-Section "Authentication" "POST $BaseUrl/api/auth/login returned a bearer token for user `$Username`. Token value is intentionally not recorded."
    return $token
}

$endpoints = @(
    @{ Name = "System profile"; Path = "/api/system/profile" },
    @{ Name = "Business health summary"; Path = "/api/system/observability/business-health" },
    @{ Name = "Products"; Path = "/api/masterdata/products" },
    @{ Name = "Customers"; Path = "/api/masterdata/customers" },
    @{ Name = "Suppliers"; Path = "/api/masterdata/suppliers" },
    @{ Name = "Warehouses"; Path = "/api/masterdata/warehouses" },
    @{ Name = "Purchase orders"; Path = "/api/purchase/orders" },
    @{ Name = "Purchase receipts"; Path = "/api/purchase/receipts" },
    @{ Name = "Sales orders"; Path = "/api/sales/orders" },
    @{ Name = "Sales deliveries"; Path = "/api/sales/deliveries" },
    @{ Name = "Inventory balances"; Path = "/api/inventory/balances" },
    @{ Name = "Inventory transactions"; Path = "/api/inventory/transactions" },
    @{ Name = "Finance receivables"; Path = "/api/finance/receivables" },
    @{ Name = "Finance payables"; Path = "/api/finance/payables" },
    @{ Name = "Finance vouchers"; Path = "/api/finance/vouchers" },
    @{ Name = "Finance periods"; Path = "/api/finance/periods" },
    @{ Name = "Production BOMs"; Path = "/api/production/boms" },
    @{ Name = "Production orders"; Path = "/api/production/orders" },
    @{ Name = "Workflow tasks"; Path = "/api/workflow/tasks" },
    @{ Name = "Import jobs"; Path = "/api/import/jobs" },
    @{ Name = "Purchase order report"; Path = "/api/reports/purchase-orders" },
    @{ Name = "Sales order report"; Path = "/api/reports/sales-orders" }
)

Push-Location $RepoRoot
try {
    $sections.Add("# Business smoke evidence")
    $sections.Add("")
    $sections.Add("- Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")")
    $sections.Add("- Repository: $RepoRoot")
    $sections.Add("- Base URL: $BaseUrl")
    $sections.Add("- Mode: read-only API smoke")

    $token = Get-AccessToken
    $headers = @{
        Authorization = "Bearer $token"
    }

    foreach ($endpoint in $endpoints) {
        Invoke-SmokeRequest -Name $endpoint.Name -Path $endpoint.Path -Headers $headers
    }

    Add-SummarySection

    $failed = @($results | Where-Object { -not $_.Success })
    if ($failed.Count -gt 0) {
        $failedPaths = ($failed | ForEach-Object { $_.Path }) -join ", "
        throw "Business smoke failed for $($failed.Count) endpoint(s): $failedPaths"
    }
}
catch {
    $failure = $_
    Add-Section "Failure" ($failure | Out-String)
}
finally {
    if ($results.Count -gt 0 -and -not (($sections -join [Environment]::NewLine).Contains("## Summary"))) {
        Add-SummarySection
    }

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

            $failed = @($results | Where-Object { -not $_.Success })
            $status = "PASSED"
            $actualResult = "业务只读冒烟通过；证据文件：$OutputPath"
            $failureReason = $null
            if ($failure -or $failed.Count -gt 0) {
                $status = "FAILED"
                $actualResult = "业务只读冒烟失败；失败接口数：$($failed.Count)；证据文件：$OutputPath"
                $failureReason = (($failure | Out-String).Trim())
            }

            $registration = Register-ReadinessEvidenceWithOfflineFallback `
                -BaseUrl $BaseUrl `
                -Headers $headers `
                -ReadinessRunId $ReadinessRunId `
                -ItemCode "BUSINESS_SMOKE" `
                -ItemName "业务只读冒烟" `
                -Category "BUSINESS" `
                -Priority "P1" `
                -ExpectedResult "核心模块只读查询入口全部返回成功" `
                -Status $status `
                -ActualResult $actualResult `
                -FailureReason $failureReason `
                -EvidenceSummary "业务只读冒烟脚本验收：$status" `
                -EvidenceDetail $evidence `
                -EvidenceRequestUri $OutputPath `
                -BusinessType "BUSINESS_SMOKE" `
                -BusinessNo "BUSINESS-SMOKE"

            Add-Section "Readiness evidence registration" @"
Readiness run ID: $ReadinessRunId
Readiness item ID: $($registration.ItemId)
Readiness evidence ID: $($registration.EvidenceId)
Readiness attachment ID: $($registration.AttachmentId)
Readiness item status: $($registration.Status)
"@
        }
        catch {
            $readinessRegistrationFailure = $_
            Add-Section "Readiness evidence registration failure" ($readinessRegistrationFailure | Out-String)
        }
    }

    $evidence = $sections -join [Environment]::NewLine
    Set-Content -LiteralPath $OutputPath -Value $evidence -Encoding UTF8
    [System.Console]::WriteLine("Business smoke evidence written to $OutputPath")
    Pop-Location
}

if ($failure -and -not $AllowFailures) {
    throw $failure
}

if ($failure -and $AllowFailures) {
    [System.Console]::WriteLine("Business smoke completed with failures because -AllowFailures was specified.")
}

if ($readinessRegistrationFailure) {
    throw $readinessRegistrationFailure
}
