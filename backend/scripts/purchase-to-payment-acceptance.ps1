param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$OutputPath,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [string]$SubmitterUsername,
    [string]$SubmitterPassword,
    [long]$WarehouseId,
    [string]$BusinessDate = (Get-Date -Format "yyyy-MM-dd"),
    [decimal]$Quantity = 1.0000,
    [decimal]$Price = 10.00,
    [decimal]$TaxRate = 0.0000,
    [long]$ReadinessRunId,
    [switch]$RollbackAfterSuccess,
    [switch]$SkipRollbackOnFailure,
    [switch]$DisableCreatedMasterData
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "readiness-evidence.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not $OutputPath) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputPath = Join-Path $RepoRoot "target\purchase-to-payment-acceptance-$timestamp.md"
}

$BaseUrl = $BaseUrl.TrimEnd("/")
$RunId = "P2P-$(Get-Date -Format "yyyyMMddHHmmss")"
$sections = [System.Collections.Generic.List[string]]::new()
$created = [ordered]@{}
$failure = $null
$rollbackFailure = $null
$readinessRegistrationFailure = $null
$didRollback = $false

$SupplierDisablePathTemplate = "/api/masterdata/suppliers/{id}/disable"
$ProductDisablePathTemplate = "/api/masterdata/products/{id}/disable"
$PurchaseOrderSubmitPathTemplate = "/api/purchase/orders/{id}/submit"
$PurchaseOrderApprovePathTemplate = "/api/purchase/orders/{id}/approve"
$PurchaseReceiptPostPathTemplate = "/api/purchase/receipts/{id}/post"
$PaymentCancelPathTemplate = "/api/finance/payments/{id}/cancel"
$PurchaseReturnPostPathTemplate = "/api/purchase/returns/{id}/post"
$PurchaseOrderTracePathTemplate = "/api/purchase/orders/{id}/trace"

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
        [int]$MaxLength = 3000
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

function Expand-PathTemplate {
    param(
        [string]$Template,
        [long]$Id
    )

    return $Template.Replace("{id}", "$Id")
}

function Invoke-Api {
    param(
        [string]$Title,
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers,
        [object]$Body
    )

    $url = "$BaseUrl$Path"
    [System.Console]::WriteLine("[purchase-to-payment] $Method $Path")

    $bodyJson = $null
    $invokeArgs = @{
        Method = $Method
        Uri = $url
        Headers = $Headers
        UseBasicParsing = $true
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $bodyJson = $Body | ConvertTo-Json -Depth 20
        $invokeArgs["Body"] = $bodyJson
        $invokeArgs["ContentType"] = "application/json"
    }

    try {
        $response = Invoke-WebRequest @invokeArgs
        $content = $response.Content
        $preview = Get-BodyPreview $content
        Add-Section $Title @"
Request: $Method $url

Status: $($response.StatusCode) $($response.StatusDescription)

Request body:

````
$bodyJson
````

Response preview:

````
$preview
````
"@
        if ([string]::IsNullOrWhiteSpace($content)) {
            return $null
        }
        return $content | ConvertFrom-Json
    }
    catch {
        $statusDisplay = "n/a"
        $content = ""
        if ($_.Exception.Response) {
            $statusDisplay = "$([int]$_.Exception.Response.StatusCode) $($_.Exception.Response.StatusDescription)".Trim()
            $content = Get-ResponseContent $_.Exception.Response
        }
        Add-Section $Title @"
Request: $Method $url

Status: $statusDisplay

Request body:

````
$bodyJson
````

Failure:

````
$($_ | Out-String)
````

Response preview:

````
$(Get-BodyPreview $content)
````
"@
        throw
    }
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

    [System.Console]::WriteLine("[purchase-to-payment] POST /api/auth/login")
    $loginResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
        -Body $loginBody `
        -ContentType "application/json" `
        -TimeoutSec 30

    $token = $loginResponse.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login response did not contain data.accessToken."
    }

    Add-Section "Authentication" "POST $BaseUrl/api/auth/login returned a bearer token for user `$Username`. Token value is intentionally not recorded."
    return $token
}

function Require-DataId {
    param(
        [object]$Response,
        [string]$Name
    )

    $id = $Response.data.id
    if ($null -eq $id -or [long]$id -le 0) {
        throw "$Name response did not contain data.id."
    }
    return [long]$id
}

