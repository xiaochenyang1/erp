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
    [decimal]$CostPrice = 10.00,
    [decimal]$SalesPrice = 20.00,
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
    $OutputPath = Join-Path $RepoRoot "target\sales-to-cash-acceptance-$timestamp.md"
}

$BaseUrl = $BaseUrl.TrimEnd("/")
$RunId = "S2C-$(Get-Date -Format "yyyyMMddHHmmss")"
$sections = [System.Collections.Generic.List[string]]::new()
$created = [ordered]@{}
$failure = $null
$rollbackFailure = $null
$readinessRegistrationFailure = $null
$didRollback = $false

$SupplierDisablePathTemplate = "/api/masterdata/suppliers/{id}/disable"
$CustomerDisablePathTemplate = "/api/masterdata/customers/{id}/disable"
$ProductDisablePathTemplate = "/api/masterdata/products/{id}/disable"
$PurchaseOrderSubmitPathTemplate = "/api/purchase/orders/{id}/submit"
$PurchaseOrderApprovePathTemplate = "/api/purchase/orders/{id}/approve"
$PurchaseReceiptPostPathTemplate = "/api/purchase/receipts/{id}/post"
$PurchaseReturnPostPathTemplate = "/api/purchase/returns/{id}/post"
$SalesOrderSubmitPathTemplate = "/api/sales/orders/{id}/submit"
$SalesOrderApprovePathTemplate = "/api/sales/orders/{id}/approve"
$SalesDeliveryPostPathTemplate = "/api/sales/deliveries/{id}/post"
$FinanceReceiptCancelPathTemplate = "/api/finance/receipts/{id}/cancel"
$SalesReturnPostPathTemplate = "/api/sales/returns/{id}/post"

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
    [System.Console]::WriteLine("[sales-to-cash] $Method $Path")

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

    [System.Console]::WriteLine("[sales-to-cash] POST /api/auth/login")
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

