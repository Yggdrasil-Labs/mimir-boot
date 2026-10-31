---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# MyBatis 能力补齐实施计划

> 推荐方案，未实施。本计划为静态输入，本轮只补SDD；不表示源码、测试、依赖或发布已实施，不产生新的Git/部署授权。

导航：[版本入口](../brainstorm.md) · [需求大纲](./brainstorm.md) · [规格](./spec.md) · [设计](./design.md)

## 目标与背景

落实M-04慢SQL、IPage→COLA和searchCount=false说明，对应MB-01至MB-07。既有JSON beforePrepare仅能看到执行前SQL，不能计时；普通SqlLogMaskUtils按字段名脱敏，不能作为默认慢日志的完整保护器。MyBatis3.5.19、MyBatis-Plus3.5.17、COLADTO5.0.0为当前静态核对基线，实施前保留实际解析版本证据。

依据[设计](./design.md)及[Common转换契约](../common-contracts/design.md)、[Log路由契约](../log-runtime/design.md)、[BOM依赖计划](../bom-dependencies/plan.md)。不恢复D-58撤销的M-01、不恢复D-59/D-65暂缓的无总数分页与PageResult带函数输出，不改加解密/审计/分页限制。

## 关键选择与行为约束

- D-56/D-57：慢SQL默认开启，推荐threshold-ms=1000且>0，严格超过阈值才WARN输出SQL.SLOW；不依赖JSON或Mapper打印开关。
- 推荐标准StatementHandler的query/update/queryCursor计时，单调时钟，finally覆盖失败；不包含连接/prepare，query含映射/嵌套时间，cursor只含创建，batch flush首版不覆盖。
- 保留SQLException底层实例及既有MyBatis包装；常规日志故障不改业务，proceed恰好1次。
- 推荐保守结构模板：不读参数、不保留标识符/值；关键词白名单按ASCII大小写不敏感匹配，但TRUE/FALSE/NULL不在白名单内，任何位置均替换为`?`（包括`IS NULL`→`IS ?`）；支持范围外或原SQL>65536字符则省略；安全模板≤4096字符，日志不输出异常message/stack或原文诊断。模板只保留有限结构信息，statementId用于定位。
- D-55/D-68/D-69/D-99：独立page.IPageColaConverters两toCola重载（D-99 2026-10-31用户确认改名，避免与Common `common.page.ColaPageConverters` 同类路径重名），无Serializable上界（Common版输入上界来自PageResult，差异有意保留）；source/mapper拒绝null，三个long元数据先校验后映射，越int拒绝；null元素保留且不调用mapper，异常同实例。
- COLA依赖optional且隔离；旧PageConverters、普通模型和自动配置不得出现COLA类型。Common原生map保留null数据列表，COLA读取规范为空列表。
- D-59只补searchCount=false使用说明，无额外模型或总数推断。

## 任务依赖与文件归属

| 任务 | 依赖 | 独占范围与并行边界 |
|---|---|---|
| T1 慢SQL属性与标准拦截器 | 无 | 新config/log源码、AutoConfiguration.imports及对应测试 |
| T2 安全模板与日志失败隔离 | T1 | 新包内模板器/测试；对T1 SlowSqlInterceptor接线须串行交接，不能并行写同文件 |
| T3 COLA适配与隔离 | BOM已管理DTO5.0.0、Common契约稳定 | MyBatis POM、新page工具和相关测试；与T1/T2源码互不重叠可并行 |
| T4 真实语句与跨模块联调 | T2、T3、Log路由可用 | 新模块内IT，JSON及旧分页测试仅按需补回归；不改Log/Common/BOM生产文件 |
| T5 说明与最终回归 | T4 | MyBatis README；前序源码问题返回所属任务修改并重验 |

以下路径均以 `mimir-boot-starters/mimir-boot-starter-mybatis/` 为根；属于未来实施范围，本轮没有创建源码/测试。所有测试放模块标准src/test，不新增tools脚本或工具测试目录。

## T1：默认属性、注册与计时

文件：