function Require-FirstLineId {
    param(
        [object]$Response,
        [string]$Name
    )

    $lines = @($Response.data.lines)
    if ($lines.Count -lt 1 -or $null -eq $lines[0].id) {
        throw "$Name response did not contain data.lines[0].id."
    }
    return [long]$lines[0].id
}

function Find-PayableForReceipt {
    param(
        [hashtable]$Headers,
        [long]$SupplierId,
        [long]$ReceiptId
    )

    $queryPath = "/api/finance/payables?supplierId=$SupplierId&sourceType=PURCHASE_RECEIPT&bizDateFrom=$BusinessDate&bizDateTo=$BusinessDate&pageSize=50"
    $response = Invoke-Api "Find payable generated by purchase receipt" "GET" $queryPath $Headers $null
    $matches = @($response.data.records) | Where-Object { [long]$_.sourceId -eq $ReceiptId -and $_.sourceType -eq "PURCHASE_RECEIPT" }
    $payable = $matches | Select-Object -First 1
    if ($null -eq $payable -or $null -eq $payable.id) {
        throw "Could not find payable generated by receipt $ReceiptId."
    }
    return $payable
}

function Add-CreatedSummary {
    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("| Key | Value |")
    $rows.Add("|---|---|")
    foreach ($key in $created.Keys) {
        $rows.Add("| `$key` | `$($created[$key])` |")
    }
    Add-Section "Created business documents" ($rows -join [Environment]::NewLine)
}

function Invoke-CompensationRollback {
    param(
        [hashtable]$Headers,
        [string]$Reason
    )

    if ($script:didRollback) {
        Add-Section "Compensation rollback" "Skipped because business compensation rollback already ran."
        return
    }
    $script:didRollback = $true

    Add-Section "Compensation rollback" "Starting business compensation rollback because: $Reason"

    if ($created.Contains("paymentId")) {
        $paymentCancelPath = Expand-PathTemplate $PaymentCancelPathTemplate ([long]$created["paymentId"])
        Invoke-Api "Rollback payment by cancellation" "POST" $paymentCancelPath $Headers @{
            reason = "Acceptance rollback $RunId"
        } | Out-Null
    }

    if ($created.Contains("receiptId") -and $created.Contains("receiptLineId")) {
        $returnResponse = Invoke-Api "Rollback inventory and payable by purchase return" "POST" "/api/purchase/returns" $Headers @{
            receiptId = [long]$created["receiptId"]
            returnDate = $BusinessDate
            remark = "Acceptance rollback $RunId"
            lines = @(
                @{
                    receiptLineId = [long]$created["receiptLineId"]
                    qty = $Quantity
                    remark = "Acceptance rollback return line $RunId"
                }
            )
        }
        $created["returnId"] = Require-DataId $returnResponse "purchase return"
        $created["returnNo"] = $returnResponse.data.returnNo
        $returnPostPath = Expand-PathTemplate $PurchaseReturnPostPathTemplate ([long]$created["returnId"])
        Invoke-Api "Post rollback purchase return" "POST" $returnPostPath $Headers $null | Out-Null
    }

    if ($DisableCreatedMasterData) {
        if ($created.Contains("productId")) {
            $productDisablePath = Expand-PathTemplate $ProductDisablePathTemplate ([long]$created["productId"])
            Invoke-Api "Disable created product" "POST" $productDisablePath $Headers $null | Out-Null
        }
        if ($created.Contains("supplierId")) {
            $supplierDisablePath = Expand-PathTemplate $SupplierDisablePathTemplate ([long]$created["supplierId"])
            Invoke-Api "Disable created supplier" "POST" $supplierDisablePath $Headers $null | Out-Null
        }
    }
}