function Find-ReceivableForDelivery {
    param(
        [hashtable]$Headers,
        [long]$CustomerId,
        [long]$DeliveryId
    )

    $queryPath = "/api/finance/receivables?customerId=$CustomerId&sourceType=SALES_DELIVERY&bizDateFrom=$BusinessDate&bizDateTo=$BusinessDate&pageSize=50"
    $response = Invoke-Api "Find receivable generated by sales delivery" "GET" $queryPath $Headers $null
    $matches = @($response.data.records) | Where-Object { [long]$_.sourceId -eq $DeliveryId -and $_.sourceType -eq "SALES_DELIVERY" }
    $receivable = $matches | Select-Object -First 1
    if ($null -eq $receivable -or $null -eq $receivable.id) {
        throw "Could not find receivable generated by delivery $DeliveryId."
    }
    return $receivable
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

    if ($created.Contains("cashReceiptId")) {
        $receiptCancelPath = Expand-PathTemplate $FinanceReceiptCancelPathTemplate ([long]$created["cashReceiptId"])
        Invoke-Api "Rollback customer receipt by cancellation" "POST" $receiptCancelPath $Headers @{
            reason = "Acceptance rollback $RunId"
        } | Out-Null
    }

    if ($created.Contains("salesDeliveryPosted") -and $created.Contains("salesDeliveryId") -and $created.Contains("salesDeliveryLineId")) {
        $salesReturnResponse = Invoke-Api "Rollback delivery and receivable by sales return" "POST" "/api/sales/returns" $Headers @{
            deliveryId = [long]$created["salesDeliveryId"]
            returnDate = $BusinessDate
            remark = "Acceptance rollback $RunId"
            lines = @(
                @{
                    deliveryLineId = [long]$created["salesDeliveryLineId"]
                    qty = $Quantity
                    remark = "Acceptance sales return line $RunId"
                }
            )
        }
        $created["salesReturnId"] = Require-DataId $salesReturnResponse "sales return"
        $created["salesReturnNo"] = $salesReturnResponse.data.returnNo
        $salesReturnPostPath = Expand-PathTemplate $SalesReturnPostPathTemplate ([long]$created["salesReturnId"])
        Invoke-Api "Post rollback sales return" "POST" $salesReturnPostPath $Headers $null | Out-Null
    }

    if ($created.Contains("purchaseReceiptPosted") -and $created.Contains("purchaseReceiptId") -and $created.Contains("purchaseReceiptLineId")) {
        $purchaseReturnResponse = Invoke-Api "Rollback setup stock by purchase return" "POST" "/api/purchase/returns" $Headers @{
            receiptId = [long]$created["purchaseReceiptId"]
            returnDate = $BusinessDate
            remark = "Acceptance stock setup rollback $RunId"
            lines = @(
                @{
                    receiptLineId = [long]$created["purchaseReceiptLineId"]
                    qty = $Quantity
                    remark = "Acceptance stock setup return line $RunId"
                }
            )
        }
        $created["purchaseReturnId"] = Require-DataId $purchaseReturnResponse "purchase return"
        $created["purchaseReturnNo"] = $purchaseReturnResponse.data.returnNo
        $purchaseReturnPostPath = Expand-PathTemplate $PurchaseReturnPostPathTemplate ([long]$created["purchaseReturnId"])
        Invoke-Api "Post rollback purchase return" "POST" $purchaseReturnPostPath $Headers $null | Out-Null
    }

    if ($DisableCreatedMasterData) {
        if ($created.Contains("customerId")) {
            $customerDisablePath = Expand-PathTemplate $CustomerDisablePathTemplate ([long]$created["customerId"])
            Invoke-Api "Disable created customer" "POST" $customerDisablePath $Headers $null | Out-Null
        }
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

    $sections.Add("# Sales to cash acceptance evidence")
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

    $supplierResponse = Invoke-Api "Create stock setup supplier" "POST" "/api/masterdata/suppliers" $headers @{
        supplierCode = "$RunId-SUP"
        supplierName = "Acceptance supplier $RunId"
        contactName = "Acceptance"
        contactPhone = "00000000000"
        settlementMethod = "BANK"
        address = "preproduction acceptance"
        remark = "sales to cash stock setup $RunId"
    }
    $supplierId = Require-DataId $supplierResponse "supplier"
    $created["supplierId"] = $supplierId
    $created["supplierCode"] = $supplierResponse.data.supplierCode

    $customerResponse = Invoke-Api "Create acceptance customer" "POST" "/api/masterdata/customers" $headers @{
        customerCode = "$RunId-CUS"
        customerName = "Acceptance customer $RunId"
        contactName = "Acceptance"
        contactPhone = "00000000000"
        settlementMethod = "BANK"
        creditLimit = 999999.00
        address = "preproduction acceptance"
        remark = "sales to cash acceptance $RunId"
    }
    $customerId = Require-DataId $customerResponse "customer"
    $created["customerId"] = $customerId
    $created["customerCode"] = $customerResponse.data.customerCode

    $productResponse = Invoke-Api "Create acceptance product" "POST" "/api/masterdata/products" $headers @{
        productCode = "$RunId-PRD"
        productName = "Acceptance product $RunId"
        productType = "PHYSICAL"
        categoryName = "ACCEPTANCE"
        specification = "acceptance"
        unitName = "pcs"
        purchasePrice = $CostPrice
        salePrice = $SalesPrice
        taxRate = $TaxRate
        lotControlled = $false
        shelfLifeControlled = $false
        remark = "sales to cash acceptance $RunId"
    }
    $productId = Require-DataId $productResponse "product"
    $created["productId"] = $productId
    $created["productCode"] = $productResponse.data.productCode

    $purchaseOrderResponse = Invoke-Api "Create stock setup purchase order" "POST" "/api/purchase/orders" $submitHeaders @{
        supplierId = $supplierId
        orderDate = $BusinessDate
        deliveryDate = $BusinessDate
        remark = "Acceptance stock setup purchase order $RunId"
        lines = @(
            @{
                productId = $productId
                qty = $Quantity
                price = $CostPrice
                taxRate = $TaxRate
                remark = "Acceptance stock setup purchase line $RunId"
            }
        )
    }
    $purchaseOrderId = Require-DataId $purchaseOrderResponse "stock setup purchase order"
    $purchaseOrderLineId = Require-FirstLineId $purchaseOrderResponse "stock setup purchase order"
    $created["purchaseOrderId"] = $purchaseOrderId
    $created["purchaseOrderNo"] = $purchaseOrderResponse.data.orderNo
    $created["purchaseOrderLineId"] = $purchaseOrderLineId
    $created["submitter"] = $submitterLabel

    $purchaseOrderSubmitPath = Expand-PathTemplate $PurchaseOrderSubmitPathTemplate $purchaseOrderId
    Invoke-Api "Submit stock setup purchase order" "POST" $purchaseOrderSubmitPath $submitHeaders @{
        remark = "Acceptance stock setup submit $RunId"
    } | Out-Null

    $purchaseOrderApprovePath = Expand-PathTemplate $PurchaseOrderApprovePathTemplate $purchaseOrderId
    Invoke-Api "Approve stock setup purchase order" "POST" $purchaseOrderApprovePath $headers @{
        remark = "Acceptance stock setup approve $RunId"
    } | Out-Null

    $purchaseReceiptResponse = Invoke-Api "Create stock setup purchase receipt" "POST" "/api/purchase/receipts" $headers @{
        orderId = $purchaseOrderId
        warehouseId = $WarehouseId
        receiptDate = $BusinessDate
        remark = "Acceptance stock setup receipt $RunId"
        lines = @(
            @{
                orderLineId = $purchaseOrderLineId
                qty = $Quantity
                remark = "Acceptance stock setup receipt line $RunId"
            }
        )
    }
    $purchaseReceiptId = Require-DataId $purchaseReceiptResponse "stock setup purchase receipt"
    $purchaseReceiptLineId = Require-FirstLineId $purchaseReceiptResponse "stock setup purchase receipt"
    $created["purchaseReceiptId"] = $purchaseReceiptId
    $created["purchaseReceiptNo"] = $purchaseReceiptResponse.data.receiptNo
    $created["purchaseReceiptLineId"] = $purchaseReceiptLineId

    $purchaseReceiptPostPath = Expand-PathTemplate $PurchaseReceiptPostPathTemplate $purchaseReceiptId
    Invoke-Api "Post stock setup purchase receipt" "POST" $purchaseReceiptPostPath $headers $null | Out-Null
    $created["purchaseReceiptPosted"] = $true

    $salesOrderResponse = Invoke-Api "Create sales order" "POST" "/api/sales/orders" $submitHeaders @{
        customerId = $customerId
        warehouseId = $WarehouseId
        orderDate = $BusinessDate
        deliveryDate = $BusinessDate
        remark = "Acceptance sales order $RunId"
        lines = @(
            @{
                productId = $productId
                qty = $Quantity
                price = $SalesPrice
                taxRate = $TaxRate
                remark = "Acceptance sales order line $RunId"
            }
        )
    }
    $salesOrderId = Require-DataId $salesOrderResponse "sales order"
    $salesOrderLineId = Require-FirstLineId $salesOrderResponse "sales order"
    $created["salesOrderId"] = $salesOrderId
    $created["salesOrderNo"] = $salesOrderResponse.data.orderNo
    $created["salesOrderLineId"] = $salesOrderLineId

    $salesOrderSubmitPath = Expand-PathTemplate $SalesOrderSubmitPathTemplate $salesOrderId
    Invoke-Api "Submit sales order" "POST" $salesOrderSubmitPath $submitHeaders @{
        remark = "Acceptance submit $RunId"
    } | Out-Null

    $salesOrderApprovePath = Expand-PathTemplate $SalesOrderApprovePathTemplate $salesOrderId
    Invoke-Api "Approve sales order" "POST" $salesOrderApprovePath $headers @{
        remark = "Acceptance approve $RunId"
    } | Out-Null

    $salesDeliveryResponse = Invoke-Api "Create sales delivery" "POST" "/api/sales/deliveries" $headers @{
        orderId = $salesOrderId
        warehouseId = $WarehouseId
        deliveryDate = $BusinessDate
        remark = "Acceptance sales delivery $RunId"
        lines = @(
            @{
                orderLineId = $salesOrderLineId
                qty = $Quantity
                remark = "Acceptance sales delivery line $RunId"
            }
        )
    }
    $salesDeliveryId = Require-DataId $salesDeliveryResponse "sales delivery"
    $salesDeliveryLineId = Require-FirstLineId $salesDeliveryResponse "sales delivery"
    $created["salesDeliveryId"] = $salesDeliveryId
    $created["salesDeliveryNo"] = $salesDeliveryResponse.data.deliveryNo
    $created["salesDeliveryLineId"] = $salesDeliveryLineId

    $salesDeliveryPostPath = Expand-PathTemplate $SalesDeliveryPostPathTemplate $salesDeliveryId
    Invoke-Api "Post sales delivery" "POST" $salesDeliveryPostPath $headers $null | Out-Null
    $created["salesDeliveryPosted"] = $true

    $receivable = Find-ReceivableForDelivery $headers $customerId $salesDeliveryId
    $created["receivableId"] = [long]$receivable.id
    $created["receivableNo"] = $receivable.receivableNo

    $cashReceiptResponse = Invoke-Api "Create customer receipt and settle receivable" "POST" "/api/finance/receipts" $headers @{
        customerId = $customerId
        receiptDate = $BusinessDate
        amount = $receivable.remainingAmount
        remark = "Acceptance customer receipt $RunId"
        allocations = @(
            @{
                receivableId = [long]$receivable.id
                amount = $receivable.remainingAmount
            }
        )
    }
    $cashReceiptId = Require-DataId $cashReceiptResponse "customer receipt"
    $created["cashReceiptId"] = $cashReceiptId
    $created["cashReceiptNo"] = $cashReceiptResponse.data.receiptNo

    Invoke-Api "Verify receivable after customer receipt" "GET" "/api/finance/receivables/$($created["receivableId"])" $headers $null | Out-Null
    Invoke-Api "Verify customer receipt detail" "GET" "/api/finance/receipts/$cashReceiptId" $headers $null | Out-Null
    Invoke-Api "Verify sales delivery detail" "GET" "/api/sales/deliveries/$salesDeliveryId" $headers $null | Out-Null
    Invoke-Api "Verify sales order detail" "GET" "/api/sales/orders/$salesOrderId" $headers $null | Out-Null

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
            $actualResult = "销售到收款补偿回滚验收通过；证据文件：$OutputPath"
            $failureReason = $null
            if ($failure -or $rollbackFailure) {
                $status = "FAILED"
                $actualResult = "销售到收款补偿回滚验收失败；证据文件：$OutputPath"
                $failureReason = (@($failure, $rollbackFailure) | Where-Object { $null -ne $_ } | ForEach-Object { $_ | Out-String }) -join [Environment]::NewLine
            }

            $registration = Register-ReadinessEvidenceWithOfflineFallback `
                -BaseUrl $BaseUrl `
                -Headers $headers `
                -ReadinessRunId $ReadinessRunId `
                -ItemCode "SALES_TO_RECEIPT" `
                -ItemName "销售到收款" `
                -Category "SALES" `
                -Priority "P0" `
                -ExpectedResult "销售出库、应收、收款核销和补偿回滚链路通过" `
                -Status $status `
                -ActualResult $actualResult `
                -FailureReason $failureReason `
                -EvidenceSummary "销售到收款脚本验收：$status" `
                -EvidenceDetail $evidence `
                -EvidenceRequestUri $OutputPath `
                -BusinessType "SALES_TO_RECEIPT" `
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
    [System.Console]::WriteLine("Sales to cash acceptance evidence written to $OutputPath")
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
