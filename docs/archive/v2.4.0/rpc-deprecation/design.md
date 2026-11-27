---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# RPC Core 旧入口废弃设计

推荐方案，未实施。[规格](./spec.md) · [计划](./plan.md)

## 背景、目标与非目标

RpcTracerBridge 与 RpcHookChain 已有兼容入口及替代生命周期。本轮目标是 RP1/RP2：预告删除并提供完整迁移说明；不更改运行行为，不接入新的HTTP客户端，不删除适配层。

## 架构与接口契约

旧调用仍走原适配路径；新调用使用 trace scope 和每次调用独立 invocation。仅将下列现有注解的 `forRemoval = false` 改为 `true`，保留 `since = "2.2.1"`。

| 现有签名 | 迁移入口 |
|---|---|
| `void RpcTracerBridge.extract(RpcCallContext context, Map<String,String> carrier)` | `RpcTraceScope extractScope(RpcCallContext context, Map<String,String> carrier)` |
| `void RpcHookChain.before(RpcCallContext context)` | `RpcHookInvocation open(RpcCallContext context)` 后调用 `before()` |
| `void RpcHookChain.after(RpcCallContext context, RpcCallResult result)` | `completeSuccess(RpcCallResult result)` |
| `void RpcHookChain.onError(RpcCallContext context, RpcCallResult result)` | `completeFailure(RpcCallResult result, Throwable primaryError)`（primaryError 来源见下） |
| `void RpcHookChain.cleanup(RpcCallContext context)` | 同步 `close()`，异步 `completeWithoutResult()` 兜底 |

新入口本轮不改签名。异步从 `RpcAsyncHookInvocation openAsync(RpcCallContext context)` 获得句柄，调用 `before()` 后将终态责任交给回调；它不是 AutoCloseable，不能照搬同步 try-with-resources。成功走 completeSuccess，失败走 completeFailure；未成功注册回调走 completeWithoutResult 或携带实际主异常的 completeFailure。

审查补充：旧 `onError(context, result)` 没有独立 Throwable 参数，异常只能经 `RpcCallResult.getError()` 取得。迁移后 `primaryError` 就是调用方 catch 到、并用于构造 `RpcCallResult.failure(duration, error)` 的同一业务异常实例，与内置 `RpcExecutionTemplate` 的 `completeFailure(RpcCallResult.failure(duration, throwable), throwable)` 一致。`RpcHookInvocation` 与 `RpcAsyncHookInvocation` 的 `completeFailure` 会把 Hook onError/cleanup 抛出的异常 `addSuppressed` 到该对象，调用方随后重抛它；因此它必须非 null（当前实现在 Hook 抛错时会对 null 调用 addSuppressed），没有业务异常时改用同步 `close()` 或异步 `completeWithoutResult()`。异步场景取完成回调收到的失败 Throwable 或回调注册失败时捕获的异常。

推荐内置 MdcRpcTracerBridge、NoopRpcTracerBridge 的旧 extract 覆盖方法同步标记，使静态类型为实现类时也可见警告。~~2.x 中 extract 仍为抽象接口，新的实现者仍须保留它；不得声称只实现 extractScope 即可编译~~（第三轮审查经用户决定改为空默认方法：只实现 extractScope 的 Bridge 可编译且无 removal 警告；覆盖旧方法的实现不受影响）。不得让两方法互相委托形成递归。

审查补充（推荐，修正原“外部实现不强制改写注解”）：按 JLS §9.6.4.6，覆盖 `@Deprecated(forRemoval = true)` 方法本身就是一次“使用”，除非处于 `@SuppressWarnings("removal")` 作用范围或与声明位于同一最外层类，否则产生 removal 警告；覆盖方法自身标 `@Deprecated` 只豁免普通 deprecation 警告，不豁免 removal。因此所有外部 Bridge 实现者即使已迁移 extractScope，仍会因必须覆盖 extract 得到不可避免的 removal 警告，在 `-Werror` 下构建失败。框架不能消除该警告，只能在 README/升级说明中给出示例：覆盖方法加 `@SuppressWarnings("removal")`（必需），可再加 `@Deprecated(since = "2.2.1", forRemoval = true)` 向自身下游传递预告。内置 MdcRpcTracerBridge、NoopRpcTracerBridge 与接口不在同一最外层类，其覆盖方法同样两者都加，保持与外部示例一致。

框架自身调用点：`LegacyMdcTraceScope.extract` 直接调用 `bridge.extract(context, carrier)`（`LegacyMdcTraceScope.java:36`），`RpcTracerBridge.extractScope` 默认实现经它间接依赖旧方法。该类与 `RpcTracerBridge` 不同最外层类，标记后会产生框架自身 removal 警告；推荐在该静态方法上加 `@SuppressWarnings("removal")` 并注释说明其为 2.x 兼容适配、随旧入口一同删除。接口内 extractScope 默认方法与 extract 同一最外层类，不受影响。

## 文档、兼容与错误处理

在模块 README 增加“2.4.0升级”段，明确最初废弃2.2.1、本次标记删除、下个主版本计划删除。同步示例包含 before、业务成功/失败和最终close；异步示例包含回调注册失败路径，不提前清理。trace scope在所属线程关闭；跨线程传播另按现有RPC机制处理。

LegacyMdcTraceScope 保留：只恢复抽取修改的MDC键；提取失败先回滚再抛原异常，未知Tracing上下文由实现者自行实现scope。Hook后置异常仍沿用既有“成功仅记录、失败附加到主异常”行为；不以改注解为由重写错误链。

替代仅改文档不能落实D-75；直接删旧入口破坏本版本兼容，因此选择注解与迁移说明同步。技术债TD-013/TD-023保留完成条件，仅登记删除周期。

## 验证策略

| 契约 | 方法 | 通过条件 |
|---|---|---|
| RP1注解与链接 | 反射检查、旧源码编译、旧二进制运行 | 五个入口及内置覆盖均标记；since不变；旧二进制仍可运行 |
| RP1严格告警 | 默认参数编译（removal 默认开启），再附加`-Werror` | 普通模式警告可编译，严格模式失败被明确记录；外部 Bridge 覆盖 extract 未抑制时有警告、加 `@SuppressWarnings("removal")` 后通过；rpc-core 自身编译无来自 LegacyMdcTraceScope 及内置 Bridge 的 removal 警告 |
| RP2作用域 | 既有LegacyRpcTracerBridgeCompatibilityTest等 | 恢复与原异常语义不变 |
| RP2调用生命周期 | RpcHookChainTest及迁移示例编译 | 同步/异步成功、失败、注册失败、重复终态均符合既有语义 |

本轮仅静态核对接口，未执行兼容验证。
