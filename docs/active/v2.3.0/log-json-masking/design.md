---
id: log-json-masking-design
version: v2.3.0
status: verified
owner: YoungerYang-Y
created: 2026-09-22
updated: 2026-09-23
---

# 日志 JSON 脱敏补全 — 技术设计

## Context

行为依据为 [spec.md](./spec.md)，执行依据为 [plan.md](./plan.md)。本轮按用户要求提供完整 SDD；实现仍为单模块局部改造，不改变模块依赖方向。

TD-038 记录了 `api_key`、`account` 等字段型预置规则在普通 `key=value` 日志中能脱敏、在 JSON 的带引号键名中不能匹配的问题。`SensitiveDataConverter` 已具备可处理键名引号、值引号和转义引号的字段值扫描器，但当前仅为 `password`、`token` 和 `secret` 三组预置规则提供字段名集合；其他字段型规则仍通过正则处理。

本设计在 `mimir-boot-starter-log` 内统一字段型预置规则的匹配路径，使已启用的规则在普通赋值和 JSON 形式中得到相同的保护。规划版本 `v2.3.0` 由 `main` 分支和最近 `v2.2.1` tag 推导；该目录仅记录需求设计，不承诺发布版本。

## Goal

启用任一字段型预置规则时，对符合 IC-03 文本语法的标量值完成脱敏：键名无论是否使用单引号或双引号，均替换值并保留原键名、分隔符、值的外围引号及可识别的后续非敏感内容；包含转义引号或转义反斜杠的带引号值不得泄露原值。键名沿用别名后缀匹配，不承诺完整键名精确匹配。

## Non-Goal

- 不新增、改名或改变 `mimir.boot.log.mask` 现有配置语义，不改变 `SensitiveDataConverter` 的公开方法签名。
- 不引入完整 JSON 解析器，不验证日志是否为合法 JSON，也不支持嵌套对象的语义化字段路径匹配。
- 不保证对象或数组作为敏感字段值时的完整脱敏，也不把未引用且含逗号、闭合括号或空白的文本视为单个完整值。此类内容须在写日志前移除或预先脱敏；不能把日志转换器作为其保护措施。
- 不扩大纯值规则（身份证号、手机号、银行卡号、邮箱地址）的匹配范围，不改变自定义或编程式正则规则的配置接口。

## Architecture

```mermaid
flowchart LR
    A[ILoggingEvent 格式化消息] --> B[SensitiveDataConverter]
    Config[配置发布或首次读取] --> C[构建并原子发布配置快照]
    C --> D[字段名集合及正则列表]
    B --> E[使用当前快照扫描键名和值]
    D --> E
    E --> F[保留键名、分隔符和外围引号并替换值]
    F --> G[纯值预置规则与自定义正则]
    G --> H[脱敏后的日志消息]
```

配置快照继续在发布时一次性构建并以原子引用生效。构建时先将已启用预置规则划分为字段型与纯值型：字段型规则的别名汇入按长度降序排列的字段名集合，纯值型规则才编译为预置正则。自定义和编程式正则仍在随后追加。转换时先扫描字段值，再把扫描后的完整结果交给纯值、自定义和编程式正则。字段扫描已识别并替换的标量原值不会进入正则阶段；后续正则仍可匹配 replacement、普通日志文本，以及对象/数组值等扫描不完整时留下的残余。

字段值扫描沿用现有解析顺序：在消息的每个字符位置尝试匹配别名，不检查别名前的左边界；允许别名后紧跟一个单引号或双引号，再跳过空白并要求 `=` 或 `:`，随后跳过空白并读取值边界。具体语法和保护范围见 IC-03。实现必须移除固定首字符白名单，不能因 `id_card`、`bank_card`、`email`、`name` 等别名首字符不在旧列表中跳过匹配。带引号值使用反斜杠奇偶性识别闭合引号；未闭合的带引号值视为直到消息末尾，仍以脱敏结果替代该段内容。

