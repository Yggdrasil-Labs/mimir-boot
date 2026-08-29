---
id: arch-technical-debt-remediation
status: draft
owner: YoungerYang-Y
tags: [architecture, technical-debt, governance]
created: 2026-08-28
verified: null
---

# 技术债修复长期约束同步 RFC

## 背景与动机

T1–T7 已在 `feature/technical-debt-remediation` 完成代码、测试、构建与发布模型修复；每个任务的主实现提交与跨任务补充提交均在下表和计划台账中显式追溯。本 RFC 只为 T9 提供长期文档同步的边界：把已经落地且已经验证的事实，按权威文档的职责写回 `ARCHITECTURE.md`、`docs/SECURITY.md`、`docs/RELIABILITY.md` 和 `docs/design-docs/module-boundaries.md`。

本 RFC 是状态为 `draft` 的架构约束同步提案，不是对长期约束的直接修改，也不是对 T9 的预授权。它不改变模块依赖方向、公开 API、发布结构或安全策略；在“批准记录”完成前，T9 不得据此修改四个长期文档，也不得把任何残余风险写成已关闭。

### 已验证事实基线

以下事实来自当前任务链中的实现提交和执行记录，T9 只能在对应来源仍与代码一致时同步：

| Task | 提交 | 可同步的事实摘要 | 当前边界 |
|------|------|------------------|----------|
| T1 | `29e645b` | 公共异常响应保留中文消息；BindException 继续使用纯消息列表；分页结果构造路径校验 null/非法值；分页请求在偏移计算前纠正边界；新增 nullable 枚举查询而不改变既有 fallback。 | 既有 `fromCode`、响应形状和可用 API 不得被文档改写为新语义。 |
| T2 | `3a97c62`、`e45e457` | 日志脱敏覆盖 JSON、编码键和私钥/访问密钥；公钥保持可见；规则与 replacement 以不可变快照原子刷新；非 Logback 绑定降级并告警；MDC 单键/批量语义保持既有约定。 | 历史单次 benchmark 输出不固化为当前性能事实；T9 必须在当前 HEAD 按固定参数独立运行三次，并验证算术均值不超过 20µs。 |
| T3 | `bf493b6`、`2dd1b83`、`4e428e6` | Dubbo 支持依赖以完整快照发布；异步回调恢复 trace scope；关闭失败不覆盖业务结果；Feign 支持地址回退和非敏感多值请求头；框架内部改用调用级 Hook API。 | 旧 `RpcTracerBridge.extract`、`RpcExecutionTemplate` Bean 和四个旧 Hook 直调方法仍保留；TD-013、TD-023 不得关闭。 |
| T4 | `5e71294`、`2dd1b83` | Nacos 仅在当前或旧加密前缀实际绑定时处理 ENC；遗留 AES/ECB 顶层调用每次产生一次迁移告警；无前缀应用不被误触发。 | 遗留 API 仍是迁移边界，不得被描述为推荐的新安全方案。 |
| T5 | `c6006b2`、`4e428e6` | MyBatis 提供应用级 AAD v2 读能力和默认关闭的渐进写入开关；v1 密文及旧 Handler/Bean 保持可读可用；Mapper 有效包查询与实际扫描一致；SQL、审计输出脱敏。 | AAD 只绑定应用 context；同 context 的跨字段/跨记录调换仍可能解密，TD-016 不得关闭。v2 写入后回退下限必须支持 v2 且使用相同 context。 |
| T6 | `bb651cd`、`4e428e6` | 测试 starter 去除数据库、副作用和固定应用名等危险隐式默认值；弃用 API 和公开成员保持兼容；日志工具和随机测试标识收敛。 | 下游依赖隐式测试默认值者需显式配置，这属于迁移说明，不得恢复危险默认值。 |
| T7 | `a5dba88` | 普通构建默认跳过 GPG 签名、正式发布显式启用；formatter 固定为 1.23.0；BOM 坐标、隔离 consumer、动态 revision、报告门禁和 Apache-2.0 LICENSE 已验证；15 个 Reactor 模块 clean verify、CI preflight、consumer 与签名 fixture 均通过。 | 发布凭证、tag、发布结构和正式部署仍受既有流程约束；签名通过 fixture 不等于已执行生产发布。 |

