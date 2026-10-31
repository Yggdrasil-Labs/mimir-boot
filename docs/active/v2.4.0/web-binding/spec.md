---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Web 日期绑定与 JSON 输出需求规格

推荐规格，未实施；D-45至D-49以及C-07方向已确认，具体接入与迁移开关是本轮推荐。[讨论](./brainstorm.md) · [设计](./design.md) · [计划](./plan.md)

## 目标与非目标

提供默认日期格式衔接、可选安全整数输出，并承接Common响应状态迁移及Exception统一封装后的既有追踪增强兼容。保留业务显式定制；不扩展CORS、请求标识回传或Servlet异步MDC，不引入强制接口描述依赖。

## 生效前提（审查补充）

W1至W4都挂在Web自动配置之下：仅当应用为Servlet Web应用且 `mimir.boot.web.enabled=true`（缺省即true）时生效。非Servlet应用（WebFlux、无Web的消息/任务服务）或 `mimir.boot.web.enabled=false` 时，日期格式器、数值策略、R MixIn与响应增强均不装配；此时Boot共享mapper仍按原样输出 `fail`，C-07的默认新协议不适用。该前提是推荐草案中的审查补充，不是新的用户确认决定。

## Behavior W1：日期参数默认与JSON格式一致

| Scenario | Given | When | Then |
|---|---|---|---|
| 默认绑定 | 默认日期统一能力开启、无业务覆盖 | 提交日期时间参数2026-10-29 13:14:15 | 绑定为对应的本地日期时间；日期2026-10-29与时间13:14:15也可各自绑定 |
| 业务覆盖 | 业务显式指定日期格式dd/MM/yyyy，或字段声明自身格式 | 提交29/10/2026或符合字段格式的文本 | 按显式格式处理；不被框架默认覆盖 |
| ISO边界 | 默认格式开启 | 提交2026-10-29T13:14:15 | 保留ISO回退并得到相同时间值 |
| 无效输入 | 没有业务转换器接受该文本 | 提交not-a-date | 绑定失败，经标准MVC错误路径返回400；不以当前时间代替 |
| 关闭或接管 | 关闭该能力，或业务接管MVC配置 | 绑定日期参数 | 沿用业务/原框架行为，新增格式器不介入；传统日期类型不受新增默认影响 |
| 总开关或应用类型不满足 | `mimir.boot.web.enabled=false`，或非Servlet应用；W1开关保持默认true | 绑定日期参数 | 新增格式器不装配，沿用Boot原行为 |

## Behavior W2：安全整数可选输出

| Scenario | Given | When | Then |
|---|---|---|---|
| 开启 | 业务开启大整数策略、无字段级覆盖 | 输出业务整数9007199254740992及其相反数 | 分别输出字符串，十进制值完整保留 |
| 临界值 | 策略开启 | 输出±9007199254740991、0、null | 非空值仍为JSON number；null遵守既有空值输出配置 |
| 容器 | 策略开启 | 输出对象字段、列表、Map值、包装数组及基本类型长整数数组 | 每个值按同一阈值处理，顺序不变；Map键不因此重写 |
| 元数据 | 策略开启、无显式消费者覆盖 | 输出框架响应时间戳及分页数量 | 这些已列明字段仍为number，即使超出阈值；业务大整数不因名字相同而豁免 |
| 元数据子类 | 策略开启，业务继承R、PageResult或PageRequest且未另行改写对应属性的序列化 | 输出子类实例的已列明元数据字段 | 仍为number；子类新增的其他Long字段按普通业务数值处理 |
| 总开关不满足 | 策略开启，但 `mimir.boot.web.enabled=false` 或非Servlet应用 | 输出大整数 | 不安装数值策略，保留既有number格式 |
| 关闭或异常 | 策略关闭，或业务序列化器抛错 | 输出相同对象 | 关闭时保留既有格式；序列化错误沿既有异常路径传播，不返回静默截断的值 |

## Behavior W3：响应成功状态迁移（承接C-07）

