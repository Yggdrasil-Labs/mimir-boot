---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-30
---

# Common 公共契约方案大纲

本文拆解[版本级 brainstorm](../brainstorm.md)中已纳入的 Common 方向，保留逐项讨论历史。已有决策继续有效；2026-10-29 按用户“不要再问，先给一些推荐的方案。帮我把每个需求的 SDD 补齐”的要求，补充了推荐草案：[规格](./spec.md)、[设计](./design.md)、[实施计划](./plan.md)。本轮推荐不等于用户逐项确认，尚未实施 Java/POM 变更。

## 本轮 SDD 推荐

下表补齐历史讨论中的未决细节；后文“候选”“仍需确定”描述保留其讨论时点含义，当前推荐以设计为准，未追加新的已确认 D 决策。

| 方向 | 当前推荐 | 依据与边界 |
|---|---|---|
| C-01 | `ErrorDefinition`、`R.failCode`、两类异常 `fromError`；异常一参、二参 String、三参 String+Throwable | 避免 null 重载歧义；D-90 至 D-96 不变；默认与覆盖 message 均 null 时允许保留 null，文案交下游决定 |
| C-02 原生 | 新结果、新可变列表，六个元数据原样复制；null 函数拒绝，null 列表及元素保留，函数返回 null 保留 | 不重算或修正旧对象；异常同一实例传播 |
| C-02 COLA | `page.ColaPageConverters.toCola` 两种入口；严格校验全部元数据后映射，success=true | optional DTO 依赖；输出无 Serializable 上界，普通模型不出现 COLA 类型 |
| C-04 | 不可变 BatchResult/BatchItemResult，保留逐项结果列表原顺序，计数从列表派生；可按输入标识或序号关联 | `BatchResult.of(items)` 不校验输入完整性或条目顺序；完整性由业务执行方保证；空批次 EMPTY，成功数据和失败错误互斥 |
| C-07 | Web `mimir.boot.web.json.include-legacy-fail=false`，true 提供过渡，业务 R MixIn 优先 | 默认无显式业务覆盖时仅输出 success；Common 独立和手工 mapper 不自动改变；2.x 保留开关 |

## 目标与范围

让业务错误定义、分页转换和批量结果有清晰且可复用的接入契约，保留现有 Java API 的兼容性。

| 方向 | 已确认边界 | 决策来源 |
|---|---|---|
| C-01 错误码接口 | 独立定义 code 与默认 message；R 采用异名入口，BizException 与 SystemException 采用命名工厂；拒绝 null 错误定义及 D-96 中的无效 code，消息覆盖按 D-94 处理 | D-20、D-21、D-90 至 D-96 |
| C-02 分页转换 | 逐条转换，返回新结果；增加 Common 可选 COLA 适配，超出 int 范围拒绝转换 | D-22、D-55、D-62、D-66 至 D-69 |
| C-04 批量结果 | 汇总数量并保留全部逐项结果；不承担执行、事务和重试 | D-23、D-24、D-63 |
| C-07 成功状态 | JSON 收敛到 success，Java 保留 `isSuccess()`、`isFail()`；迁移及生效范围需单独确定 | D-39、D-64 |

本轮不加入无总数分页、多字段排序、整页列表映射、`R.map`、`MultiResponse` 转换；`R<T>` 和 `PageResult<T>` 的 `Serializable` 上界不变，不新增模块或 Spring 运行时依赖。

## 当前代码事实