补充提交不是新的任务主提交，也不改变各任务的独占提交边界：`2dd1b83` 同时补强 T3 Feign 与 T4 Nacos，`4e428e6` 补强 T3 异步端到端、T5 rollout fixture 和 T6 日志测试工具，`e45e457` 补强 T2 脱敏实现与三样本 benchmark。它们没有单一 `Task-ID` trailer，T9 必须以本表、计划台账和对应测试文件共同追溯，不能只依据主提交 SHA。

## 设计原则

1. **只同步事实，不改变约束。** RFC 授权的唯一动作是将 T1–T7 已验证的实现、配置、版本和验证事实写入相应长期文档；不得借文档同步新增规则或重新设计架构。
2. **权威来源分层。** 运行时代码和测试是实现事实来源；`ARCHITECTURE.md`、`docs/SECURITY.md`、`docs/RELIABILITY.md`、`docs/design-docs/module-boundaries.md` 各自只接收属于自身职责的信息，`docs/design-docs/core-beliefs.md` 仅作为只读长期信条约束，不在 T9 修改；需求级细节继续留在 `docs/active/v2.2.1/technical-debt-remediation/`。
3. **兼容性优先。** 文字必须同时说明新增能力、保留的旧入口、默认值和迁移条件，不得把新增 API 或可选开关写成强制替换。
4. **残余债务可见。** TD-013、TD-016、TD-023 必须继续以“残余风险/未关闭”表述，并保留触发条件和边界；通过 T3/T5 的测试不代表这些债务消失。
5. **批准是解锁条件。** RFC 保持 `draft`，直到用户或架构负责人在固定字段中提供批准人、角色、带时区的 ISO-8601 批准时间、批准依据和可追溯证据；批准前 T9 必须保持 blocked，不得假设口头或历史意见构成批准。批准时间必须早于 T9 的 `Dispatch At`。
6. **证据可复核。** 每一条同步内容都应能回指 T1–T7 提交、测试/脚本或现有长期文档；证据不足、代码与文档冲突或验证失败时停止同步并保留 `draft`。

## 标准做法

### 长期文档逐项同步矩阵

下表定义每个长期文档允许接收的事实、禁止扩大范围、兼容性影响、验证方式和回退方式。T9 只能在批准后按此矩阵实施；矩阵本身不授权修改文件。

#### `ARCHITECTURE.md`

| 项目 | 约束 |
|------|------|
| 允许同步事实 | 同步 Java 17、Spring Boot 3.3.13、Maven 多模块和现有 parent/BOM/common/starters 职责；补充 T1–T6 已验证的能力仍落在既有模块（公共模型、日志、RPC、Nacos、MyBatis、测试 starter）；补充 T7 的 formatter、BOM/revision 和质量门禁仅作为仓库工程事实。 |
| 禁止扩大范围 | 不新增或删除模块，不改变 Starter → Common、Dubbo/Feign → RPC Core 等依赖方向；不把 RFC 变成新 Starter、运行时架构、依赖升级或公开 API 设计；不重画与现有代码不一致的分层图。 |
| 兼容性影响 | 该文件只描述已经存在的模块职责和版本事实，不产生运行时影响。新增 nullable 查询、应用级 AAD、RPC 快照等必须注明旧入口仍可用，不能暗示接入方必须迁移。 |
| 验证 | 以实现提交和测试记录核对模块路径、POM、自动配置入口和依赖树；执行 T9 验证命令集（文档健康、Markdown lint、目标路径存在性、RFC/index 引用和 `git diff --check`），并检查 diff 只包含获批的事实性文字；所有命令退出码均为 0 才算通过。 |
| 回退 | 若代码或有效依赖树与文字不一致，停止 T9 的该文件同步，保留原文件并将 RFC 维持 `draft`；若多个目标文档已在同一内容提交中，按失败文件生成反向提交并保留已通过文件，同时不回退 T1–T7 代码。 |

#### `docs/SECURITY.md`

