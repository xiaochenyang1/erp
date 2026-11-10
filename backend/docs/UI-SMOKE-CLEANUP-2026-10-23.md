**2026-10-23：浏览器 CI 退出修复及后续交付顺序**

浏览器检查在 Linux 上完成全部页面和业务断言后仍不退出，导致 30 分钟 CI 超时。隔离 Node 22.12.0 / Debian 复现发现，`npm → sh → node` 的第三层进程在旧清理后仍存活；npm 已触发 `exit`，但后代仍持有 stdout/stderr，导致 `close` 没有发生。

修复后，每个 Unix 服务在独立进程组中启动，清理向整个所属进程组发送 TERM，等待进程组消失及输出管道关闭；超过宽限时间则发送 KILL，并在清理期限内报告结果。父进程提前退出时仍清理该组，不影响其他服务的进程组。Windows 保留 `taskkill /T /F`，增加命令超时及管道关闭等待。

清理逐项执行，即使一个服务清理失败也会尝试其余服务。`target/ui-smoke-cleanup.json` 记录服务 PID、是否强制结束、管道是否关闭、耗时、候选提交及工作区状态；失败返回非零退出码。CI 归档此报告，本地候选入口要求三项服务清理全部成功且版本对应。

开发期验证：macOS 和 Linux 的进程/CDP 回归分别 16/16 通过，覆盖多层后代、宽限退出、忽略 TERM、父进程先退出、不同进程组隔离及启动失败。Linux 的真实 npm 复现场景也已验证修复后无遗留进程。开发期证据位于仓库外 `erp-validation/2026-10-23-ui-cleanup/`。

完整候选结果以同一提交实际生成的 Actions 和独立证据目录为准。运行：

```bash
cd backend
PWSH="$HOME/.local/bin/pwsh" caffeinate -is ./scripts/release-candidate-local.sh \
  /absolute/new/evidence-directory
```

后续顺序仍是：完整候选检查与代码评审 → 独立预生产部署 → 真实期初数据/岗位/业务验收 → 目标环境运维验收和试运行 → 根据业务反馈安排下一期模块。

预生产地址、部署方式及私有配置尚待绑定。沿用 [预生产交接](PREPROD-HANDOFF-2026-10-21.md) 和 [业务验收清单](business-readiness-checklist.md)，技术报告不替代财务/质检签字和实际上线决定。早期缺口文档中的“多币种未接通”“监控未实现”等历史描述不再代表当前代码。