- [ErrorCode](../../../../mimir-boot-common/src/main/java/com/yggdrasil/labs/common/exception/ErrorCode.java)是枚举，已有字符串 code/message；[BaseException](../../../../mimir-boot-common/src/main/java/com/yggdrasil/labs/common/exception/BaseException.java)、`BizException`、`SystemException` 目前接受该枚举或字符串。
- [R](../../../../mimir-boot-common/src/main/java/com/yggdrasil/labs/common/response/R.java)已有 `fail(String)` 与 `fail(String, String)`；success 由 code 是否为 `00000` 派生，fail 是其取反。
- [PageResult](../../../../mimir-boot-common/src/main/java/com/yggdrasil/labs/common/page/PageResult.java)使用 `Long` 元数据；带参构造校验总数非负、页码/大小至少 1，计算总页数及前后页标记，但无参构造和 setter 可产生未初始化或不一致状态。
- [Common POM](../../../../mimir-boot-common/pom.xml)的 Jackson 仅为测试依赖；目前没有 COLA 依赖。不能为 C-07 直接引入 Jackson 运行时耦合。
- 本地 COLA 5.0.0 源码确认：`PageResponse.of(data, totalCount, pageSize, pageIndex)` 设置 success=true；三个元数据为 int，`getData()` 将 null 集合表现为空列表。该顺序与 Common 构造参数顺序不同。
- 三个内部项目未引用 Mimir Common 类型、接口层使用 COLA 的结论来自版本级文档中的历史检查，本次未重新检查这些消费者。

## C-01：错误定义与实例分离

建议先确定一个小接口，仅提供 `String getCode()` 和 `String getMessage()`；`ErrorDefinition` 可作候选名称，由内置枚举与业务枚举实现。`IException` 不改为错误枚举接口。

### 已确认：R 使用异名入口接入错误定义（D-90）

2026-10-29 用户选择方案 A：为新错误码接口提供与 `fail` 不同名称的 R 工厂入口，避免增加 `R.fail(错误定义)` 同名重载造成源码兼容问题。`R.failCode(...)` 仅为命名示意，最终名称与完整签名仍待统一。

- 保留 `R.fail(String)`、`R.fail(String, String)` 及其现有行为，不要求消费者迁移已有调用。
- `R.fail(null)` 应继续匹配原有字符串入口，不产生新的重载歧义；旧编译产物的二进制兼容也需单独验证。
- 内置 ErrorCode 与业务错误枚举均通过独立错误定义接口接入；接口名称仍待统一，null 错误定义按 D-93 处理。

未选择的方案 B 是新增 `R.fail(错误定义)` 重载：调用形式统一，但会与 `fail(String)` 竞争 null 实参，要求旧调用方显式转换类型或迁移。

### 已确认：消息格式化由业务负责（D-91）

2026-10-29 用户选择方案 A：框架使用错误定义的默认消息，或接收业务已生成的完整消息。本轮不新增消息模板替换、格式化参数入口或国际化处理能力。

- 未显式提供消息时读取错误定义的默认 message；显式提供完整消息时按约定覆盖，不执行占位符替换或二次格式化。
- 业务自行选择格式化与国际化工具；例如“用户 123 不存在”在业务侧生成后传入框架。
- 默认消息或显式消息中的 `{}`、`%s` 等字符按普通文本处理，不由错误码接入入口解释。
- null、空白的显式消息按 D-94 处理；最终入口签名仍待统一，旧 API 的现有行为保留。

未选择的方案 B 是由框架提供可选的模板与参数格式化入口。它可减少业务重复代码，但需要额外定义占位符、转义、参数不匹配和格式错误策略，本轮不纳入。

### 已确认：业务异常采用命名工厂（D-92）

2026-10-29 用户选择方案 A：业务异常通过新增命名工厂接入错误定义接口，保留现有构造方法。`BizException.fromError(...)` 是调用示意，最终名称和完整签名仍待统一。

- 旧的字符串、内置 ErrorCode 及带 cause 的构造签名继续保留；不能直接把旧构造器参数类型改为新接口来代替新增入口。
- 新工厂承载错误定义默认消息、业务提供的完整消息及 cause，消息处理沿用 D-91；具体入口组合需避免 String 与 Throwable 重载在 null 实参下产生歧义。
- 工厂创建并返回业务异常，由调用方决定抛出时机；不改变异常处理模块的既有 HTTP 语义。
- 此项确认覆盖 BizException 的接入方式；SystemException 后续按 D-95 单独确认纳入，复用相同规则。

