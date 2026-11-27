---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Common 公共契约实施计划

导航：[需求大纲](./brainstorm.md) · [规格](./spec.md) · [详细设计](./design.md) · [版本大纲](../brainstorm.md)

## Goal

实现 C-01 错误定义、C-02 分页映射与可选 COLA 转换、C-04 完整批量协议，联动 Web 交付 C-07 的受管 JSON 迁移。Java 旧入口兼容，所有新增行为与边界遵循 B1 至 B6。

本计划是用户要求补齐 SDD 后形成的推荐实施输入；本轮只交付文档，尚未授权或执行 Java/POM 修改。推荐细节不等于用户逐项确认，历史 D 决策优先；真正开始实施时以当时用户授权为准。

## Context

- 源码基线：Java 17；Common 无 Spring/Jackson 运行时依赖，Jackson 仅测试；现有 ErrorCode 是枚举，IException 表示异常实例。
- `R.fail(String)` 与现有枚举异常构造已经公开，新增接口不能替换旧描述符；`R.fail(null)` 的可编译性需专门保留。
- `PageResult` 无参构造/setter 可以保存 null 或不一致元数据。原生 map 必须保留这些值；COLA 转换单独严格校验三个必要整数，避免被 COLA 自身的页码/大小校正掩盖无效输入。
- COLA 5.0.0 本地源码的四参顺序是 data、totalCount、pageSize、pageIndex；其返回泛型无 Serializable 上界。
- Web 和 Exception 的实现/测试归各自需求，不能在多个任务中同时编辑同一配置或处理器测试文件。Common 不新增反向依赖。

## Key Decisions

1. `exception.ErrorDefinition` 提供 getCode/getMessage，ErrorCode 实现；R 新增 failCode，两类异常新增 fromError。保留所有旧工厂和构造，IException 不变。
2. 新入口 null 定义、null/blank/精确 `00000` code 抛 `IllegalArgumentException`；其余 code 原样。message 为 null 回退默认，默认也 null 时允许；其他消息包括空白原样保留。
3. fromError 只有一参、二参 String、三参 String+Throwable；默认消息加 cause 使用 `(error, null, cause)`，cause 身份保留。
4. 原生 map 新结果/新可变列表，保留全部六个元数据；null 列表保留。null mapper 拒绝，元素 null 跳过并保留，返回 null 保留，异常原样抛出。
5. `page.ColaPageConverters.toCola(PageResult<T>)` 与 `toCola(PageResult<T>, Function<? super T, ? extends R>)`；输出 R 无 Serializable 上界。source、mapper、元数据全部校验后才映射。COLA optional 且由 BOM 管理 5.0.0。
6. batch 模型不可变，`BatchResult.of(items)` 保留列表原顺序并从列表派生计数，不校验输入完整性或序号连续性；EMPTY 为独立状态，只承载结果，不做执行事务。
7. C-07 默认 `mimir.boot.web.json.include-legacy-fail=false`，无业务显式 R MixIn 时受管 JSON 无 fail；true 不安装忽略规则。业务 MixIn 优先。Common 独立/手工 mapper 不自动改变。2.x 保留开关，下个主版本再评估删除，不自动到期。

2026-10-31 审查补充（推荐，非新 D 决策；其中 MyBatis 工具名引用用户确认的 D-99）：

- C-07 受管 JSON 以 Servlet 应用且 `mimir.boot.web.enabled=true`（由 Web 自动配置提供）为前提；非 Servlet 或关闭 Web 时共享 mapper 仍输出 fail。

- `R<BatchResult<T>>` 的跨服务（含 Feign）JSON 反序列化不承诺、不验收，测试不得断言反序列化失败或成功：当前 Jackson 2.17.3 不识别 `BatchResult` 的单参私有构造，读取确定失败；`BatchItemResult` 的私有多参构造在 `-parameters` 与参数名模块下可能被创建；该现状依赖 Jackson 启发式规则，不作为契约。调用方自建 DTO；不为此增加反序列化入口。
- 批量模型实例字段只存私有构造参数（字段与参数同名一一对应），校验在构造内；成功标记、计数、状态不存字段，在 getter 中由已校验状态计算，避免 Jackson 默认 `INFER_PROPERTY_MUTATORS`/`ALLOW_FINAL_FIELDS_AS_MUTATORS` 在创建器构造后用 JSON 覆盖派生字段（已本地复现）。计数读取因此放宽为 O(n)。
- 默认/覆盖消息均为 null 时，经 Exception 处理器写出的 R message 为 null，Web 默认 write-nulls=false 下 JSON 无 `message` 键；Common 只在文档中记录，实际形态由 Web W-T4 联合验收。
- `RTest` 现有 fail 断言保持不变（Common 独立路径仍输出 fail），取代版本大纲“同步调整 RTest”一句。
- Common 新工具最终名为 `ColaPageConverters`，区别于 MyBatis 既有 `PageConverters` 及按 D-99 新增的 `IPageColaConverters`；`ErrorDefinition`（错误定义）与 `IException`（异常实例）签名相同但语义分开，见设计 I1。