| 项目 | 约束 |
|------|------|
| 允许同步事实 | 说明 T2 的 JSON/编码键/私钥与访问密钥脱敏边界、公钥可见和快照刷新；说明 T4 的 Nacos 前缀门控及遗留 ECB 迁移告警；说明 T5 的应用级 AAD、同 context 边界、默认关闭 v2 写入和 v1 兼容读取；如引用发布安全，只同步 T7 的显式 GPG 签名与凭证不入库要求。 |
| 禁止扩大范围 | 不新增认证、授权、token 传播、XSS、上传限制或字段/记录级完整性策略；不把应用级 AAD 宣称为字段级/记录级保护；不把脱敏覆盖范围写成“所有敏感数据”；不把遗留 ECB API 写成推荐方案；不改变任何安全默认值。 |
| 兼容性影响 | 日志脱敏对既有敏感字段只增加保护，不改变 opt-in 属性；Nacos 无加密前缀时不再误处理普通 ENC；旧密文仍按既有路径读取；v2 密文要求相同 application context，启用写入前必须完成全量升级和列容量预检。 |
| 验证 | 逐条回看 `SensitiveDataPatternTest`、`SensitiveDataConverterTest`、Nacos 配置/告警测试和 `CryptoUtilsTest`/`MybatisCryptoRolloutContractTest`；检查文档不包含真实凭证，并用 `rg` 验证 TD-013、TD-016、TD-023 仍标为残余或未关闭。 |
| 回退 | 发现安全边界表述超出代码证据时，立即删除越界段落并保持原安全文档；若 v2 数据已写入，回退只能选择支持 v2 且 context 相同的版本，不得回到仅支持 v1 的版本。 |

#### `docs/RELIABILITY.md`

| 项目 | 约束 |
|------|------|
| 允许同步事实 | 同步 T7 的普通 `clean verify`、独立 CI preflight、测试/JaCoCo 报告分离、effective POM、隔离 BOM consumer、签名 fixture 和动态 revision 事实；同步 T2 的基准方法与“发布仍需三次独立运行”的待验收条件；同步 T5 的 v2 rollout 顺序、列容量预检和回退下限。 |
| 禁止扩大范围 | 不宣称已发布到 Maven Central/GitHub Packages，不修改 release-please、tag、凭证或 GPG 触发结构；不把一次基准结果写成性能达标，不把测试 fixture 写成生产实例/容量证明，不以固定测试数量替代报告门禁。 |
| 兼容性影响 | 普通构建无需用户私钥，正式发布仍需显式签名；consumer 继续通过 BOM 获取版本；应用级 AAD 的滚动升级须先让所有实例可读，再在满足容量预检后统一开写；v2 写入后允许的最低回退版本必须支持相同 context。 |
| 验证 | 复核 `./mvnw clean verify`、`scripts/ci-preflight.sh`、`scripts/verify-build-model.py`、`scripts/test-suite-consumer.sh`、`scripts/verify-release-signing.sh` 的已有 PASS 记录；T9 另行连续运行三次 benchmark，记录 baseline/candidate/delta、均值和最大值，均值不超过 20µs 才能作为发布证据。 |
| 回退 | 普通构建失败时仅回退文档同步；发布签名失败必须阻断发布并保留失败证据；v2 rollout 失败时停留在同 context、v1 写入且 v2 可读的状态，不能使用不支持 v2 的旧版本覆盖已写入数据。 |

#### `docs/design-docs/module-boundaries.md`

| 项目 | 约束 |
|------|------|
| 允许同步事实 | 说明 T1 的稳定公共模型仍由 `mimir-boot-common` 承担；T2/T4/T5/T6 的运行时能力继续落在各自既有 starter；T3 的通用抽象在 `starter-rpc-core`、专用行为在 Dubbo/Feign；T7 的 parent/BOM 仍分别负责构建与版本，且发布规则属于仓库级工程边界。 |
| 禁止扩大范围 | 不把业务逻辑、运行时自动配置或单 starter 内部实现搬入 common；不新增隐式 starter 环依赖；不让 BOM/parent 承担运行时逻辑；不把发布规则散落到 starter README；不借此 RFC 新建 starter 或改变能力落位。 |
| 兼容性影响 | 仅补充既有落位说明，无二进制、配置或运行时影响。公共模型、旧 RPC SPI、旧 Handler/Bean 和 starter 接入方式仍按 T1–T6 的兼容约束描述。 |
| 验证 | 对照 `ARCHITECTURE.md` 的分层图、各模块 POM 与自动配置；执行 `mise exec java@17 -- ./mvnw -q -pl :mimir-boot-common dependency:tree -Dscope=compile` 并确认输出不含 `mimir-boot-starter-`，再执行 `mise exec node@22 -- node /mnt/c/Users/YangYang/.codex/skills/docs-evolve/scripts/lint-docs.mjs`、`mise exec node@22 -- env npm_config_cache=/tmp/mimir-boot-markdownlint-cache npx --yes markdownlint-cli2@0.23.2 "docs/**/*.md" "*.md"`、目标 Markdown 文件的 `test -f` 循环和 `git diff --check -- ARCHITECTURE.md docs/SECURITY.md docs/RELIABILITY.md docs/design-docs/core-beliefs.md docs/design-docs/module-boundaries.md`；Maven 依赖方向、目标路径和文档检查均通过才算完成。 |
| 回退 | 若模块职责或依赖图无法由当前代码证明，停止该文件更新并恢复原文；不得通过修改代码、POM 或聚合注册来“补齐”RFC 的文字结论。 |

