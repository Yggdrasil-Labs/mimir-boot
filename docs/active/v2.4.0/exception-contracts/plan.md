---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Exception HTTP 与校验错误实施计划

导航：[需求大纲](./brainstorm.md) · [规格](./spec.md) · [详细设计](./design.md) · [版本大纲](../brainstorm.md)

## Goal

按 E1 至 E6 实现正常可写响应的标准 HTTP 状态/头保留、工厂安全降级及可选结构化校验明细，并与Web联合保持统一封装后的既有traceId增强。默认旧 data 形态保持，返回值校验不可返回400，Common业务异常HTTP200/系统500映射约定不变；原生MVC协商和请求处理仍按框架规则执行。

本计划是推荐草案，尚未实施。用户本轮授权补齐SDD，不代表逐项接受所有推荐，也不授权代码、Git写入或发布。

## Context 与关键决策

- 现状入口均在 `mimir-boot-starter-exception`：MimirExceptionHandler、ExceptionResponseFactory、DefaultExceptionResponseFactory、ExceptionProperties、ExceptionAutoConfiguration。Spring基线6.1.21由Boot3.3.13管理，不为本需求升级框架。
- 保留每个已有 public handler 的方法名、参数和 Object 返回描述符，以及原单参构造；实际可统一返回 ResponseEntity，不改公开描述符。新增双参构造注入配置。
- 新标准入口为 ResponseStatusException / ErrorResponseException。HandlerMethodValidationException 专用分支优先；不全局捕获所有 ErrorResponse，避免扩大E-03范围。
- 框架固定类型Biz/System/Base优先，普通异常才查询当前类型的merged状态注解；不遍历cause。IException的既有code/message仍保留。
- HTTP状态和异常协议头先解析；工厂最多调用一次，返回ResponseEntity只解包body及可接受头，不能覆盖已解析状态或异常同名协议头。工厂抛异常直接R.fail，data=null。
- Advice合并body、已解析状态和合法头后直接交给原生MVC。不增加返回值处理器、媒体协商器或请求包装；不可表示格式、Resource、条件/Range请求不承诺原状态、完整body或空体。状态不可覆盖仅约束异常映射和工厂，不能据此改写框架后续结果。
- Object声明实际ResponseEntity会使现有Web增强器无法从HttpEntity泛型解析到R；由Web W-TC补默认supports的限定候选，沿用内部Object Advice的instanceof R保护和用户delegate.supports退出。Exception不得填traceId或改写原MethodParameter及containing class，也不依赖Web模块；E6见I3b。
- 推荐 `mimir.boot.exception.validation.structured-details-enabled=false`，只改变data模式。默认精确保留两种旧字符串列表和另外两种null；开启后采用ValidationErrorData/Detail。
- 四类入口为MethodArgumentNotValidException、BindException、HandlerMethodValidationException、ConstraintViolationException。输出/未知约束来源一律500/null；空或纯Bean路径从原400改为500，是用户已确认（D-98）、需在升级说明中明确列出的变化，不提供恢复400的开关。
- 结构化最多100项；path/constraint/message长度256/64/256 code points；确定排序，保留重复；固定安全消息，不读取拒绝值、arguments、容器key或插值文本。默认路径不应用这些新截断规则。

