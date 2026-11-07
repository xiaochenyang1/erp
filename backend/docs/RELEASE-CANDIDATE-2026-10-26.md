**2026-10-26：前端制品归档与候选验证**

本轮按项目盘点的第一步收尾发布工具。业务实现沿用上一候选；新增前端制品构建、上传后下载复验，以及前后端/浏览器证据 90 天保留。正式交付仍须独立预生产与业务验收。

前端入口为 `npm run release:build`、`npm run release:verify` 和 `npm run test:release`。
制品包含 `dist/`、`build.log`、`manifest.json`。构建要求干净候选；验证检查候选 SHA、逐文件长度/摘要、清单完整性，并可通过 `--expected-manifest-sha256` 绑定独立保存的原始清单摘要。详见 [前端发布说明](../../frontend/README.md)。

Frontend Verify 在类型、lint、单测、契约和制品测试通过后重新构建；生产依赖审计通过才上传。随后按本轮 artifact ID 下载到独立临时目录，使用相同候选 SHA 和上传前的清单摘要再次验证。后端与 UI 工作流的保留期同步延长至 90 天；此设置不延长已有制品的期限。

开发期验证已执行前端制品测试 12 项、后端 CI 配置测试 2 项，均通过。完整候选结论必须读取相应 SHA 的实际 CI 与独立归档，不能将本段开发期测试视作全量通过。

在仓库根目录，以新的仓库外目录运行全部本机检查：

```bash
backend/scripts/release-candidate-local.sh /absolute/new/candidate-evidence
```

入口会检查后端发布、前端质量及制品、监控、浏览器、扩展 API、数据权限和 F/Q 技术预检；结果写入 `summary.json`，只有全部通过才为 `PASSED`。前端制品位于 `frontend/`，其清单摘要进入 `artifact-sha256.json`。失败目录保留诊断，复跑须使用新目录。

随后可对已通过的候选运行隔离生产配置/数据库恢复演练：

```bash
cd backend
node scripts/production-rehearsal.mjs /absolute/accepted/candidate-evidence /absolute/new/recovery-evidence
```

历史候选 `da6e371` 的 GitHub 证据保全目录为仓库外的
`/Users/xiao/Desktop/python/erp-release-evidence/2026-10-26-existing-da6e371/`。
以 `archive-index.json` 实际存在且各 ZIP 摘要匹配 GitHub 元数据为保全完成依据；下载中的文件不能作为完整归档。后续候选应另建目录。

接下来按 [预生产交接](PREPROD-HANDOFF-2026-10-21.md) 绑定目标地址、私有配置文件路径、验收账套、岗位账号、真实期初和负责人。已有 [F/Q 执行包](P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-10-16.md) 可直接复用。未提供这些信息时，本机技术报告不能替代目标环境部署、财务/质检签字或正式 GO；不创建正式 release tag。
