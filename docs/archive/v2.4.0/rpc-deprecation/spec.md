---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# RPC Core 旧入口废弃需求规格

推荐规格，未实施；D-75 已确认。[讨论](./brainstorm.md) · [设计](./design.md) · [计划](./plan.md)

## Behavior RP1：预告删除且保持当前兼容

| Scenario | Given | When | Then |
|---|---|---|---|
| 旧二进制 | 消费者已按旧版编译并使用旧入口 | 以新版本加载运行 | 原入口仍能链接，既有调用结果与清理行为不变 |
| 新编译 | 消费者源码继续调用旧入口，使用默认编译参数（javac 对 `forRemoval=true` 默认输出 removal 警告，无需 `-Xlint`） | 编译 | 出现计划删除告警，普通编译仍可完成 |
| 严格构建 | 同一消费者将告警视为错误 | 编译 | 可能因 removal 告警失败；升级说明提供迁移方向，不承诺所有源构建零改动 |
| 外部 Bridge 覆盖 extract（审查补充） | 外部 `RpcTracerBridge` 实现已迁移到 `extractScope`，但 2.x 中 `extract` 仍为抽象方法必须覆盖 | 默认编译及 `-Werror` 编译 | 覆盖 `forRemoval=true` 方法本身即产生 removal 警告，迁移 `extractScope` 不能消除；覆盖方法加 `@SuppressWarnings("removal")` 后警告消失、严格构建通过；仅加 `@Deprecated(forRemoval = true)` 可向下游传递删除预告，但按 JLS 不豁免 removal 警告，须与前者同用 |

## Behavior RP2：迁移说明覆盖生命周期

| Scenario | Given | When | Then |
|---|---|---|---|
| 同步调用 | 每次调用独立取得上下文与调用生命周期 | 正常或异常结束 | 后置与清理遵循现有约定，作用域在创建线程关闭 |
| 异步调用 | 生命周期所有权交给完成回调 | 成功、失败或注册回调失败 | 对应终态执行，重复终态不会重复清理；不在返回异步结果时提前关闭 |
| 旧实现 | 自定义桥仅实现旧抽取接口 | 使用兼容作用域 | 仍可加载；仅恢复适配层知道的MDC键，不能宣称恢复自定义Span/ThreadLocal |
| 失败终态迁移（审查补充） | 旧代码在 catch 中调用 `onError(context, result)`，异常仅经 `result.getError()` 携带 | 改用 `completeFailure(result, primaryError)` | `primaryError` 传入 catch 到的同一非 null 业务异常（即构造 `RpcCallResult.failure(duration, error)` 的 error）；onError/cleanup 异常附加为其 suppressed，调用方随后重抛该异常；无异常时不得传 null，应走 `close()`/`completeWithoutResult()` |

## Constraints

v2.4.0 不删除任何旧公开方法及兼容适配层；计划在下一个主版本删除，准确清单届时复核。保留原始 since=2.2.1，2.4.0 只增强删除标记。技术债不因本轮文档与注解改动而自动关闭。