### T9 消费与治理文档范围矩阵

T9 除四个长期约束文档外，还会产生消费文档、发布说明、索引和技术债台账。下表逐一列出允许修改的路径；这些文件不能借同步事实之名改变运行时语义、需求契约或发布结构。长期约束文件仍必须同时满足上方对应矩阵。

| 文件集合 | 允许同步事实 | 禁止扩大范围 | 验证与回退 |
|---------|-------------|-------------|-----------|
| `README.md`、`AGENTS.md`、`docs/index.md`、`docs/active/v2.2.1/index.md`、`docs/active/v2.2.1/technical-debt-remediation/index.md`、`docs/active/v2.2.1/technical-debt-remediation/spec.md`、`docs/active/v2.2.1/technical-debt-remediation/design.md` | 版本、wrapper/命令、可解析链接、任务与需求状态；只引用已验证证据。 | 不修改需求 Scenario、IC、签名或架构长期约束；未通过最终门禁不得提前发布 `verified`/`shipped`。 | `lint-docs.mjs`、Markdownlint、已知断链精确扫描、目标路径 `test -e` 与 reviewer 链接复核；失败时保留原状态文件与 draft RFC。 |
| `mimir-boot-bom/README.md`、`mimir-boot-starters/mimir-boot-starter-log/README.md`、`mimir-boot-starters/mimir-boot-starter-rpc-core/README.md`、`mimir-boot-starters/mimir-boot-starter-nacos/README.md`、`mimir-boot-starters/mimir-boot-starter-mybatis/README.md`、`mimir-boot-starters/mimir-boot-starter-test/README.md` | 实际 Maven 坐标、BOM 管理版本、配置键、API/包名、默认值、迁移与回退提示。 | 不新增配置语义、不删除兼容 API、不把 fixture/测试输出写成生产保证、不恢复危险默认值。 | `scripts/verify-build-model.py`、consumer、定向测试、版本/配置 `rg` 扫描、目标路径检查和 reviewer 链接复核；失败时只回退文档。 |
| `docs/PRODUCT_SENSE.md`、`docs/active/v2.2.1/release.md`、`docs/active/tech-debt-tracker.md` | 已验证能力、rollout/rollback 矩阵、关闭项的提交与测试证据、TD-013/016/023 残余状态。 | 不宣称生产发布、不删除残余债务、不用单次 benchmark 或 fixture 证明容量/性能。 | consumer、签名脚本、三次 benchmark、tracker 残余与 SHA 扫描；失败时保留原发布和台账状态。 |
| `docs/design-docs/arch-technical-debt-remediation.md`、`docs/design-docs/index.md` | RFC/index 的 draft→verified 状态、批准证据和引用路径。 | 未获批不得改为 verified；不得在 T9 content 提交中同时伪造或修改批准记录。 | 批准记录字段精确扫描、带时区时间比较、已知断链扫描、frontmatter `rg`、目标路径检查和 `git diff-tree` 复核；失败时 RFC 保持 draft。 |

### T9 验证命令与通过标准

