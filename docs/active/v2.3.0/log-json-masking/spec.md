---
id: log-json-masking-spec
version: v2.3.0
status: draft
owner: YoungerYang-Y
created: 2026-09-22
updated: 2026-09-22
---

# 日志 JSON 脱敏补全 — 需求规格

## 目标与范围

补齐 TD-038：已启用的 10 组字段型预置规则，对普通赋值和带引号键名的标量日志采用一致的保护范围。行为以本文为准，实现方案见 [设计](./design.md)，任务与验证映射见 [计划](./plan.md)。本需求仅规划，尚未实施，不改变发布版本。

不提供完整 JSON 解析、精确键名匹配、对象/数组值整体脱敏或嵌套路径选择。不改变纯值规则、自定义规则接口及默认不开启预置规则的语义。接入方必须在记录前移除或预先脱敏复合敏感值。

## 场景约定

以下输入/输出均为日志原始文本，不是 Java 字符串字面量。除单独声明外，只启用所述字段规则，替换文本为 `****`，无自定义或编程式规则；Then 均要求输出全文相等，不仅检查敏感值消失。

## B1：按配置覆盖全部字段别名

| 规则 | 别名（行为目录唯一来源） |
|---|---|
| password | password、pwd、passwd、%70assword、密码 |
| token | token、access_token、refresh_token |
| secret | secret、private_key、privateKey、secret_key、secretKey、access_key、accessKey、%73ecretKey、私钥 |
| api_key | apikey、api_key、app_key |
| account | account、accountId、account_id、账号 |
| id_card | idcard、id_card、身份证 |
| phone | phone、mobile、tel、手机、电话 |
| bank_card | bankcard、bank_card、银行卡 |
| email | email、mail |
| name | name、realname、真实姓名 |

- **S01 正常**：Given 单独启用目录中每组规则；When 对该组每个别名 K 输入 `{"K":"sensitive","tail":"sentinel"}`；Then 输出 `{"K":"****","tail":"sentinel"}`，K 保持原样。
- **S02 普通赋值**：Given 同 S01；When 对每个 K 输入 `K=sensitive, tail=sentinel` 及 `K: sensitive, tail=sentinel`；Then 分别输出 `K=****, tail=sentinel` 及 `K: ****, tail=sentinel`。
- **S03 未启用/异常名称**：Given 未启用任何规则，或规则名列表仅含空白、空元素和未知名；When 输入 `{"account":"alice"}`；Then 原样输出，无处理异常。
- **S04 大小写与引号**：Given 启用 account；When 输入 `'ACCOUNT' : 'alice', tail=sentinel`；Then 输出 `'ACCOUNT' : '****', tail=sentinel`。

## B2：识别标量值边界并保留可识别尾部

- **S05 标量**：Given 启用 account；When 分别输入 `account=123, tail=sentinel`、`account=true, tail=sentinel`、`account=null, tail=sentinel`；Then 均输出 `account=****, tail=sentinel`，不承诺保留 JSON 类型。
- **S06 引号内分隔符**：Given 启用 account；When 输入 `account="alice, bob ] }", tail=sentinel`；Then 输出 `account="****", tail=sentinel`。
- **S07 转义奇偶性**：Given 启用 api_key；When 输入下表各行；Then 输出与表中严格相等，奇数个连续反斜杠后的引号不闭合，偶数个后的引号闭合。
- **S08 未闭合**：Given 启用 api_key；When 输入 `api_key="secret, tail=sentinel` 或 `api_key='secret, tail=sentinel`；Then 分别输出 `api_key="****"` 和 `api_key='****'`；无法可靠区分的尾部一并隐藏。
- **S09 非引用边界**：Given 启用 account；When 分别输入 `account=alice}`、`account=alice]`、`account=alice tail=sentinel`、`account=alice`；Then 分别输出 `account=****}`、`account=****]`、`account=**** tail=sentinel`、`account=****`。

| S07 输入 | 输出 |
|---|---|
| `api_key="secret\"still-secret", tail=sentinel` | `api_key="****", tail=sentinel` |
| `api_key="secret\\", tail=sentinel` | `api_key="****", tail=sentinel` |
| `api_key='secret\'still-secret', tail=sentinel` | `api_key='****', tail=sentinel` |
| `api_key="secret\"still-secret, tail=sentinel` | `api_key="****"` |

