---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Web 日期绑定与 JSON 输出实施计划

静态输入，未执行。[规格](./spec.md) · [设计](./design.md)

## 目标与决策

实现W1/W2及C-07在Web的W3接入，并通过W4保持Exception统一封装后的既有响应增强。JavaTime默认开启，大整数默认关闭，旧fail恢复默认关闭；业务覆盖及作用域以design为准。三个新增开关互不联动，但均以Servlet应用及既有 `mimir.boot.web.enabled=true` 为上层前提（审查补充）；W4复用原增强开关。Common不添加Jackson运行依赖；不自动增加springdoc功能。

## 任务依赖与文件范围

下文主代码相对路径前缀为 `mimir-boot-starters/mimir-boot-starter-web/src/main/java/com/yggdrasil/labs/web/`，测试相对路径前缀为同模块 `src/test/java/com/yggdrasil/labs/web/`。WebProperties由W-T1独占，一次性加入三组新字段（`mvc.date-format-enabled`、`serialization.write-large-integers-as-string`、`json.include-legacy-fail`），W-T2/W-T3只读取不修改；W-T2、W-T3串行修改共享的JacksonConfig；W-TC独占增强器及其测试，可与它们及Exception局部实现并行。W-T4须在W-TC交接测试文件后整合，不能同时编辑同一文件。

**测试文件归属（审查补充）**：测试文件与主代码一样按任务独占，避免并行任务同时编辑。W-T1拥有新增的W1自动配置ApplicationContextRunner测试（如 `config/MimirDateTimeWebMvcAutoConfigurationTest.java`，名称可调整）及既有 `config/WebPropertiesTest.java`（三组新字段默认值与绑定）；W-T2拥有新增 `serialization/` 下数值序列化测试；W-T3在W-T2之后拥有新增R MixIn测试（如 `serialization/RSuccessMixinTest.java`）；W-TC拥有下文所列增强器测试（含既有 `advice/ResponseBodyEnhancerWebMvcRegistrationsMvcIntegrationTest.java`），完成后交接给W-T4；W-T4独占既有 `config/WebAutoConfigurationTest.java`、`config/WebAutoConfigurationIT.java` 的复用与扩展及联合MVC测试。W-T1至W-T3不修改WebAutoConfigurationTest/IT。

| 任务 | 依赖 | 范围 |
|---|---|---|
| W-T1 配置与日期绑定 | 无 | config/WebProperties.java（含三组新字段）、新独立W1自动配置与格式器、`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`；测试：新增W1自动配置ApplicationContextRunner测试、既有WebPropertiesTest；不改WebAutoConfiguration条件 |
| W-T2 数值序列化 | W-T1配置结构 | config/JacksonConfig.java、新数值serializer/module与元数据modifier及其新增测试；读取W-T1已加字段 |
| W-T3 成功状态迁移 | W-T2共享装配；Common C-07契约 | config/JacksonConfig.java、新R MixIn及其新增测试；读取W-T1已加字段 |
| W-TC 异常追踪兼容 | W4/Exception E6契约；局部实现不依赖Exception代码落地 | advice/ResponseBodyEnhancer.java、内部Object包装器边界及增强器测试，不改共享配置 |
| W-T4 集成与文档 | W-T1至W-T3、W-TC；联合异常验收另依赖Exception T1至T3 | README、复用扩展既有WebAutoConfigurationTest/IT，独占Web联合MVC测试，不修改Exception文件 |

### W-T1：MVC缺省策略

文件：

- Modify: `config/WebProperties.java` — 一次性加入W1/W2/W3三组字段及默认值（true/false/false），后续任务不再修改。
- Create: `config/MimirDateTimeWebMvcConfigurer.java`、`config/MimirDateTimeWebMvcAutoConfiguration.java`（名称可调整）— 后者为 `@AutoConfiguration(after = WebMvcAutoConfiguration.class)`，带SERVLET、`mimir.boot.web.enabled`、`mimir.boot.web.mvc.date-format-enabled` 及 `EnableWebMvcConfiguration` Bean条件。
- Modify: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — 在既有WebAutoConfiguration之后追加上述自动配置。
- Create: 测试 `config/MimirDateTimeWebMvcAutoConfigurationTest.java`（名称可调整）— ApplicationContextRunner装配顺序、条件及无格式器Bean暴露。
- Modify: 测试 `config/WebPropertiesTest.java` — 三组新字段默认值与绑定，含非法布尔值启动失败。

实施约束：按设计仅在Boot自动MVC条件装配、顺序-1、仅补缺省的三类型，保留字段注解与ISO回退；格式器只在 `addFormatters` 内创建注册，不声明为Formatter/Converter类Bean，避免被 `ApplicationConversionService.addBeans` 以顺序0重复注册（审查补充）。不经WebAutoConfiguration的 `@Import` 引入W1，不修改WebAutoConfiguration既有条件、现有WebMvcConfig构造器或拦截器路径。