- Create: `src/main/java/com/yggdrasil/labs/mybatis/config/SlowSqlProperties.java` — 独立enabled/thresholdMs绑定、启动校验。
- Create: `src/main/java/com/yggdrasil/labs/mybatis/config/MybatisPlusSlowSqlConfiguration.java` — 条件Bean与MyBatis-Plus装配顺序。
- Create: `src/main/java/com/yggdrasil/labs/mybatis/log/SlowSqlInterceptor.java` — 三个标准StatementHandler入口，nanoTime/finally计时，安全日志接线。
- Modify: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — 新装配入口，保留现有列表。
- Create: `src/test/java/com/yggdrasil/labs/mybatis/config/MybatisPlusSlowSqlConfigurationTest.java`、`src/test/java/com/yggdrasil/labs/mybatis/log/SlowSqlInterceptorTest.java` — 属性、时钟及代理异常回归。

约束：默认true/1000ms；毫秒合法范围[1,9223372036854]，禁用也校验。用相同类型缺失条件退让，标准Interceptor由Boot收集，仅对MyBatis-Plus Boot自动创建的SqlSessionFactory添加一次；消费者声明任一SqlSessionFactory时MyBatis-Plus不创建自动工厂（无参`@ConditionalOnMissingBean`），默认覆盖为0，除非自建工厂注入全部Interceptor并setPlugins，否则由README指导手工添加（审查补充）。不注入InnerInterceptor列表或改其顺序。测试时钟仅包内注入。

先用失败测试固定阈值999ms/1000ms/1000ms+1ns、业务proceed一次、成功和固定SQLException实例传播；补默认/关闭/WARN关闭、非法属性、自定义Bean退让；审查补充：上下文声明用户SqlSessionFactory时断言MyBatis-Plus自动工厂未创建，且未注入全部Interceptor的自建工厂插件链不含慢拦截器、注入`Interceptor[]`并setPlugins的自建工厂恰好含1个。WARN关闭必须观察到0次SQL提取/模板处理，不能只断言无日志。

验收：正常/失败计时均符合MB-01/MB-03，属性与JSON打印独立；异常观察不改变反射包装的正常展开，旧装配及分页顺序测试不变。

## T2：安全模板、载荷与失败隔离

文件：

- Create: `src/main/java/com/yggdrasil/labs/mybatis/log/SlowSqlTextSanitizer.java` — 包内有界结构模板，不增加公共原文开关。
- Modify: `src/main/java/com/yggdrasil/labs/mybatis/log/SlowSqlInterceptor.java` — 使用模板、固定payload与Logger SQL.SLOW；与T1串行。
- Create: `src/test/java/com/yggdrasil/labs/mybatis/log/SlowSqlTextSanitizerTest.java` — 支持范围、未知语法、秘密与长度边界。
- Modify: `src/test/java/com/yggdrasil/labs/mybatis/log/SlowSqlInterceptorTest.java` — 故障注入、消息协议、参数不读取。

约束：完整词法/白名单/省略规则及字段类型以设计IC-3为准，不复用现有黑名单承诺，不修改SqlLogMaskUtils或SQL.JSON。使用PluginUtils现有接口只读MappedStatement/BoundSql；元数据不可得使用安全占位，不能吞掉整个超阈值事件而无说明。

验收：

- 字符串、数字、标识符、TRUE/FALSE/NULL字面token（含大小写变体）、注释、动态拼接的测试秘密在最终payload/输出中出现0次；`INSERT INTO t(flag) VALUES(TRUE)`、`... VALUES(false)`、`... VALUES(NULL)` 中对应token均替换为`?`，`WHERE x IS NULL`结构等价于`WHERE ? IS ?`。不支持双引号/反引号/方括号/美元引号、可执行/未闭合注释、多语句等均省略，不回退原文。
- 原SQL65536/65537字符、模板4096边界、控制字符/换行与超长ID符合设计，不能先截断原文再解析。
- 在SQL提取、模板、JSON、appender注入常规异常后业务结果和异常链原实例保持；参数对象getter/toString故意抛错也不因日志调用。
- 并发/嵌套调用不串时钟、语句或状态；无共享计时缓存。没有性能量测时不写零开销结论。

## T3：COLA适配与可选依赖

文件：