| Scenario | Given | When | Then |
|---|---|---|---|
| 新默认 | Web受管序列化器、未配置业务响应MixIn | 输出成功或失败的公共响应 | 保留success，省略fail；Java两个判断方法仍存在 |
| 过渡模式 | 显式开启旧字段兼容 | 输出公共响应 | 同时保留success和fail，值互为相反 |
| 接管边界 | 业务自建序列化器，或给响应类型显式定义MixIn | 输出响应 | 采用业务规则；不能承诺全部JSON路径自动移除fail |
| 业务R MixIn导致跳过 | 默认兼容开关关闭，业务为R类型本身注册了任意MixIn（即使未涉及fail） | 构建受管mapper并输出响应 | 框架不安装自身MixIn，fail按业务规则输出（通常仍出现）；首次构建受管ObjectMapper时（Boot的builder为原型作用域，mapper可能延迟构建，不一定在启动阶段）有一条INFO日志说明跳过原因及业务MixIn类名（审查补充） |
| 晚于框架登记的R MixIn（审查补充） | 业务在排序晚于框架的builder后处理中，或在mapper构建后直接为R添加MixIn | 输出响应 | 业务MixIn替换框架MixIn，fail按业务规则输出；框架无法感知，不记录INFO日志；README说明该盲区及推荐登记方式 |
| 总开关不满足 | `mimir.boot.web.enabled=false` 或非Servlet应用，兼容开关保持默认 | 用共享mapper输出公共响应 | 框架MixIn不安装，success与fail均输出，与升级前一致 |

## Behavior W4：异常封装保留响应追踪增强

本行为是 [Exception E6](../exception-contracts/spec.md#behavior-e6统一异常封装保留既有响应追踪增强) 的兼容协作，不新增追踪能力。

| Scenario | Given | When | Then |
|---|---|---|---|
| 默认与工厂回退 | Web及响应增强、自动填充开启；上下文traceId为trace-1；失败响应无标识 | 输出统一封装后的业务、系统、普通异常及工厂失败后的默认响应 | 可写公共响应体均含traceId=trace-1；业务200、系统500、普通500及回退的已解析状态保留 |
| 已有值及优先级 | body已有body-1，或无标识且上下文traceId=trace-1、requestId=request-1 | 输出公共响应体 | 已有值保持body-1；否则优先trace-1，仅requestId存在时用request-1；两者皆无时不生成 |
| 开关关闭 | 关闭Web、响应增强或自动填充任一对应入口 | 输出无标识响应 | 默认增强不补填traceId，不改变既有body与HTTP语义 |
| 业务接管 | 业务替换增强器，分别允许或拒绝当前响应 | 经相同响应链输出 | 允许时仅委托一次；拒绝时不调用其增强动作，也不由额外兼容路径补填 |
| 非公共格式及null | 工厂返回可写字符串、其他可写对象或null | 输出响应 | 非公共响应格式保持原值且无强转异常；默认增强不为null构造新响应 |
| 正常接口回归 | 正常接口返回公共响应、显式封装的公共响应或非公共格式 | 输出可写响应 | 前两者保留原增强，后者保持原格式；兼容调整不改变正常状态、头和内容选择 |
| 既有ResponseEntity分支（审查补充） | 升级前已返回ResponseEntity的8个异常分支：方法级校验（HandlerMethodValidationException）、约束违反（ConstraintViolationException）、缺少请求头、缺少路径变量、406、415、413、静态资源不存在；上下文traceId为trace-1 | 输出可写公共响应 | 均含traceId=trace-1；这些分支当前丢失traceId属于既有缺陷，按回归项验收 |
| null消息（审查补充） | 失败响应message为null，Web默认不写null | 输出公共响应 | JSON无 `message` 键；开启写null时为 `"message": null`；traceId增强不受影响 |
| 下游自定义Advice（审查补充） | 业务另有自己的ResponseBodyAdvice（不替换框架增强器），异常处理以Object声明、运行期返回ResponseEntity | 输出异常响应 | 记录该Advice的supports判定与beforeBodyWrite实际收到的body；框架不为业务Advice补做候选判定，行为差异写入升级说明 |

## Constraints

安全整数阈值为±(2^53-1)，即±9007199254740991（D-48）。W1默认开启；W2默认关闭。三个新增开关彼此独立，但都位于Servlet应用与 `mimir.boot.web.enabled` 总开关之下；总开关关闭时三者均不生效。新增逻辑不改变Java字段类型或反序列化规则。Common不增加Jackson运行依赖；共享受管mapper可能服务MVC以外用途，升级需检查这些消费者。推荐旧fail兼容开关在2.x保留，下个主版本再评估，不自动到期。

W4复用既有增强开关，不增加配置、公共SPI或追踪策略；既有增强器公开构造、方法及泛型接口保留。适配归属Web且联合验证依赖Exception统一封装实现，不引入Exception到Web的反向模块依赖。

W4保留原返回值元信息和原生MVC处理；不可表示格式、Resource、条件/Range请求按框架规则执行，不保证这些情况下仍为原状态或空体。
