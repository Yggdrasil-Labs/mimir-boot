---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# MyBatis 能力补齐方案大纲

> 推荐方案，未实施。已确认的有效决定保持；本轮补齐剩余技术选择，不冒充历史用户确认，不声明功能或消费者已通过。

本文承接[版本级 brainstorm](../brainstorm.md)的 MyBatis 有效决策，保留候选比较与事实依据。根据本轮“不再问，先推荐并补齐每项SDD”的要求，拦截层、阈值、配置键、日志协议与完整API已形成推荐设计，不再以逐项询问作为文档前置；本轮只改文档。

产物导航：[行为规格](./spec.md) · [详细设计](./design.md) · [静态实施计划](./plan.md) · [版本入口](../brainstorm.md)。以下候选分析中的细化事项，以设计推荐和具体验收为准，除 D-99（2026-10-31 用户确认的 MyBatis COLA 工具命名）外不新增用户确认的D编号。

## 本轮推荐

- 使用标准StatementHandler拦截器观测query/update/queryCursor，finally覆盖成功与失败；普通query包含结果映射，cursor只含创建。首版不覆盖batch flush，不把addBatch当执行；count/主查询分别记录，不调整InnerInterceptor排序。
- `mimir.boot.mybatis.slow-sql.enabled=true`，`threshold-ms=1000`，正值且验证纳秒换算上限；严格大于才以SQL.SLOW WARN输出，不依赖SQL.JSON或Mapper打印开关。
- 慢日志不读取参数，普通DML只输出保守结构模板，标识符/字面量全替换；原SQL上限65536、安全模板4096字符，未知语法/失败省略原文。现有SQL.JSON原结构、INFO级别、环境和显式开关不变。
- 独立 `com.yggdrasil.labs.mybatis.page.IPageColaConverters.toCola(IPage<T>)` 及带Function重载（D-99：2026-10-31 用户确认由 `ColaPageConverters` 改名，避免与 Common 同名类冲突）；无Serializable上界，元数据先校验再映射，null元素原位保留且不调用函数，异常同实例，COLA空列表及类路径隔离与Common一致。
- Log新容器预设SQL.SLOW→stdout，新服务器预设→sql.log，WARN且additivity=false；不新增MyBatis→Log依赖。searchCount只补两类使用说明，不恢复已撤销/暂缓需求。

## 目标与范围

| 方向 | 已确认行为 | 决策来源 |
|---|---|---|
| M-04 慢 SQL | 默认开启、阈值可调且可整体关闭；独立 Logger 以 WARN 输出；保留 SQL.JSON | D-56、D-57 |
| IPage 到 COLA | 逐条映射到 `PageResponse`，遵循 null/异常及 int 范围契约 | D-55、D-68、D-69 |
| 分页说明 | 单独 count 或只取前 N 条时说明 `searchCount=false` 用法 | D-59 |

不恢复已撤销的 M-01 拦截器顺序编排；不恢复暂缓的 M-02 无总数分页或 M-03 带函数输出 `PageResult`。防全表写、分页上限和审计人类型调整不在本轮范围。

## 当前代码事实

- [MybatisPlusLoggingConfiguration](../../../../mimir-boot-starters/mimir-boot-starter-mybatis/src/main/java/com/yggdrasil/labs/mybatis/config/MybatisPlusLoggingConfiguration.java)在未配置 `enable-json-sql-log` 时，仅在 dev/local/development/test 启用 JSON 日志；显式配置优先。
- [JsonSqlLogInnerInterceptor](../../../../mimir-boot-starters/mimir-boot-starter-mybatis/src/main/java/com/yggdrasil/labs/mybatis/log/JsonSqlLogInnerInterceptor.java)在 `beforePrepare` 以 `SQL.JSON` INFO 输出 SQL 与参数，发生在执行前，不具备耗时结果。
- [SqlLogMaskUtils](../../../../mimir-boot-starters/mimir-boot-starter-mybatis/src/main/java/com/yggdrasil/labs/mybatis/util/SqlLogMaskUtils.java)提供敏感字段/参数名和 SQL 赋值脱敏，并非通用 SQL 字面量或注释过滤器；不能直接据此承诺任意 SQL 无秘密。
- [PageConverters](../../../../mimir-boot-starters/mimir-boot-starter-mybatis/src/main/java/com/yggdrasil/labs/mybatis/page/PageConverters.java)仅有 `toMybatisPage(PageRequest)` 与 `toPageResult(IPage)`，没有带函数的 COLA 转换；既有入口需要保留。
- [MyBatis POM](../../../../mimir-boot-starters/mimir-boot-starter-mybatis/pom.xml)当前没有 COLA 依赖。[BOM](../../../../mimir-boot-bom/pom.xml)指定 MyBatis-Plus 3.5.17，其本地 POM 引用 MyBatis 3.5.19。
- 本地 MyBatis 3.5.19 源码确认：`StatementHandler` 有 query/update/queryCursor/batch 入口；`BatchExecutor` 在 `doFlushStatements` 中调用 JDBC executeBatch，batch 入口仅加入批次，查询前还可能触发内部 flush。