## Interface Contract

### IC-01：字段型预置规则归类

- 代码接口：`static List<String> SensitiveDataPattern.keyValueFieldNames(Collection<SensitiveDataPattern> patterns)`；签名和可见性不变。
- 输入：已启用的预置规则集合；输出：对应字段型规则的全部既有别名，按长度降序排序并以不可变列表返回。
- 字段型规则与别名必须完全按下表维护；下表为 Spec B1 行为目录的实现映射，变更时先更新 Spec，再同步本表与 Plan 任务副本：

| 预置规则 | 字段别名 |
|---|---|
| `password` | `password`、`pwd`、`passwd`、`%70assword`、`密码` |
| `token` | `token`、`access_token`、`refresh_token` |
| `secret` | `secret`、`private_key`、`privateKey`、`secret_key`、`secretKey`、`access_key`、`accessKey`、`%73ecretKey`、`私钥` |
| `api_key` | `apikey`、`api_key`、`app_key` |
| `account` | `account`、`accountId`、`account_id`、`账号` |
| `id_card` | `idcard`、`id_card`、`身份证` |
| `phone` | `phone`、`mobile`、`tel`、`手机`、`电话` |
| `bank_card` | `bankcard`、`bank_card`、`银行卡` |
| `email` | `email`、`mail` |
| `name` | `name`、`realname`、`真实姓名` |

- `id_card_number`、`phone_number`、`bank_card_number`、`email_address` 不返回字段名，继续使用既有纯值正则。
- 正常路径：启用 `api_key` 时，返回集合包含 `api_key`、`apikey` 和 `app_key`，使 `"api_key":"value"` 与 `api_key=value` 都进入字段值扫描。
- 边界路径：空集合返回空不可变列表；相同前缀的别名按长度优先匹配，避免短别名抢先截断长别名。
- 错误码：无；该内部方法不接收外部 I/O，也不为未知规则抛出异常。

### IC-02：预置规则编译与分流

- 代码接口：`private static List<String> SensitiveDataConverter.compilePresetPatterns(List<Pattern> target, List<String> names)`；签名不变。
- 输入：用于存放预置正则的 `target` 和已启用规则名称；输出：IC-01 定义的字段别名集合。
- 正常路径：先解析有效规则名称，再以 IC-01 的字段型分类将 10 个字段型规则仅放入返回的字段名集合；仅 4 个纯值规则的 `getPattern()` 加入 `target`。
- 边界路径：`null`、空白或未知规则名称维持现有忽略语义；字段型规则不能同时出现在返回字段名集合和 `target`，避免字段值扫描后再次被预置正则处理。
- 错误码：无；预置规则是枚举常量，正则编译异常仍沿用当前告警和忽略策略。

### IC-03：字段值替换

