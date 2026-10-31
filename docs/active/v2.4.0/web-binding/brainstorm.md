---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Web：日期绑定与 JSON 输出方案大纲

## 本轮推荐与 SDD 产物

2026-10-29 用户要求停止逐项提问，并补齐每项需求的 SDD。本文保留前期讨论过程；未逐项确认的细节已由本轮推荐方案收敛到 [spec](./spec.md)、[design](./design.md)、[plan](./plan.md)，推荐不追记为历史用户决定。以下“候选”“待讨论”“后续”等措辞描述此前状态，当前推荐以 SDD 为准；文档不代表实现或验收完成。

本需求归属 `mimir-boot-starter-web`，承接[版本级 Brainstorm](../brainstorm.md) 的 W-01、W-02（D-45 至 D-49）。本文保留讨论草案；其中推荐方案、属性组织和验证方向已由 SDD 收敛为推荐草案（非用户逐项确认）。2026-10-31 审查补充的生效前提、W1 独立自动配置、白名单子类匹配和 MixIn 跳过日志见 [design](./design.md)。

## 目标与已确认范围

| 需求 | 目标 | 已确认约束 |
|---|---|---|
| W-01 | MVC 查询参数、表单中的 Java Time 类型与 JSON 日期格式统一 | 默认开启、可关闭；注解与业务显式配置优先；保留 ISO 回退 |
| W-02 | 避免 JavaScript 消费大整数时丢失精度 | 默认关闭；开启后 Long/long、BigInteger 超出 ±(2^53-1) 才输出字符串；时间戳与分页数量保持数字 |
| C-07 接入协作 | 为 Common 的 R 成功状态收敛提供 Jackson 接入 | 需求归属 [Common](../common-contracts/brainstorm.md)，本模块承担序列化适配，不重复立项 |

W-03 回传 `X-Request-Id` 仍为候选。CORS、请求标识生成、响应增强和 Servlet 异步 MDC 不扩展范围。

SDD中的W4仅保持Exception统一封装后的既有traceId增强：公开增强器接口不变，内部Object适配器检查实际R值，保留原MethodParameter及containing class。Exception合并响应后直接交给原生MVC，不额外保证不可表示格式、Resource或条件/Range请求下的固定状态或空体；实现及联合验证归Web W-TC/W-T4。

## 当前入口与依据

本轮静态核对以下代码，未运行 MVC 或序列化验证：

- [WebProperties](../../../../mimir-boot-starters/mimir-boot-starter-web/src/main/java/com/yggdrasil/labs/web/config/WebProperties.java)：已有 `mimir.boot.web.serialization` 日期格式、时区及 JSON 属性。
- [JacksonConfig](../../../../mimir-boot-starters/mimir-boot-starter-web/src/main/java/com/yggdrasil/labs/web/config/JacksonConfig.java)：通过 builder customizer 注册 JavaTimeModule，并收集消费者的 Module。
- [WebMvcConfig](../../../../mimir-boot-starters/mimir-boot-starter-web/src/main/java/com/yggdrasil/labs/web/config/WebMvcConfig.java)：当前只注册拦截器，没有日期 formatter 接入。

Boot 默认解析顺序及 `java.util.Date` 风险沿用版本级源码核对结论；具体装配顺序留到方案验证。

## W-01 方案大纲

优先比较两个接入方案：

| 方案 | 优点 | 需要解决的问题 |
|---|---|---|
| 推荐候选：仅为 LocalDate、LocalTime、LocalDateTime 注册格式器 | 不把 Mimir 的日期默认值扩展到传统 Date 类型；边界较清晰 | 必须验证与 Boot 格式器、业务 `WebMvcConfigurer`、`@DateTimeFormat` 的覆盖顺序 |
| 备选：向 `spring.mvc.format.*` 提供低优先级默认值 | 复用 Boot 的绑定和格式器装配 | `date` 同时影响 `java.util.Date`，有截断时间的风险；还需处理早期属性注入与关闭后的恢复 |

推荐候选仍须兑现 D-47：`spring.mvc.format.*` 是业务显式配置入口；只有相应配置缺失时才采用 Mimir JSON 格式。不能注册一个无条件格式器覆盖业务配置。

预期处理链为：读取业务注解或显式格式 → 缺省时读取已有 Mimir 日期格式 → 按已选格式处理 → 保留约定的 ISO 回退。开关关闭后恢复原有 MVC 配置路径。

后续首先确认：

1. 开关放在 MVC 专属配置组还是现有 serialization 组；名称在确认装配方式后确定。（已同步：SDD 推荐 `mimir.boot.web.mvc.date-format-enabled`，见 [design](./design.md#配置和接口契约)。）
2. `@EnableWebMvc`、自定义 ConversionService 等接管场景是否自动退让，哪些需要手动接入。
3. Mimir 格式启用后 locale SHORT 输入不再兼容的升级提示，以及空字符串、非法格式的处理边界。
4. 传统 `java.util.Date` 默认不受新增行为影响的方案是否可行；若不成立，需要显式提出兼容性取舍。

## W-02 方案大纲

推荐在现有 Jackson 装配路径增加可选数值序列化策略，按数值是否越界选择 JSON number 或 string；不修改 Java 字段类型，也不改变反序列化契约。

替代方案是只给特定字段加注解，影响面更小，但无法满足已确认的统一开关目标，可作为消费者覆盖方式保留。

后续展开四个部分：

- **覆盖面**：对象属性、集合、Map 值、Long 数组、基本类型 `long[]`、BigInteger；Map 键与数值输出分开处理。
- **优先级**：字段级 `@JsonFormat`、`@JsonSerialize`、消费者 Module 与框架策略之间的明确顺序，避免覆盖既有自定义序列化。
- **框架元数据**：明确 `R.timestamp`、分页数量保持数字的适用类型清单；比较 MixIn/字段级豁免与专用序列化器，不把普通业务 Long 一概豁免。
- **接口描述**：同一字段可能输出数字或字符串，需要在升级示例与 OpenAPI 描述中表达；先确认是否自动适配 springdoc，不能默默新增强制依赖。

## C-07 序列化协作

优先评估 Jackson MixIn，保持 Common 无 Jackson 运行时依赖。共享 Boot ObjectMapper 及通过 Boot builder 构建的 mapper 都可能受影响，范围不只 MVC；手动 `new ObjectMapper()` 和 fastjson2 等路径不自动变化。

是否提供恢复 `fail` 输出的过渡开关、开关位置和移除时机由 Common 与本需求共同讨论。（已同步：SDD 推荐 `mimir.boot.web.json.include-legacy-fail=false`，2.x 保留、下个主版本再评估；属推荐草案，不是已确认决定。）

## 后续验证方向

| 主题 | 需要观察的结果 |
|---|---|
| 日期绑定 | 缺省、业务配置、字段注解、ISO、开关关闭及传统 Date 的组合结果 |
| 数值边界 | ±(2^53-1) 两侧、Long 极值、BigInteger、null、集合和基本类型数组的实际 JSON |
| 序列化组合 | 消费者 Module 与字段注解能够按确定的优先级覆盖；框架元数据保持约定类型 |
| R 迁移 | 成功/失败响应、共享与独立 mapper、过渡模式分别验证，不将一种 mapper 的结果外推到全部序列化路径 |

建议先收敛 W-01 的接入层，再讨论 W-02 的覆盖与豁免，最后与 Common 一起确认 C-07 迁移范围。本文不包含实施计划或已完成的验收结论。
