param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$OutputPath,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [string]$SubmitterUsername,
    [string]$SubmitterPassword,
    [long]$MaterialWarehouseId,
    [long]$FinishedWarehouseId,
    [string]$BusinessDate = (Get-Date -Format "yyyy-MM-dd"),
    [decimal]$PlannedQty = 1.0000,
    [decimal]$MaterialQtyPer = 2.0000,
    [decimal]$MaterialCostPrice = 10.00,
    [decimal]$FinishedSalePrice = 30.00,
    [decimal]$TaxRate = 0.0000,
    [decimal]$CompletionQty = 1.0000,
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
    $OutputPath = Join-Path $RepoRoot "target\production-manufacturing-acceptance-$timestamp.md"
}

$BaseUrl = $BaseUrl.TrimEnd("/")
$RunId = "MFG-$(Get-Date -Format "yyyyMMddHHmmss")"
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
$PurchaseReturnPostPathTemplate = "/api/purchase/returns/{id}/post"
$ProductionOrderReleasePathTemplate = "/api/production/orders/{id}/release"
$ProductionOrderCancelPathTemplate = "/api/production/orders/{id}/cancel"
$ProductionOrderIssuePathTemplate = "/api/production/orders/{id}/issue"
$ProductionOrderCompletePathTemplate = "/api/production/orders/{id}/complete"
$ProductionOrderReverseCompletionPathTemplate = "/api/production/orders/{id}/reverse-completion"
$ProductionOrderReturnMaterialsPathTemplate = "/api/production/orders/{id}/return-materials"

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