- Modify: `pom.xml` — 添加optional `com.alibaba.cola:cola-component-dto`，版本由BOM管理，不导入COLA BOM。
- Create: `src/main/java/com/yggdrasil/labs/mybatis/page/IPageColaConverters.java` — 两种设计IC-5签名，直接IPage→PageResponse；类名按D-99。
- Create: `src/test/java/com/yggdrasil/labs/mybatis/page/IPageColaConvertersTest.java` — 泛型、元数据与映射行为。
- Create: `src/test/java/com/yggdrasil/labs/mybatis/page/ColaOptionalClasspathTest.java` — 真正移除COLA的类加载/普通路径测试。

约束：source/mapper校验→快照total/current/size→所有元数据校验→读records→逐条映射→`PageResponse.of(data,total,size,current)`。总数≥0、页码/大小≥1、三个值≤int最大；非法元数据必须先于用户函数失败。不得通过PageResult中转引入Serializable限制，不调用数据库或自动修复total。

验收：

- 两重载接受非Serializable输入/输出；identity新列表、元素身份保持；null records COLA读取为空，非null新列表可变；null元素不调mapper，mapper返回null保留，原异常实例传播。
- int最大值成功、最大+1/负total/非正current-size失败；先校验元数据，mapper计数0。success=true、errCode/errMessage=null，页码/大小无参数颠倒。
- 无DTO时普通自动配置/Mapper/旧工具/普通模型反射和Jackson均可运行；不能只用模块本身含COLA的classpath证明可选隔离。实际发布POM保留optional，dependency:tree不向普通消费者传递DTO。
- 有DTO消费者转换成功；先编译再移除DTO，显式适配路径保持类路径错误，不转失败分页。Common工具遵循同一合同，允许Common输入泛型因PageResult保留已有上界。

## T4：实际执行与跨模块验收

文件：

- Create: `src/test/java/com/yggdrasil/labs/mybatis/log/SlowSqlMapperIT.java` — 真实SqlSessionFactory/H2/JDBC桩、缓存/分页/游标/batch覆盖边界及异常。
- Modify: `src/test/java/com/yggdrasil/labs/mybatis/log/JsonSqlLogInnerInterceptorTest.java`、`src/test/java/com/yggdrasil/labs/mybatis/config/MybatisPlusLoggingConfigurationTest.java` — 仅按需补JSON/slow独立组合回归，保留原断言。
- Modify: `src/test/java/com/yggdrasil/labs/mybatis/page/PageConvertersTest.java` — 仅补searchCount说明相关回归时使用，旧null records断言保留。
- 相关模块内IT fixture可放标准 `src/test/resources/`，不新增工具目录或外部数据库依赖；具体资源仅在确有测试输入时确定。

验收：

1. 使用真实MyBatis插件链证明MyBatis-Plus Boot自动创建的SqlSessionFactory仅1个默认慢拦截器；审查补充：分别以独立上下文验证“仅自建工厂”（此时无自动工厂）——未注入全部Interceptor时0个，注入全部Interceptor并setPlugins时恰好1个，按README手工注册后恰好1个且MybatisPlusInterceptor分页仍生效；不构造自动工厂与自建工厂并存的上下文作为默认场景。query/update/cursor创建、分页count与主查询、嵌套、`selectKey order=AFTER` 生成键（内外各1条、时间重叠不可求和）、缓存命中分别匹配设计范围。addBatch/内部flush不能冒充已计时，README明确batch不覆盖。
2. JDBC桩故意阻塞/抛出固定SQLException，确认失败超过阈值也WARN，原异常链底层同实例；无prepare阶段计时承诺。时间边界仍用可控时钟单测，避免sleep驱动精确阈值断言。
3. JSON/slow四组合、dev/local/development/test/prod默认与显式覆盖，JSON仍是INFO与sql/params原结构。只关闭JSON时slow真实运行，不靠Mapper DEBUG打印。
4. 与Log需求使用实际新预设联调：容器stdout、服务器sql.log，SQL.SLOW WARN各出口每事件1条；无LogStarter以及自定义XML不发生横向依赖错误。不得在本任务直接改Log文件。
5. 统计实际COUNT：业务单独count再关闭searchCount仅1次COUNT；前N条0次自动COUNT；不拿慢日志条数代替SQL次数。
6. 在目标数据库方言/驱动可用时验收受支持文本及省略结果；H2通过只算本地集成，不宣称MySQL/PostgreSQL全部语法支持。没有环境时保留未验证。

