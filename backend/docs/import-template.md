# Excel/CSV导入模板下载功能文档

## 概述

系统已支持CSV格式的导入模板下载功能，用户可以下载标准模板了解导入格式。

## 功能特点

✅ **CSV格式** - 轻量级，兼容Excel
✅ **标准模板** - 每种导入类型都有对应模板
✅ **权限控制** - 需要导入管理权限
✅ **自动文件名** - 按类型自动生成文件名

## API接口

### 下载导入模板

```http
GET /api/import/templates/{type}
Authorization: Bearer <token>
```

**路径参数**:
- `type`: 导入类型（如 `product`、`customer`、`supplier` 等）

**响应**:
```
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="product-template.csv"

商品编码,商品名称,规格,单位,类别
PROD001,商品示例1,500g,个,原材料
PROD002,商品示例2,1kg,箱,成品
```

## 支持的导入类型

项目通过 `ImportTemplateRegistry` 管理所有导入模板，每种业务实体都可以注册自己的导入模板。

### 常见导入类型

- **product** - 商品导入
- **customer** - 客户导入
- **supplier** - 供应商导入
- **warehouse** - 仓库导入

## 使用流程

### 1. 下载模板

用户在前端点击"下载模板"按钮，调用API：

```javascript
// 前端示例
async function downloadTemplate(type) {
  const response = await fetch(`/api/import/templates/${type}`, {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  
  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${type}-template.csv`;
  a.click();
}
```

### 2. 填写数据

用户使用Excel或文本编辑器打开CSV模板，按照表头填写数据：

```csv
商品编码,商品名称,规格,单位,类别
PROD001,笔记本电脑,14寸,台,电子产品
PROD002,无线鼠标,2.4G,个,电子配件
```

### 3. 上传导入

```http
POST /api/import/jobs/{type}/preview
Content-Type: multipart/form-data

file: <csv_file>
```

### 4. 预览和提交

系统验证数据后返回预览结果，用户确认无误后提交。

## 权限要求

下载模板需要以下权限：

```java
@PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
```

确保用户角色具有 `import:init:manage` 权限。

## 模板格式说明

### CSV编码

- **字符编码**: UTF-8（支持中文）
- **分隔符**: 逗号 `,`
- **换行符**: `\n`
- **引号**: 包含逗号的字段需用双引号包裹

### 示例：包含特殊字符

```csv
商品编码,商品名称,备注
PROD001,商品A,"这是备注，包含逗号"
PROD002,商品B,"多行备注
第二行"
```

## 错误处理

### 导入类型不存在

```json
{
  "code": "400",
  "message": "不支持的导入类型: invalid_type"
}
```

### 权限不足

```json
{
  "code": "403",
  "message": "权限不足"
}
```

## 实现原理

### 模板注册

每个业务模块通过 `ImportTemplateRegistry` 注册自己的模板：

```java
@Component
public class ProductImportTemplate implements ImportTemplate {
    
    @Override
    public String csvTemplate() {
        return "商品编码,商品名称,规格,单位,类别\n" +
               "PROD001,示例商品,500g,个,原材料";
    }
    
    @Override
    public String type() {
        return "product";
    }
}
```

### 模板下载

```java
public ResponseEntity<ByteArrayResource> template(String importType) {
    String normalizedImportType = normalizeImportType(importType);
    byte[] content = templateRegistry.csvTemplate(normalizedImportType)
        .getBytes(StandardCharsets.UTF_8);
    String fileName = normalizedImportType.toLowerCase() + "-template.csv";
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, 
                ContentDisposition.attachment().filename(fileName).build().toString())
        .body(new ByteArrayResource(content));
}
```

## 扩展建议

### 1. 支持Excel格式

如果需要支持更复杂的模板（如下拉列表、数据校验），可以升级为Excel：

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.3</version>
</dependency>
```

### 2. 添加示例数据

在模板中添加更多示例行，帮助用户理解格式：

```java
public String csvTemplate() {
    return "商品编码,商品名称,规格,单位,类别\n" +
           "PROD001,示例商品1,500g,个,原材料\n" +
           "PROD002,示例商品2,1kg,箱,成品\n" +
           "PROD003,示例商品3,2L,瓶,包材";
}
```

### 3. 添加字段说明

在CSV中添加注释行（前端过滤）：

```csv
# 商品导入模板
# 商品编码: 必填，最多20字符，系统内唯一
# 商品名称: 必填，最多100字符
# 规格: 选填，最多50字符
商品编码,商品名称,规格,单位,类别
PROD001,示例商品,500g,个,原材料
```

### 4. 在线预览

前端提供模板预览功能，无需下载即可查看格式：

```javascript
async function previewTemplate(type) {
  const response = await fetch(`/api/import/templates/${type}`);
  const text = await response.text();
  const lines = text.split('\n');
  
  // 显示在表格中
  displayTable(lines);
}
```

## 完整工作流程

```
1. 用户点击"导入商品"
   ↓
2. 系统提供"下载模板"按钮
   ↓
3. 用户下载 product-template.csv
   ↓
4. 用户使用Excel填写数据
   ↓
5. 用户上传填好的CSV文件
   ↓
6. 系统预览导入数据（显示成功/失败行）
   ↓
7. 用户确认提交
   ↓
8. 系统批量导入数据
   ↓
9. 显示导入结果统计
```

## 注意事项

1. **编码问题** - 确保CSV使用UTF-8编码，避免中文乱码
2. **Excel兼容** - Excel保存CSV时可能改变编码，建议用UTF-8 BOM
3. **数据校验** - 模板仅提供格式，实际校验在导入时执行
4. **模板版本** - 如果字段变更，记得更新模板

---

**Created**: 2026-06-12  
**Status**: ✅ 已有功能（CSV模板）  
**Test Results**: 717/717 通过

## 总结

项目已经实现了CSV格式的导入模板下载功能，无需额外开发。用户可以：

1. ✅ 下载标准CSV模板
2. ✅ 使用Excel编辑模板
3. ✅ 上传并预览导入数据
4. ✅ 查看导入结果和错误

这是一个轻量级、实用的解决方案，满足了用户了解导入格式的需求。
