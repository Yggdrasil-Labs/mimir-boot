---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# MyBatis 能力补齐规格

> 推荐方案，未实施。D-55 至 D-69 及 D-99（MyBatis COLA 工具命名为 `IPageColaConverters`）的有效决定是约束；阈值、计时范围、日志协议与 API 细节为本轮推荐，不代表用户曾逐项确认或功能已验收。

导航：[版本入口](../brainstorm.md) · [需求大纲](./brainstorm.md) · [设计](./design.md) · [计划](./plan.md)

## 概述与范围

补齐独立慢 SQL 观测、可选 `IPage` 到 COLA `PageResponse` 的逐条转换，以及 `searchCount=false` 使用说明。保留 SQL.JSON 原结构、INFO 级别、环境默认及显式开关；不恢复拦截器排序、无总数分页或输出 PageResult 的带函数转换。

## 行为 MB-01：独立配置慢 SQL 记录

### 场景：默认开启并记录成功与失败慢调用

Given 未配置慢 SQL 属性，`SQL.SLOW` 接受 WARN，某次受支持语句耗时 1001ms
When 语句成功返回或抛出数据库异常
Then 发出 1 条 SQL.SLOW WARN，含语句 ID、耗时、计时范围、结果状态及安全 SQL 模板
And 失败调用仍被计时，原有返回结果或原异常传播不因常规日志处理而改变。

### 场景：严格大于阈值与独立关闭

Given 阈值为 1000ms
When 耗时分别为 999ms、1000ms、1000ms 加 1ns
Then 前两次不记录，第三次记录
And `slow-sql.enabled=false` 或 SQL.SLOW 禁用 WARN 时均不生成慢日志，不进行 SQL 模板处理；不要求开启普通 SQL 打印或 JSON 日志。

### 场景：自定义 SqlSessionFactory 需手工注册（审查补充）

Given 消费者声明了任一自建 SqlSessionFactory（如多数据源、手工 `MybatisSqlSessionFactoryBean`），此时 MyBatis-Plus 3.5.17 的自动工厂受无参 `@ConditionalOnMissingBean` 约束不再创建，应用中只有自建工厂
When 自建工厂执行一条超过阈值的语句
Then 未把 SlowSqlInterceptor 放入该工厂插件链时不记录，即该应用默认慢 SQL 覆盖为 0；若自建工厂注入容器全部 `Interceptor` Bean 并调用 `setPlugins`，则已自动包含 SlowSqlInterceptor 并记录 1 条
And 按 README 先检查现有插件链、未包含时显式添加 SlowSqlInterceptor 一次后，该工厂每次受支持调用同样最多记录 1 条，且 `MybatisPlusInterceptor` 等原有插件仍保留；框架不自动后处理或重复注册用户工厂。仅使用 MyBatis-Plus 自动工厂（含以路由 DataSource 实现的动态数据源）时默认记录 1 条。

### 场景：非法阈值拒绝启动

Given `mimir.boot.mybatis.slow-sql.threshold-ms` 为 0、负数或超过设计安全上限
When 应用绑定配置
Then 启动失败，诊断说明属性名及合法范围
And 不静默回退到 1000ms；即使慢 SQL 开关关闭，非法配置仍被拒绝。

## 行为 MB-02：新增慢日志不泄露 SQL 值

### 场景：输出结构模板

Given 受支持 DML 含参数对象、字符串/数字字面量、TRUE/FALSE/NULL token（大小写不限）和非敏感字段名中的秘密值
When 该语句超过阈值
Then 慢日志不输出参数、参数对象、原始标识符或字面量；仅保留白名单结构关键词、操作符和占位符
And 例如 `SELECT name FROM account WHERE id = 123` 输出结构等价于 `SELECT ? FROM ? WHERE ? = ?`，日志本身不依赖 Log Starter 才能保护这些值。
And `INSERT INTO t(flag) VALUES(TRUE)`、`... VALUES(false)`、`... VALUES(NULL)` 中的对应 token 均替换为 `?`；`WHERE x IS NULL` 输出结构等价于 `WHERE ? IS ?`，不保留布尔或空值字面量。

