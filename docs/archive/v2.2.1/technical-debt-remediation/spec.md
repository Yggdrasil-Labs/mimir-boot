---
id: technical-debt-remediation
version: v2.2.1
status: shipped
owner: YoungerYang-Y
created: 2026-08-16
updated: 2026-08-29
---

# 技术债修复

## Overview

框架在保持公开类型、方法和配置可绑定性的源码/二进制兼容前提下，修复已核对的安全、稳定性、可观测性、构建和文档缺陷。本 Spec 是 v2.2.1 的唯一实施行为来源，已吸收原敏感信息保护专项。测试扩展的危险隐式默认值移除属于已确认的配置行为调整，必须提供迁移说明；枚举转换增加兼容的可空查询能力，旧 RPC Trace 与 Hook 直调契约保留残余债务，v2 密文采用读写开关分离的零停机切换。

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

Given 服务端尝试创建页大小为 0 或总记录数为负数的分页结果
When 创建分页结果
Then 调用返回明确的非法分页参数错误
And 不返回错误的总页数

### Scenario: 分页请求在属性赋值后仍保持可用

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

Given 访问日志或持久化语句日志包含 password=secret
When 写入对应专用日志
Then 输出不包含 secret
And 持久化语句中 password='secret' 的字面量也不包含 secret

### Scenario: 已初始化脱敏处理实例接收配置刷新

Given 两个已初始化的脱敏处理实例正在使用旧规则与旧替换文本
When 配置发布方并发发布一组新规则和新替换文本
Then 两个实例的后续输出均使用完整的新配置快照
And 更新期间每条输出只能使用完整旧快照或完整新快照，不得混用两代配置

## Behavior: 未知状态不伪装为已知业务状态

### Scenario: 已知状态保持原含义

Given 状态码为 1 或删除标记为 0
When 转换为框架枚举
Then 分别返回“启用”和“未删除”
And 对应判定方法返回 true

### Scenario: 未知状态可被调用方识别

Given 状态码为 99
When 转换为框架枚举
Then 既有兼容转换分别返回原有禁用态与未删除态
And 新增的可空转换入口返回 null
And 任意已知状态判定均返回 false

### Scenario: 空错误码不伪装为系统故障

Given 错误码为空或不存在
When 转换为框架错误码
Then 既有兼容转换返回原有系统错误态
And 新增的可空转换入口返回 null
And 可空查询不自动映射为“系统错误”

## Behavior: RPC 调用在异常输入与异步完成时保持可观测

### Scenario: RPC 附件中含空值

Given RPC 调用携带一个值为 null 的附件
When 调用通过过滤器
Then 调用继续执行
And 调用扩展按前置、一个终态、清理的顺序各执行一次

### Scenario: 异步完成回调保留原链路标识

Given Provider 收到含 traceId 的异步 RPC 调用
When 异步结果完成
Then 完成回调与完成日志使用该 traceId
And 回调结束后线程原有日志上下文被恢复
And 上下文恢复失败只产生告警，不改变异步完成结果

### Scenario: 非标准客户端地址与多值头

Given 客户端请求没有可用 host 且包含两个非敏感同名请求头
When 生成调用观测元数据
Then 服务标识按 host、authority、原始 URL 的顺序回退
And 非敏感多值头按原有迭代顺序用逗号拼接保存

### Scenario: 仅实现旧提取契约的自定义追踪扩展

Given 下游自定义追踪扩展只实现旧提取契约
When 框架通过兼容层适配该扩展
Then 下游实现仍可加载和调用
And 框架不得宣称能恢复该扩展管理的未知上下文
And TD-013 作为旧扩展点残余债务继续保留
And 旧 Hook 分阶段直调入口仍可用但不承诺调用级状态隔离，TD-023 继续保留

## Behavior: 安全能力只在明确边界内生效

### Scenario: 未配置解密能力的应用包含普通 ENC 文本