完整签名、字段和 JSON 例子必须读取[设计的 Interface Contract](./design.md#interface-contract)，不能按本段缩写猜测重载。

## Behavior Contract

| 规格 | 可观察结果 | 任务 |
|---|---|---|
| B1 | 业务枚举生成失败响应，默认/覆盖/双 null 保留约定；旧 fail(null) 可编译；双 null 的 JSON 无 `message` 键形态由联合验收确认 | T1、T5、外部 W/E |
| B2 | 两类异常工厂保持 cause、类型和 HTTP 200/500；旧构造仍可链接 | T1、T5、外部 E |
| B3 | 映射保留顺序、数量、六字段和 null；函数次数与异常身份可断言 | T2 |
| B4 | int 校验先于映射，success=true；无 COLA 的普通模型正常加载序列化 | T3、T5、外部 B/M |
| B5 | 完整逐项结果、互斥字段、派生计数、不变列表、序列化可用；README 写明跨服务反序列化需自建 DTO | T4、T5 |
| B6 | 默认 success 单字段、迁移恢复、业务覆盖优先、非受管路径明确 | 外部 W、T5 |

## Task Dependencies

| 任务 | 依赖 | 独占范围 |
|---|---|---|
| 外部 B：COLA BOM 管理 | 无 | BOM 需求拥有 `mimir-boot-bom/pom.xml`，Common 不代改 |
| T1：错误定义与命名工厂 | 无 | Common exception 包既有三类及新增接口、R.java、对应错误/响应测试 |
| T2：原生分页 map | 无 | PageResult.java、PageResultTest.java、分页泛型测试 |
| T3：COLA 适配 | 外部 B、T2 完成后交接 | Common POM、新 ColaPageConverters.java 及独立转换/隔离测试；不与 T2 同改 PageResult |
| T4：批量结果 | T1 | Common batch 包及其测试 |
| 外部 W：C-07 Jackson 接入 | 本设计 I6 已锁定；集成验收等 T1/T4 | Web 需求拥有 JacksonConfig/WebProperties 及 Web 测试 |
| 外部 E：异常 HTTP 回归 | T1 | Exception 需求拥有其处理器测试，Common 不改处理器 |
| 外部 M：IPage 转 COLA | 外部 B、T3 的语义锁定 | MyBatis 需求独立适配，不由 Common 反向依赖 |
| T5：兼容、消费者与文档整合 | T1 至 T4、外部 W/E/M | Common README、Common 兼容测试及最终文档整合；此时才合并其他任务已有测试修改 |

T1、T2 可并行；T3 在 T2 交接后可与 T4 并行。T5 串行收口，避免共同修改 README 或同一测试。上述外部任务只表示依赖和验收责任，不是在此重复授权其他模块写入。

## Tasks

### T1：错误定义与命名工厂

目标：完成 I1、I2，覆盖 B1、B2 的本地对象行为。

文件：

- Create: `mimir-boot-common/src/main/java/com/yggdrasil/labs/common/exception/ErrorDefinition.java` — 只含 code/message 访问契约。
- Modify: `mimir-boot-common/src/main/java/com/yggdrasil/labs/common/exception/ErrorCode.java` — 实现接口，保留枚举常量与现有方法。
- Modify: `mimir-boot-common/src/main/java/com/yggdrasil/labs/common/exception/BizException.java`、`SystemException.java` — 各增三种 fromError。
- Modify: `mimir-boot-common/src/main/java/com/yggdrasil/labs/common/response/R.java` — 增两种 failCode。
- Modify: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/exception/ErrorCodeTest.java`、`response/RTest.java`、`response/RGenericBoundCompilationTest.java` — 现有行为与新增接口回归；`RTest` 只新增 failCode 用例，现有序列化用例中的 fail 断言及字段总数 7 不修改。
- Create: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/exception/ErrorDefinitionFactoryTest.java` — 两类异常、消息/code/null/cause 参数矩阵及旧描述符检查。

行为约束：按 I1/I2 先校验再创建，code 只按 null/isBlank/精确成功码拒绝；保留 `" 00000 "`。双 null 消息允许，三参 cause 原样。BaseException、IException 不需要改公共结构；如内部去重需要同包辅助实现，不额外暴露公共 API。

验收：先以新枚举和 null 调用编译用例复现缺少接口，再实现；新 API 全矩阵通过，旧 `R.fail(null)` 重新编译成功；原构造和工厂全部仍存在。R 的 timestamp、traceId、data 和 Java 派生成功判断保持既有行为；`RTest` 原 fail 断言未改动且通过。HTTP 验收由外部 E 完成，单纯对象测试不能代替它。

### T2：原生分页 map

文件：

- Modify: `mimir-boot-common/src/main/java/com/yggdrasil/labs/common/page/PageResult.java` — 新 map，不引入 COLA 类型。
- Modify: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/page/PageResultTest.java` — 元数据、null、列表隔离与异常回归。
- Create: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/page/PageMappingCompilationTest.java` — Serializable 输出上界及 Function 通配符编译用例。

验收：原对象与结果、非 null 数据列表均不是同一引用；结果列表可增删；六个元数据包括 null、负值或不一致值原样；null 源列表保留；非 null 元素每个调用一次。遇第二条异常时同实例抛出且第三条未被调用；不更改源列表。null mapper 即使面对空列表也抛参数异常。非 Serializable 原生输出仍编译失败。

### T3：COLA 转换及可选依赖

文件：

- Modify: `mimir-boot-common/pom.xml` — 加入 BOM 管理的 cola-component-dto optional 依赖，Jackson 仍 test scope；审查补充：另加 test scope 的 `jackson-module-parameter-names`（版本由 BOM 管理），否则 I5 反序列化用例全部走"抛异常"分支，不变量断言不会执行。
- Create: `mimir-boot-common/src/main/java/com/yggdrasil/labs/common/page/ColaPageConverters.java` — 两种静态转换入口。
- Create: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/page/ColaPageConvertersTest.java` — 参数、数据与映射异常验证。
- Create: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/page/ColaOptionalDependencyTest.java` — 有/无 COLA 的实际隔离加载及序列化验证。
- Modify: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/page/PageMappingCompilationTest.java` — 由 T2 交接后串行补非 Serializable COLA 输出可编译用例。

步骤约束：source/mapper → 三项元数据快照及校验 → 顺序数据映射 → 使用正确的 COLA 参数顺序。不要先 map 后校验；不要把 COLA 引用放进 PageResult；不要以捕获 LinkageError 模拟可选依赖隔离。

验收：0 总数、1 页码/大小和 int 最大值合法；null、负总数、零/负页码大小、`2147483648L`、`Long.MAX_VALUE` 均在 mapper 0 次时拒绝。有效对象 success=true，errCode/errMessage=null，页码/大小无对调；返回集合隔离。默认映射与显式 identity 一致。无 COLA classpath 的隔离测试必须真正阻止父加载器提供 COLA，并实际加载、反射和 Jackson 序列化 R、PageResult 及异常；batch 在 T4 完成后由 T5 补入相同矩阵；测试不得只是断言 optional XML。

依赖图核对须确认下游只声明 Common 时没有自动传递 COLA。源代码消费者声明 COLA 时两个入口可编译、可运行。外部 M 用相同边界测试矩阵验证 IPage 适配，Common 不增加 MyBatis 依赖。

### T4：不可变批量协议

文件：

- Create: `mimir-boot-common/src/main/java/com/yggdrasil/labs/common/batch/BatchStatus.java`。
- Create: `mimir-boot-common/src/main/java/com/yggdrasil/labs/common/batch/BatchItemResult.java`。
- Create: `mimir-boot-common/src/main/java/com/yggdrasil/labs/common/batch/BatchResult.java`。
- Create: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/batch/BatchResultTest.java` — 条目及批次约束。
- Create: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/batch/BatchSerializationTest.java` — Java 往返、Jackson 输出及 R 嵌套。

行为约束：严格依照 I5 签名，私有构造、final 字段、无 setter；实例字段只存私有构造参数且同名一一对应（`BatchItemResult` 为 index/inputId/data/code/message，`BatchResult` 仅 items）。`BatchResult` 私有构造只接收 items 并在构造内校验，`BatchItemResult` 的关联、互斥及 code 校验在构造内执行，使工厂与 Jackson 创建器路径都经过校验；`isSuccess()`、三个计数与状态在 getter 中计算，不存字段、不加 transient 缓存（审查补充）；保存不可修改列表快照并保留传入顺序。`BatchResult.of(items)` 不接收原始输入总数，不校验条目是否完整、序号是否连续/唯一或是否按输入顺序排列；三个计数从实际条目派生。条目至少提供一种关联：非 null index 须非负；缺少 index 时 inputId 须为非空白文本。index 存在时 inputId 原样保留且可重复。失败项校验复用 I1 语义，不保存 Throwable 或 ErrorDefinition 对象；data 允许 null。

验收：EMPTY、ALL_SUCCESS、PARTIAL_SUCCESS、ALL_FAILED 各有用例；验证两种关联方式、无效/缺失关联、重复业务标识及按传入顺序保留（包括按完成顺序收集的非递增序号）。不以缺项、重复序号、乱序或数量不符拒绝结果；null 列表/元素仍拒绝。失败的成功数据为空，成功的错误字段为空；双 null 消息允许。原列表修改不改变批次，返回列表拒绝修改；Java 序列化往返正常数据图，默认 ObjectMapper JSON 字段和计数匹配设计例子；`R<BatchResult<String>>` 可用。派生值验收采用“若反序列化成功，则不变量成立”：以测试范围 Jackson 模拟 Boot 配置读取含矛盾派生键（如 `{"index":1,"code":"E","success":true}`、计数与 items 不符的批次）及无效关联的 JSON，未抛异常时断言 `isSuccess()==(code==null)`、计数/状态等于由条目派生值、关联规则成立，抛异常同样通过；另以反射核对实例字段集合等于私有构造参数名且无派生字段。不再反射调用私有构造。跨服务反序列化不承诺、不验收，测试不得断言反序列化失败或成功；不要为实现反序列化新增 Jackson 运行时依赖，也不增加 `@JsonCreator`、公开无参构造或 setter 来迁就跨服务反序列化。

### T5：兼容性、跨模块与接入文档收口

文件：

- Modify: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/page/ColaOptionalDependencyTest.java` — T4 完成后补入 batch 普通模型无 COLA 加载及序列化矩阵。
- Create: `mimir-boot-common/src/test/java/com/yggdrasil/labs/common/response/CommonApiCompatibilityTest.java` — JavaCompiler 旧调用源码及 public/protected 描述符回归。推荐复用既有 [LoggableCompatibilityTest](../../../../mimir-boot-common/src/test/java/com/yggdrasil/labs/common/annotation/LoggableCompatibilityTest.java) 模式：历史 API 源码与预编译消费者放入测试资源 fixture（参照 [compat/PrecompiledLoggableConsumer](../../../../mimir-boot-common/src/test/resources/compatibility/loggable-pre-v2.2.1/com/yggdrasil/labs/common/compat/PrecompiledLoggableConsumer.java)），在临时目录一起编译后删除历史 API 的 class，再以当前 Common 类加载消费者并调用。
- Create: `mimir-boot-common/src/test/resources/compatibility/common-api-v2.3.0/` — 推荐目录名；含取自 v2.3.0 标签的 R、ErrorCode、IException、BaseException、BizException、SystemException 等最小历史源码，及 `com/yggdrasil/labs/common/compat/` 下覆盖旧 R 工厂与两类异常四种构造的预编译消费者；fixture 头部注明来源标签。
- Modify: `mimir-boot-common/README.md` — 新 API 示例、null/code/映射语义、批量状态及 optional COLA 接入；说明 Java 序列化与 JSON 输出边界。审查补充四项必写：`R<BatchResult<T>>` 的跨服务（含 Feign）JSON 反序列化不承诺、不验收，调用方需自建 DTO；`ErrorDefinition` 表示错误定义、`IException` 表示异常实例，签名相同但不可互换；双 null 消息在 Web 默认空值策略下 JSON 无 `message` 键；受管 JSON 不输出 fail 的前提为 Servlet 应用且 `mimir.boot.web.enabled=true`（由 Web 自动配置提供），非 Servlet 或关闭 Web 时共享 mapper 仍输出 fail。
- Modify: `ARCHITECTURE.md` — 仅在实际实施后由文档整合者同步 Common 的 optional COLA 事实，不改依赖方向。
- Modify: `docs/active/v2.4.0/release.md` — 实施验收后由版本负责人登记 C-07 JSON 默认变化、迁移开关及实际证据；本计划阶段不预填完成。
- 同步入口：Web README/测试由外部 W 修改，Exception HTTP 回归由外部 E 修改，MyBatis COLA 示例由外部 M 修改，BOM 文档由外部 B 修改，Common 不重复编辑其 ownership。

兼容与消费者验收：

1. 模块内回归按上方 LoggableCompatibilityTest 模式执行，属于源码 fixture 近似验证。另使用真实 v2.3.0 Common 制品编译旧消费者，覆盖 R 两种 fail、success、两类异常各四种构造；保留该字节码，仅替换为实施后的 Common 运行，核对无链接错误。基线制品与消费者来源、版本/摘要留在验证记录中；没有真实基线时如实标记二进制验证未完成，不能拿仅针对新版本重新编译代替。临时消费者放临时验证目录，不新增 tools 或仓库专项脚本。
2. 单独重新编译包含 `R.fail(null)` 的旧源码与全部新入口样例；验证 R/PageResult 上界未放宽，而 COLA 输出没有额外上界。
3. 对比有/无 COLA 的消费者依赖图和实际运行；隔离类加载测试与真实消费者结果分别报告，不将前者写成发布制品验证。
4. 与外部 E 共同验证新 BizException/SystemException 经 HTTP 处理仍为 200/500；双 null 消息在 Web 默认 write-nulls=false 下 JSON 无 `message` 键、开启后为 `"message": null` 的形态由 Web W-T4 联合验收覆盖，Common 只核对 README 描述与之一致。与外部 W 共同验证默认无 fail、true 双字段、业务 MixIn 优先及非受管路径。满足 Servlet 且 `mimir.boot.web.enabled=true` 前提时，Web 共享 mapper 在非 MVC 用途也会变更，文档不能宣称“仅 MVC 生效”；非 Servlet 或关闭 Web 时共享 mapper 仍输出 fail，文档也不能宣称“所有共享 mapper 均无 fail”。
5. 文档示例能编译，Java 序列化/普通 Jackson 输出的承诺有本地测试证据；不写“全部 JSON 序列化器统一”“自动执行批次”或“已迁移所有消费者”。

## Integration Acceptance

实施后按真实改动范围执行，以下为计划命令，本轮没有运行：

```bash
# Common 单元、编译和类加载测试
./mvnw -pl mimir-boot-common -am test

# Common 与相关 Starter 集成回归（含 Failsafe）
./mvnw -pl mimir-boot-common,mimir-boot-starters/mimir-boot-starter-web,mimir-boot-starters/mimir-boot-starter-exception,mimir-boot-starters/mimir-boot-starter-mybatis -am verify
```

读取本次 Surefire/Failsafe 报告确认目标用例实际执行，不以退出码或陈旧报告代替。Common 模块测试不等于 HTTP 集成、真实下游、CI 或发布消费者验证。最终仓库验收由版本整合者依据[测试规程](../../../engineering/testing.md)选择统一入口；本任务不改工具链，不把专项断言嵌入 `tools/`。

本轮文档交付仅检查本目录 Markdown、相对链接、术语、历史决策及 spec/design/plan 覆盖；统一独立审查由主代理安排。Java 测试、消费者执行、CI、发布后制品检查均不在本轮执行范围内。

## Decision Boundary

- 可自行决定：私有方法拆分、局部变量和测试组织、同包内部实现细节；不得因此增加公共接口、配置项或模块耦合。
- 不可静默改变：ErrorDefinition/工厂公开签名、三种 null 语义、旧构造/工厂、泛型上界、mapper 顺序/异常身份、metadata 复制方式、batch 条目关联规则/计数/状态、C-07 默认值或作用域。batch 派生读取不得引入可被反序列化覆盖的字段（审查补充）。
- 改变上述公共行为或新增运行时依赖须先说明差异并重新对齐；本轮推荐草案也不能作为删除旧 API、实施代码、提交或发布的授权。
- 不增加一次性 tools 脚本、验证脚本、迁移脚本或夹带无关重构。需要长期行为测试时放对应 Java 模块测试路径；临时验收材料不升级为工程工具。