### 场景：不支持或过长文本保守省略

Given SQL 使用不支持的方言引号、存储过程或多语句，或原 SQL 超过 65536 个 UTF-16 字符
When 超过阈值
Then 仍记录语句 ID 与耗时，SQL 固定为 `<omitted>` 并给出非敏感状态标记
And 不回退原文；模板处理后的 SQL 字段最长 4096 个 UTF-16 字符，单条日志不含物理换行。

### 场景：日志处理异常不替换数据库异常

Given 语句抛出的 SQLException 包含秘密，随后 SQL 提取、模板处理、序列化或 appender 发生常规异常
When 慢日志路径结束
Then 数据库异常按 MyBatis 既有展开规则传播，底层 SQLException 仍为原实例
And 日志不输出异常消息、堆栈、原 SQL 或参数；成功语句的常规日志失败也不改变成功返回。

## 行为 MB-03：明确语句计时覆盖

### 场景：分页与嵌套查询分别观测

Given 一次分页触发 count 和主查询，或查询内触发另一条实际语句
When 各实际语句独立超过阈值
Then 每个受支持的语句调用分别记录一次，不合并为一个分页总耗时
And 不调整现有分页、租户或其他 InnerInterceptor 顺序。

### 场景：生成键语句嵌套（审查补充）

Given update 语句配置 `selectKey order=AFTER`，`SelectKeyGenerator.processAfter` 在 update 调用内执行另一条 selectKey 查询，且内外两条语句均超过阈值
When update 返回
Then 内层 selectKey 查询与外层 update 各记录 1 条 SQL.SLOW，分别带各自语句 ID
And 外层耗时包含内层耗时，两条事件时间区间重叠，不可相加作为总 SQL 耗时；不对内层去重或从外层扣除。

### 场景：缓存、游标及批量边界

Given 缓存命中没有实际语句调用，或使用游标/批量执行
When 获取缓存结果、创建游标或加入并执行批次
Then 缓存命中不记录；游标只观测创建耗时，不包含随后遍历/关闭
And 首版不覆盖批量实际 flush，不把 addBatch 入队耗时标为 SQL 执行耗时。

### 场景：普通语句失败与前置阶段失败

Given update/query/queryCursor 内抛错且耗时超过阈值，或错误发生在其之前的连接获取/prepare 阶段
When 调用失败
Then 前者记录 ERROR 结果状态的 WARN 慢事件并保留原异常
And 后者保持原错误，本计时方案不承诺捕获未进入受支持调用的耗时；不能描述为纯数据库执行时间或所有数据库错误监控。

## 行为 MB-04：保留 JSON SQL 日志与输出后端边界

### 场景：两个日志能力独立

Given JSON 日志关闭，慢 SQL 开启；或反向配置
When 分别执行快语句与慢语句
Then JSON 关闭不妨碍慢日志；慢日志关闭不妨碍原 JSON 日志
And SQL.JSON 保持 `sql`、`params` 原载荷与 INFO 级别，不新增耗时/结果字段。

### 场景：环境默认与新日志路由

Given JSON 开关未配置，分别启动 dev/local/development/test 和 prod
When 执行语句
Then 前四类环境按既有策略启用 JSON，其余默认关闭；慢 SQL 默认开启不受这些环境影响
And 选择 Log 新容器预设时 SQL.SLOW 输出 stdout，新服务器预设输出 sql.log，同一出口每事件一次。

### 场景：没有 Log Starter 或后端不可用

Given 仅使用 MyBatis Starter，或应用自定义日志配置/输出介质失败
When 发出 SQL.SLOW 事件
Then MyBatis 不因缺少 Log Starter 产生类链接错误，使用实际 SLF4J 后端路由
And 不承诺事件已经写入文件、传到采集端或零阻塞；不会擅自替换应用日志配置。

## 行为 MB-05：将 IPage 转为 COLA 成功分页

### 场景：逐条转换并保持 null 位置

