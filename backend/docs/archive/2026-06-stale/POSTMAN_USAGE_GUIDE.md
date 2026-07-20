# Postman测试使用指南

## 📦 文件说明

本目录包含完整的Postman测试集合：

1. **ERP-API-Tests.postman_collection.json** - 完整的API测试集合
2. **ERP-API-Tests.postman_environment.json** - 测试环境配置
3. **API_TEST_SCRIPTS.md** - 详细的测试脚本文档

---

## 🚀 快速开始

### 步骤1: 导入到Postman

1. 打开Postman
2. 点击左上角 `Import`
3. 选择这两个JSON文件：
   - `ERP-API-Tests.postman_collection.json`
   - `ERP-API-Tests.postman_environment.json`
4. 点击 `Import`

### 步骤2: 配置环境

1. 点击右上角的环境下拉菜单
2. 选择 `ERP-API测试环境`
3. 点击眼睛图标查看/编辑环境变量
4. 根据实际情况修改：
   - `baseUrl`: 后端服务地址（默认：http://localhost:8080）
   - `username`: 登录用户名（默认：admin）
   - `password`: 登录密码（默认：password）

### 步骤3: 启动后端服务

```bash
cd E:/tuowei/python/erpServer

# 方式1: 跳过Maven版本检查
mvn spring-boot:run -Dmaven.enforcer.skip=true

# 方式2: 使用IDE直接运行
# 在IDEA或Eclipse中运行ErpServerApplication主类
```

### 步骤4: 运行测试

**方式1: 手动运行单个测试**
1. 展开测试集合
2. 先运行 `00-认证 > 登录获取Token`
3. 确认token已自动保存到环境变量
4. 按顺序运行其他测试

**方式2: 运行整个文件夹**
1. 右键点击文件夹（如 `01-库存模块`）
2. 选择 `Run folder`
3. 查看测试结果

**方式3: 使用Collection Runner批量运行**
1. 点击集合右侧的三个点
2. 选择 `Run collection`
3. 选择要运行的测试
4. 点击 `Run ERP系统API测试集合`

---

## 📋 测试集合结构

```
ERP系统API测试集合/
├── 00-认证/
│   └── 登录获取Token (自动保存token)
│
├── 01-库存模块/
│   ├── 库存调整/
│   │   ├── 创建库存调整单
│   │   ├── 查询调整单列表 (新增)
│   │   ├── 过账调整单
│   │   └── 取消调整单（应失败）
│   │
│   ├── 库存盘点/
│   │   ├── 创建盘点单
│   │   ├── 查询盘点列表 (新增)
│   │   └── 生成调整
│   │
│   └── 库存调拨/
│       ├── 创建调拨单
│       ├── 查询调拨列表 (新增)
│       └── 过账调拨单
│
├── 02-采购模块/
│   ├── 创建采购订单
│   ├── 提交审批
│   ├── 审批通过
│   ├── 关闭订单 (新增)
│   ├── 订单跟踪 (新增)
│   └── 导出订单列表 (新增)
│
├── 03-生产模块/
│   ├── 创建生产订单
│   ├── 下达生产
│   ├── 生产领料 (参数优化)
│   ├── 生产完工 (参数优化)
│   ├── 完工红冲 (参数优化)
│   └── 生产退料
│
└── 04-财务模块/
    ├── 创建费用单
    ├── 更新费用单
    ├── 费用过账
    ├── 查看生成的凭证
    ├── 查询凭证分录
    └── 费用红冲
```

---

## 🎯 测试要点

### 自动化特性

**1. Token自动管理**
- 登录后自动保存token
- 所有请求自动携带token
- Token失效时重新登录即可

**2. ID自动传递**
- 创建资源后自动保存ID到变量
- 后续操作自动使用保存的ID
- 无需手动复制粘贴

**3. 测试断言**
- 每个请求都有自动验证
- 检查状态码
- 检查响应数据结构
- 检查业务状态

### 新增功能测试标记

所有新增功能都标记了 `(新增)` 或 `(参数优化)`：

**库存模块新增**:
- ✅ 查询调整单列表
- ✅ 查询盘点列表
- ✅ 查询调拨列表
- ✅ 取消操作接口

**采购模块新增**:
- ✅ 关闭订单
- ✅ 订单跟踪
- ✅ 导出订单列表

**生产模块优化**:
- ✅ 领料接口参数优化（增加issueDate）
- ✅ 完工接口参数优化（增加批次、日期信息）
- ✅ 红冲接口参数优化