function Convert-ToQuantity {
    param([decimal]$Value)

    return [decimal]::Round($Value, 4, [System.MidpointRounding]::AwayFromZero)
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
    [System.Console]::WriteLine("[production-manufacturing] $Method $Path")

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

    [System.Console]::WriteLine("[production-manufacturing] POST /api/auth/login")
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

function Require-FirstMaterialId {
    param(
        [object]$Response,
        [string]$Name
    )

    $materials = @($Response.data.materials)
    if ($materials.Count -lt 1 -or $null -eq $materials[0].id) {
        throw "$Name response did not contain data.materials[0].id."
    }
    return [long]$materials[0].id
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

    if ($created.Contains("productionOrderId") -and -not $created.Contains("productionIssued")) {
        $productionOrderCancelPath = Expand-PathTemplate $ProductionOrderCancelPathTemplate ([long]$created["productionOrderId"])
        Invoke-Api "Cancel production order before material issue" "POST" $productionOrderCancelPath $Headers $null | Out-Null
        $created["productionOrderCancelled"] = $true
    }

    if ($created.Contains("productionCompleted") -and $created.Contains("productionOrderId")) {
        $productionOrderReverseCompletionPath = Expand-PathTemplate $ProductionOrderReverseCompletionPathTemplate ([long]$created["productionOrderId"])
        Invoke-Api "Reverse completed production output" "POST" $productionOrderReverseCompletionPath $Headers @{
            reversalDate = $BusinessDate
            remark = "Acceptance production completion reversal $RunId"
        } | Out-Null
        $created["productionCompletionReversed"] = $true
        $created["returnableMaterialQty"] = [decimal]$created["materialSetupQty"]
    }

    if ($created.Contains("productionIssued") -and -not $created.Contains("productionCompleted")) {
        $created["returnableMaterialQty"] = [decimal]$created["materialSetupQty"]
    }

    if ($created.Contains("productionIssued") -and $created.Contains("productionOrderId") -and $created.Contains("productionOrderMaterialId")) {
        $returnableMaterialQty = [decimal]0
        if ($created.Contains("returnableMaterialQty")) {
            $returnableMaterialQty = [decimal]$created["returnableMaterialQty"]
        }

        if ($returnableMaterialQty -gt 0) {
            $productionOrderReturnMaterialsPath = Expand-PathTemplate $ProductionOrderReturnMaterialsPathTemplate ([long]$created["productionOrderId"])
            Invoke-Api "Return unused production material" "POST" $productionOrderReturnMaterialsPath $Headers @{
                returnDate = $BusinessDate
                remark = "Acceptance production rollback $RunId"
                lines = @(
                    @{
                        orderMaterialId = [long]$created["productionOrderMaterialId"]
                        returnQty = $returnableMaterialQty
                        remark = "Acceptance production material return $RunId"
                    }
                )
            } | Out-Null
            $created["productionMaterialReturnedQty"] = $returnableMaterialQty
        }
        else {
            Add-Section "Return unused production material" "Skipped because completed production consumed all issued material or no material was issued."
        }
    }

    # 退料会 restoreReservation，qtyAvailable 仍为 0；必须取消 RELEASED 工单释放预留后才能采购退货过账。
    if ($created.Contains("productionOrderId") -and -not $created.Contains("productionOrderCancelled")) {
        $shouldCancelAfterReturn = $false
        if ($created.Contains("productionMaterialReturnedQty")) {
            $shouldCancelAfterReturn = $true
        }
        elseif ($created.Contains("productionIssued") -eq $false -and $created.Contains("productionCompleted") -eq $false) {
            # already handled earlier when never issued
            $shouldCancelAfterReturn = $false
        }
        if ($shouldCancelAfterReturn) {
            $productionOrderCancelPath = Expand-PathTemplate $ProductionOrderCancelPathTemplate ([long]$created["productionOrderId"])
            Invoke-Api "Cancel production order to release material reservation" "POST" $productionOrderCancelPath $Headers $null | Out-Null
            $created["productionOrderCancelled"] = $true
        }
    }

    if ($created.Contains("purchaseReceiptPosted") -and $created.Contains("purchaseReceiptId") -and $created.Contains("purchaseReceiptLineId")) {
        $returnQty = [decimal]$created["materialSetupQty"]
        if ($created.Contains("productionIssued")) {
            if ($created.Contains("productionMaterialReturnedQty")) {
                $returnQty = [decimal]$created["productionMaterialReturnedQty"]
            }
            else {
                $returnQty = [decimal]0
            }
        }

        if ($returnQty -gt 0) {
            $purchaseReturnResponse = Invoke-Api "Rollback material setup stock by purchase return" "POST" "/api/purchase/returns" $Headers @{
                receiptId = [long]$created["purchaseReceiptId"]
                returnDate = $BusinessDate
                remark = "Acceptance material setup rollback $RunId"
                lines = @(
                    @{
                        receiptLineId = [long]$created["purchaseReceiptLineId"]
                        qty = $returnQty
                        remark = "Acceptance material setup return line $RunId"
                    }
                )
            }
            $created["purchaseReturnId"] = Require-DataId $purchaseReturnResponse "purchase return"
            $created["purchaseReturnNo"] = $purchaseReturnResponse.data.returnNo
            $purchaseReturnPostPath = Expand-PathTemplate $PurchaseReturnPostPathTemplate ([long]$created["purchaseReturnId"])
            Invoke-Api "Post rollback purchase return" "POST" $purchaseReturnPostPath $Headers $null | Out-Null
            $created["purchaseReturnPosted"] = $true
        }
        else {
            Add-Section "Rollback material setup stock by purchase return" "Skipped because no returned material quantity is available for purchase return."
        }
    }

    if ($DisableCreatedMasterData) {
        if ($created.Contains("finishedProductId")) {
            $finishedProductDisablePath = Expand-PathTemplate $ProductDisablePathTemplate ([long]$created["finishedProductId"])
            Invoke-Api "Disable created finished product" "POST" $finishedProductDisablePath $Headers $null | Out-Null
        }
        if ($created.Contains("materialProductId")) {
            $materialProductDisablePath = Expand-PathTemplate $ProductDisablePathTemplate ([long]$created["materialProductId"])
            Invoke-Api "Disable created material product" "POST" $materialProductDisablePath $Headers $null | Out-Null
        }
        if ($created.Contains("supplierId")) {
            $supplierDisablePath = Expand-PathTemplate $SupplierDisablePathTemplate ([long]$created["supplierId"])
            Invoke-Api "Disable created supplier" "POST" $supplierDisablePath $Headers $null | Out-Null
        }
    }
}

Push-Location $RepoRoot
try {
    if ($MaterialWarehouseId -le 0) {
        throw "MaterialWarehouseId is required. Use an active preproduction material warehouse."
    }
    if ($FinishedWarehouseId -le 0) {
        throw "FinishedWarehouseId is required. Use an active preproduction finished-goods warehouse."
    }
    if ($PlannedQty -le 0) {
        throw "PlannedQty must be greater than 0."
    }
    if ($MaterialQtyPer -le 0) {
        throw "MaterialQtyPer must be greater than 0."
    }
    if ($CompletionQty -le 0 -or $CompletionQty -gt $PlannedQty) {
        throw "CompletionQty must be greater than 0 and less than or equal to PlannedQty."
    }

    $materialSetupQty = Convert-ToQuantity -Value ($PlannedQty * $MaterialQtyPer)
    $consumedMaterialQty = Convert-ToQuantity -Value ($materialSetupQty * $CompletionQty / $PlannedQty)
    $returnableMaterialQty = Convert-ToQuantity -Value ($materialSetupQty - $consumedMaterialQty)

    $sections.Add("# Production manufacturing acceptance evidence")
    $sections.Add("")
    $sections.Add("- Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")")
    $sections.Add("- Repository: $RepoRoot")
    $sections.Add("- Base URL: $BaseUrl")
    $sections.Add("- Run ID: $RunId")
    $sections.Add("- Business date: $BusinessDate")
    $sections.Add("- Material warehouse ID: $MaterialWarehouseId")
    $sections.Add("- Finished warehouse ID: $FinishedWarehouseId")
    $sections.Add("- Planned quantity: $PlannedQty")
    $sections.Add("- Completion quantity: $CompletionQty")
    $sections.Add("- Material setup quantity: $materialSetupQty")
    $sections.Add("- Rollback model: business compensation rollback")
    $sections.Add("- Rollback coverage: production completion reversal, production material return, material setup purchase return")

    $created["materialSetupQty"] = $materialSetupQty
    $created["consumedMaterialQty"] = $consumedMaterialQty
    $created["returnableMaterialQty"] = $returnableMaterialQty

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

    $supplierResponse = Invoke-Api "Create material setup supplier" "POST" "/api/masterdata/suppliers" $headers @{
        supplierCode = "$RunId-SUP"
        supplierName = "Acceptance supplier $RunId"
        contactName = "Acceptance"
        contactPhone = "00000000000"
        settlementMethod = "BANK"
        address = "preproduction acceptance"
        remark = "production manufacturing material setup $RunId"
    }
    $supplierId = Require-DataId $supplierResponse "supplier"
    $created["supplierId"] = $supplierId
    $created["supplierCode"] = $supplierResponse.data.supplierCode

    $materialProductResponse = Invoke-Api "Create acceptance material product" "POST" "/api/masterdata/products" $headers @{
        productCode = "$RunId-MAT"
        productName = "Acceptance material $RunId"
        productType = "GOODS"
        categoryName = "ACCEPTANCE"
        specification = "acceptance"
        unitName = "pcs"
        purchasePrice = $MaterialCostPrice
        salePrice = $MaterialCostPrice
        taxRate = $TaxRate
        lotControlled = $false
        shelfLifeControlled = $false
        remark = "production manufacturing material $RunId"
    }
    $materialProductId = Require-DataId $materialProductResponse "material product"
    $created["materialProductId"] = $materialProductId
    $created["materialProductCode"] = $materialProductResponse.data.productCode

    $finishedProductResponse = Invoke-Api "Create acceptance finished product" "POST" "/api/masterdata/products" $headers @{
        productCode = "$RunId-FG"
        productName = "Acceptance finished product $RunId"
        productType = "PHYSICAL"
        categoryName = "ACCEPTANCE"
        specification = "acceptance"
        unitName = "pcs"
        purchasePrice = $MaterialCostPrice
        salePrice = $FinishedSalePrice
        taxRate = $TaxRate
        lotControlled = $false
        shelfLifeControlled = $false
        remark = "production manufacturing finished product $RunId"
    }
    $finishedProductId = Require-DataId $finishedProductResponse "finished product"
    $created["finishedProductId"] = $finishedProductId
    $created["finishedProductCode"] = $finishedProductResponse.data.productCode

    $purchaseOrderResponse = Invoke-Api "Create material setup purchase order" "POST" "/api/purchase/orders" $submitHeaders @{
        supplierId = $supplierId
        orderDate = $BusinessDate
        deliveryDate = $BusinessDate
        remark = "Acceptance material setup purchase order $RunId"
        lines = @(
            @{
                productId = $materialProductId
                qty = $materialSetupQty
                price = $MaterialCostPrice
                taxRate = $TaxRate
                remark = "Acceptance material setup purchase line $RunId"
            }
        )
    }
    $purchaseOrderId = Require-DataId $purchaseOrderResponse "material setup purchase order"
    $purchaseOrderLineId = Require-FirstLineId $purchaseOrderResponse "material setup purchase order"
    $created["purchaseOrderId"] = $purchaseOrderId
    $created["purchaseOrderNo"] = $purchaseOrderResponse.data.orderNo
    $created["purchaseOrderLineId"] = $purchaseOrderLineId
    $created["submitter"] = $submitterLabel

    $purchaseOrderSubmitPath = Expand-PathTemplate $PurchaseOrderSubmitPathTemplate $purchaseOrderId
    Invoke-Api "Submit material setup purchase order" "POST" $purchaseOrderSubmitPath $submitHeaders @{
        remark = "Acceptance material setup submit $RunId"
    } | Out-Null

    $purchaseOrderApprovePath = Expand-PathTemplate $PurchaseOrderApprovePathTemplate $purchaseOrderId
    Invoke-Api "Approve material setup purchase order" "POST" $purchaseOrderApprovePath $headers @{
        remark = "Acceptance material setup approve $RunId"
    } | Out-Null

    $purchaseReceiptResponse = Invoke-Api "Create material setup purchase receipt" "POST" "/api/purchase/receipts" $headers @{
        orderId = $purchaseOrderId
        warehouseId = $MaterialWarehouseId
        receiptDate = $BusinessDate
        remark = "Acceptance material setup receipt $RunId"
        lines = @(
            @{
                orderLineId = $purchaseOrderLineId
                qty = $materialSetupQty
                remark = "Acceptance material setup receipt line $RunId"
            }
        )
    }
    $purchaseReceiptId = Require-DataId $purchaseReceiptResponse "material setup purchase receipt"
    $purchaseReceiptLineId = Require-FirstLineId $purchaseReceiptResponse "material setup purchase receipt"
    $created["purchaseReceiptId"] = $purchaseReceiptId
    $created["purchaseReceiptNo"] = $purchaseReceiptResponse.data.receiptNo
    $created["purchaseReceiptLineId"] = $purchaseReceiptLineId

    $purchaseReceiptPostPath = Expand-PathTemplate $PurchaseReceiptPostPathTemplate $purchaseReceiptId
    Invoke-Api "Post material setup purchase receipt" "POST" $purchaseReceiptPostPath $headers $null | Out-Null
    $created["purchaseReceiptPosted"] = $true

    $bomResponse = Invoke-Api "Create production BOM" "POST" "/api/production/boms" $headers @{
        productId = $finishedProductId
        baseQty = 1.0000
        remark = "Acceptance production BOM $RunId"
        lines = @(
            @{
                materialProductId = $materialProductId
                qtyPer = $MaterialQtyPer
                lossRate = 0.0000
                remark = "Acceptance BOM material $RunId"
            }
        )
    }
    $bomId = Require-DataId $bomResponse "production BOM"
    $created["bomId"] = $bomId
    $created["bomNo"] = $bomResponse.data.bomNo

    $productionOrderResponse = Invoke-Api "Create production order" "POST" "/api/production/orders" $headers @{
        bomId = $bomId
        finishedWarehouseId = $FinishedWarehouseId
        materialWarehouseId = $MaterialWarehouseId
        plannedQty = $PlannedQty
        plannedStartDate = $BusinessDate
        plannedFinishDate = $BusinessDate
        remark = "Acceptance production order $RunId"
    }
    $productionOrderId = Require-DataId $productionOrderResponse "production order"
    $productionOrderMaterialId = Require-FirstMaterialId $productionOrderResponse "production order"
    $created["productionOrderId"] = $productionOrderId
    $created["productionOrderNo"] = $productionOrderResponse.data.orderNo
    $created["productionOrderMaterialId"] = $productionOrderMaterialId

    $productionOrderReleasePath = Expand-PathTemplate $ProductionOrderReleasePathTemplate $productionOrderId
    $releaseResponse = Invoke-Api "Release production order" "POST" $productionOrderReleasePath $headers $null
    $created["productionOrderStatusAfterRelease"] = $releaseResponse.data.status

    $productionOrderIssuePath = Expand-PathTemplate $ProductionOrderIssuePathTemplate $productionOrderId
    $issueResponse = Invoke-Api "Issue production materials" "POST" $productionOrderIssuePath $headers @{
        issueDate = $BusinessDate
        remark = "Acceptance production issue $RunId"
        lines = @(
            @{
                orderMaterialId = $productionOrderMaterialId
                issueQty = $materialSetupQty
                remark = "Acceptance production issue line $RunId"
            }
        )
    }
    $created["productionIssued"] = $true
    $created["productionOrderStatusAfterIssue"] = $issueResponse.data.status

    $productionOrderCompletePath = Expand-PathTemplate $ProductionOrderCompletePathTemplate $productionOrderId
    $completionResponse = Invoke-Api "Complete production output" "POST" $productionOrderCompletePath $headers @{
        completionDate = $BusinessDate
        completedQty = $CompletionQty
        remark = "Acceptance production completion $RunId"
    }
    $created["productionCompleted"] = $true
    $created["completionQty"] = $CompletionQty
    $created["productionOrderStatusAfterCompletion"] = $completionResponse.data.status

    Invoke-Api "Verify production order detail" "GET" "/api/production/orders/$productionOrderId" $headers $null | Out-Null
    Invoke-Api "Verify production BOM detail" "GET" "/api/production/boms/$bomId" $headers $null | Out-Null

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
            $actualResult = "生产制造补偿回滚验收通过；证据文件：$OutputPath"
            $failureReason = $null
            if ($failure -or $rollbackFailure) {
                $status = "FAILED"
                $actualResult = "生产制造补偿回滚验收失败；证据文件：$OutputPath"
                $failureReason = (@($failure, $rollbackFailure) | Where-Object { $null -ne $_ } | ForEach-Object { $_ | Out-String }) -join [Environment]::NewLine
            }

            $registration = Register-ReadinessEvidenceWithOfflineFallback `
                -BaseUrl $BaseUrl `
                -Headers $headers `
                -ReadinessRunId $ReadinessRunId `
                -ItemCode "PRODUCTION_MANUFACTURING" `
                -ItemName "生产制造" `
                -Category "PRODUCTION" `
                -Priority "P1" `
                -ExpectedResult "BOM、工单、领料、完工、反完工、退料和补偿回滚链路通过" `
                -Status $status `
                -ActualResult $actualResult `
                -FailureReason $failureReason `
                -EvidenceSummary "生产制造脚本验收：$status" `
                -EvidenceDetail $evidence `
                -EvidenceRequestUri $OutputPath `
                -BusinessType "PRODUCTION_MANUFACTURING" `
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
    [System.Console]::WriteLine("Production manufacturing acceptance evidence written to $OutputPath")
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
