#!/bin/bash
# 批量修复PageResponse字段名

cd "E:/tuowei/python/erp-frontend"

echo "开始批量替换PageResponse字段名..."

# 查找所有Vue文件中使用.content和.totalElements的地方
find src/views -name "*.vue" -type f | while read file; do
    # 备份原文件
    # cp "$file" "$file.bak"

    # 替换 .content 为 .records
    sed -i 's/response\.content/response.records/g' "$file"
    sed -i 's/result\.content/result.records/g' "$file"
    sed -i 's/data\.content/data.records/g' "$file"

    # 替换 .totalElements 为 .total
    sed -i 's/response\.totalElements/response.total/g' "$file"
    sed -i 's/result\.totalElements/result.total/g' "$file"
    sed -i 's/data\.totalElements/data.total/g' "$file"

    # 替换 .number 为 .pageNo (注意：只替换分页相关的)
    sed -i 's/response\.number/response.pageNo/g' "$file"

    # 替换 .size 为 .pageSize (注意：只替换分页相关的)
    sed -i 's/response\.size/response.pageSize/g' "$file"

    echo "已处理: $file"
done

echo "完成！"