Given 总数 10、页码 2、大小 3、记录为 `[a,null,b]`，转换函数把 a/b 转为非 Serializable 的 DTO
When 调用带函数的 `IPageColaConverters.toCola`（D-99 命名，避免与 Common 的 `ColaPageConverters` 同名）
Then 得到 success=true、totalCount=10、pageIndex=2、pageSize=3、数据 `[dtoA,null,dtoB]`
And 函数只调用 2 次；结果为新分页对象及新列表，原对象/列表不被框架修改。

### 场景：空记录、身份转换与函数返回 null

Given records 为 null 或空列表；或存在非 null 记录而函数返回 null
When 调用转换
Then null records 在 COLA 读取为非 null 空列表，空页成功；函数返回 null 原位保留
And 无函数重载保留元素身份并复制非 null 列表；不查数据库，不补总数，不要求输出类型实现 Serializable。

### 场景：参数、元数据与映射异常

Given 源对象或函数为 null；或 total<0、current/size<1、任一元数据超过 2147483647
When 调用转换
Then 在调用用户函数前抛 IllegalArgumentException，指出问题字段，不截断或借 COLA 自动修正
And 合法元数据下 mapper 抛出的异常原实例传播，不返回部分成功页、不包装为 COLA 失败响应。

## 行为 MB-06：COLA 是显式可选能力

### 场景：显式引入 DTO 的消费者

Given 消费者显式声明 `cola-component-dto`，使用本轮 BOM 管理的 5.0.0
When 编译运行独立 COLA 转换工具
Then 两种 toCola 重载均可调用，结果符合 MB-05
And 不要求导入整个 cola-components-bom。

### 场景：无 COLA 的普通消费者

Given 消费者未引入任何 COLA 类
When 启动 MyBatis 自动配置、执行普通 Mapper、调用旧 PageConverters，以及反射/序列化普通分页模型
Then 均不因新增适配类触发 NoClassDefFoundError
And 发布 POM 中 COLA 依赖标记 optional，不传递给普通消费者。

### 场景：消费者显式使用适配类却缺少 DTO

Given 程序已编译为调用 COLA 适配器，但运行时删除 DTO
When 链接或调用该适配路径
Then 保留类路径配置错误，不捕获 LinkageError 伪装成业务失败分页
And 普通消费者隔离场景仍须单独证明可运行，不能靠从未加载适配器的单元测试代替。

## 行为 MB-07：仅说明 searchCount=false

### 场景：业务已单独 count

Given 业务先执行一次 count 并获得真实 total，再用 `new Page<>(current,size,false)` 查询记录
When 查询并构造分页响应
Then 无第二次自动 COUNT，total 由业务明确传入，转换工具不额外查询。

### 场景：只取前 N 条

Given 业务只需要前 10 条记录，不需要总数
When 按 README 示例关闭 searchCount 查询
Then 返回所需记录，不把未查询的 total=0 宣称为数据库真实总数
And 不新增无总数模型、多查一条入口或新的 PageResult 映射方法。

### 场景：把未知总数误作真实总数

Given 调用方未查询总数，但给转换器传入 total=0
When 转为 COLA 分页
Then 转换器仅保留调用方提供的合法数值，不补查数据库或推断真实总数
And README 明确该结果不能证明真实总数为 0，业务需要真实总数时应自行 count。

## 全局约束

- 默认阈值推荐 1000ms，比较为严格大于；时间用单调计时，业务调用仅执行 1 次（D-56）。
- 新增日志 Logger 固定 SQL.SLOW、级别 WARN；SQL.JSON 原结构与行为改变数为 0（D-57）。
- 首版计时接口 3 类：query、update、queryCursor；不将 batch 入队伪装为 flush 执行。
- 转换 O(n) 时间和 O(n) 新列表空间，非 null 元素最多调用 mapper 1 次；3 个分页元数据均在 int 范围内且映射前校验（D-55、D-68）。
- 新增 MyBatis → Log 横向依赖数为 0；COLA 依赖 optional，仅显式调用者需要（D-66 至 D-69）。
- 以上是目标契约，尚无运行、性能、真实数据库或发布后采集验证。
