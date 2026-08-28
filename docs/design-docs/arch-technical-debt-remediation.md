---
id: arch-technical-debt-remediation
status: draft
owner: YoungerYang-Y
tags: [architecture, technical-debt, governance]
created: 2026-08-28
verified: 2026-08-28
---

# 技术债修复长期约束同步 RFC

## 背景与动机

T1–T7 已在 `feature/technical-debt-remediation` 完成代码、测试、构建与发布模型修复，并分别以独立提交记录了验证证据。本 RFC 只为 T9 提供长期文档同步的边界：把已经落地且已经验证的事实，按权威文档的职责写回 `ARCHITECTURE.md`、`docs/SECURITY.md`、`docs/RELIABILITY.md` 和 `docs/design-docs/module-boundaries.md`。

本 RFC 是状态为 `draft` 的架构约束同步提案，不是对长期约束的直接修改，也不是对 T9 的预授权。它不改变模块依赖方向、公开 API、发布结构或安全策略；在“批准记录”完成前，T9 不得据此修改四个长期文档，也不得把任何残余风险写成已关闭。

### 已验证事实基线

以下事实来自当前任务链中的实现提交和执行记录，T9 只能在对应来源仍与代码一致时同步：

| Task | 提交 | 可同步的事实摘要 | 当前边界 |
|------|------|------------------|----------|
| T1 | `29e645b` | 公共异常响应保留中文消息；BindException 继续使用纯消息列表；分页结果构造路径校验 null/非法值；分页请求在偏移计算前纠正边界；新增 nullable 枚举查询而不改变既有 fallback。 | 既有 `fromCode`、响应形状和可用 API 不得被文档改写为新语义。 |
| T2 | `3a97c62`、`e45e457` | 日志脱敏覆盖 JSON、编码键和私钥/访问密钥；公钥保持可见；规则与 replacement 以不可变快照原子刷新；非 Logback 绑定降级并告警；MDC 单键/批量语义保持既有约定。 | 单次基准为 `+63087.03ns/op`，不能作为发布性能通过证据；T9 仍须独立连续运行三次并验证均值不超过 20µs。 |
| T3 | `bf493b6` | Dubbo 支持依赖以完整快照发布；异步回调恢复 trace scope；关闭失败不覆盖业务结果；Feign 支持地址回退和非敏感多值请求头；框架内部改用调用级 Hook API。 | 旧 `RpcTracerBridge.extract`、`RpcExecutionTemplate` Bean 和四个旧 Hook 直调方法仍保留；TD-013、TD-023 不得关闭。 |
| T4 | `5e71294` | Nacos 仅在当前或旧加密前缀实际绑定时处理 ENC；遗留 AES/ECB 顶层调用每次产生一次迁移告警；无前缀应用不被误触发。 | 遗留 API 仍是迁移边界，不得被描述为推荐的新安全方案。 |
| T5 | `c6006b2` | MyBatis 提供应用级 AAD v2 读能力和默认关闭的渐进写入开关；v1 密文及旧 Handler/Bean 保持可读可用；Mapper 有效包查询与实际扫描一致；SQL、审计输出脱敏。 | AAD 只绑定应用 context；同 context 的跨字段/跨记录调换仍可能解密，TD-016 不得关闭。v2 写入后回退下限必须支持 v2 且使用相同 context。 |
| T6 | `bb651cd` | 测试 starter 去除数据库、副作用和固定应用名等危险隐式默认值；弃用 API 和公开成员保持兼容；日志工具和随机测试标识收敛。 | 下游依赖隐式测试默认值者需显式配置，这属于迁移说明，不得恢复危险默认值。 |
| T7 | `a5dba88` | 普通构建默认跳过 GPG 签名、正式发布显式启用；formatter 固定为 1.23.0；BOM 坐标、隔离 consumer、动态 revision、报告门禁和 Apache-2.0 LICENSE 已验证；15 个 Reactor 模块 clean verify、CI preflight、consumer 与签名 fixture 均通过。 | 发布凭证、tag、发布结构和正式部署仍受既有流程约束；签名通过 fixture 不等于已执行生产发布。 |

## 设计原则

1. **只同步事实，不改变约束。** RFC 授权的唯一动作是将 T1–T7 已验证的实现、配置、版本和验证事实写入相应长期文档；不得借文档同步新增规则或重新设计架构。
2. **权威来源分层。** 运行时代码和测试是实现事实来源；`ARCHITECTURE.md`、`docs/SECURITY.md`、`docs/RELIABILITY.md`、`docs/design-docs/module-boundaries.md` 各自只接收属于自身职责的信息；需求级细节继续留在 `docs/active/v2.2.1/technical-debt-remediation/`。
3. **兼容性优先。** 文字必须同时说明新增能力、保留的旧入口、默认值和迁移条件，不得把新增 API 或可选开关写成强制替换。
4. **残余债务可见。** TD-013、TD-016、TD-023 必须继续以“残余风险/未关闭”表述，并保留触发条件和边界；通过 T3/T5 的测试不代表这些债务消失。
5. **批准是解锁条件。** RFC 保持 `draft`，直到用户或架构负责人在固定字段中提供明确批准人、批准日期和批准依据；批准前 T9 必须保持 blocked，不得假设口头或历史意见构成批准。
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
| 验证 | 以实现提交和测试记录核对模块路径、POM、自动配置入口和依赖树；运行文档健康检查、路径存在检查、Markdown lint，并检查 diff 只包含获批的事实性文字。 |
| 回退 | 若代码或有效依赖树与文字不一致，停止 T9 的该文件同步，保留原文件并将 RFC 维持 `draft`；若已产生文档提交，按提交粒度回退该文档，不回退 T1–T7 代码。 |

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
| 验证 | 对照 ARCHITECTURE.md 的分层图、各模块 POM 与自动配置；运行依赖方向检查、模块目录/README 检查、文档健康检查和链接检查，确认没有新模块或反向依赖被文字引入。 |
| 回退 | 若模块职责或依赖图无法由当前代码证明，停止该文件更新并恢复原文；不得通过修改代码、POM 或聚合注册来“补齐”RFC 的文字结论。 |

