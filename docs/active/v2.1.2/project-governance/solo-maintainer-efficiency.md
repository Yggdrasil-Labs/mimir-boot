---
id: solo-maintainer-efficiency
status: in-progress
owner: Yggdrasil Labs
created: 2026-08-02
updated: 2026-08-11
---

# 单人维护提效治理清单

## 目标

减少重复构建、重复刷新和零散依赖 PR，把可机械判断的文档事实交给 CI；不以降低更新及时性、
自动合并或重构发布补偿机制换取表面效率。

## GOV-011：CI 与 Sonar 重复完整构建

- **优先级**：P2
- **收益**：高
- **领域**：CI / quality
- **证据位置**：`.github/workflows/ci.yml`
- **问题**：普通非 Dependabot 变更先执行一次 `verify -Pci`，Sonar Job 随后再次执行
  `clean verify sonar:sonar -Pci`，同一 Reactor 被完整构建两次。
- **确认方向**：合并为一个 Build Job；完整构建始终执行一次，具备密钥时 Sonar 复用现有编译、
  测试和 JaCoCo 产物，仅执行分析目标。
- **边界**：Dependabot、fork PR 或密钥缺失只跳过 Sonar，不得跳过完整构建和报告上传；资格脚本只
  通过 `GITHUB_OUTPUT` 输出布尔结果，不打印密钥或配置值。
- **关闭条件**：单次工作流中 Maven 完整构建次数为 1；有密钥时 Sonar 成功，无密钥时 Build 成功。
- **状态**：已设计

## GOV-012：文档事实校验未进入 CI

- **优先级**：P2
- **收益**：中高
- **领域**：docs / CI
- **证据位置**：`docs/QUALITY_SCORE.md`、`docs/active/index.md`、现有文档健康检查流程
- **问题**：版本、Starter 清单、Reactor 模块数量和索引链接依赖人工同步，当前已存在事实漂移；
  Markdown lint 只能验证格式，不能验证项目事实。
- **确认方向**：建立仓库内可直接运行的只读文档检查，并接入 CI。
- **边界**：只校验可从 POM、目录和链接推导的客观事实；失败时报告差异，不自动改写文档。
- **关闭条件**：故意修改一个版本号、Starter 数量或内部链接时 CI 失败并指出不一致项；
  `2.1.2-SNAPSHOT`、目录 `v2.1.2` 和索引 `v2.1.2` 归一后相等，恢复后通过。
- **状态**：已设计

## GOV-013：GitHub Actions 更新仍产生零散 PR

- **优先级**：P2
- **收益**：中
- **领域**：dependencies / maintenance
- **证据位置**：`.github/dependabot.yml`、近期 Dependabot 合并记录
- **问题**：Maven 已按领域分组，但 GitHub Actions 更新仍逐项创建 PR，增加单人审阅切换成本。
- **确认方向**：Maven 与 GitHub Actions 均保持每周检查；保留 Maven 分组，新增 Actions 的
  minor/patch 分组。
- **边界**：不降低更新频率、不自动合并、不降低当前开放 PR 上限；major 更新保持独立审阅。
- **关闭条件**：同一周期内多个 Actions minor/patch 更新进入同一 PR，major 更新不被混入。
- **状态**：已设计

## GOV-014：默认强制刷新 Maven 远程元数据

- **优先级**：P2
- **收益**：中
- **领域**：build / release
- **证据位置**：`.github/workflows/ci.yml`、`.github/workflows/release.yml`
- **问题**：普通 CI、发布验证和发布命令普遍使用 `-U`，每次构建都强制检查远程更新，增加网络请求
  和构建波动，但项目使用固定发布版本且缺失依赖本就会正常下载。
- **确认方向**：从普通 CI 和发布流程移除默认 `-U`；仅在明确排查缓存或元数据问题时手动使用。
- **关闭条件**：工作流默认 Maven 命令不含 `-U`，依赖缓存为空时仍能完成正常解析和构建。
- **状态**：已设计

## GOV-015：Release 前置验证重复

- **优先级**：P2
- **收益**：中
- **领域**：release / maintenance
- **证据位置**：`.github/workflows/release.yml`
- **问题**：Release 依次执行跳过测试的构建和包含测试的最终构建，重复检出、环境初始化与 Maven package。
- **确认方向**：合并为一个前置验证 Job，一次完成格式、编译和单元测试；两个发布仓库继续独立构建、
  发布和补偿。
- **边界**：不共享 GPR/Central 发布产物；以 YAML 结构检查锁定 `needs`、`if`、权限、手动补偿选择、
  并发控制和已发布坐标不可覆盖策略，而不是只检查 Job 名称存在。
- **关闭条件**：发布前置 Maven 验证次数为 1；GPR、Central、GitHub Release 和开发版本回写仍可独立补偿。
- **状态**：已设计

## 明确不采纳

- 月度或季度 Dependabot：只会把变化累积成更大批次，不能减少总更新量。
- 依赖自动合并：宽 BOM 中存在未被当前 Starter 覆盖的依赖，仓库 CI 不能证明全部消费者兼容。
- 跨仓库发布产物复用：会增加部分发布失败后的恢复复杂度，收益不足以覆盖风险。
- 文档变更自动跳过 Maven：需要额外维护可靠的变更分类和分支保护规则，净收益偏低。
