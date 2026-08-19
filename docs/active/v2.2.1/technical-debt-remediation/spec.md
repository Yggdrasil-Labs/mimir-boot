---
id: technical-debt-remediation
version: v2.2.1
status: draft
owner: YoungerYang-Y
created: 2026-08-16
updated: 2026-08-19
---

# 技术债修复

## Overview

框架在不破坏已发布调用方的前提下，修复已核对的安全、稳定性、可观测性、构建和文档缺陷。本 Spec 是 v2.2.1 的唯一实施行为来源，已吸收原敏感信息保护专项。

## Behavior: 校验与分页输入保持可诊断

### Scenario: 中文校验消息返回给调用方

Given 请求参数校验失败且默认消息为“用户名不能为空”
When 框架生成 400 响应
Then 响应 data 包含“用户名不能为空”
And 日志输出不改变原有安全清洗策略

### Scenario: 绑定错误维持既有错误项格式

Given 对象绑定校验失败
When 框架生成 400 响应
Then data 仍为纯消息字符串列表
And 不新增字段名前缀

### Scenario: 分页结果收到无效服务端值

Given 服务端尝试创建 pageSize 为 0 或 totalCount 为负数的分页结果
When 创建分页结果
Then 调用抛出 IllegalArgumentException
And 不返回错误的总页数

### Scenario: 分页请求在 setter 后仍保持可用

Given 分页请求先后被设置 pageIndex 为 null、pageSize 为 0、pageSize 为 1001 和排序方向为 OTHER
When 调用偏移量计算
Then 页码为 1、页大小为 1000、排序方向为 ASC
And 偏移量为 0

## Behavior: 结构化日志与专用日志不泄露敏感值

### Scenario: JSON 与编码键被脱敏

Given 已启用 password 脱敏规则
When 日志包含 `{"password":"secret"}` 或 `%70%61%73%73%77%6f%72%64=secret`
Then 输出保留原键与分隔结构
And 两处 secret 均替换为 `****`

### Scenario: 私钥保护而公钥保持可见

Given 已启用 secret 脱敏规则
When 日志同时包含 privateKey=private-value 与 publicKey=public-value
Then private-value 替换为 `****`
And public-value 保持不变

### Scenario: 专用输出承接脱敏结果

Given 访问日志或 SQL 日志包含 password=secret
When 写入对应专用日志
Then 输出不包含 secret
And SQL 中 password='secret' 的字面量也不包含 secret

## Behavior: 未知状态不伪装为已知业务状态

### Scenario: 已知状态保持原含义

Given 状态码为 1 或删除标记为 0
When 转换为框架枚举
Then 分别返回“启用”和“未删除”
And 对应判定方法返回 true

### Scenario: 未知状态可被调用方识别

Given 状态码为 99
When 转换为框架枚举
Then 转换结果为 null
And 任意已知状态判定均返回 false

### Scenario: 空错误码不伪装为系统故障

Given 错误码为空或不存在
When 转换为框架错误码
Then 转换结果为 null
And 不自动映射为“系统错误”

## Behavior: RPC 调用在异常输入与异步完成时保持可观测

### Scenario: RPC 附件中含空值

Given RPC 调用携带一个值为 null 的附件
When 调用通过过滤器
Then 调用继续执行
And Hook 按 before、一个终态回调、cleanup 的顺序各执行一次

### Scenario: 异步完成回调保留原链路标识

Given Provider 收到含 traceId 的异步 RPC 调用
When 异步结果完成
Then 完成 Hook 与完成日志使用该 traceId
And 回调结束后线程原有 MDC 被恢复

### Scenario: 非标准 Feign 地址与多值头

Given Feign 请求没有可用 host 且包含两个非敏感同名请求头
When 生成 Hook 元数据
Then 服务标识按 host、authority、原始 URL 的顺序回退
And 非敏感多值头按原有迭代顺序用逗号拼接保存

## Behavior: 安全能力只在明确边界内生效

### Scenario: 未配置解密前缀的应用包含普通 ENC 文本