---

## 🔍 测试验证

### 成功标准

每个测试用例都包含自动验证：

**1. HTTP状态码验证**
```javascript
pm.test('状态码为200', function() {
    pm.response.to.have.status(200);
});
```

**2. 响应数据验证**
```javascript
pm.test('返回数据包含ID', function() {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data).to.have.property('id');
});
```

**3. 业务状态验证**
```javascript
pm.test('状态变为POSTED', function() {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data.status).to.eql('POSTED');
});
```

### 查看测试结果

- ✅ **绿色勾**: 测试通过
- ❌ **红色叉**: 测试失败
- 点击请求查看详细的响应数据
- 查看 `Test Results` 标签页看具体断言结果

---

## 🐛 故障排查

### 常见问题

**1. 401 Unauthorized错误**
- 原因: Token过期或未获取
- 解决: 重新运行 `00-认证 > 登录获取Token`

**2. 404 Not Found错误**
- 原因: 后端服务未启动或URL错误
- 解决: 
  - 检查后端是否运行：`curl http://localhost:8080/actuator/health`
  - 检查baseUrl环境变量是否正确

**3. 400/422 业务错误**
- 原因: 请求参数不符合业务规则
- 解决: 
  - 查看响应的错误消息
  - 检查请求体中的数据是否合法
  - 参考 `API_TEST_SCRIPTS.md` 查看正确的参数格式

**4. 500 Internal Server Error**
- 原因: 后端代码错误或数据库问题
- 解决:
  - 查看后端日志
  - 检查数据库连接
  - 确认测试数据是否存在（如productId、warehouseId等）

---

## 📊 高级用法

### 使用Newman进行CI/CD集成

Newman是Postman的命令行工具，可用于自动化测试：

```bash
# 安装Newman
npm install -g newman

# 运行测试集合
newman run ERP-API-Tests.postman_collection.json \
  -e ERP-API-Tests.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export results.json

# 运行特定文件夹
newman run ERP-API-Tests.postman_collection.json \
  -e ERP-API-Tests.postman_environment.json \
  --folder "01-库存模块"
```

### 生成HTML测试报告

```bash
# 安装newman-reporter-html
npm install -g newman-reporter-html

# 运行并生成HTML报告
newman run ERP-API-Tests.postman_collection.json \
  -e ERP-API-Tests.postman_environment.json \
  --reporters cli,html \
  --reporter-html-export report.html
```

### 在Jenkins中集成

```groovy
pipeline {
    stages {
        stage('API Testing') {
            steps {
                sh 'newman run ERP-API-Tests.postman_collection.json \
                    -e ERP-API-Tests.postman_environment.json \
                    --reporters cli,junit \
                    --reporter-junit-export results.xml'
            }
            post {
                always {
                    junit 'results.xml'
                }
            }
        }
    }
}
```

---

## 📝 自定义测试数据

如果你的测试环境中数据不同，可以修改环境变量：

**修改基础ID**:
1. 点击环境 > 编辑
2. 添加自定义变量：
   - `warehouseId`: 仓库ID（默认：1）
   - `supplierId`: 供应商ID（默认：1）
   - `productId`: 产品ID（默认：1）
   - `bomId`: BOM ID（默认：1）
3. 在请求体中使用 `{{warehouseId}}` 引用

**修改测试数据**:
1. 点击请求
2. 编辑 `Body` 标签页中的JSON
3. 保存修改

---

## 🎓 学习资源

- **Postman官方文档**: https://learning.postman.com/
- **Newman文档**: https://www.npmjs.com/package/newman
- **本项目API文档**: 查看 `API_TEST_SCRIPTS.md`

---

## ✅ 测试清单

运行测试前确认：

- [ ] 后端服务已启动
- [ ] 数据库已初始化
- [ ] 测试数据已准备（供应商、产品、仓库等）
- [ ] 环境变量配置正确
- [ ] 已成功获取token

开始测试：

- [ ] 运行认证测试
- [ ] 运行库存模块测试
- [ ] 运行采购模块测试
- [ ] 运行生产模块测试
- [ ] 运行财务模块测试

---

## 📞 支持

如有问题，请参考：
- `API_TEST_SCRIPTS.md` - 详细的测试脚本文档
- 项目README文档
- 后端日志文件

---

**文档版本**: v1.0  
**最后更新**: 2026-06-16  
**作者**: Claude Code