性能验证在固定环境对关闭、WARN关闭、未超阈值、慢事件+模板作对比，分别报告吞吐/P95/P99；实际输出同步/队列满单独记录，不把未输出事件当作零开销。不增加一次性基准脚本到tools。

## T5：使用说明与回归

文件：

- Modify: `README.md` — 慢SQL配置/范围/安全结构模板/失败隔离、独立Logger和Log新预设路由；COLA可选依赖和两个接口；searchCount两个场景。

README必须说明：阈值严格大于、1000ms推荐默认、batch不覆盖和cursor仅创建；默认只作用于MyBatis-Plus Boot自动创建的SqlSessionFactory（以路由DataSource实现的动态数据源仍属此类）；声明任一自建SqlSessionFactory时自动工厂不存在，默认覆盖为0。审查补充：自建工厂先检查现有插件链，若已注入全部Interceptor Bean并setPlugins则已包含、不得再加；否则注入SlowSqlInterceptor Bean，在一次`setPlugins(...)`中传入完整插件列表（含MybatisPlusInterceptor，因setPlugins整体替换插件数组），或通过`Configuration.addInterceptor(...)`追加，重复添加会重复记录；审查补充（推荐）：`Configuration.addInterceptor(...)` 追加到无同步的 `InterceptorChain` 列表，对之后新建的StatementHandler生效（`newStatementHandler`每次调用`pluginAll`），但只能在启动阶段、任何SQL执行前完成，不得运行期追加；第三种方式为MyBatis-Plus 3.5.17 `MybatisSqlSessionFactoryBean#addPlugins(...)`（字节码核对为合并已有插件数组后setPlugins的追加语义），须在工厂Bean构建SqlSessionFactory前调用，其他MP版本需复核；生成键selectKey等嵌套语句内外各记一条且耗时重叠；不保留表列名/参数，未知SQL省略，语句ID也不得动态拼秘密；SQL.JSON保护能力保持原边界。同步或异步日志输出可能阻塞/丢失，事件发出不等于采集端已收到。

审查补充（推荐）：README须单列“结构模板取舍”，说明D-57仅要求脱敏SQL，本实现以安全优先：函数名被替换（`COUNT(*)`→`?(*)`）、`WITH` CTE、PostgreSQL `::`/`||`/JSON运算符、带引号标识符与可执行注释等常见语法会被整条省略（`sqlStatus=UNSUPPORTED`）；排障以`statementId`定位Mapper语句为主，不提供原文回退开关。

COLA示例显式引入DTO，total必须由调用方负责；原PageConverters继续可用，不把原null列表行为改成COLA规则。业务单独count后关闭自动count并setTotal；前N条直接返回记录，不虚构真实total或恢复无总数模型。

最终验收：模块 `./mvnw -pl mimir-boot-starters/mimir-boot-starter-mybatis -am verify`，按项目规程再运行完整质量入口；这些是将来实施检查，本轮只验证文档。分别报告单元、本地H2、真实方言、Log预设联调、CI和发布后消费者/采集状态，缺项不写通过。

## 决策边界

实现者可在既定协议内选择局部辅助结构、测试fixture组织、Clock测试注入和断言方式；不能扩大原文输出、放宽元数据范围、改变阈值比较、引入隐式JSON/print联动、添加MyBatis→Log依赖或恢复撤销需求。

若发现实际MyBatis版本/API、Boot收集Interceptor机制、分页count路径或COLA类隔离与设计不符，提供最小反例、有效依赖/装配证据后修订设计；不得改成无条件重复注册或在COLA显式适配路径捕获LinkageError掩盖问题（慢日志旁路的LinkageError隔离及其理由见设计IC-3）。涉及批量可靠观测、JDBC包装、其他模块代码、tools或线上部署应先重新界定范围。本轮仅改四份文档，不进入上述实施任务。
