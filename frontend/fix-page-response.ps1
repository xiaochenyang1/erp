# 批量修复PageResponse字段名

Write-Host "开始批量替换PageResponse字段名..." -ForegroundColor Green

$viewFiles = Get-ChildItem -Path "src/views" -Filter "*.vue" -Recurse

foreach ($file in $viewFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content

    # 替换 .content 为 .records
    $content = $content -replace '([a-zA-Z_]+)\.content\b', '$1.records'

    # 替换 .totalElements 为 .total
    $content = $content -replace '([a-zA-Z_]+)\.totalElements\b', '$1.total'

    # 只有内容改变才写回文件
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
        Write-Host "已处理: $($file.FullName)" -ForegroundColor Yellow
    }
}

Write-Host "完成！" -ForegroundColor Green