Given 应用引入 Nacos starter，但未配置 `mimir.boot.nacos.encrypt` 或 `mimir.nacos.encrypt` 前缀，且普通配置含 ENC( 文本
When 应用启动
Then 文本保持原值
And 不要求配置解密密钥

### Scenario: Logback 不可用

Given 下游使用非 Logback 日志绑定
When 日志 starter 自动配置
Then 应用正常启动
And 每个应用上下文初始化最多输出一次无法注册 Logback 专属转换器的 WARN

### Scenario: 遗留密文 API 被调用

Given 调用方执行遗留 AES 迁移加密或解密
When 操作完成
Then 结果保持旧格式与旧内容
And 发出一条含“legacy ECB migration API”的 WARN

## Behavior: MyBatis 与测试扩展提供准确的默认边界

### Scenario: Mapper 包查询反映实际扫描范围

Given 使用方配置一个 Mapper 包且运行时检测到另一个 Mapper 包
When 查询有效 Mapper 包集合
Then 结果包含默认包、配置包和检测包，且无重复项
And 旧查询 API 仍可调用并标记为弃用

### Scenario: 审计人获取失败与 SQL 输出

Given 审计人提供者抛出异常且 SQL 包含 password='secret'
When 写入审计字段并输出结构化 SQL 日志
Then 审计人使用 system 且发出 WARN
And SQL 输出不包含 secret

### Scenario: 下游测试工具使用弃用类型和随机用户标识

Given 下游代码手工导入 TestAutoConfiguration 并连续生成 10000 个用户标识
When 编译并运行测试
Then 弃用类型仍可用且编译器报告弃用提示
And 10000 个用户标识互不重复

## Behavior: 字段密文可以选择绑定应用上下文

### Scenario: 配置上下文后的新密文

Given 使用方配置稳定的应用上下文为 order-service
When 写入加密字段
Then 密文带有 v2 格式标记
And 只能在相同上下文中读回

### Scenario: 跨上下文密文搬移

Given 密文由 order-service 应用上下文写入
When payment-service 应用上下文读取该密文
Then 解密失败
And 不返回明文

### Scenario: 未配置上下文或读取旧密文

Given 未配置 cryptoContext 或存在旧格式密文
When 读写加密字段
Then 旧行为保持兼容
And 不带 v2: 前缀的旧密文可被读取

### Scenario: 应用级绑定不伪装为字段或记录绑定

Given 两段密文使用同一应用上下文和同一密钥
When 它们被放入同一应用内的不同字段或不同记录
Then v2.2.1 不承诺识别或拒绝该调换
And 技术债记录继续保留字段与记录级完整性保护的后续工作

## Behavior: 日志上下文工具维持明确的替换语义

### Scenario: 单个空值写入

Given 当前线程已有 traceId
When 调用日志上下文工具写入值为 null 的键
Then 既有 traceId 保持不变
And 该空值键不会被写入

### Scenario: 批量上下文写入

Given 当前线程已有 traceId=old
When 调用日志上下文工具设置包含 requestId=new 的完整上下文
Then 当前上下文只包含 requestId=new
And 不再包含 traceId=old

### Scenario: 调用方需要增量更新

Given 调用方只需要增加一个 MDC 键
When 调用单键写入 API
Then 未涉及的 MDC 键保持不变
And API 文档明确批量 API 不提供增量合并

## Behavior: 构建、测试与文档给出一致的可执行信息

### Scenario: 本地质量验证

Given 开发者没有 GPG 私钥
When 执行 ./mvnw verify
Then 不因签名失败而中断
And 测试目标没有失败或跳过

### Scenario: 显式发布签名

Given 发布环境已导入 GPG 私钥
When 使用 Maven Central 发布配置并设置 gpg.skip=false 执行 deploy
Then 每个发布制品都生成签名
And 签名失败会使发布命令失败

### Scenario: 下游测试启用 test profile

Given 下游项目引入测试 starter
When 启用 test profile
Then 框架不注入 create-drop、show-sql 或固定应用名
And 数据库策略由下游明确配置

### Scenario: 依赖与文档被消费

Given 使用方按 BOM、README 和文档索引配置项目
When 解析依赖与链接
Then 所有管理坐标可解析
And 版本、许可证与文档链接准确可访问

## Constraints

- 不新增第三方依赖，兼容基线为 Java 17、Spring Boot 3.3.13。
- 旧密文、既有异常响应形状、现有 `RpcExecutionTemplate` Bean 与旧 `extract` SPI 保持可用。
- 新增或修改的缺陷均有先失败后通过的回归测试；TD-016 的字段与记录级完整性不在本次关闭范围。
- CI 验收的测试目标必须零失败、零跳过；签名插件在 CI 质量验证中显式跳过，不计入测试跳过数。
- 脱敏处理不做整条日志百分号解码；性能证据采用 JDK 17、1 KiB 消息、3 个敏感字段、10 万次预热后 100 万次测量，平均增量目标为不超过 20µs，作为发布前人工基准而非 CI 硬门禁。
- `cryptoContext` 是不可变的应用标识，配置变更等同数据格式变更；滚动发布期间所有可读写同一数据集的实例必须使用相同值，且字段列长度须容纳 `v2:` 前缀。