- 代码接口：`private static String SensitiveDataConverter.maskSensitiveData(String message, MaskConfigurationSnapshot snapshot)`；签名不变。
- 输入：非空日志消息和当前配置快照；输出：保留非敏感片段的脱敏消息。`null` 或空消息仍由现有调用方原样返回。
- 正常路径：启用 `account`、replacement 为 `****` 时，`{"account":"alice","tail":"sentinel"}` 必须输出 `{"account":"****","tail":"sentinel"}`；`account=alice, tail=sentinel` 必须输出 `account=****, tail=sentinel`。
- 闭合值边界：`{"api_key":"secret\"still-secret","tail":"sentinel"}` 必须输出 `{"api_key":"****","tail":"sentinel"}`；`{"api_key":"secret\\","tail":"sentinel"}` 必须输出 `{"api_key":"****","tail":"sentinel"}`。
- 未闭合值边界：`api_key="secret\"still-secret, tail=sentinel` 必须输出 `api_key="****"`。该输入没有可识别的值终点，扫描器以消息末尾为界；安全优先于保留无法可靠区分的尾部。
- 键名匹配：沿用现有大小写不敏感的别名比较与长度优先顺序，不要求左边界，也不校验外围键名引号是否成对。别名后只能紧跟一个可选引号、空白及 `=`/`:`；例如启用 `name` 时 `service_name=orders` 输出 `service_name=****`，启用 `account` 时 `not_account=alice` 输出 `not_account=****`，但 `account_extra=alice` 原样保留。这是兼容性的文本后缀匹配，不是 JSON 键名解析。
- 标量语法：跳过分隔符后的空白后，若值以单引号或双引号开头，按连续反斜杠个数的奇偶性确定引号状态：奇数个连续反斜杠使紧随其后的同类型引号作为值内容，偶数个使其闭合。找到未转义的同类型闭合引号后保留外围引号并替换内部；未找到时以消息末尾为界。未引用值扫描到第一个逗号、`}`、`]`、空白或消息末尾。数字、布尔值、`null` 均按普通非引用文本替换，不保留 JSON 类型，也不承诺输出仍为合法 JSON。包含分隔符的完整敏感文本必须以引号包裹。
- 缺失值与空字符串：`account=`、`account=, tail=sentinel` 原样保留；`account=""` 输出 `account="****"`，`account=''` 输出 `account='****'`。后两者是有外围引号的值，继续替换以兼容现有扫描器。上述断言仅启用对应字段规则，replacement 为 `****`，不启用其他规则。
- 复合值限制：`{"account":["alice","bob"]}` 和 `account={...}` 不属于完整值保护契约；沿用分隔符扫描可能只遮住前半段，留下后续元素并破坏原结构。仅补文档不能修复此限制，不能据此宣称支持所有 JSON 值；本轮测试要记录该限制，不把“字符串发生变化”当作完整脱敏通过。
- 错误码：无。不存在已启用别名与上述右侧分隔符组合，或未引用值在跳过分隔符后的空白后区间长度为零时，字段扫描保留原片段并继续扫描。区间包含外围引号，因此 `""` 和 `''` 不属于零长度区间；纯值和自定义正则仍可能随后修改这些片段。

### 配置与兼容性

现有 `mimir.boot.log.mask` 配置、预置规则名称、replacement、自定义正则、`SensitiveDataPattern.getPattern()` 和 `SensitiveDataConverter.publishConfiguration(List<String>, List<String>, String)` 均不变。字段型预置规则的输出从“带引号 JSON 可能遗漏”收敛为“同样脱敏”。

字段型规则的日志文本输出存在一项有意修正：值后紧接 `,`、`}`、`]` 或空白时，新的扫描器保留这些非敏感分隔符和尾部文本；旧正则可能将它们包含在匹配中并丢弃。该修正不要求接入方调整配置，测试必须固定断言 `account=alice, tail=sentinel` 输出为 `account=****, tail=sentinel`。字段型规则先于纯值、自定义和编程式正则执行，这是把既有 password/token/secret 的安全顺序扩展到全部字段型规则。后续正则的输入是字段扫描后的完整消息结果，可匹配 replacement、非敏感文本及扫描未完整保护的复合值残余；不得依赖已识别字段标量的原始敏感值仍然存在。

若实施后出现与既有接入方日志格式不兼容的结果，可在不变更配置契约的前提下回退本次字段型规则归类扩展；已部署版本则以回退该实现版本恢复原行为。回退会重新暴露 TD-038 的 JSON 漏脱敏边界，不能作为长期处置。

别名后缀匹配与空引号字符串替换均沿用既有行为，本轮不通过收紧左边界减少遮罩范围。启用 `name` 等宽泛别名时，接入方须接受 `service_name` 等字段也可能被遮罩；需要精确键名选择时，应单独设计匹配契约。数组、对象值不属于本轮补全范围，发布说明和模块 README 必须列出这一限制。

## Error Handling