## M-04：先确定“慢”的计时口径

需要新增标准 MyBatis 拦截器；不能从现有 `beforePrepare` 推导执行耗时。以下保留候选比较，本轮推荐 StatementHandler，具体覆盖见[设计](./design.md)。

| 候选 | 可以观测什么 | 局限与成本 |
|---|---|---|
| Executor 层 | 一次逻辑调用的整体耗时，容易取得语句 ID；可能含连接获取、缓存和结果映射 | 缓存命中不代表执行 SQL；嵌套查询、count、query 重载需避免漏记或重复；内部 flush 不能简单视作独立代理调用 |
| StatementHandler 层 | 更靠近实际语句执行，可读到最终 BoundSql；普通查询通常包含结果映射 | 不含连接获取和 prepare；不覆盖 JDBC executeBatch；游标创建不包含后续遍历 |

推荐 StatementHandler 层的普通语句耗时，字段明确标注计时范围；Executor 可作为未来逻辑调用观测的备选，本轮不同时叠加两层。两者都不能直接宣称“纯数据库执行时间”。

推荐单调时钟在finally判定严格大于阈值；等于不记，失败超阈值也记，毫秒数保留至纳秒对应的6位小数。常规日志失败不改变返回或原异常，反射代理中的SQLException按既有MyBatis展开规则保留原实例，细节见设计。

### 批量、游标与分页 count

- 批量：必须区分加入批次和实际 flush。只拦截 `StatementHandler.batch` 会把入队耗时误标为执行耗时；只拦截外部 `Executor.flushStatements` 又可能漏掉查询/提交引起的内部 flush。
- 本轮推荐首版明确不覆盖批量实际执行，README清楚列出；可靠flush/JDBC观测及批次关联属于备选扩展，不将其写成本轮已覆盖。
- 游标：推荐只记录创建耗时，不包括迭代、读取完整结果或关闭；不包装完整生命周期。
- 分页count：推荐与主查询独立记录；不得为实现日志重新编排现有分页/租户拦截器。

### 开关与默认值

慢SQL默认开启是既有决策，不依赖当前Profile，也不随JSON SQL开关关闭。具体推荐采用 `mimir.boot.mybatis.slow-sql.enabled` 与 `threshold-ms`，默认true/1000ms；不新增总print开关。

Duration保留为比较方案；本轮推荐毫秒long，合法范围[1,9223372036854]，避免纳秒换算溢出；禁用时也拒绝非法值。1000ms是推荐默认，不能写成历史已确认策略。

## M-04：日志内容与秘密保护

独立Logger推荐名 `SQL.SLOW`，WARN输出语句ID、耗时、计时范围、执行结果及安全SQL模板；具体字段、类型和样例已写入设计IC-3。不能把错误消息、参数对象或原始SQL拼进失败日志。

| 候选 | 优点 | 代价 |
|---|---|---|
| 优先讨论：不输出参数，保留占位符并移除字面量和注释；无法安全处理时省略 SQL | 默认开启时暴露较少，日志文本本身即可保护值，不依赖 Log Starter | SQL 细节减少；仍需定义支持的语法及失败时的省略标记 |
| 参数脱敏后输出 | 排查参数相关问题更方便 | 泛型参数名、原始标量、动态 SQL、注释和未登记敏感字段可能漏出秘密，不能仅复用字段名黑名单 |

设计已明确保守边界：普通DML模板连标识符也替换；方言引号、存储过程、可执行注释、未知语法等省略。`${...}`拼接值不能依赖参数名脱敏，SQL文本不回退原文。语句ID也不得由秘密拼成，应用需遵守这一配置边界。

安全模板推荐最长4096字符、固定 （一个空格加 `[truncated]`） 后缀；原SQL超过65536字符直接省略，单行JSON由序列化器转义。禁止在脱敏前截取再按残缺文本解析，避免切断引号造成泄漏。

这一策略只约束新增 `SQL.SLOW`。D-57 要求现有 SQL.JSON 不变；不能宣传已有参数日志也因此得到更强保护。

