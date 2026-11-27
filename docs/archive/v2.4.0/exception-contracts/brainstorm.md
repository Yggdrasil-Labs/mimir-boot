---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Exception：HTTP 语义与校验明细方案大纲

本需求归属 `mimir-boot-starter-exception`，承接[版本级 Brainstorm](../brainstorm.md) 的 E-01、E-02（D-36 至 D-38）。本文保留逐项讨论历史。2026-10-29 按用户要求直接推荐并补齐 SDD，已有[规格](./spec.md)、[详细设计](./design.md)和[实施计划](./plan.md)。新增细节是推荐草案，不表示用户逐项确认，尚未实施。

## 本轮推荐摘要

- 在现有Advice内分离HTTP元信息与body工厂，保留旧public handler的Object返回描述符及原单参构造；工厂不能覆盖已解析状态和异常协议头，构建失败直接降级且不重试。
- 合并后的响应直接交给原生MVC，不增加媒体选择、返回值编排或请求包装。不可表示格式、Resource、条件/Range请求沿用框架处理，不保证这些情况下仍为原状态或空体。
- 保留无实体状态的简单判断：1xx、204、205、304使用null body，不构造统一错误body；HEAD沿用原生MVC。
- 固定Biz/System/Base语义优先；标准状态异常保留状态/合法头；普通自定义异常解析当前类型的合并/继承注解，不遍历cause。不借ErrorResponse泛化引入E-03。
- 推荐配置 `mimir.boot.exception.validation.structured-details-enabled=false`。新模式输出ValidationErrorData/Detail，最多100项，路径/约束/消息上限256/64/256字符；稳定排序、重复保留、固定安全消息、不携带拒绝值及动态Map key。
- 返回值、混合或无法证明为输入的约束异常推荐500/null；其中空集合和纯Bean路径由原400改500需显式迁移说明。旧data默认保持，不等于保持错误的返回值状态。
- 2026-10-31 用户确认（D-98）：接受空集合与纯Bean路径由400改为500，不提供恢复400的开关；业务明确属于输入时在自身边界转换或用更高优先级Advice处理。

后文“待讨论”“候选”保留讨论当时含义，当前推荐细节以[设计](./design.md)为准；除 D-98 外不新增已确认D决策。

## 目标与已确认范围

| 需求 | 目标 | 已确认约束 |
|---|---|---|
| E-01 | 标准 HTTP 异常返回正确状态及必要协议头 | 同时支持 Spring 标准异常和业务异常类型上的 `@ResponseStatus`；保持 BizException 默认 HTTP 200 |
| E-02 | 为客户端提供可稳定消费的校验明细 | 默认保持现有字符串列表或 null；显式开启结构化明细；不默认回传被拒绝值；返回值校验按服务端错误处理 |

继续使用已有响应工厂扩展点。安全框架异常、异步超时及客户端断开语义（E-03）仍未纳入。

## 当前入口与依据

本轮仅静态核对代码：

- [MimirExceptionHandler](../../../../mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/handler/MimirExceptionHandler.java)：已有类型专用处理与 Exception 兜底；兜底固定 500，405 分支只在消息中列出支持的方法。
- [ExceptionResponseFactory](../../../../mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/handler/ExceptionResponseFactory.java)：`createResponse(String code, String message, Object data)` 负责响应体，HTTP 状态和头不在其签名内。
- [ExceptionProperties](../../../../mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/config/ExceptionProperties.java)：当前只有总开关。

已有 MethodArgumentNotValidException 与 BindException 返回字符串列表；HandlerMethodValidationException 和 ConstraintViolationException 路径未提供结构化明细。实际 MVC resolver 交互还需后续验证。

## E-01 方案大纲

