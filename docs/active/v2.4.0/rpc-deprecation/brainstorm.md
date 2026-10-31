---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# RPC Core：旧入口废弃周期方案大纲

## 本轮推荐与 SDD 产物

2026-10-29 用户要求停止逐项提问，并补齐每项需求的 SDD。本文保留前期讨论过程；未逐项确认的细节已由本轮推荐方案收敛到 [spec](./spec.md)、[design](./design.md)、[plan](./plan.md)，推荐不追记为历史用户决定。以下“候选”“待讨论”“后续”等措辞描述此前状态，当前推荐以 SDD 为准；文档不代表实现或验收完成。

本需求归属 `mimir-boot-starter-rpc-core`，承接[版本级 Brainstorm](../brainstorm.md) 的 D-75 及技术债 TD-013、TD-023。范围是废弃标记和迁移说明，运行时功能保持现状。

## 已确认方向

- 旧 Bridge `extract()` 及 RpcHookChain 的 `before`、`after`、`onError`、`cleanup` 改标 `@Deprecated(forRemoval = true)`。
- v2.4.0 保留旧入口及兼容适配；计划在下一个主版本删除，而非本轮删除。
- 接入方迁移到受管 trace scope 与 `open`/`openAsync` 创建的调用级 invocation。

上述方向已由 D-75 确认；具体文档示例、涉及的覆盖实现和下个主版本的删除清单仍待展开。

## 当前入口与依据

本轮静态核对：

- [RpcTracerBridge](../../../../mimir-boot-starters/mimir-boot-starter-rpc-core/src/main/java/com/yggdrasil/labs/rpc/core/tracing/RpcTracerBridge.java)：旧入口标记 `since = "2.2.1", forRemoval = false`；`extractScope()` 默认实现继续适配旧 extract。
- [RpcHookChain](../../../../mimir-boot-starters/mimir-boot-starter-rpc-core/src/main/java/com/yggdrasil/labs/rpc/core/hook/RpcHookChain.java)：四个直调入口仍保留，新入口已按调用创建生命周期对象。
- [技术债台账](../../tech-debt-tracker.md)：TD-013、TD-023 的上下文与保留原因。

版本级 brainstorm 已记录三个内部业务项目的静态使用情况；本轮不把该历史样本当作全部消费者清单。

## 方案大纲

推荐沿用已有迁移入口，统一注解、Javadoc、README 与升级说明的表达：

| 旧接入方式 | 迁移方向 | 说明重点 |
|---|---|---|
| Bridge 直接 extract | 实现/使用 extractScope，并关闭调用作用域 | 作用域所属线程、关闭时机、自定义上下文的恢复责任 |
| Chain before/after/onError/cleanup | 同步 open 返回的 invocation | 成功、失败及最终关闭的完整流程，避免漏清理 |
| 业务自行拼装异步 Hook 调用 | openAsync 的调用级生命周期 | 终态所有权与回调关闭责任，不将同步示例机械套用到异步 |

兼容适配层继续保留原行为。`since` 表示最初废弃版本，推荐保留 2.2.1，并在文字中说明 2.4.0 开始计划删除；不把注解日期改写为本次版本。

替代方案是仅补文档而保留 `forRemoval = false`，告警影响较小，但不能落实 D-75 的删除预告，因此不推荐。直接删除或重写旧适配层超出本轮范围。

## 待讨论问题

1. 实现类覆盖的旧方法是否也统一标记，如何让直接引用实现类的消费者收到一致提示。
2. Bridge 旧 extract 当前仍是接口抽象方法；迁移说明应说明实现者在 2.x 中仍需保留的兼容方法，不能声称只实现 extractScope 即可编译。
3. 下一个主版本删除范围是仅旧公开入口，还是一并删除只为其服务的内部适配层；本轮记录候选范围，删除前再核对实际消费者。
4. 将告警视为错误（`-Werror`）的消费者可能需要调整构建；说明这种源代码构建影响与运行时兼容的区别。

## 依赖与后续验证方向

不依赖本轮 Common、Log 新能力；Feign、Dubbo 功能范围不随之扩展。标记完成后应核对旧调用方仍可编译、旧实现仍可加载、新同步与异步路径仍保持既有行为；迁移示例须能编译，告警变化单独记录。

TD-013、TD-023 本轮只明确废弃周期，是否关闭须依据台账各自完成条件，不能以添加注解代替主版本清理。当前未改源码、未执行兼容性验证。