完整类型优先级、状态映射、头合并、Web兼容协作和字段来源见[详细设计I1至I5（含I3b）](./design.md#interface-contract)。实施者应使用该矩阵，不能按“标准异常都处理”自行扩大类型集合。

## Task Dependencies

| 任务 | 依赖 | 独占范围与并行条件 |
|---|---|---|
| T1 HTTP元信息与工厂链 | 无 | MimirExceptionHandler、内部HTTP支持类型及HTTP测试；独占handler写入，完成后交T3 |
| T2 结构化模型与纯提取 | 规格I4/I5 | validation包、新增handler/ValidationErrorExtractor及其测试；不修改MimirExceptionHandler或配置，能与T1并行 |
| T3 配置接入与校验分支 | T1、T2 | 接管handler、ExceptionProperties、ExceptionAutoConfiguration及校验测试；与T1串行 |
| T4 MVC/兼容验证与文档 | T1至T3、Web W-TC；Common新工厂可用后补其回归 | Exception测试整合、模块README及版本说明；消费Web W-T4联合结果，不编辑Web文件 |

Common只提供现有异常模型就能开始；新增ErrorDefinition工厂不是T1/T2的前置依赖。T4补验证Common的新工厂产物仍走同一HTTP语义。Web C-07仅改变R的fail字段，E-02开关独立。统一封装的traceId兼容由 [Web W-TC/W-T4](../web-binding/plan.md#任务依赖与文件范围) 独占Web实现与测试；W-TC局部实现可与T1并行，真实联合测试必须等待T1至T3及W-TC。Exception T4和Web W-T4共享联合验收证据，但不交叉写对方模块文件、不增加Exception→Web模块依赖。

## Tasks

### T1：统一HTTP元信息和响应工厂封装

文件：

- Modify: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/handler/MimirExceptionHandler.java` — 新标准状态异常入口、根类型注解、固定状态与头组合；保持旧公开签名。
- Create: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/handler/ExceptionHttpMetadata.java` — 包内元信息容器；若实现足够小可作同包内部类型，不升级为公共SPI。
- Modify: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/handler/ExceptionResponseFactory.java` — 只补文档说明body职责和ResponseEntity解包约定，方法签名不变。
- Modify: `mimir-boot-starters/mimir-boot-starter-exception/src/test/java/com/yggdrasil/labs/exception/handler/MimirExceptionHandlerTest.java` — 原类型/工厂回退基线及新增标准异常局部测试。
- Create: `mimir-boot-starters/mimir-boot-starter-exception/src/test/java/com/yggdrasil/labs/exception/handler/HttpExceptionMvcIntegrationTest.java` — 真实MVC状态、头、注解、工厂和协商回归。

实施约束：先写405 Allow缺失、429/注解被转500的回归，再统一Advice内的响应构建。每次处理先解析元信息快照再调用工厂，避免catch里重新解析异常或调用工厂。构建普通错误DTO或工厂body，合并状态和合法头后直接返回ResponseEntity，由原生MVC处理。不要改变业务异常内容、默认Advice顺序、用户自定义Bean退让条件，也不新增写出编排。

验收（状态和body断言以正常可写请求为前提，原生MVC边界见下文）：

- 标准ResponseStatusException429/Retry-After、ErrorResponseException503、普通自定义注解404、合并/继承注解、IException+注解、包装cause不透传均符合I2。
- Biz/System/Base状态200/500/500，新旧构造产物一致；框架类型子类状态注解不覆盖固定语义。现有普通兜底500保留。
- Allow等合法异常头保留，异常同名头优先于工厂；多值不被join丢失；非法CR/LF/NUL和逐跳头被过滤；不清空过滤器无关头。
- 工厂正常、null、抛异常、ResponseEntity状态冲突均有MVC用例。抛异常后原状态和头仍在，工厂调用一次，data为null。
- Advice为1xx、204、205、304组装的ResponseEntity使用null body，不构造统一错误body，合法协议头保留；HEAD继续交给原生MVC处理。
- 异常Content-Type不套统一body，实体长度/编码由实际MVC转换链确定；工厂合法显式类型和消费者converter沿用原生处理，不增加媒体预判或请求头隔离。不可表示格式、Resource、条件/Range请求不验收固定状态或空体；真实写出失败不计作工厂降级。
- 原public handler的Object返回和参数描述符保留；不以把签名改成ResponseEntity来省略包装。
- 向Web W-TC提供真实Object声明/实际ResponseEntity路径，保持原MethodParameter及containing class；可写R经过既有Advice链增强一次。此处不修改Web增强器或重复填traceId，不为null构造body，最终兼容由T4联合验收。

### T2：结构化模型与校验元数据提取

文件：

- Create: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/validation/ValidationErrorSource.java`。
- Create: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/validation/ValidationErrorDetail.java`。
- Create: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/validation/ValidationErrorData.java`。
- Create: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/handler/ValidationErrorExtractor.java` — handler包内可见提取器，可访问公开只读模型，避免为跨包调用新增公共SPI。
- Create: `mimir-boot-starters/mimir-boot-starter-exception/src/test/java/com/yggdrasil/labs/exception/handler/ValidationErrorExtractorTest.java` — 分类、路径、安全消息、顺序与限长。
- Create: `mimir-boot-starters/mimir-boot-starter-exception/src/test/java/com/yggdrasil/labs/exception/validation/ValidationErrorDataTest.java` — 模型不变量、序列化与列表快照。

行为：按I4/I5，只提取允许的声明元数据。明确禁止读取/序列化FieldError.rejectedValue、ConstraintViolation.invalidValue/leafBean/executableParameters、ParameterValidationResult.argument/container/containerKey以及error.arguments。不得通过toString取得完整违例或路径；Map key一律通配符。四种异常分别取字段/对象/参数信息，禁止把返回值明细塞进同一输入列表。

验收：

- FIELD/PARAMETER/OBJECT及argN降级、数组/List数值索引、Map通配符、对象`$`可断言。
- 固定消息不包含恶意自定义插值中的口令/请求值；未知约束安全兜底；安全消息没有min/max/regexp参数。
- 100项不截断，101项totalCount=101、errors=100、truncated=true；规范化后排序一致，重复保留，Unicode截断不破坏代理对。
- 返回值节点优先；混合/空/纯Bean/未知路径按服务端分类；ConstraintViolationException 的参数与 CROSS_PARAMETER 路径才可确认输入。HandlerMethodValidationException 只消费当前 Spring 6.1.21 提供的参数/字段/Bean 全局结果，不编写该版本不存在的跨参数结果 API；跨参数用例通过实际 ConstraintViolationException 路径验证。
- 模型implements Serializable、列表快照不可修改，默认工厂能够承载data，普通Jackson输出字段名与设计一致。

### T3：配置与四类校验入口整合

文件：

- Modify: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/config/ExceptionProperties.java` — 嵌套Validation和默认false开关。
- Modify: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/config/ExceptionAutoConfiguration.java` — 把属性传入新增构造，保留既有总开关及条件注册；（审查推荐）`@ConditionalOnWebApplication` 补 `type = SERVLET`，与处理器依赖 HttpServletRequest 的实际作用域一致，若不纳入则在 README 记录为范围外；（审查推荐）采用类级条件，README 同时说明 Reactive 应用也不再注册缺省 `ExceptionResponseFactory` Bean（仓库内仅本处理器消费，需要时由应用自行声明），且类级 `@EnableConfigurationProperties(ExceptionProperties.class)` 不再生效、不再提供 `ExceptionProperties` Bean（需要时由应用自行启用绑定）。
- Modify: `mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/handler/MimirExceptionHandler.java` — T1交接后增加两种data路径与输入/输出分类，不再次调整已锁定HTTP头规则。
- Modify: `mimir-boot-starters/mimir-boot-starter-exception/src/test/java/com/yggdrasil/labs/exception/config/ExceptionPropertiesTest.java`、同目录`ExceptionAutoConfigurationTest.java` — 默认、显式、非法绑定和旧构造。
- Modify: `mimir-boot-starters/mimir-boot-starter-exception/src/test/java/com/yggdrasil/labs/exception/handler/DefaultExceptionResponseFactoryTest.java` — 新包装不会因非Serializable被丢弃。
- Modify: T1已交接的 `MimirExceptionHandlerTest.java` — 默认旧data逐项回归与新模式工厂参数断言。

验收：（审查补充）Reactive 上下文不装配处理器、缺省工厂 Bean 及 `ExceptionProperties` Bean（若纳入 type=SERVLET），Servlet 上下文装配不变；子类仅调用单参构造时开启配置仍为旧data，改用双参构造后生效。默认旧字符串的字段前缀、原始message、顺序保持；其他两类旧data仍为null。开启后仅输入路径结构化；输出/未知无论开关都500/null。原单参构造使用默认false；属性禁用总处理器仍有效。开关无效文本绑定失败。提取异常时data降级null，分类异常按500，不暴露元数据内容或重复调用工厂。

### T4：真实MVC、兼容与迁移文档

前置：Exception T1至T3完成；Web W-TC已完成限定候选与Object包装器安全回归。Web联合用例由Web W-T4独占其测试文件，Exception实施者只补本模块用例并核对联合报告；任一侧尚未实现时E6保持未验收。

文件：

- Create: `mimir-boot-starters/mimir-boot-starter-exception/src/test/java/com/yggdrasil/labs/exception/handler/ValidationDetailsMvcIntegrationTest.java` — 实际Controller与验证器触发四入口/两种模式/返回值边界。
- Modify: `mimir-boot-starters/mimir-boot-starter-exception/src/test/java/com/yggdrasil/labs/exception/config/ExceptionAutoConfigurationIT.java` — 自定义工厂、总开关与Bean覆盖的集成证据。
- Modify: `mimir-boot-starters/mimir-boot-starter-exception/pom.xml` — 仅在实际MVC测试缺验证实现时增加测试范围提供器；不得给运行时增加校验实现。
- Modify: `mimir-boot-starters/mimir-boot-starter-exception/README.md` — 精确配置名、默认data、结构化例子、消息安全边界、状态优先级与迁移变化。
- Modify: `docs/active/v2.4.0/release.md` — 由版本整合者在实施验收后记录实际结果，本轮不写已完成。

迁移内容必须覆盖：

1. 原被通用500吞掉的标准状态/根类型注解开始生效；固定Biz/System不变，cause注解仍不透传。
2. 工厂ResponseEntity只能提供body/合法头，不能改变处理器状态；异常协议头优先；静态handler的@ResponseStatus反射元信息不再是状态来源。
3. 默认data未切换；开启新模式后使用errors/totalCount/truncated和安全固定消息，不能继续把data当字符串列表。
4. 约束返回值、混合、空集合或纯Bean未知路径由原400改500，独立于data模式；业务明确输入语境可在自身Advice边界处理。
5. 旧模式自定义消息可能含业务插值，不宣称本轮全部响应都已脱敏；新模式不返回拒绝值和动态Map key。
6. C-07由Web单独控制fail字段，和本开关独立；Common新异常工厂不改变HTTP状态。
7. 合并后的响应直接交给原生MVC；不可表示格式、Resource、条件/Range请求沿用框架行为，不保证原状态、完整body或空体。工厂显式Content-Type也沿用原生处理，协商或真实写出失败不属于工厂回退承诺。
8. 安装Web时统一封装保留既有traceId增强；Web兼容适配需与Exception共同交付。保持原增强开关、已有body标识、MDC traceId/requestId优先级和用户增强器接管；无Web、非R及null不新增追踪行为。当前已返回ResponseEntity的8个分支（HMVE、CVE、缺请求头、缺路径变量、406、415、413、NoResource）本就缺失traceId，属既有缺陷，随联合交付修复（审查更正）。
9. （审查补充）下游自定义ResponseBodyAdvice在全部异常分支看到运行期ResponseEntity外层（MethodParameter参数类型为ResponseEntity），不再是R/Object；按声明类型或泛型判断R的签名、加密、包装Advice可能静默失效，须改为在beforeBodyWrite按运行期body类型判断，并给出改写示例。
10. （审查补充）子类化MimirExceptionHandler：以其他方法名声明`@ExceptionHandler(ResponseStatusException.class)`或ErrorResponseException的子类会在启动时报二义映射，应改为覆盖新方法或删除重复映射；仅调用单参构造的子类结构化开关固定false，需改用双参构造；业务ResponseStatusException/ErrorResponseException子类按继承深度进入新分支。
11. （审查补充）处理器仅适用于Servlet MVC；工厂收到null message时NON_NULL策略会省略message键，最终形态以Web W-T4联合结果为准。

E6联合通过条件：由Web真实应用上下文的MockMvc触发Biz200、System500、普通500和工厂抛错回退，以及既有8个ResponseEntity分支，确认可写R均增强、工厂最多一次；null message的输出形态一并记录；（审查补充）下游自定义ResponseBodyAdvice在运行期ResponseEntity外层下的supports结果与所见body一并记录，与Web W4口径一致；已有traceId不覆盖，MDC traceId优先、requestId回退、均无不生成。关闭Web/response/auto-fill或用户supports=false时不由默认路径补填；用户允许时只委托一次。工厂返回可写字符串、其他可写非R对象或null时，不出现ClassCastException或被重新造body；正常R/ResponseEntity<R>保持增强，正常非R不变。核对原MethodParameter及containing class不被改写，不能通过伪造泛型使断言通过。

（审查补充）兼容测试另覆盖：子类以其他方法名映射ResponseStatusException时启动二义映射（断言为已知迁移结果）、同名覆盖可启动；业务ResponseStatusException子类进入新分支；按运行期body类型判断的自定义ResponseBodyAdvice仍生效、仅按声明类型判断者失效的对照示例。

真实MVC验收需覆盖框架resolver/Advice顺序和消息转换器，不能仅mock所有异常再直接调handler。兼容检查核对原方法/构造描述符；可在本模块测试资源放有来源的旧消费者样例，不创建tools专项脚本。

## Integration Acceptance

以下为后续实施命令，本轮未执行：

```bash
# 模块单元与相关依赖
./mvnw -pl mimir-boot-starters/mimir-boot-starter-exception -am test

# 含MVC及自动配置集成测试
./mvnw -pl mimir-boot-starters/mimir-boot-starter-exception -am verify
```

核对本次Surefire/Failsafe报告中目标类、实际执行数量、失败/错误/跳过；`test`不证明`*IntegrationTest`已运行。E6还需取得Web W-T4执行实际ResponseBodyEnhancerMvcIntegrationTest/Override及联合新增用例的报告，仅运行Exception模块不足以验收E6。T4跨Common新工厂回归依赖其真实实现，若尚未实施则明确未验证，不能用手写模拟工厂宣称完成。

版本整合阶段按[测试规程](../../../engineering/testing.md)执行必要完整验收。模块结果不等于远端CI、生产或发布消费者通过；本轮只做文档结构、链接、术语和跨文档契约检查，独立reviewer由主代理安排。

## Decision Boundary

允许内部解析器拆分、helper命名和同模块测试组织；改变公开签名、状态/头优先级、根异常作用域、默认data、配置名称、结构化字段/排序/上限或安全消息策略须先修订契约并重新对齐。不得借内部重构扩展E-03，新增模块/运行时校验实现，或改Common/Web其他行为。Web增强适配只能交其W-TC范围实施；Exception不得建立反向依赖、改写泛型元信息或增加第二条增强路径。

禁止为本需求新增一次性tools脚本、专项验收入口或迁移脚本；正式回归放模块测试，临时消费者材料放临时验证目录。SDD草案及审查不构成实施、Git写入或发布授权。