| 方案 | 优点 | 代价 |
|---|---|---|
| 推荐候选：在现有 Advice 中分离 HTTP 元信息解析与响应体构建 | 保留响应工厂扩展点，能够同时维护统一响应体、状态和必要头 | 需要明确异常优先级、状态映射、消息暴露及失败回退 |
| 备选：让 Spring 默认 resolver 处理标准 HTTP 异常 | 更直接沿用框架语义 | 响应体可能绕过 Mimir 工厂，和已有消费者格式不一致，需评估兜底如何退让 |

推荐候选的处理链为：识别异常与既有业务约定 → 解析状态和必要协议头 → 映射响应错误码及对外消息 → 调用响应工厂 → 合并 HTTP 元信息 → 交给原生 MVC。工厂失败不改变 Advice 已解析的状态和协议头；后续内容协商及请求处理仍按框架规则执行。

后续需要逐项收敛：

1. **类型与优先级**：列出 ResponseStatusException、ErrorResponseException、已有专用 HTTP 异常、类型上的 `@ResponseStatus`；明确与 BizException、BaseException、IException 的重叠优先级。
2. **注解语义**：是否解析组合注解、继承和 cause 链，`reason` 是否进入响应消息；不得顺手扩大异常遍历范围。
3. **状态与业务码**：HTTP 状态保持语义，响应 code 继续服务业务；优先复用现有 ErrorCode，缺少对应项时再比较状态映射表与通用错误码。
4. **协议头**：以 405 的 Allow、Retry-After 等用例制定保留清单及合并规则；Content-Type、已有过滤器响应头和重复值不能直接无差别覆盖。
5. **消息与回退**：区分可公开客户端错误与服务端内部细节；工厂抛异常后返回兼容响应，并避免错误处理再次递归失败。

## E-02 方案大纲

推荐增加显式的明细模式配置，将旧格式作为默认路径；在新模式下，把不同校验来源统一转成结构化项，再通过工厂现有 data 参数输出。暂不更改工厂接口或强制消费者接受新模型。

替代方案是各异常分别返回原生错误对象，改造成本较低，但客户端仍需识别多套结构，也容易暴露框架内部对象，不利于统一协议。

建议先讨论以下模型维度，字段名和类型留待确认：

| 维度 | 候选表达与问题 |
|---|---|
| 来源 | 区分字段、参数、对象级错误；容器元素和跨字段约束如何归类 |
| 定位 | 字段路径、参数名称或序号；无参数名时提供稳定降级方式 |
| 内容 | 约束标识与对外消息；校验器自定义消息本身也可能带值，需定义暴露边界 |
| 顺序 | 按定位与约束稳定排序，还是保留框架顺序；重复错误是否保留 |
| 返回值校验 | 服务端错误语义保持；是否仅返回通用消息而隐藏内部字段明细 |

首先覆盖已有四类校验入口。对 ConstraintViolationException 的返回值路径需要单独识别，不能把全部违例都默认理解为请求参数错误。

## 模块依赖与讨论顺序

- [Common](../common-contracts/brainstorm.md) 提供错误码与 R；E-01 不等待新增错误码接口才能讨论，也不借此改写旧业务异常语义。
- [Web](../web-binding/brainstorm.md) 承担 JSON 接入；C-07 的 `fail` 迁移与 E-02 明细模式是两个独立兼容维度。
- 先确认 HTTP 类型及优先级，再确认协议头与消息策略，最后讨论结构化校验字段。两条需求可以分别推进，但共享响应工厂回退约定。

## 后续验证方向

重点通过真实 MVC 异常路径观察状态、头和响应体的组合，而不只直接调用 handler：标准 HTTP 异常、类型注解、既有 BizException、405 Allow、Retry-After、自定义工厂及工厂失败；校验覆盖字段、对象、参数、容器元素与返回值，并比较默认/结构化两种模式。

这些方向已展开为[实施计划](./plan.md)的验收矩阵。本轮仅核对本地项目与 Spring 6.1.21 源码并补齐 SDD，未新增 Java 测试、修改实现或执行运行验收。