## M-04：故障隔离与路由

- 日志构建、脱敏或常规appender异常不能替换业务结果/原异常；诊断不含原SQL、参数或错误消息。设计区分常规失败、已有业务异常优先和无业务异常时致命JVM错误传播，不承诺JVM失效时可用。
- Logger 级别关闭或开关关闭时应避免额外 SQL 解析和参数遍历；若最终不输出参数，任何路径都不应为日志反射读取参数对象。
- “不抛日志异常”不等于“日志无延迟”：同步输出、异步队列满时阻塞、丢弃策略需与 Log 预设一起解释，不能承诺持久化成功或零阻塞。
- 与 Log 的接口是 logger 名称和安全文本，不新增 MyBatis → Log 的模块依赖。单独使用 MyBatis 时由应用日志后端路由。
- 与Log推荐对齐：容器预设SQL.SLOW到stdout，服务器复用sql.log，WARN且additivity=false；旧预设/自定义后端按其实际配置解释，不承诺自动改写路由。

## IPage 到 COLA 的候选结构

推荐独立 `com.yggdrasil.labs.mybatis.page.IPageColaConverters`，避免旧 `PageConverters` 公共签名混入可选COLA类型。2026-10-31 用户确认（D-99）：原候选名 `ColaPageConverters` 与 Common 的 `com.yggdrasil.labs.common.page.ColaPageConverters` 短类名相同，而 MyBatis Starter 依赖 Common、两者总在同一类路径，故改名为 `IPageColaConverters`。Common 版输入带 `T extends Serializable`（来自 PageResult），MyBatis 版无此上界，差异有意保留。提供 `toCola(IPage<T>)` 和 `toCola(IPage<S>, Function<? super S, ? extends T>)`，返回 `PageResponse<T>`，输入/输出均无Serializable上界；完整签名与契约见设计IC-5。

MyBatis 自身需声明 COLA 的 optional 依赖以编译该工具，不能期待 Common 的 optional 依赖传递；BOM 仅管理 DTO 5.0.0，接入方显式加入 DTO。自动配置和普通分页路径不得提前加载 COLA 工具。

遵循[Common 大纲](../common-contracts/brainstorm.md)的转换契约：null 元素跳过函数并原位保留，异常原样抛出；total/current/size 不得截断到 int；记录顺序与数量不变；成功转换不包装成失败响应。

推荐null源/函数拒绝；元数据在读records及用户映射前校验，非null记录列表复制为新可变列表；null records的COLA读取为空列表且不保证该空列表可变；函数返回null原位保留。输出success=true，不借COLA自动修正无效元数据。

COLA 5.0.0 的 `PageResponse.of(data, totalCount, pageSize, pageIndex)` 已由本地源码核对，内部会设置 success=true；参数顺序不可按 Common 构造器顺序照搬。

## searchCount=false 使用说明

README 解释 `new Page<>(current, size, false)` 可跳过自动 COUNT；单独获取总数的业务需要将真实总数正确带入后续响应。只取前 N 条不应把未查询的 total 当成“真实总数为 0”。

拟提供“业务单独 count”和“只需要前 N 条记录”两个小例子，不修改模板项目、不新增无总数模型。若未得到真实总数就输出 COLA 分页，应明确 total 只是调用方提供值；转换工具不会补查数据库，也不能证明 total 是否真实。

## 模块依赖与实施衔接

静态任务依赖见[计划](./plan.md)：属性/拦截器→安全模板→真实执行及路由联调→使用说明；独立COLA适配在BOM版本可用、Common契约稳定后可并行。Log负责输出预设，MyBatis负责计时和内容安全，BOM负责DTO版本管理，不产生横向Starter依赖。

## 后续验收方向

- 计时：阈值前/等于/超过、正常和异常、缓存、分页 count、嵌套查询、重复拦截；batch/cursor 按最终承诺的覆盖范围验证。
- 安全：无 Log Starter、自定义日志后端、SQL 字面量/注释/动态拼接、超长文本及脱敏异常，原文秘密不得意外回退输出。
- 可靠性：关闭路径无额外解析；日志失败不改业务结果；队列阻塞/丢失边界按实际日志配置验证。
- 适配：映射 null/异常、int 边界、有/无 COLA 类路径及原分页入口兼容；说明示例避免重复 COUNT 和错误总数。

本轮仅核对仓库源码及本地依赖源码/POM，未实现拦截器或转换工具，未运行功能测试、性能基准或真实数据库验收。