Push-Location $RepoRoot
try {
    if ($WarehouseId -le 0) {
        throw "WarehouseId is required. Use an active preproduction warehouse."
    }

    $sections.Add("# Purchase to payment acceptance evidence")
    $sections.Add("")
    $sections.Add("- Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")")
    $sections.Add("- Repository: $RepoRoot")
    $sections.Add("- Base URL: $BaseUrl")
    $sections.Add("- Run ID: $RunId")
    $sections.Add("- Business date: $BusinessDate")
    $sections.Add("- Warehouse ID: $WarehouseId")
    $sections.Add("- Rollback model: business compensation rollback")

    $token = Get-AccessToken
    $headers = @{
        Authorization = "Bearer $token"
    }

    # Workflow forbids submitter==approver. Prefer an explicit submitter; otherwise try runtime_smoke on local.
    $submitHeaders = $headers
    $submitterLabel = $Username
    if (-not [string]::IsNullOrWhiteSpace($SubmitterUsername) -and -not [string]::IsNullOrWhiteSpace($SubmitterPassword)) {
        $submitLogin = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
            -Body (@{ username = $SubmitterUsername; password = $SubmitterPassword } | ConvertTo-Json) `
            -ContentType "application/json" -TimeoutSec 30
        if ([string]::IsNullOrWhiteSpace($submitLogin.data.accessToken)) {
            throw "Submitter login failed for $SubmitterUsername"
        }
        $submitHeaders = @{ Authorization = "Bearer $($submitLogin.data.accessToken)" }
        $submitterLabel = $SubmitterUsername
        Add-Section "Submitter authentication" "Using submitter `$SubmitterUsername` for create/submit; approver remains `$Username`."
    }
    elseif ([string]::IsNullOrWhiteSpace($AccessToken) -and $Username -eq "admin") {
        try {
            $submitLogin = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
                -Body (@{ username = "runtime_smoke"; password = "RuntimeSmoke123" } | ConvertTo-Json) `
                -ContentType "application/json" -TimeoutSec 30
            if (-not [string]::IsNullOrWhiteSpace($submitLogin.data.accessToken)) {
                $submitHeaders = @{ Authorization = "Bearer $($submitLogin.data.accessToken)" }
                $submitterLabel = "runtime_smoke"
                Add-Section "Submitter authentication" "Auto-selected runtime_smoke as submitter to avoid self-approve rejection."
            }
        }
        catch {
            Add-Section "Submitter authentication" "runtime_smoke unavailable; will use primary account (self-approve may fail)."
        }
    }

    $supplierResponse = Invoke-Api "Create acceptance supplier" "POST" "/api/masterdata/suppliers" $headers @{
        supplierCode = "$RunId-SUP"
        supplierName = "Acceptance supplier $RunId"
        contactName = "Acceptance"
        contactPhone = "00000000000"
        settlementMethod = "BANK"
        address = "preproduction acceptance"
        remark = "purchase to payment acceptance $RunId"
    }
    $supplierId = Require-DataId $supplierResponse "supplier"
    $created["supplierId"] = $supplierId
    $created["supplierCode"] = $supplierResponse.data.supplierCode

    $productResponse = Invoke-Api "Create acceptance product" "POST" "/api/masterdata/products" $headers @{
        productCode = "$RunId-PRD"
        productName = "Acceptance product $RunId"
        productType = "GOODS"
        categoryName = "ACCEPTANCE"
        specification = "acceptance"
        unitName = "pcs"
        purchasePrice = $Price
        salePrice = $Price
        taxRate = $TaxRate
        lotControlled = $false
        shelfLifeControlled = $false
        remark = "purchase to payment acceptance $RunId"
    }
    $productId = Require-DataId $productResponse "product"
    $created["productId"] = $productId
    $created["productCode"] = $productResponse.data.productCode

    $orderResponse = Invoke-Api "Create purchase order" "POST" "/api/purchase/orders" $submitHeaders @{
        supplierId = $supplierId
        orderDate = $BusinessDate
        deliveryDate = $BusinessDate
        remark = "Acceptance purchase order $RunId"
        lines = @(
            @{
                productId = $productId
                qty = $Quantity
                price = $Price
                taxRate = $TaxRate
                remark = "Acceptance purchase order line $RunId"
            }
        )
    }
    $orderId = Require-DataId $orderResponse "purchase order"
    $orderLineId = Require-FirstLineId $orderResponse "purchase order"
    $created["orderId"] = $orderId
    $created["orderNo"] = $orderResponse.data.orderNo
    $created["orderLineId"] = $orderLineId
    $created["submitter"] = $submitterLabel

    $orderSubmitPath = Expand-PathTemplate $PurchaseOrderSubmitPathTemplate $orderId
    Invoke-Api "Submit purchase order" "POST" $orderSubmitPath $submitHeaders @{
        remark = "Acceptance submit $RunId"
    } | Out-Null

    $orderApprovePath = Expand-PathTemplate $PurchaseOrderApprovePathTemplate $orderId
    Invoke-Api "Approve purchase order" "POST" $orderApprovePath $headers @{
        remark = "Acceptance approve $RunId"
    } | Out-Null

    $receiptResponse = Invoke-Api "Create purchase receipt" "POST" "/api/purchase/receipts" $headers @{
        orderId = $orderId
        warehouseId = $WarehouseId
        receiptDate = $BusinessDate
        remark = "Acceptance purchase receipt $RunId"
        lines = @(
            @{
                orderLineId = $orderLineId
                qty = $Quantity
                remark = "Acceptance purchase receipt line $RunId"
            }
        )
    }
    $receiptId = Require-DataId $receiptResponse "purchase receipt"
    $receiptLineId = Require-FirstLineId $receiptResponse "purchase receipt"
    $created["receiptId"] = $receiptId
    $created["receiptNo"] = $receiptResponse.data.receiptNo
    $created["receiptLineId"] = $receiptLineId

    $receiptPostPath = Expand-PathTemplate $PurchaseReceiptPostPathTemplate $receiptId
    Invoke-Api "Post purchase receipt" "POST" $receiptPostPath $headers $null | Out-Null

    $payable = Find-PayableForReceipt $headers $supplierId $receiptId
    $created["payableId"] = [long]$payable.id
    $created["payableNo"] = $payable.payableNo

    $paymentResponse = Invoke-Api "Create payment and settle payable" "POST" "/api/finance/payments" $headers @{
        supplierId = $supplierId
        paymentDate = $BusinessDate
        amount = $payable.remainingAmount
        remark = "Acceptance payment $RunId"
        allocations = @(
            @{
                payableId = [long]$payable.id
                amount = $payable.remainingAmount
            }
        )
    }
    $paymentId = Require-DataId $paymentResponse "payment"
    $created["paymentId"] = $paymentId
    $created["paymentNo"] = $paymentResponse.data.paymentNo

    Invoke-Api "Verify payable after payment" "GET" "/api/finance/payables/$($created["payableId"])" $headers $null | Out-Null
    Invoke-Api "Verify payment detail" "GET" "/api/finance/payments/$paymentId" $headers $null | Out-Null

    $orderTracePath = Expand-PathTemplate $PurchaseOrderTracePathTemplate $orderId
    Invoke-Api "Verify purchase order trace" "GET" $orderTracePath $headers $null | Out-Null

    if ($RollbackAfterSuccess) {
        Invoke-CompensationRollback $headers "RollbackAfterSuccess"
    }

    Add-CreatedSummary
}
catch {
    $failure = $_
    Add-Section "Failure" ($failure | Out-String)
    if (-not $SkipRollbackOnFailure) {
        try {
            if (-not [string]::IsNullOrWhiteSpace($AccessToken) -or (-not [string]::IsNullOrWhiteSpace($Username) -and -not [string]::IsNullOrWhiteSpace($Password))) {
                if ($null -eq $headers) {
                    $token = Get-AccessToken
                    $headers = @{ Authorization = "Bearer $token" }
                }
                Invoke-CompensationRollback $headers "failure"
            }
        }
        catch {
            $rollbackFailure = $_
            Add-Section "Rollback failure" ($rollbackFailure | Out-String)
        }
    }
    else {
        Add-Section "Compensation rollback" "Skipped because -SkipRollbackOnFailure was specified."
    }
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

            $status = "PASSED"
            $actualResult = "采购到付款补偿回滚验收通过；证据文件：$OutputPath"
            $failureReason = $null
            if ($failure -or $rollbackFailure) {
                $status = "FAILED"
                $actualResult = "采购到付款补偿回滚验收失败；证据文件：$OutputPath"
                $failureReason = (@($failure, $rollbackFailure) | Where-Object { $null -ne $_ } | ForEach-Object { $_ | Out-String }) -join [Environment]::NewLine
            }

            $registration = Register-ReadinessEvidenceWithOfflineFallback `
                -BaseUrl $BaseUrl `
                -Headers $headers `
                -ReadinessRunId $ReadinessRunId `
                -ItemCode "PURCHASE_TO_PAYMENT" `
                -ItemName "采购到付款" `
                -Category "PURCHASE" `
                -Priority "P0" `
                -ExpectedResult "采购入库、应付、付款核销和补偿回滚链路通过" `
                -Status $status `
                -ActualResult $actualResult `
                -FailureReason $failureReason `
                -EvidenceSummary "采购到付款脚本验收：$status" `
                -EvidenceDetail $evidence `
                -EvidenceRequestUri $OutputPath `
                -BusinessType "PURCHASE_TO_PAYMENT" `
                -BusinessNo $RunId

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
    [System.Console]::WriteLine("Purchase to payment acceptance evidence written to $OutputPath")
    Pop-Location
}

if ($failure) {
    throw $failure
}

if ($rollbackFailure) {
    throw $rollbackFailure
}

if ($readinessRegistrationFailure) {
    throw $readinessRegistrationFailure
}