未选择的方案 B 是扩展接收错误定义接口的构造方法，调用形式更贴近现有 `new` 习惯，但消息和 cause 的组合会增加与旧构造器一起核对的重载情形。

### 已确认：拒绝 null 错误定义（D-93）

2026-10-29 用户选择方案 A：C-01 新增的 R 与 BizException 工厂收到 null 错误定义对象时，抛出 `IllegalArgumentException`，明确说明错误定义不能为空。

- 在读取 code、message 或构造结果前校验，避免偶然的空指针异常；不自动返回框架通用失败响应，也不以通用 BizException 代替参数错误。
- 下游需要通用错误兜底时，显式选择并传入相应错误定义。
- 此契约仅适用于新增入口，保留旧 `R.fail(null)` 与旧异常构造器的行为。
- 本次决定针对错误定义对象本身为 null；覆盖消息为 null 的含义见 D-94，code 校验见 D-96，默认 message 的空值边界仍待收敛，不混为同一个校验规则。

未选择的方案 B 是自动替换为框架通用错误码和消息，虽然方便兜底，但可能掩盖调用方遗漏错误定义的问题。

### 已确认：null 消息回退，非 null 文本原样覆盖（D-94）

2026-10-29 用户选择方案 A：C-01 新增工厂将 null 覆盖消息视为未提供覆盖值，使用错误定义的默认消息；所有非 null 覆盖文本原样保留。

| 覆盖消息输入 | 结果 |
|---|---|
| 未使用消息覆盖入口，或覆盖值为 null | 读取错误定义的默认 message |
| 空字符串 `""` | 保留空字符串 |
| 纯空白文本 | 保留原有空白，不修剪、不回退 |
| 其他文本 | 原样使用，不修剪、不做模板替换或二次格式化 |

此规则用于新增 R 与 BizException 工厂，并按 D-95 扩展到 SystemException 工厂，旧 API 行为保留。未选择的方案 B 是将 null、空字符串和纯空白文本全部回退到默认消息；当前选择尊重业务显式提供的非 null 文本。

### 已确认：系统异常同步接入错误定义（D-95）

2026-10-29 用户选择方案 A：SystemException 同步提供错误定义命名工厂，让下游按需选择业务异常或系统异常。`SystemException.fromError(...)` 为命名示意，最终名称及签名与 BizException 一起收敛。

- 保留现有字符串、内置 ErrorCode 及带 cause 的构造签名和行为，不用新接口替换旧参数类型。
- 复用 D-91、D-93、D-94：null 错误定义抛出 `IllegalArgumentException`；覆盖消息为 null 时读取默认消息，非 null 文本原样保留，不进行模板替换或国际化处理。
- 工厂支持默认消息、完整覆盖消息及 cause 的接入，创建并返回 SystemException，由调用方决定抛出时机；签名组合避免消息与 cause 的 null 重载歧义。
- 异常类型由调用方选择，不根据错误码自动切换类型。既有 MimirExceptionHandler 对 BizException 返回 HTTP 200、对 SystemException 返回 HTTP 500 的语义保持不变；下游自定义处理器仍由下游控制。

未选择的方案 B 是本轮仅为 BizException 提供新工厂，SystemException 继续使用字符串或内置枚举构造；它减少本轮新增 API，但业务错误定义接入两类异常时需要不同的调用方式。

### 已确认：新增工厂校验错误码（D-96）

2026-10-29 用户选择方案 A：新增 R 失败工厂、BizException 与 SystemException 错误定义工厂拒绝 null、空白及成功码 `00000`，抛出 `IllegalArgumentException`。