无外部网络、文件系统或服务依赖。所有失败路径均在单条日志消息的本地解析中处理：

| 场景 | 处理 |
|---|---|
| 没有已启用别名满足 IC-03 的右侧匹配条件 | 字段扫描保留该片段，继续扫描后续字符；不等于整条消息绕过其他规则。 |
| 跳过分隔符后的空白后，值区间长度为零 | 保留原文本，如 `account=` 或 `account=, tail=sentinel`。 |
| 值为 `""` 或 `''` | 替换引号内部并保留引号，分别输出 `"****"` 或 `'****'`（replacement 为 `****`）。 |
| 敏感字段值为对象或数组 | 不保证完整脱敏；接入方须在日志输出前移除或预先脱敏，详见 IC-03。 |
| 带引号值包含转义引号或反斜杠 | 按连续反斜杠奇偶性定位闭合引号，替换整个值。 |
| 带引号值未闭合 | 将值边界扩展到消息末尾并替换，优先保证原值不输出；不承诺保留该边界后的文本。 |
| 内置、自定义或编程式正则编译失败 | 沿用当前告警并忽略无效规则；配置发布仍会以剩余有效规则构建新快照并替换旧快照，不承诺保留旧规则集合。 |

## Data Model 与并发

沿用私有不可变记录 `MaskConfigurationSnapshot(List<Pattern> patterns, List<String> keyValueFieldNames, String replacement)`：前两项是已编译正则与长度降序字段别名，replacement 是当前替换文本；构建完成后才原子发布。沿用 `SensitiveFieldValue(int valueStart, int valueEnd)` 表示消息内左闭右开的值区间。无数据库、索引、持久化和外部服务。

单条消息只读取一份快照，不在扫描期间重新读取配置；不能把旧别名和新 replacement 混用。重复发布同配置应产生相同行为，但不要求引用相等；重复脱敏不承诺幂等，因为自定义正则可以继续匹配 replacement。保留编程式规则现有同步与发布路径，不增加锁或改变全局生命周期。

## 消费入口与契约映射

以下现有入口签名不变：`public String SensitiveDataConverter.convert(ILoggingEvent event)`、`public String SensitiveDataConverter.maskSensitiveData(String message)`、`public static void SensitiveDataConverter.publishConfiguration(List<String> enabledPatternNames, List<String> customPatternExpressions, String replacement)`、`public String SensitiveThrowableProxyConverter.convert(ILoggingEvent event)`。普通入口先取得格式化消息，异常入口先渲染异常再共享脱敏逻辑；无新增错误码。配置的空/无效名称与无效正则处理见 IC-02 和 Error Handling。

| 契约或模块 | Spec 行为 | Plan 任务 |
|---|---|---|
| IC-01 别名归类 | B1 | T1 |
| IC-02 预置分流 | B1、B4 | T2 |
| IC-03 字段值替换 | B2、B3 | T2 |
| 格式化消息、异常消费与配置快照 | B4 | T2 |
| 模块说明、架构能力、发布说明与技术债状态 | Constraints | T3、T4 |

## NFR

- Java 17、Spring Boot 3.3.13；新增运行时依赖、公开方法、配置键改名、默认启用规则均为 0。
- 单条消息使用 1 份配置快照；新增外部 I/O 和逐消息正则编译次数均为 0。
- 扫描复杂度随消息长度与启用别名数量增长；不新增完整 JSON 树或整条日志结构化解析。没有实测性能 SLA，不将任意延迟数字作为验收承诺。
- 自定义正则的运行成本和输入长度上限仍沿用现状；本需求不宣称解决正则回溯或日志尺寸治理。

## Alternatives

| 方案 | 取舍 |
|---|---|
| 为 10 组字段分别扩展正则 | 引号、转义及尾部边界容易分叉，重复维护；不选。 |
| 引入完整 JSON 解析 | 无法统一普通赋值和非合法 JSON 日志，增加依赖与结构重写行为；不选。 |
| 复用字段扫描器并统一分类 | 沿用既有 3 组规则的保护语法与快照，局部改造；采用，但明确不保证复合值保护。 |

