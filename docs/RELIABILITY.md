---
updated: 2026-07-26
---

# 可靠性要求

本文档定义本仓库在构建、发布与运行时能力设计上的可靠性偏好。

## 1. 总体原则

- 可靠性优先于短期便利
- 默认行为应可预测
- 失败模式应清晰
- 发布链路应尽量可验证

## 2. 当前仓库中的可靠性事实

从现有流程可见：

- CI 会执行格式检查、编译、测试与覆盖率产物上传
- Dependabot 变更同样执行格式与 Maven `verify -Pci`；仅 SonarCloud 因 secrets 不可用而跳过
- SonarCloud 作为新代码质量门禁，具体执行纪律见 [`SONAR_QUALITY_DISCIPLINE.md`](./SONAR_QUALITY_DISCIPLINE.md)
- 发布通过 GitHub Actions + release-please 驱动
- Maven Wrapper 是首选构建入口

## 3. 发布可靠性

对本仓库来说，发布链路本身就是核心可靠性的一部分。

当前应视为稳定事实的关键点：

- 根 `pom.xml` 用 `revision` 统一版本
- 发布前需要 flatten 解析版本占位符
- Maven Central 正式版发布需要 GPG 签名
- GitHub Actions 中的 release workflow 同时覆盖校验、发布和版本推进
- 发布版本仅接受各数字段无前导零的稳定版 `MAJOR.MINOR.PATCH`，并在写入 Maven `revision` 前校验，避免 tag 或手动输入进入 shell 修改命令与 Bash 八进制歧义
- Release Please 仅由 `main` 的 push 自动驱动；自动 tag 仅接受本仓库中由 `github-actions[bot]` 创建且标题、分支均符合约定的已合并 Release Please PR；手动打 tag 恢复必须提供版本和目标提交 SHA
- tag 检查使用其最终 commit，已指向同一提交时幂等成功；tag 创建和开发版本回写都要求 `RELEASE_TOKEN`，确保后续 CI 能被触发
- 瞬态发布失败应优先使用 GitHub 的“重新运行失败 job”；它复用原始 ref/SHA。手动 `Release` 是确定性补偿入口，默认不产生外部副作用，必须显式选择要补偿的仓库发布、GitHub Release 或开发版本回写
- 发布与打 tag 使用按目标版本/提交分组的等待队列和 job 超时，不取消正在运行或等待的同目标恢复请求；同组最多保留 100 个等待项，按开始等待时间处理，但实际启动顺序不保证
- 开发版本回写从最新 `main` 收敛：已是目标 `-SNAPSHOT` 则幂等成功；仅允许从刚发布版本推进，其他版本明确失败而不覆盖
- `parent`、`bom`、发布 workflow 的改动会影响所有下游使用方

因此，凡是涉及以下内容的修改，都应谨慎：

- `distributionManagement`
- Maven Central 凭证与发布流程
- GPG 签名要求
- release / release-please workflow
- 版本推进策略

## 4. 对模块设计的要求

### 对 starter

- 默认配置应合理
- 关闭开关应明确
- 失败时应提供可理解的日志或异常
- 避免隐藏式副作用

### 对 `common`

- 公共模型语义必须稳定
- 任何破坏性变化都应先计划

### 对 `parent` / `bom`

- 修改会影响全局接入方式，应视为高风险
- 变更前必须评估兼容性与发布影响

## 5. 变更时的可靠性检查

进行结构性变更时，至少考虑：

- 是否影响已有模块构建
- 是否影响已有 starter 默认行为
- 是否影响版本发布与消费方式
- 是否更新了对应 README 与总览文档

## 6. 文档可靠性

文档本身也是可靠性的一部分。

如果使用者或智能体无法从仓库中判断：

- 哪个模块负责什么
- 哪个文档是权威来源
- 哪个计划还在进行中

那就是一种“认知层面的不可靠”。