验收：MockMvc覆盖W1全部场景及非法pattern启动失败；独立比较Date、Calendar结果。ApplicationContextRunner验证W1在WebMvcAutoConfiguration之后评估且Bean条件命中，`mimir.boot.web.enabled=false` 与非Servlet应用不装配，上下文中无W1提供的Formatter/Converter类Bean。显式Configurer排序覆盖限制写入README。

### W-T2：安全整数与白名单

Modify JacksonConfig；Create 同模块 `serialization/` 内数值模块、Long/BigInteger/long[] serializers、元数据modifier，允许按内部职责合并文件。仅内部实现，不作为新公共SPI。按设计安装顺序及显式字段覆盖语义，反序列化不改。

验收：W2各容器与界值测试，含Long.MIN_VALUE；白名单/同名业务字段反例；R/PageResult/PageRequest子类继承及覆盖getter的元数据仍为number；JsonFormat、JsonSerialize及消费者Module组合（以实际注册顺序验证消费者Module覆盖Mimir兜底，不按modulesToInstall列表书写顺序推断）；关闭开关或总开关的JSON与旧基线相同。

### W-T3：R迁移适配

Create 同模块 `serialization/RSuccessMixin.java`；Modify JacksonConfig的builder后处理，只在无业务R MixIn时装配。Common需求负责行为定义、旧Java方法兼容；本任务拥有所有Jackson实现。

实施约束：检测与安装放在customizer登记的 `Jackson2ObjectMapperBuilder.postConfigurer` 中，对已构建mapper调用 `findMixInClassFor(R.class)`，不在customizer主体中判断（builder无mixIns getter；postConfigurer在registerModules与mixIns登记之后执行）（审查补充）。检测到业务R MixIn而跳过时，在首次构建受管ObjectMapper时记录一条INFO日志（含业务MixIn类名、跳过原因和迁移提示，不含业务数据）；现有customizer为无状态lambda（JacksonConfig约61-112行），须改为有状态的内部customizer类或由@Bean方法一次创建的闭包承载去重集合，集合并发安全（如 `ConcurrentHashMap.newKeySet()`），同一上下文内同一MixIn类只记一次，不使用静态Set（审查补充）；检测仅针对R类本身。

验收：W3全部场景，外加受管builder二次构建、业务完全自建mapper、已有业务MixIn与兼容开关组合；兼容开关不会删除业务定制。跳过时同一上下文内多次（含并发）构建受管mapper，INFO日志恰好一条；两个先后运行的ApplicationContextRunner上下文各自恰好一条，结果不依赖测试执行顺序；为R子类注册的MixIn不阻止框架MixIn安装；经builder `mixIn`、Module及排序早于框架的postConfigurer登记的R MixIn均被检测；排序晚于框架的postConfigurer或构建后 `mapper.addMixIn` 覆盖框架MixIn且无INFO，作为已知盲区用例固化；总开关关闭或非Servlet时共享mapper仍输出fail。

### W-TC：Exception封装的既有追踪增强兼容