## Testing Strategy

纯文本逻辑使用单元测试；消息与异常转换器采用组件级入口验证，不需要数据库、网络或浏览器 E2E。Spec S01–S20 的任务和断言映射位于 Plan，静态配置测试必须隔离并恢复全局状态，禁止这些任务并行修改相同测试文件。

| 契约 | 层级 | 验证方法 | 通过标准 |
|---|---|---|---|
| IC-01 字段型规则归类 | 单元测试 | 在 `SensitiveDataPatternTest` 逐项断言字段别名表，纯值规则返回空集合；覆盖长别名优先顺序 | 每个字段型与纯值规则的归类符合契约，返回列表不可修改。 |
| IC-02 预置规则分流 | 单元测试 | 以全部 10 个字段型规则分别处理 `key=value, tail=sentinel`，并以 4 个纯值规则覆盖既有纯值匹配 | 字段型输出保留 `, tail=sentinel`，证明其未在扫描后再次走预置正则；纯值规则仍完成脱敏。 |
| IC-03 JSON 与普通赋值替换 | 单元测试 | 在 `SensitiveDataConverterTest` 参数化覆盖 10 个字段型预置规则的 JSON 键和值引号、普通 `key=value`、`:` 分隔符和尾随文本 | 输出不包含输入敏感值，保留键名、分隔符、外围引号和可识别的非敏感尾部。 |
| IC-03 转义与异常边界 | 单元测试 | 使用 Spec S07 的原始文本样例覆盖闭合双引号前 1/2 个连续反斜杠、单引号转义和未闭合引号；另覆盖缺失值、空引号字符串和非匹配文本 | 反斜杠奇偶性决定引号状态；闭合值保留 `tail`，未闭合值脱敏至消息末尾；缺失值保留；空引号字符串继续替换。 |
| IC-03 别名后缀兼容 | 单元测试 | 单独启用 `name` 或 `account`，使用 `service_name=orders`、`not_account=alice` 和 `account_extra=alice` | 前两者的值被替换，最后一个原样保留；不添加左边界限制。 |
| IC-03 标量与复合值限制 | 单元测试及文档核对 | 覆盖引号内逗号、非引用数字/布尔/null；以 `{"account":["alice","bob"]}` 作为限制样例核对模块 README | 支持的标量按语法完整替换；README 明确复合值无完整保护保证及调用方处置要求，不以复合值部分替换作为通过证据。 |
| 无效正则与快照替换 | 单元测试 | 清空编程式规则且不启用预置规则，先发布自定义规则 `old-only`，再发布 `new-only` 和无效正则 `[`，replacement 均为 `****` | 首次 `old-only new-only` 输出 `**** new-only`；第二次输出 `old-only ****`，并记录无效正则告警，证明新快照生效且旧规则未保留。 |
| 模块回归 | 模块单元测试 | `./mvnw -pl mimir-boot-starters/mimir-boot-starter-log -am test` | 所选模块及其 Reactor 依赖的 Surefire 测试全部通过。 |

## Design Self-Check

- [x] 数据流从格式化日志消息到脱敏输出可追踪。
- [x] 内部接口的签名、正常路径、边界路径和错误语义均已定义；公开接口无变更。
- [x] 无外部依赖，字符串解析失败路径已列出处理方式。
- [x] 每个接口契约均映射到至少一个可执行单元测试。
- [x] 未包含未授权的实现占位项。

## References

- [技术债 TD-038](../../tech-debt-tracker.md#td-038-日志-json-脱敏)
- [日志 Starter](../../../../mimir-boot-starters/mimir-boot-starter-log/)
- [安全约束](../../../standards/security.md)
- [测试与质量指南](../../../engineering/testing.md)