- 先按 D-93 校验错误定义对象，再校验其 code；校验失败时不创建响应或目标异常，也不自动替换为通用错误码。
- 空白包括空字符串及全为空白字符的字符串，按 `String.isBlank()` 判断；成功码按原值与 `00000` 精确比较。
- 其他非空白 code 原样保留，不修剪、不转换，不限制长度、字符格式、业务前缀或数值区间；例如 `ORDER_NOT_FOUND` 可以接入。
- 约束仅作用于这些新增工厂。内置 ErrorCode 仍可包含 SUCCESS 并实现错误定义接口，但 SUCCESS 不能用于这些失败或异常工厂；旧 `R.fail(...)`、异常构造器及成功判断行为保持不变。

未选择的方案 B 是 code 原样接收、正确性完全由下游负责。它更接近旧入口的宽松行为，但会允许新增失败工厂创建被 `R.isSuccess()` 判为成功的响应。

### 本轮推荐：默认消息允许 null，统一入口命名

历史上此问题尚未得到逐项回答，随后用户要求直接推荐并补齐 SDD。本轮推荐允许默认 message 与覆盖 message 都为 null，此时结果保留 null，不补默认文案；覆盖 message 为 null 仍先按 D-94 回退默认值。推荐接口名 `ErrorDefinition`，R 使用 `failCode`，两类异常使用 `fromError`，完整签名见[详细设计](./design.md#interface-contract)。这些是推荐草案，不记录为新的用户确认，也不追溯修改旧 API。

兼容检查需覆盖旧枚举构造描述符及 `R.fail(null)` 源码编译，不能用“新接口可接收旧枚举”代替二进制兼容结论。

## C-02：分页映射和 COLA 适配

### 原生分页映射

候选入口为 `PageResult.map(Function<? super T, ? extends U>)`，U 继续满足 `Serializable` 上界。已有 D-55 要求：源 null 元素不交给函数、原位置保留 null；函数异常原样抛出；记录顺序和数量不变。

优先讨论“新结果、新列表、复制分页元数据”的方案，避免转换对象数据时顺带重算或修正分页状态。替代方案是调用带参构造重算派生字段，它更容易保持派生值一致，但会改变 setter 已写入的元数据，并拒绝部分旧对象，需显式选择。

仍需确定：null 函数、null 数据列表、转换函数返回 null 的行为；返回列表可变性；是否支持尚未初始化的分页对象。建议 null 函数直接拒绝、转换函数返回 null 原位保留，列表与源隔离但不宣称深复制，具体异常与列表契约后续收敛。

### COLA 独立工具

按 D-66 放在 Common 的独立工具类，候选为 `page.ColaPageConverters`；`PageResult` 自身不出现 COLA 类型。工具接受分页源及逐条转换函数，是否另有保持元素类型的便利入口后续讨论。

已确认超过 int 范围抛参数异常，不截断、不饱和。还需确定 null、负总数、页码/页大小小于 1 的处理；建议先完成所有元数据校验，再执行用户函数，避免必然失败的请求已产生部分映射副作用。

输出建议使用成功的 COLA 分页响应，保留原总数、页码、页大小及记录顺序，空集合按 COLA 规则表现为空列表。分页转换本身不将异常包装成 COLA 失败响应。

可选依赖必须分别验证：有 COLA 时转换可用；无 COLA 时普通 Common 模型加载、Jackson 扫描和序列化仍可用。直接调用 COLA 工具却不提供 COLA 依赖不在“无依赖仍可用”的承诺内。

## C-04：批量结果协议

批次汇总持有由逐项结果列表派生的总数、成功数、失败数和列表；逐项结果持有输入关联、成功标记及成功数据或错误定义。关联可使用输入标识或序号，结果列表保留调用方提供的原顺序。

| 待选择项 | 优先讨论方向 | 替代与代价 |
|---|---|---|
| 输入关联 | 输入标识或从 0 开始的序号至少提供一种 | 标识可能缺失或重复；序号适合调用方按输入位置关联 |
| 数量一致性 | 总数及成功/失败数由所持逐项结果列表派生，不开放独立数量 setter；输入完整性由业务保证 | 模型无法独立判断是否遗漏了输入 |
| 总体状态 | 派生全部成功、部分成功、全部失败；空批次单独规定 | 仅一个 success 更简单，但需解释部分成功与空批次 |
| 对象形态 | 不可变结果及列表快照，构造时校验 | 延续可变 JavaBean 更贴近现有模型，但容易破坏不变量 |

必须讨论的不变量：总数=成功数+失败数=逐项数；每项至少有一种可用输入关联；成功项不能携带错误，失败项不能同时携带成功数据；错误码是否允许成功码、空白消息如何处理。

外层 `R` 的成功应表达“请求已处理”还是“每项均成功”尚未确认；不能让批次处理成功、逐项结果部分失败和 HTTP 失败混为一谈。空批次及成功数据允许 null 也需形成明确例子后定稿。

本协议只承载结果，不自动捕获执行异常，不决定事务回滚或重试，不新增批量执行器。

## C-07：序列化收敛与迁移

优先讨论由 Web 注册 Jackson MixIn 忽略 `isFail()`，Common 保留 Java 方法、派生逻辑和无 Jackson 运行时依赖的边界。备选是消费者逐个注册 MixIn，侵入更少但接入和迁移成本分散。

必须按 mapper 来源划定作用域：Boot 管理的共享 ObjectMapper 及由其受管 builder 创建的 mapper 可能同时服务 MVC、消息、缓存或 HTTP 客户端；业务自行 `new ObjectMapper()`、完全自定义 mapper 和 fastjson2 不会自动得到相同规则。不能宣传为全局移除所有 JSON 的 fail。

过渡开关可考虑 `mimir.boot.web.json.include-legacy-fail`，旧字段恢复与否由该入口表达；该名称、默认值、保留周期以及消费者已有 MixIn 的优先级均为讨论项，尚未确认。版本方向已确认收敛到 success，不因此推导出迁移开关的默认策略。

Common 单独使用时仍可能输出 fail；Web 的测试应验证受管 mapper 的新协议，Common 的测试仍需体现独立使用的真实行为。消费者升级说明需覆盖依赖 fail 的客户端及复用受管 mapper 的持久化格式。

## 模块依赖与边界

- BOM：仅管理 `com.alibaba.cola:cola-component-dto:5.0.0`，不引入 COLA 全家桶。
- [MyBatis](../mybatis-capabilities/brainstorm.md)：复用 D-55 与 int 范围语义，承接 `IPage` 到 COLA；可选依赖和类加载隔离须两边一致。
- Web：承接 C-07 Jackson 集成与过渡配置；Common 负责定义语义和独立使用边界。
- Exception：继续处理异常实例；错误定义接口不改变既有处理器对业务异常 HTTP 200、系统异常 HTTP 500 的语义。

## 后续验收方向

- 编译及二进制兼容：旧调用保留，新业务枚举可接入 R、BizException 与 SystemException，null 重载无新增歧义；验证消息、null 错误定义、无效 code、cause 及既有异常处理语义。code 覆盖 null、空字符串、纯空白、成功码和业务自定义字符串，并核对旧入口行为未变。
- 分页：顺序、元数据、null、映射异常身份、列表隔离、int 上下界及异常状态；有/无 COLA 的消费者类路径分别验证。
- 批量：空批次、部分成功、两种关联方式、关联缺失/无效、重复标识、列表顺序保留及互斥字段；不校验输入完整性或序号连续性。
- C-07：Common 独立路径、Web 受管 mapper、手工 mapper、过渡配置及已有自定义 MixIn，分别给出实际结果。

以上历史验收方向已展开到[规格](./spec.md)、[设计测试矩阵](./design.md#testing-strategy)与[实施计划](./plan.md)，并非测试已通过。本轮核对当前源码与本地 COLA 源码包并补齐 SDD，未修改 Java/POM、运行功能测试或验证真实消费者。
