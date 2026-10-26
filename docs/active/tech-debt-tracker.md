---
updated: 2026-10-25
---

# 技术债务追踪

本清单只记录仍处于活跃状态、需要后续设计、修复、兼容迁移或明确接受的技术债务。条目按编号升序排列；已关闭债务的表格行和明细均移除，处理结果与验收证据归入对应计划或验收记录；没有独立计划时由版本 release 记录承接。

## 维护规则

- 新增债务时核对本页、历史计划和版本记录，以历史已分配最大编号加一生成 `TD-xxx`；关闭或删除后不得复用编号。
- 每项补全领域、优先级、状态、Owner、记录日期、处置与验收条件、目标版本及复查条件。未确定目标版本时明确填写 `待排期`，不得把文档目录编号当作发布承诺；Owner 在每月架构回顾及条目列出的触发条件发生时复查。
- **高**：存在发布消费者不可用、数据泄露、配置错误或核心行为错误的风险，应优先纳入最近迭代。
- **中**：质量门禁、测试可靠性或兼容性存在缺口，需要排期处理。
- **低**：影响受限的兼容包袱或测试维护项，可随相关改动一并处理。
- `兼容保留` 表示框架已规避内部主路径，但公开旧接口仍保留已知风险；只有完成迁移或废弃后才能移除。
- `部分修复` 必须明确剩余风险；`验收中` 必须区分已有证据和未闭环条件。配置修改或普通构建通过不能替代专项验收。
- 关闭前核对实现、适用验收和剩余风险结论。风险接受须记录 Owner、原因、影响边界及重新评估条件，不能把接受风险写成已修复。
- 清理已关闭项前，先确认完整记录已有归属、切换仓库内引用，再删除台账明细及旧锚点；不保留兼容入口。历史执行和验收结论保留原意，后续迁移另记决策。
- 债务关闭与制品发布分开记录：满足约定验收范围即可关闭，`未发布` 由 release 记录跟踪。本地结果不得写成 CI、真实发布或发布后消费者已验证。
- Owner 不明确时填写 `ORPHAN`；30 天内未指派的条目应在架构回顾中决定归档、删除或分配维护者。

## 活跃清单