## B3：保留文本匹配兼容边界

- **S10 后缀匹配**：Given 单独启用 name 或 account；When 分别输入 `service_name=orders` 或 `not_account=alice`；Then 分别输出 `service_name=****` 或 `not_account=****`，不新增左边界要求。
- **S11 右侧不匹配**：Given 启用 account；When 输入 `account_extra=alice`、`account text alice`；Then 原样输出。
- **S12 缺失与空串**：Given 启用 account；When 输入 `account=`、`account=, tail=sentinel`、`account=""`、`account=''`；Then 分别原样输出前两项、输出 `account="****"` 和 `account='****'`。
- **S13 空消息**：Given 启用 account；When 日志消息为空值或空字符串；Then 分别返回空值或空字符串。
- **S14 宽松键名引号**：Given 启用 account；When 输入 `account"=alice`；Then 输出 `account"=****`，不新增键名引号成对校验。

## B4：保持规则组合与配置更新行为

- **S15 纯值规则**：Given 依次仅启用 id_card_number、phone_number、bank_card_number、email_address；When 分别输入 `110105194912310021`、`13800138000`、`6222021234567890123`、`alice@example.com`；Then 各自输出 `****`，规则匹配范围不扩大。
- **S16 执行顺序**：Given 启用 account，自定义规则 `alice`，替换文本为 `[MASK]`；When 输入 `account=alice, other=alice`；Then 输出 `account=[MASK], other=[MASK]`。另 Given 启用 account，自定义规则 `\*{4},\x20`，替换文本为 `****`；When 输入同一消息；Then 输出 `account=****other=alice`，证明后续正则可匹配字段扫描生成的替换文本。
- **S17 更新含无效规则**：Given 无预置和编程式规则，先发布自定义规则 `old-only`，后发布 `new-only` 和无效表达式 `[`；When 每次处理 `old-only new-only`；Then 先输出 `**** new-only`，后输出 `old-only ****`，无效规则产生告警但不阻止剩余新规则替换旧配置。
- **S18 格式化消息与异常**：Given 启用 account；When 普通日志模板 `account={}` 参数为 alice，或异常消息为 `account=alice, tail=sentinel`；Then 普通消息输出 `account=****`；异常渲染文本包含 `account=****, tail=sentinel`，不包含 alice，保留异常类型及栈信息。
- **S19 配置原子性**：Given 规则 account、替换文本 A 与规则 token、替换文本 B 两种配置交替发布；When 并发处理 `account=alice token=secret`；Then 每条结果只能为 `account=A token=secret` 或 `account=alice token=B`，不得混用同一快照内的规则和替换文本。
- **S20 编程式规则**：Given 无预置和配置式规则；When 注册表达式 `alice` 后处理 `other=alice`，再清空该规则并处理同一消息；Then 依次输出 `other=****`、`other=alice`。

## Constraints 与验收边界

- Java 17、Spring Boot 3.3.13；新增运行时依赖 0，新增公开方法 0，配置键改名 0，默认启用规则新增 0。
- 仅 10 组字段规则扩展保护，4 组纯值规则保持原范围；公开预置正则获取方式不升级为 JSON 解析 API。
- 每条消息使用 1 份不可变配置快照；不增加网络、文件 I/O 或逐消息正则编译。没有经测量的延迟/吞吐 SLA，本轮不虚构指标；沿用现有并发回归。
- 保护限于上述文本标量语法。对象/数组可能部分泄露并破坏结构，不能以“输出发生变化”通过验收；README 和发布说明必须包含 `{"account":["alice","bob"]}` 限制样例与业务侧处置要求。
- 自定义正则可能再次改变替换文本；未匹配字段仍会经过后续规则，不承诺整条日志原样保留。
- 所有 S01–S20 有测试证据，模块回归和仓库质量门禁通过后才能标记 TD-038 完成；文档审查通过不等于实现验收通过。

## Spec Self-Check

- [x] 4 个 Behavior 均有至少 3 个可构造、可断言场景。
- [x] 行为未绑定内部方法；输入/输出及异常、兼容边界明确。
- [x] 所有量化约束来自现有技术栈或本次范围，不引入无依据的性能承诺。