T9 只能使用以下命令集作为文档同步门禁；`lint-docs.mjs` 不覆盖 `docs/design-docs/`，因此 RFC、索引和目标文件引用必须由后续命令单独验证。一次性 T9 范围不新增仓库脚本：已知断链用 `rg` 精确扫描，目标文件存在性用 `test -e`，其余新增或修改链接由 reviewer 对 diff 逐项复核：

```bash
set -euo pipefail
mise exec node@22 -- node /mnt/c/Users/YangYang/.codex/skills/docs-evolve/scripts/lint-docs.mjs
mise exec node@22 -- env npm_config_cache=/tmp/mimir-boot-markdownlint-cache npx --yes markdownlint-cli2@0.23.2 "docs/**/*.md" "*.md"
! rg -n '\]\(\./generated/?\)' docs/index.md
! rg -n '\]\(\.\./(README|mimir-boot-common/README|mimir-boot-parent/README)\.md\)' \
  mimir-boot-starters/mimir-boot-starter-log/README.md
test "$(rg -c '^批准(人|角色|时间|依据|证据): 待用户/架构负责人批准' \
  docs/design-docs/arch-technical-debt-remediation.md)" -eq 5
rg -n '^status: draft$' docs/design-docs/arch-technical-debt-remediation.md
rg -n '^- \*\*Dispatch At:\*\* null$' docs/active/v2.2.1/technical-debt-remediation/plan.md
for path in \
  README.md AGENTS.md docs/index.md \
  mimir-boot-bom/README.md \
  mimir-boot-starters/mimir-boot-starter-log/README.md \
  mimir-boot-starters/mimir-boot-starter-rpc-core/README.md \
  mimir-boot-starters/mimir-boot-starter-nacos/README.md \
  mimir-boot-starters/mimir-boot-starter-mybatis/README.md \
  mimir-boot-starters/mimir-boot-starter-test/README.md \
  docs/PRODUCT_SENSE.md docs/SECURITY.md docs/RELIABILITY.md \
  docs/active/v2.2.1/release.md docs/active/tech-debt-tracker.md \
  ARCHITECTURE.md \
  docs/design-docs/core-beliefs.md \
  docs/design-docs/module-boundaries.md \
  docs/design-docs/_template.md \
  docs/design-docs/arch-technical-debt-remediation.md \
  docs/design-docs/index.md \
  docs/active/v2.2.1/technical-debt-remediation/plan.md \
  docs/active/v2.2.1/technical-debt-remediation/design.md; do
  test -f "$path"
done
git diff --check -- \
  ARCHITECTURE.md docs/SECURITY.md docs/RELIABILITY.md \
  docs/design-docs/core-beliefs.md docs/design-docs/module-boundaries.md \
  docs/design-docs/_template.md docs/design-docs/arch-technical-debt-remediation.md \
  docs/design-docs/index.md docs/active/v2.2.1/technical-debt-remediation/plan.md \
  docs/active/v2.2.1/technical-debt-remediation/design.md
tree="$(mise exec java@17 -- ./mvnw -q -pl :mimir-boot-common dependency:tree -Dscope=compile)"
! printf '%s\n' "$tree" | rg -q 'mimir-boot-starter-'
build_model_cache="$(mktemp -d -t mimir-build-model-m2.XXXXXX)"
MIMIR_BUILD_MODEL_M2="$build_model_cache" mise exec java@17 -- mise exec python@3 -- python scripts/verify-build-model.py
rm -rf "$build_model_cache"
mise exec java@17 -- bash scripts/test-suite-consumer.sh
mise exec java@17 -- bash scripts/verify-release-signing.sh --preheat
rg -n 'fromCodeOrNull|mimir\.boot\.mybatis\.crypto-v2-write-enabled|2\.3\.6|8\.11\.0|3\.3\.13|3\.9\.16' \
  README.md AGENTS.md ARCHITECTURE.md docs/PRODUCT_SENSE.md docs/SECURITY.md docs/RELIABILITY.md \
  mimir-boot-bom/README.md mimir-boot-starters/*/README.md docs/active/v2.2.1/release.md
rg -n '测试 starter|fromCodeOrNull|旧 RPC SPI|legacy ECB|v2 密文|回退下限' docs/active/v2.2.1/release.md
rg -n '^\| TD-(013|016|023) \|' docs/active/tech-debt-tracker.md
rg -n '^status: draft$|arch-technical-debt-remediation|待用户/架构负责人批准' \
  docs/design-docs/arch-technical-debt-remediation.md docs/design-docs/index.md
for run in 1 2 3; do
  mise exec java@17 -- ./mvnw -pl :mimir-boot-starter-log -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SensitiveDataConverterBenchmark test \
    | tee "/tmp/mimir-boot-benchmark-${run}.log"
done
T9_CONTENT_SHA="${T9_CONTENT_SHA:?set to the verified T9 content SHA}"
git diff-tree --no-commit-id --name-status -r "$T9_CONTENT_SHA"
! git diff-tree --no-commit-id --name-status -r "$T9_CONTENT_SHA" |
  rg -q '^D[[:space:]]'
! git diff-tree --no-commit-id --name-only -r "$T9_CONTENT_SHA" |
  rg -q '^(docs/active/v2\.2\.1/technical-debt-remediation/(spec|design|index)\.md|docs/active/v2\.2\.1/index\.md)$'
# T9 dispatch 后将上面的 draft 检查切换为字段与时间顺序检查：
# ! rg -n '待用户/架构负责人批准' docs/design-docs/arch-technical-debt-remediation.md
# test "$(rg -c '^批准(人|角色|时间|依据|证据): .+$' docs/design-docs/arch-technical-debt-remediation.md)" -eq 5
# approved_at="$(sed -n 's/^批准时间: //p' docs/design-docs/arch-technical-debt-remediation.md)"
# dispatch_at="$(sed -n 's/^- \*\*Dispatch At:\*\* //p' docs/active/v2.2.1/technical-debt-remediation/plan.md)"
# test "$(printf '%s\n%s\n' "$approved_at" "$dispatch_at" |
#   rg -c '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(Z|[+-][0-9]{2}:[0-9]{2})$')" -eq 2
# test "$(date -d "$approved_at" +%s)" -lt "$(date -d "$dispatch_at" +%s)"
# T9 status 提交完成后还需执行：
# actual="$(git diff-tree --no-commit-id --name-only -r "$T9_STATUS_SHA" | sort)"
# expected="$(printf '%s\n' docs/active/v2.2.1/index.md docs/active/v2.2.1/technical-debt-remediation/design.md docs/active/v2.2.1/technical-debt-remediation/index.md docs/active/v2.2.1/technical-debt-remediation/spec.md | sort)"
# test "$actual" = "$expected"
```