| 编号 | 主题 | 领域 | 优先级 | 状态 | Owner | 记录日期 | 关联计划 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [TD-013](#td-013-rpc-mdc-scope) | RPC MDC scope 兼容入口 | starter-rpc-core | 中 | 兼容保留 | YoungerYang-Y | 2026-08-29 | [技术债修复计划](../archive/v2.2.1/technical-debt-remediation/plan.md) |
| [TD-016](#td-016-字段加密-aad) | 字段加密 AAD 绑定 | starter-mybatis | 中 | 待规划 | YoungerYang-Y | 2026-08-29 | [技术债修复计划](../archive/v2.2.1/technical-debt-remediation/plan.md) |
| [TD-023](#td-023-rpc-hook-legacy-api) | RPC Hook 旧 API 兼容 | starter-rpc-core / feign | 低 | 兼容保留 | YoungerYang-Y | 2026-08-29 | [技术债修复计划](../archive/v2.2.1/technical-debt-remediation/plan.md) |
| [TD-047](#td-047-nacos-消费者迁移) | Nacos 配置删除后的消费者迁移 | starter-nacos / 业务配置消费 | 高 | 待规划 | YoungerYang-Y | 2026-10-09 | [拆分与框架关闭记录](../archive/v2.3.0/runtime-debt-followup/plan.md#第九阶段td-039-框架关闭与消费者迁移拆分)；实际应用迁移范围待核实 |

## 债务明细

<a id="td-013-rpc-mdc-scope"></a>

### TD-013：RPC MDC scope 兼容入口

- **现状与风险**：直接调用 `extract()` 的非 scope 入口仍不管理 MDC 生命周期；默认兼容 scope 只能恢复 MDC，不能管理自定义 Span 或其他 ThreadLocal。
- **已缓解范围**：框架内部调用已迁移到调用级 scope（证据：`bf493b6`、`2dd1b83`、`4e428e6`）。2026-10-03 补齐旧 extract-only Bridge 默认 scope 对提取期间净修改 MDC 键的回滚，包含提取异常、嵌套及真实 Dubbo 异步完成路径；[本轮计划与验收](../archive/v2.3.0/runtime-debt-followup/plan.md)记录本地证据。无关 MDC 变更保留，scope 必须在创建线程按 LIFO 关闭。
- **处置与验收**：完成旧入口迁移或废弃，并验证自定义 Bridge 异常、嵌套调用和异步调用后 MDC 均被恢复。
- **规划进展（2026-10-25）**：v2.4.0 规划讨论中静态检查三个内部业务项目源码，仅 valhalla-project 经 Dubbo Starter 间接使用 RPC Core，未自定义 `RpcTracerBridge`，未调用旧 `extract()` 入口。用户确认在 v2.4.0 将该旧入口改标为 `@Deprecated(forRemoval = true)`，在 README 与升级说明中写明迁移方式，并计划在下一个主版本删除（D-75）；尚未实施，删除前仍需按上述验收条件验证。
- **目标版本**：待排期。
- **复查条件**：RPC Trace API 迁移、废弃或相关生命周期调整时，核对公开旧入口和自定义 Bridge 的剩余风险。

<a id="td-016-字段加密-aad"></a>

### TD-016：字段加密 AAD 绑定

- **现状与风险**：字段加密没有字段或记录级 AAD 完整性绑定；在相同密钥和应用 context 下，密文可能被跨列或跨行互换。
- **已缓解范围**：v2.2.1 仅提供应用级 context 绑定，不能消除此风险（证据：`c6006b2`、`4e428e6`）。
- **本轮调查**：现有 Handler 不接收稳定字段或记录身份；不能用 JDBC 序号或列 alias 替代业务身份。本轮字段加密注册隔离修复不改变 v1/v2 格式，也不构成 AAD 绑定修复，详见[后续计划](../archive/v2.3.0/runtime-debt-followup/plan.md)。
- **处置与验收**：设计可迁移的字段/记录级 AAD，并验证跨字段、跨记录和历史密文兼容场景。
- **目标版本**：待排期。
- **复查条件**：字段加密格式、AAD 或密钥迁移方案调整时，核对字段/记录绑定与历史密文兼容性。

<a id="td-023-rpc-hook-legacy-api"></a>

### TD-023：RPC Hook 旧 API 兼容

- **现状与风险**：`RpcHookChain` 废弃的 `before`、`after`、`onError`、`cleanup` 直调 API 仍可使用，绕开调用级 invocation 的生命周期约束。
- **已缓解范围**：框架内部已改用调用级 invocation，旧直调入口仅为兼容保留（证据：`bf493b6`、`2dd1b83`、`4e428e6`）。
- **处置与验收**：提供迁移路径和废弃周期；删除或隔离旧入口后，验证正常、异常和清理回调顺序。
- **规划进展（2026-10-25）**：v2.4.0 规划讨论中静态检查三个内部业务项目源码，未发现自定义 `RpcHook` 或对 `RpcHookChain` 旧 `before`、`after`、`onError`、`cleanup` 的直接调用。用户确认在 v2.4.0 将这些旧入口改标为 `@Deprecated(forRemoval = true)`，在 README 与升级说明中写明迁移到 `open`/`openAsync` 调用级 invocation 的方式，并计划在下一个主版本删除（D-75）；尚未实施，删除前仍需按上述验收条件验证。
- **目标版本**：待排期。
- **复查条件**：RPC Hook API 迁移、废弃或回调生命周期调整时，核对旧直调入口的迁移进度。

<a id="td-047-nacos-消费者迁移"></a>

### TD-047：Nacos 配置删除后的消费者迁移

- **来源与范围**：2026-10-09 经用户确认，从 TD-039 拆出配置删除后的消费者迁移；已验证的默认 Nacos 框架修复按约定范围关闭，完整记录见[拆分决定](../archive/v2.3.0/runtime-debt-followup/plan.md#第九阶段td-039-框架关闭与消费者迁移拆分)。本项保留原消费者风险，不代表风险已修复或接受。
- **现状与风险**：业务属性完全删除且没有下层值时，Spring Cloud 对已存在的普通 `@ConfigurationProperties` Bean 重新绑定后仍可能保留原字段值；本地负向测试已复现。缓存裸目标、字段快照或未正确更新的配置缓存也可能继续使用历史明文。该风险不再由解密覆盖层造成，实际业务应用的接入方式与迁移状态尚未核实。
- **已具备的接入契约**：`@Bean @RefreshScope @ConfigurationProperties` 的可重建 JavaBean 在完整刷新成功后，经注入代理访问新目标，恢复初始化值或下层值；单独环境事件不重建目标。可运行示例、生命周期和失败边界见[Nacos README](../../mimir-boot-starters/mimir-boot-starter-nacos/README.md#显式重建配置-bean)，本地契约与指定真实服务回归见[第二阶段](../archive/v2.3.0/runtime-debt-followup/plan.md#第二阶段本地验收2026-10-03未发布)和[第八阶段验收](../archive/v2.3.0/runtime-debt-followup/plan.md#第八阶段本地验收2026-10-09真实服务通过未发布)；框架测试不能替代实际应用迁移验收。
- **处置**：Owner 先核实需要配置删除语义的应用、配置 Bean 和实际读取/缓存路径，形成可复查清单；逐项选择可重建作用域或业务重置策略。使用刷新作用域时持续读取注入代理，处置裸目标、历史字段快照和按键更新缓存，并确认初始化、销毁、绑定与校验符合业务要求。Cloud Context 4.1.6 的事件键只反映可枚举属性原始值差异，按键缓存不能仅依赖派生属性的事件通知；完整刷新时应整体失效并重读配置，或经 RefreshScope 代理读取。
- **消费者核对（2026-10-25）**：v2.4.0 规划讨论中静态检查三个内部业务项目源码（未运行，未读取 Nacos 服务端配置内容）。仅 valhalla-project 的 valhalla-auth 接入 Nacos Starter，在 dev、test、prod 配置中导入 `optional:nacos:valhalla-auth.yaml?refreshEnabled=true`；配置 Bean 为普通 `@ConfigurationProperties` 的 `JwtProperties`（`auth.jwt`）与 `TokenProperties`（`auth.token`），均未使用 `@RefreshScope`；`LoginExecutor` 以 `@Value` 读取 `auth.password.lock-threshold`、`lock-duration`，单例下不会随推送刷新；本地配置未见加密值，JWT secret 来自环境变量 `AUTH_JWT_SECRET`。未发现依赖配置删除语义的业务路径。template-project 与 mealmate-project 未接入 Nacos。该结果作为消费者清单起点，不改变本项状态，也不替代 Owner 的实际核实与验收。
- **验收条件**：清单内实际业务消费路径经真实 Nacos 完整刷新后，验证无下层值时恢复 null/初始化值、有下层值时正确回退，前缀删除、DataId 删除、恢复及重复推送均不使用旧明文；传播到默认刷新器的解密失败保留原目标，修正后恢复消费。对按键更新缓存验证完整刷新时整体失效并重读配置，或使用 RefreshScope 代理读取，不将派生属性键必然进入事件作为前提；对选用业务重置策略的应用验证等价删除语义及自身失败策略。逐项保留应用版本、接入方式和运行证据；未核实项不得按框架回归通过关闭。
- **边界**：不自动清空任意普通 Bean，不保证擦除其他对象持有的全部历史明文。非枚举 parser、移除 import/资源身份变化、自定义刷新器、legacy bootstrap 与本地文件源消失仍按[Nacos 接入契约](../../mimir-boot-starters/mimir-boot-starter-nacos/README.md#2-配置动态刷新支持)处理；相关应用需自行满足对应契约，不能据默认框架验收推定删除保证。
- **目标版本**：待排期。
- **复查条件**：配置消费方接入或升级、刷新/绑定策略、缓存读取路径或 Spring Cloud 基线变化时，Owner 复核迁移清单和无下层值时的旧明文残留；每月架构回顾确认范围与排期。

## 历史说明

TD-001 至 TD-012、TD-014 至 TD-015、TD-017 至 TD-022、TD-024 至 TD-029 的有效部分已在 v2.2.1 完成并从活跃清单移除。TD-030 至 TD-035 的主体修复与终审补丁已分阶段提交，并通过 [v2.2.1 底座质量强化计划](../archive/v2.2.1/foundation-quality-hardening/plan.md) 的本地 Final Gate。DG-1 保留旧枚举 fallback 的误判风险；DG-3 接受写入 v2 后不能回退到 v1-only 二进制。

2026-09-30 统一迁出 TD-036、TD-037、TD-038、TD-040、TD-041 的已完成明细和旧锚点，记录归属及迁移决定见[版本记录](../archive/v2.3.0/release.md#技术债记录整理2026-09-30)。这些编号已使用，不能因本页移除而重新分配。

2026-10-02 关闭 TD-045，修复与本地验收记录见[日志断言索引边界](../archive/v2.3.0/release.md#日志断言索引边界td-045已修复未发布)。编号不再复用。

2026-10-02 关闭 TD-046，修复与本地验收记录见[测试清理顺序](../archive/v2.3.0/release.md#测试清理顺序td-046已修复未发布)。编号不再复用。

2026-10-02 关闭 TD-042，修复与本地验收记录见[分页参数校正边界](../archive/v2.3.0/release.md#分页参数校正边界td-042已修复未发布)。编号不再复用。

2026-10-03 关闭 TD-043、TD-044，历史问题、迁移后修复与本地验收记录见[覆盖率与格式门禁](../archive/v2.3.0/release.md#覆盖率与格式门禁td-043td-044已修复未发布)及其 T8 证据。编号不再复用；未发布状态由 release 跟踪。

2026-10-09 经用户确认，将 TD-039 拆为已验证的默认 Nacos 框架修复与 TD-047 消费者迁移。TD-039 按[约定框架范围关闭](../archive/v2.3.0/runtime-debt-followup/plan.md#第九阶段td-039-框架关闭与消费者迁移拆分)，完整历史验收和适用边界由该计划承接，[未发布状态](../archive/v2.3.0/release.md#td-039-框架修复关闭2026-10-09未发布)继续跟踪；原编号不再复用。消费者风险与验收条件留在 TD-047，实际业务迁移仍未核实。