Given 应用引入配置解密扩展，但未绑定当前或兼容的解密配置前缀，且普通配置含 ENC( 文本
When 应用启动
Then 文本保持原值
And 不要求配置解密密钥

### Scenario: 日志绑定不支持专用脱敏能力

Given 下游使用的日志绑定不支持框架专用脱敏能力
When 日志扩展完成自动配置
Then 应用正常启动
And 每次应用启动最多输出一次无法注册专用脱敏能力的 WARN

### Scenario: 遗留密文 API 被调用

Given 调用方执行遗留迁移加密或解密
When 操作完成
Then 结果保持旧格式与旧内容
And 发出一条含“legacy ECB migration API”的 WARN

## Behavior: 持久化与测试扩展提供准确的默认边界

### Scenario: 持久化接口包查询反映实际扫描范围

Given 使用方配置一个持久化接口包且运行时检测到另一个接口包
When 查询有效扫描包集合
Then 结果包含默认包、配置包和检测包，且无重复项
And 旧查询 API 仍可调用并标记为弃用

### Scenario: 审计人获取失败与持久化语句输出

Given 审计人提供者抛出异常且持久化语句包含 password='secret'
When 写入审计字段并输出结构化语句日志
Then 审计人使用 system 且发出 WARN
And 语句输出不包含 secret

### Scenario: 下游测试工具使用弃用类型和随机用户标识

Given 下游代码手工导入既有公开测试自动配置类型并连续生成 10000 个用户标识
When 编译并运行测试
Then 弃用类型仍可用且编译器报告弃用提示
And 10000 个用户标识互不重复

### Scenario: 下游测试启用测试环境

Given 下游项目引入测试扩展
When 启用测试环境
Then 框架不注入自动重建数据库、原始语句输出或固定应用名
And 数据库策略由下游明确配置

## Behavior: 字段密文可以选择绑定应用上下文

### Scenario: 配置上下文后的新密文

Given 使用方配置稳定的应用上下文为 order-service，并启用 v2 写入开关
When 写入加密字段
Then 密文带有 v2 格式标记
And 只能在相同上下文中读回

### Scenario: 跨上下文密文搬移

Given 密文由 order-service 应用上下文写入
When payment-service 应用上下文读取该密文
Then 解密失败
And 不返回明文

### Scenario: 未配置上下文或读取旧密文

Given 未配置应用级密文上下文或存在旧格式密文
When 读写加密字段
Then 旧行为保持兼容
And 不带 v2: 前缀的旧密文可被读取

### Scenario: 应用级绑定不伪装为字段或记录绑定

Given 两段密文使用同一应用上下文和同一密钥
When 它们被放入同一应用内的不同字段或不同记录
Then v2.2.1 不承诺识别或拒绝该调换
And 技术债记录继续保留字段与记录级完整性保护的后续工作

### Scenario: 滚动升级后启用应用上下文

Given 同一数据集可能同时被旧版本和 v2 密文兼容版本访问
When 使用方准备启用应用级密文上下文
Then 必须先为所有实例配置同一应用级上下文并保持 v2 写开关关闭，使其可读 v2 但继续写 v1
And 仅在所有实例完成升级且列长度预检通过后，才启用 v2 写开关开始写 v2

### Scenario: 已写入 v2 密文后的版本回退

Given 数据集中已经存在 v2 密文
When 使用方执行应用版本回退
Then 回退目标必须仍支持读取 v2 密文并使用相同应用级上下文
And 不允许回退到仅支持旧 Base64 密文的版本

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

Given 调用方只需要增加一个日志上下文键
When 调用单键写入 API
Then 未涉及的日志上下文键保持不变
And API 文档明确批量 API 不提供增量合并

## Behavior: 构建、测试与文档给出一致的可执行信息

### Scenario: 本地质量验证

Given 开发者没有发布签名私钥
When 执行仓库默认质量验证命令
Then 不因签名失败而中断
And 测试目标没有失败或跳过

### Scenario: 显式发布签名

Given 发布环境已导入发布签名私钥
When 使用显式启用签名的中央仓库发布配置执行发布
Then 每个发布制品都生成签名
And 签名失败会使发布命令失败

### Scenario: 依赖与文档被消费

Given 使用方按 BOM、README 和文档索引配置项目
When 解析依赖与链接
Then 所有管理坐标可解析
And 版本、许可证与文档链接准确可访问

## Constraints

- 不新增第三方依赖，兼容基线保持当前 Java 与应用框架版本；精确版本属于 Design 约束。
- 旧密文、既有异常响应形状、现有公开 RPC 执行扩展与旧追踪提取契约保持源码/二进制可用；仅实现旧提取契约的自定义追踪扩展不获得上下文恢复保证，四个旧 Hook 直调入口也不获得调用级状态保证，TD-013、TD-023 继续保留。
- 新增或修改的缺陷均有先失败后通过的回归测试；TD-016 的字段与记录级完整性不在本次关闭范围。
- 持续集成验收的测试目标必须零失败、零跳过；发布签名步骤在质量验证中显式跳过，不计入测试跳过数。
- 脱敏处理不做整条日志百分号解码；性能证据在同一运行时进程内对同一条固定 1 KiB 消息比较“关闭脱敏规则”的基线路径与“启用 3 个敏感字段规则”的候选路径。按 Design 固定运行时先各预热 100000 次，再各测量 1000000 次，以 `候选平均 ns/op - 基线平均 ns/op` 作为单次有符号增量；连续独立运行 3 次，不剔除离群值，三次增量算术平均值不超过 20µs，并同时记录三次原值与最大值。该证据是发布前人工基准，不是持续集成硬门禁。
- 应用级密文上下文是不可变的应用标识，配置变更等同数据格式变更；v2 写入采用默认关闭的独立开关，启用前完成全实例读能力升级和字段列长度预检，启用后所有可读写同一数据集的实例必须使用相同值，且不得回退到不支持 v2 的版本。