所有命令必须退出码为 0；Maven 依赖树若出现 `mimir-boot-starter-`、目标路径缺失、版本/配置扫描不一致、consumer/签名失败、Markdown/link 检查报错或 diff 空白错误均阻断 T9，并保持 RFC 为 `draft`。批准前用五项占位字段和 `Dispatch At: null` 明确证明当前仍锁定；获批后必须扫描真实字段、验证两个时间均为带时区的 ISO-8601，并确认批准早于 dispatch，同时由 reviewer 人工核对批准身份、依据和证据。T9 content 的完整 `name-status` 输出必须逐项对照上方范围矩阵；一次性批准和提交范围检查不新增仓库脚本。benchmark 仍须在 T9 现场连续独立运行三次并记录聚合结果，不能用历史单次输出替代。

### 执行与批准门禁

1. T9 先从 T1–T7 提交和测试记录重建事实清单，再逐文件产生最小 diff；任何新增事实若不能回指代码、测试、effective POM 或既有发布脚本，立即停止。
2. T9 不得在同一提交中修改本 RFC 的批准字段、四个长期文档、计划状态和发布记录；批准记录必须先于 T9 dispatch 的新鲜验证证据存在，并通过字段扫描、时间比较和 reviewer 对批准证据的人工核对。
3. 批准前只允许维护本 RFC 和设计索引的 `draft` 记录。未批准时，T9 应标记为 `blocked`，不能将 draft 解读为授权，不能把 RFC 或索引改为 `verified`。
4. 获批后仍须执行 T9 的全量文档健康、Markdown/link 检查、版本/配置扫描和发布验收；任何失败都只回退文档同步，不回退已验证的 T1–T7 实现。

## 反模式

