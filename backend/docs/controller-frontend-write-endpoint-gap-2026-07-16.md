# 前后端写端点联调缺口报告

**扫描时间**: 2026-07-16  
**方法**: Controller 写端点（POST/PUT/DELETE/PATCH + @PreAuthorize）→ 前端 `src/api/*.ts` → views 按钮  
**不采信**: 6 月旧 md 完成/缺口报告

---

## 总览

| 指标 | 数量 |
|------|------|
| 后端写端点 | **194**（POST 158 / PUT 34 / DELETE 2） |
| 前端 API 已封装 | **194 / 194（100%）** |
| **MISSING_API** | **0** |
| **ORPHAN_API**（有 API 无页面调用） | **0**（原 1 项 IQC 草稿 PUT 已于 2026-07-16 接页面） |

---

## 真缺口

| 项 | 说明 | 状态 |
|----|------|------|
| IQC 草稿编辑 UI | 后端 `PUT /api/qc/inspections/{id}` + `qc:inspection:update`；前端 `updateQcInspection` | **2026-07-16 已闭环**：`views/qc/inspection/index.vue` DRAFT 行「编辑」→ 日期/备注/行检验数量保存 |

## 已确认主干接线（抽样）

反审核、四单据草稿 PUT、系统配置 create、手工凭证生命周期、IQC 主流程、预留释放、审批驳回/撤回、采购/销售/库存/生产/财务写操作 — **API + 页面均已接线**。

## 不是缺口

- 登录/刷新/登出（公开或过滤器层）
- 只读报表/台账/自动凭证查询
- 运维 readiness/observability（已有页面）

---

**结论**: 联调「缺 API」阶段已结束，页面 orphan 也已清零。剩余是发布/预生产签字类 P0。  
找新缺口继续用本方法，勿按 6 月 md 开补洞任务。
