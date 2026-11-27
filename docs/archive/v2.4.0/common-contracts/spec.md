---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Common 公共契约规格

导航：[需求大纲](./brainstorm.md) · [详细设计](./design.md) · [实施计划](./plan.md) · [版本大纲](../brainstorm.md)

## Overview

业务方可以复用自己的错误定义、映射分页记录、输出完整批量结果，并在 Web 受管序列化路径使用单一成功标记。

D-20 至 D-24、D-39、D-55、D-62 至 D-69、D-90 至 D-96 中仍有效的决定为既有约束。本文其余细节是用户要求“先给推荐方案、补齐 SDD”后的推荐草案，不等于逐项确认，也不代表代码已实施。历史选择与本轮推荐的对应见[设计决策](./design.md#关键决策)。

2026-10-31 审查补充了 null 默认消息的跨模块输出形态（B1）、批量结果不承诺跨服务 JSON 反序列化（B5、Constraints）及受管 JSON 的 Web 生效前提（B6）三项推荐约束，同样不是新的用户确认决定。

## Behavior B1：按错误定义创建失败响应

### Scenario：业务错误及完整消息

Given 错误定义 code 为 `ORDER_NOT_FOUND`、默认消息为“订单不存在”，覆盖消息为“订单 123 不存在”  
When 业务创建失败响应  
Then code 为 `ORDER_NOT_FOUND`，message 为“订单 123 不存在”，data 为 null，成功标记为 false  
And `{}`、`%s` 等字符不被格式化，code 的前后空白不被修剪。

### Scenario：消息回退及空值

Given 一个有效错误定义  
When 未提供覆盖消息或覆盖消息为 null  
Then 使用其默认消息；默认消息也为 null 时仍允许创建，结果 message 为 null  
And 覆盖消息为 `""`、`"  "` 时分别原样保留，默认消息不覆盖显式非 null 文本。

### Scenario：null 消息的跨模块 JSON 形态（审查补充）

Given 业务异常由默认消息为 null 的错误定义创建且未提供覆盖消息，经 Exception 现有处理器写出统一响应，Web 使用默认空值策略（`mimir.boot.web.serialization.write-nulls=false`）  
When 序列化该失败响应  
Then 失败响应 Java 对象的 message 为 null，JSON 中不出现 `message` 键，而不是输出 `"message": null`；Web 开启 write-nulls 时才输出 `"message": null`  
And 该形态由现有处理器与 Web 空值策略共同决定，Common 只记录协议形态、不补默认文案；实际输出在 Exception/Web 联合验收（Web W-T4）中验证。

### Scenario：拒绝无效错误定义

Given 错误定义为 null，或其 code 为 null、`""`、`" \t"`、精确的 `00000`  
When 业务创建失败响应  
Then 抛出参数异常，不创建失败响应，不自动选择通用错误  
And `" 00000 "`、`"ORDER_NOT_FOUND"` 属于允许原样保留的非空白 code，成功标记均为 false。

### Scenario：既有调用继续兼容

Given 旧消费者使用字符串失败入口，包含单个 null 消息及传入成功码的历史调用  
When 使用升级后的 Common 重新编译或运行旧编译产物  
Then 原调用仍可链接，单个 null 不产生新增重载歧义，旧入口保留现有宽松语义  
And 新增入口的参数校验不追溯到旧入口。

## Behavior B2：按错误定义创建业务异常与系统异常

### Scenario：保持异常类型及 cause

Given code 为 `ORDER_NOT_FOUND`、默认消息为“订单不存在”、cause 为指定异常实例  
When 业务选择创建业务异常或系统异常，并提供消息“订单 123 不存在”  
Then 创建所选类型，保留 code、覆盖消息和同一个 cause 实例，不自动抛出  
And 由框架现有处理器处理时，业务异常为 HTTP 200，系统异常为 HTTP 500；不根据 code 自动切换类型。

### Scenario：无 cause 与无消息

Given 有效错误定义的默认消息为 null，且覆盖消息和 cause 都为 null  
When 业务创建任一类异常  
Then message 与 cause 都为 null，工厂正常返回  
And 非 null 空白消息原样保留，不从 cause 的文本推导默认消息。

### Scenario：无效输入及旧构造

Given 错误定义或 code 不满足 B1 的新入口约束  
When 业务通过新入口创建任一类异常  
Then 抛出参数异常，不创建目标异常  
And 原有全部字符串、内置枚举、带 cause 的构造方法保持原描述符与行为；新增入口不引入消息与 cause 的 null 重载歧义。

## Behavior B3：分页记录逐条映射

### Scenario：新对象且元数据原样保留

Given 分页数据为 `[1, 2]`，总数为 20、页码为 2、页大小为 5，且业务曾把总页数改为 99  
When 将每条记录转换为文本  
Then 新结果的数据为 `["1", "2"]`，顺序及数量不变，所有分页元数据仍与源对象相同，包括总页数 99  
And 新结果和非 null 数据列表均与源对象独立；修改任一列表的结构不改变另一列表，不承诺元素深复制。

### Scenario：null、空列表与未初始化元数据

Given 数据为 `[1, null, 2]`，其中 2 的转换结果为 null  
When 使用有效转换函数逐条映射  
Then 数据为 `["1", null, null]`，函数只收到两个非 null 元素  
And 源列表为 null 时结果列表仍为 null，源为空列表时返回新的可变空列表；元数据为 null 或不一致时按原值复制，不重算、不拒绝。

### Scenario：函数缺失及转换失败

Given 任意分页数据，包括 null 或空列表  
When 转换函数为 null  
Then 在处理数据前抛出参数异常  
And 有效函数在第 2 条非 null 记录抛异常时，调用方收到同一异常实例，后续记录不再处理，源分页不被框架修改；函数已经产生的业务副作用不回滚。

## Behavior B4：分页转换为 COLA 响应

### Scenario：保留计数与记录顺序

Given 总数为 20、页码为 2、页大小为 5，记录为 `[1, null, 2]`  
When 业务转换为 COLA 分页响应，可选择保持元素或逐条映射  
Then 输出成功标记为 true，错误码和错误消息为空，总数、页码、页大小为 20、2、5  
And 记录顺序和数量不变，null 元素不交给函数，函数返回的 null 原位保留，非 null 源列表与返回列表结构独立。

### Scenario：合法边界及空数据

Given 总数在 0 至 2147483647 之间，页码与页大小在 1 至 2147483647 之间  
When 转换为 COLA 响应  
Then 三项整数按原值保留，不截断、不饱和；页码超过实际总页数也允许  
And 源列表为 null 或空时，响应读取为空列表；不承诺空列表可变性，非空返回列表可变；总页数由目标响应计算，源自定义派生标记不转移。

### Scenario：先校验后映射

Given 源为 null、转换函数为 null，或任一必需元数据为 null、越界、总数为负、页码或页大小小于 1  
When 转换为 COLA 响应  
Then 抛出参数异常，转换函数被调用 0 次，不返回 COLA 失败响应  
And 元数据合法但函数抛异常时，按 B3 原样传播同一异常实例，不继续处理后续记录。

### Scenario：可选依赖隔离

Given 消费者只依赖 Common，未提供 COLA 运行时依赖  
When 加载普通响应、异常、分页及批量模型，或用其已有 Jackson 扫描并序列化这些模型  
Then 普通 Common 路径可用，不因这些模型暴露 COLA 类型而链接失败  
And 直接使用 COLA 转换入口需要消费者显式提供 COLA；该条件不满足时不承诺转换可用，也不自动下载或降级。

## Behavior B5：构造完整批量结果

### Scenario：部分成功及按输入标识或序号关联

Given 已完成的逐项结果包含第 0、2 项成功和第 1 项失败，失败码为 `ORDER_NOT_FOUND`；关联可提供序号或业务标识  
When 创建批量结果  
Then 总数为 3、成功数为 2、失败数为 1，状态为 `PARTIAL_SUCCESS`，按传入顺序完整保留三项  
And 提供序号时必须非负，未提供序号时必须有非空白业务标识；两者可同时提供，业务标识允许重复；成功项可有 null 数据，错误码与消息为空；失败项无成功数据。

### Scenario：空批次与全量状态

Given 结果列表为空  
When 创建批量结果  
Then 三个计数均为 0，状态为 `EMPTY`，结果列表非 null 且为空  
And 非空批次全部成功为 `ALL_SUCCESS`，全部失败为 `ALL_FAILED`；数量始终满足总数＝成功数＋失败数＝逐项数。

### Scenario：拒绝无关联条目与无效错误

Given 列表为 null、含 null 条目，或条目提供了负序号，或未提供序号且业务标识为 null/空白  
When 创建批量结果  
Then 抛出参数异常  
And 创建失败项时拒绝 B1 所列无效错误定义，允许默认消息和覆盖消息均为 null；成功数据与失败错误不能同时存在。

### Scenario：保留按完成顺序收集的结果

Given 结果条目按完成顺序收集，序号可以缺项或不递增，业务标识可以重复  
When 创建批量结果  
Then 列表保持传入顺序，计数仅由实际提供的条目派生，不补齐或重排条目  
And 是否覆盖全部原始输入由业务执行方保证，模型不接收原始输入总数或验证完整性。

### Scenario：结果稳定且可输出

Given 已构造结果且业务之后修改创建时提供的列表  
When 读取或序列化批量结果  
Then 批量结果条目及计数不变，返回列表不能增删或替换条目，无公开 setter；不承诺成功数据对象深不可变  
And 带有可序列化数据的批量结果可作为统一响应的数据，能执行 Java 序列化往返及普通 Jackson 输出；JSON 空值是否出现由当前 mapper 的空值策略决定。

### Scenario：跨服务 JSON 读取不在支持范围（审查补充）

Given 下游服务通过 Feign 等 HTTP 客户端，以 `R<BatchResult<T>>` 作为响应类型读取上游输出的批量结果 JSON  
When 使用 Jackson 反序列化该响应  
Then 框架不承诺、不验收跨服务反序列化：在当前技术栈上批次的单参私有构造不被 Jackson 识别为创建器，读取会失败；逐项结果的私有多参构造则可能在 `-parameters` 与参数名模块下被创建并绕过工厂。该现状依赖 Jackson 启发式规则，可能随版本或配置改变，测试不得断言反序列化失败或成功  
And 经工厂或 Jackson 创建器产生的批量模型满足关联规则、成功/失败互斥，且计数与状态始终由条目派生：JSON 中的成功标记、计数、状态键不能写入对象（见设计 I5 构造约束与派生值）；不承诺防御反射直接改字段或篡改的 Java 序列化流。调用方需按设计的 JSON 字段自行定义接收 DTO，框架不为此新增 Common 的 Jackson 运行时依赖或公开构造入口。

### Scenario：批次处理与请求成功分开

Given 一个已经处理完成、含失败项的批次  
When 业务把完整批次放入成功响应  
Then 外层成功标记为 true，逐项失败及批次状态仍如实保留  
And 未能形成完整批次的请求级失败继续使用现有失败响应或异常协议；结果模型不执行任务、不捕获业务异常、不决定事务或重试。

## Behavior B6：受管 JSON 只输出 success 并提供过渡策略

### Scenario：默认新协议

Given Servlet 应用且 `mimir.boot.web.enabled=true`（由 Web 自动配置提供），Web 的默认配置生效，使用由其配置的 Boot 共享 mapper 或受管 builder 创建的 mapper，且无业务自定义响应 MixIn  
When 序列化统一响应  
Then JSON 有 success 且无 fail，success 仍由 code 是否为 `00000` 派生  
And Java 的成功与失败判断均可调用；满足上述前提时，共享 mapper 的消息、缓存和 HTTP 客户端用途也遵循该输出；非 Servlet 应用或关闭 Web 时共享 mapper 仍输出 fail（审查补充）。

### Scenario：过渡及业务覆盖

Given 业务启用保留旧字段的过渡配置，且没有自定义响应 MixIn  
When 序列化统一响应  
Then 同时输出 success 和 fail，二者互为取反  
And 业务显式提供自己的响应 MixIn 时由业务规则优先，框架不强制覆盖；过渡配置不强制恢复被业务规则主动移除的字段。

### Scenario：非受管路径与非法配置

Given 业务仅使用 Common、手工创建 mapper、完全自定义 mapper，或使用其他 JSON 库  
When 序列化统一响应  
Then 本需求不自动改变这些路径的 fail 输出，不把它们算作受管协议验收通过  
And 过渡配置提供无法绑定的布尔文本时遵循现有配置绑定失败行为，不能悄悄按 false 继续。

## Constraints

- Java 基线为 17；既有公共方法和构造描述符删除数量为 0，`R` 与原生分页数据的 `Serializable` 上界保持不变。
- 新增 Common 的 Spring、Jackson 运行时依赖数量为 0；COLA 为单一可选 DTO 依赖，其缺失不影响普通模型。
- n 条记录的映射最多调用函数 n 次，null 元素调用 0 次；框架不启动并行任务、不增加 I/O、不重试。
- 批次计数范围为 0 至 2147483647（对应 Java 列表大小）；结果列表的完整性由业务执行方保证，模型不校验原始输入数量、序号连续性或条目排序；不增加人为批次上限，不提供大批次流式执行保证。
- `R<BatchResult<T>>` 只承诺作为服务端输出模型；不承诺、不验收作为跨服务（含 Feign）JSON 反序列化的目标类型，测试不得断言反序列化失败，调用方需自建 DTO（审查补充）。
- 本轮不纳入无总数分页、多字段排序、整页列表映射、统一响应数据映射、COLA 多条响应转换、批量执行器或事务策略。