| 反模式 | 为什么禁止 |
|--------|-----------|
| 把 T8 当作已批准的架构变更，直接修改四个长期文档 | T8 只产生 draft RFC；长期约束修改需要用户/架构负责人明确批准。 |
| 将 TD-013、TD-016 或 TD-023 标记为“已关闭” | T3/T5 只验证了有限修复边界，旧自定义 Bridge、应用级 AAD 的字段/记录级完整性和旧 Hook 直调入口仍有残余风险。 |
| 把应用级 AAD 写成字段级或记录级防篡改 | 这会虚构未实现的安全保证，并误导迁移和回退决策。 |
| 把一次超过 20µs 的历史基准输出写成性能达标 | 历史单次运行不能代表当前实现；发布证据必须是当前 HEAD 的三次独立运行聚合结果。 |
| 把测试 fixture、file 镜像或签名 fixture 写成生产发布结果 | fixture 只证明隔离验证路径，不能证明 Maven Central、GitHub Packages 或生产实例已部署。 |
| 删除旧 API、旧 Bean、v1 密文读取或旧配置绑定 | T1–T6 的兼容基线要求这些入口继续源码/二进制可用或继续可读。 |
| 通过 RFC 引入新依赖、新 Starter、循环依赖或 parent/BOM 运行时逻辑 | 这超出“同步已验证事实”的范围，也违反模块边界。 |
| 修改默认安全语义、发布凭证流程、tag/release 结构或依赖方向 | 这些是长期高风险约束，不属于 T8 的事实性文档同步。 |
| 伪造批准人、批准时间、批准依据，或将批准字段留空后解锁 T9 | 批准是明确的治理门禁；缺少真实批准证据时 T9 必须保持 blocked。 |

## 适用范围

本 RFC 只适用于 v2.2.1 技术债修复的 T1–T7 已验证事实，以及在批准后由 T9 执行的长期约束、消费与治理文档的最小事实性同步。长期约束目标为：

- `ARCHITECTURE.md`
- `docs/SECURITY.md`
- `docs/RELIABILITY.md`
- `docs/design-docs/module-boundaries.md`

消费与治理目标由“T9 消费与治理文档范围矩阵”逐一列出，包括仓库入口、BOM/Starter README、产品/发布/技术债台账、需求与设计索引，以及本 RFC/index；任何未列出的文件均不在 T9 范围内。

它不适用于新增功能、依赖升级、模块重划分、公共 API 设计、安全策略重写、生产发布或 MyBatis 字段/记录级完整性设计。需求级行为、测试证据、迁移矩阵和剩余债务仍以 `docs/active/v2.2.1/technical-debt-remediation/` 下的 `spec.md`、`design.md`、`plan.md` 和 `release.md` 为准；若本 RFC 与代码冲突，以代码和新鲜验证结果为准并停止同步。

### 批准记录

批准人: YangYang

批准角色: 项目负责人

批准时间: 2026-08-29T12:47:19+08:00

批准依据: 已阅读并批准本 RFC；仅允许按矩阵同步已验证事实，不改变架构方向、公开 API、依赖方向和发布结构。

批准证据: 本次 Codex 对话线程中的用户消息“批准”（2026-08-29T12:47:19+08:00）。

在五项字段均获得真实、可追溯值，且批准时间早于 T9 `Dispatch At` 时，才可解锁 T9；批准记录与 T9 content/status 提交必须分离。

## 参考

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)：系统边界、分层和依赖方向。
- [`docs/SECURITY.md`](../SECURITY.md)：安全默认原则、敏感能力和凭证边界。
- [`docs/RELIABILITY.md`](../RELIABILITY.md)：构建、发布与回退可靠性要求。
- [`docs/design-docs/core-beliefs.md`](./core-beliefs.md)：长期工程信条，只读约束来源。
- [`docs/design-docs/module-boundaries.md`](./module-boundaries.md)：模块职责、依赖与文档边界。
- [`docs/design-docs/_template.md`](./_template.md)：设计文档结构模板。
- [`docs/active/v2.2.1/technical-debt-remediation/plan.md`](../active/v2.2.1/technical-debt-remediation/plan.md)：T1–T9 任务、AC 和验证门禁。
- [`docs/active/v2.2.1/technical-debt-remediation/design.md`](../active/v2.2.1/technical-debt-remediation/design.md)：已实现接口、配置和兼容性决策。