### 执行与批准门禁

1. T9 先从 T1–T7 提交和测试记录重建事实清单，再逐文件产生最小 diff；任何新增事实若不能回指代码、测试、effective POM 或既有发布脚本，立即停止。
2. T9 不得在同一提交中修改本 RFC 的批准字段、四个长期文档、计划状态和发布记录；批准记录必须先于 T9 dispatch 的新鲜验证证据存在。
3. 批准前只允许维护本 RFC 和设计索引的 `draft` 记录。未批准时，T9 应标记为 `blocked`，不能将 draft 解读为授权，不能把 RFC 或索引改为 `verified`。
4. 获批后仍须执行 T9 的全量文档健康、Markdown/link 检查、版本/配置扫描和发布验收；任何失败都只回退文档同步，不回退已验证的 T1–T7 实现。

## 反模式

| 反模式 | 为什么禁止 |
|--------|-----------|
| 把 T8 当作已批准的架构变更，直接修改四个长期文档 | T8 只产生 draft RFC；长期约束修改需要用户/架构负责人明确批准。 |
| 将 TD-013、TD-016 或 TD-023 标记为“已关闭” | T3/T5 只验证了有限修复边界，旧自定义 Bridge、应用级 AAD 的字段/记录级完整性和旧 Hook 直调入口仍有残余风险。 |
| 把应用级 AAD 写成字段级或记录级防篡改 | 这会虚构未实现的安全保证，并误导迁移和回退决策。 |
| 把一次 `+63087.03ns/op` 基准输出写成性能达标 | 该输出来自单次运行且超过 20µs 门限；发布证据必须是三次独立运行的聚合结果。 |
| 把测试 fixture、file 镜像或签名 fixture 写成生产发布结果 | fixture 只证明隔离验证路径，不能证明 Maven Central、GitHub Packages 或生产实例已部署。 |
| 删除旧 API、旧 Bean、v1 密文读取或旧配置绑定 | T1–T6 的兼容基线要求这些入口继续源码/二进制可用或继续可读。 |
| 通过 RFC 引入新依赖、新 Starter、循环依赖或 parent/BOM 运行时逻辑 | 这超出“同步已验证事实”的范围，也违反模块边界。 |
| 修改默认安全语义、发布凭证流程、tag/release 结构或依赖方向 | 这些是长期高风险约束，不属于 T8 的事实性文档同步。 |
| 伪造批准人、批准日期、批准依据，或将批准字段留空后解锁 T9 | 批准是明确的治理门禁；缺少真实批准证据时 T9 必须保持 blocked。 |

## 适用范围

本 RFC 只适用于 v2.2.1 技术债修复的 T1–T7 已验证事实，以及在批准后由 T9 执行的四个长期文档的最小事实性同步：

- `ARCHITECTURE.md`
- `docs/SECURITY.md`
- `docs/RELIABILITY.md`
- `docs/design-docs/module-boundaries.md`

它不适用于新增功能、依赖升级、模块重划分、公共 API 设计、安全策略重写、生产发布或 MyBatis 字段/记录级完整性设计。需求级行为、测试证据、迁移矩阵和剩余债务仍以 `docs/active/v2.2.1/technical-debt-remediation/` 下的 `spec.md`、`design.md`、`plan.md` 和 `release.md` 为准；若本 RFC 与代码冲突，以代码和新鲜验证结果为准并停止同步。

### 批准记录

批准人: 待用户/架构负责人批准

批准日期: 待用户/架构负责人批准

批准依据: 待用户/架构负责人批准；在三项字段均获得真实、可追溯值前，不得解锁 T9

## 参考

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)：系统边界、分层和依赖方向。
- [`docs/SECURITY.md`](../SECURITY.md)：安全默认原则、敏感能力和凭证边界。
- [`docs/RELIABILITY.md`](../RELIABILITY.md)：构建、发布与回退可靠性要求。
- [`docs/design-docs/module-boundaries.md`](./module-boundaries.md)：模块职责、依赖与文档边界。
- [`docs/design-docs/_template.md`](./_template.md)：设计文档结构模板。
- [`docs/active/v2.2.1/technical-debt-remediation/plan.md`](../active/v2.2.1/technical-debt-remediation/plan.md)：T1–T9 任务、AC 和验证门禁。
- [`docs/active/v2.2.1/technical-debt-remediation/design.md`](../active/v2.2.1/technical-debt-remediation/design.md)：已实现接口、配置和兼容性决策。