行为契约见W4和 [Exception I3b](../exception-contracts/design.md#i3bweb-追踪增强兼容协作e6)。只在默认增强器补候选，仍由既有内部Object Advice按实际R值保护委托，不增加公共SPI、属性或追踪策略。

文件归属：

- Modify: 主代码 `advice/ResponseBodyEnhancer.java` — 保留原公开构造、方法和 `ResponseBodyAdvice<R<?>>`，仅补充“RestControllerAdvice、原泛型声明Object、运行期ResponseEntity”的候选；保留原注解作用域、enabled与泛型R判断，不泛化为所有未知HttpEntity。
- 既有主代码 `config/ResponseBodyEnhancerAdviceFactory.java` 是本任务的集成边界：保留内部 `ResponseBodyAdvice<Object>` 的supports原样委托和beforeBodyWrite的 `instanceof R<?>` 检查，无需为修复新增另一Advice。`config/WebAutoConfiguration.java` 保留原增强器Bean退让和唯一内部包装器注册；本任务不新增注册或更改配置。
- Modify: 测试 `advice/ResponseBodyEnhancerTest.java` — 以Spring运行期返回值元信息验证Object声明、实际ResponseEntity<R>的候选；保留直接R/泛型ResponseEntity<R>，同时验证显式非R、非Advice及其他未知HttpEntity不被无条件放行。
- Create: 测试 `config/ResponseBodyEnhancerAdviceFactoryTest.java` — 直接覆盖Object包装器非R/null保护、用户supports=false退出及允许时R委托一次，确认传入原MethodParameter及converter参数；不通过调用R泛型桥接方法强转非R。
- Modify: 测试 `advice/ResponseBodyEnhancerMvcIntegrationTest.java`、`advice/ResponseBodyEnhancerOverrideMvcIntegrationTest.java`、`advice/ResponseBodyEnhancerWebMvcRegistrationsMvcIntegrationTest.java`（既有，审查补充归属）— W-TC先补复现及局部回归（后者至少回归WebMvcRegistrations路径下候选修复不改变既有行为），完成后交W-T4补真实Exception联合场景。其他开关/工厂组合用例允许在同一advice测试包内按上下文拆类，均由W-TC交W-T4串行接管。

先复现Object声明/实际ResponseEntity使supports=false与现有异常traceId断言冲突，再做最小候选修复。声明类型只负责是否进入候选，解包body的运行值决定是否可调用R泛型delegate；原MethodParameter及containing class均不改。包装器不能在用户supports=false后自行fallback，不能在Exception回填traceId。

局部验收：候选修复的正反例、非R/null不调用R桥接方法、原公开构造/方法/泛型接口兼容、用户允许/拒绝语义。局部通过不代表联合验收；W-T4须等待实际Exception统一封装后再验证。

### W-T4：集成与迁移说明

Modify 模块 `README.md`；在模块 `src/test/java/com/yggdrasil/labs/web/` 内补MVC及JSON行为测试，复用现有WebAutoConfigurationTest/IT与响应增强测试。文档列三开关及其Servlet/`mimir.boot.web.enabled` 上层前提（不满足时共享mapper仍输出fail）、日期SHORT迁移、受管mapper跨MVC影响、元数据豁免及子类匹配规则、业务R MixIn导致跳过框架MixIn的行为与INFO日志、检测仅针对R类本身、检测盲区（排序晚于框架的postConfigurer或构建后 `mapper.addMixIn` 为R加MixIn会覆盖框架MixIn且无INFO，推荐经builder `mixIn`/Module登记）、oneOf示例及不自动适配OpenAPI边界；补充Exception统一封装与Web增强兼容须共同交付、原增强开关与用户接管保持，不宣称新增标识能力。

W4联合矩阵由本任务独占Web测试文件，依赖Exception T1至T3和W-TC；结果供Exception T4复核，不要求Exception模块反向依赖Web：

- 通过真实Boot上下文及DispatcherServlet的resolver/Advice/converter链触发Biz200、System500、普通500和工厂抛异常后的默认R回退；均在Object声明/实际ResponseEntity路径断言可写body的traceId及原状态、头、工厂最多一次。不能用手写替代Advice或仅反射方法声明类型验收。
- body已有非空值保持；MDC同时有traceId/requestId时取traceId，仅requestId存在时回退，两者均空不生成。分别关闭Web、response.enabled、response.auto-fill-trace-id时不由默认路径补填；用户增强器允许时委托一次、supports=false时无增强动作及额外补填。
- 既有缺陷回归（审查补充）：升级前已返回ResponseEntity的8个Exception分支——HandlerMethodValidationException、ConstraintViolationException、MissingRequestHeaderException、MissingPathVariableException、406（HttpMediaTypeNotAcceptableException）、415（HttpMediaTypeNotSupportedException）、413（MaxUploadSizeExceededException）、NoResourceFoundException——当前因supports=false丢失traceId；在响应可写时逐一断言补上traceId及原状态，作为W-TC修复的回归项，不视为新增能力。
- null message形态（审查补充，承接Common B1）：BizException的message为null时，默认 `write-nulls=false` 下响应JSON无 `message` 键，开启后为 `"message": null`；Common只记录该形态，由本任务验证。
- 下游自定义Advice（审查补充）：注册一个不替换框架增强器的业务 `ResponseBodyAdvice`，在Object声明、运行期ResponseEntity的异常路径记录其supports入参类型与beforeBodyWrite收到的body，结论写入README升级说明；框架不为业务Advice补做候选判定。
- 工厂分别返回可写字符串、其他可写非R对象或null；不增加trace字段、不出现ClassCastException，默认增强不为null构造body。与正常R、ResponseEntity<R>及非R响应做同链回归，核对原MethodParameter及containing class不被改写。原生MVC继续负责不可表示格式、Resource、条件/Range请求，不验收这些情况下的固定状态或空体。

以下为后续实施命令，本轮未运行：`./mvnw -pl mimir-boot-starters/mimir-boot-starter-web -am test` 与 `./mvnw -pl mimir-boot-starters/mimir-boot-starter-web -am verify`。按现有Failsafe入口实际执行ResponseBodyEnhancerMvcIntegrationTest、Override、WebMvcRegistrations及联合新增用例，并核对本次报告中的类名、数量、失败/错误/跳过；test通过或应用启动不能替代集成报告。独立消费者同时验证Common单独与Web受管两条路径。通过条件是W1/W2/W3与总前提成立下的8组新增开关组合、总开关关闭及非Servlet两组对照、W4矩阵均符合规格。Exception或Web任一适配尚未落地时，W4/E6明确记为未验收。

## 决策边界

内部类拆分与测试组织可自主调整；三开关默认值、公共格式优先级、元数据类型、fail迁移边界变化先修订契约。W-TC/W-T4只拥有Web文件，不能改Exception实现；不得改变公开增强器构造/方法/泛型接口、用户supports退出或converter元信息契约，不得建立Exception→Web反向依赖。不得扩展W-03或引入Common Jackson运行依赖，不新增tools专项验收脚本。本轮文档不代表已经实施或发布。
