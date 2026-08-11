---
id: project-governance
status: in-progress
owner: Yggdrasil Labs
created: 2026-07-30
updated: 2026-08-11
version: 2.1.2
resolved-path: docs/active/v2.1.2/project-governance/
---

# Project Governance

## Overview

在不扩张产品模块的前提下，修复 Mimir Boot 已确认的运行正确性和 Starter 完整性缺口，
并把单人维护中的重复工作收敛为可自动验证的工程流程。

实施范围分为两条主线：先完成 CI 与文档治理，使后续变更在 Push 前即可复现核心门禁；
再实施功能代码优化。GOV-008 只定义 v2.x 兼容性边界，不形成第三条实施主线。

## Workstreams

| 主线 | GOV 范围 | 实施目标 |
|------|----------|----------|
| B：CI 与文档治理 | GOV-001、007、009—015 | 建立本地与 CI 同源预检、一次 Reactor 构建、条件化 Sonar、单一发布前检和自动事实校验 |
| A：功能代码优化 | GOV-002—006、016—020 | 修复上下文隔离、输入信任边界、RPC 生命周期和 Starter 默认能力 |
| 兼容性约束 | GOV-008 | v2.x 保持 `Serializable` 公共边界，不修改生产 API；编译回归并入 T1 |

## Frozen Scope

| Behavior | GOV 范围 | 数量 |
|----------|----------|------|
| 构建与发布门禁可信 | GOV-001、GOV-007、GOV-011、GOV-014、GOV-015 | 5 |
| 运行时上下文与扩展生命周期安全 | GOV-002—GOV-005 | 4 |
| Starter 默认能力完整 | GOV-006、GOV-016—GOV-020 | 6 |
| 文档与依赖维护自动化 | GOV-009、GOV-010、GOV-012、GOV-013 | 4 |
| 2.x 公共兼容性保持 | GOV-008 | 1 |
| **合计** | **GOV-001—GOV-020** | **20** |

## Behavior: 构建与发布门禁可信

### Scenario: Push 前执行同源预检

Given 维护者使用 Java 17 和 Node.js 22
When 在本地执行仓库提供的预检入口
Then Markdown、项目事实、单元测试、集成测试和覆盖率门禁与 CI 使用相同命令
And Workflow 静态约束也在本地校验
And 任一确定性门禁失败时在 Push 前得到非 0 退出码和明确错误位置

### Scenario: 普通变更执行完整质量门禁

Given 仓库包含单元测试和集成测试
When 普通推送或拉取请求执行质量门禁
Then 两类测试都必须由正常构建生命周期执行
And 任一测试失败或集成测试报告缺失时门禁失败

### Scenario: 具备代码分析凭据

Given 一次质量门禁已经完成编译、测试和覆盖率收集
And 当前事件来自主仓库且允许读取完整代码分析凭据
When 执行代码分析
Then 分析复用本次构建产物
And 同一工作流中的完整项目构建次数等于 1

### Scenario: 不具备代码分析凭据

Given 当前事件来自依赖机器人、外部仓库或未配置分析凭据
When 执行质量门禁
Then 完整构建、测试和报告上传仍然执行
And 同一个 Build Step 明确记录代码分析已跳过而不是失败

### Scenario: 标签发布进入前置验证

Given 一个标签发布或显式选择的故障补偿任务
When 发布流程执行前置验证
Then 格式、编译和单元测试在一个前置验证任务中完成
And 两个远程制品仓库仍保留独立发布与独立补偿能力

### Scenario: 固定依赖正常解析

Given 项目声明的依赖版本固定且本地缓存缺少部分构件
When CI 或发布流程执行默认 Maven 命令
Then 缺失构件被正常下载
And 命令不强制刷新全部远程元数据

## Behavior: 运行时上下文与扩展生命周期安全

### Scenario: Web 请求结束

Given 请求进入前日志上下文包含两个由其他组件写入的键
When Web 增强写入自己的 traceId 和客户端 IP 并完成请求
Then Web 增强拥有的键恢复到请求进入前的值
And 两个外部键保持不变

### Scenario: 直连客户端伪造转发头

Given 连接来源地址为 `198.51.100.10`
And 请求携带值为 `203.0.113.20` 的转发地址头
And 运行环境没有显式建立可信代理边界
When Web 日志和请求上下文解析客户端地址
Then 两者记录的地址均为 `198.51.100.10`

### Scenario: 请求经过显式可信代理

Given 容器已显式配置可信代理
And 容器向应用暴露的连接来源地址为已解析客户端地址 `203.0.113.20`
When Web 日志和请求上下文读取客户端地址
Then 两者记录的地址均为 `203.0.113.20`

### Scenario: 序列化扩展与项目格式并存

Given 消费者已注册一个第三方 JSON 类型扩展
When Web Starter 应用日期时间格式
Then 日期时间按项目格式序列化
And 第三方类型扩展仍然生效

### Scenario: RPC 前置阶段失败

Given RPC 调用尚未执行业务逻辑
When 任一前置 Hook 或上下文注入失败
Then 业务逻辑不被调用
And 所有已进入生命周期的清理动作各执行一次

### Scenario: RPC 业务与清理同时失败

Given RPC 业务调用抛出异常 `business-error`
When 后置错误处理或清理也抛出异常
Then 调用方收到的主异常仍为 `business-error`
And 所有剩余清理动作仍被尝试执行

### Scenario: RPC 业务成功但后置扩展失败

Given RPC 业务调用成功并返回 `ok`
When 后置观测或清理扩展失败
Then 调用结果仍为 `ok`
And 扩展失败被记录且不阻止其他清理动作

## Behavior: Starter 默认能力完整

### Scenario: 默认 RPC 上下文传播

Given 消费者没有提供自定义 RPC 上下文桥
And 当前日志上下文包含合法 traceId `trace-123` 和 requestId `request-456`
And Feign 下游 HTTP 应用启用了 Web Starter
When 通过 Feign 或 Dubbo 发起并接收调用
Then 下游调用上下文可读取相同的 traceId 和 requestId
And 调用结束后原线程中的两个值恢复为调用前状态

### Scenario: Dubbo Provider 收到非法上下文标识

Given Dubbo 上游载体中的 traceId 和 requestId 含控制字符或长度超过 64
When Dubbo Provider 提取调用上下文
Then 两个非法值都不会写入日志上下文
And 下游使用新生成的合法 traceId，调用期间 requestId 保持缺失
And 调用结束后恢复进入前的 traceId/requestId 状态

### Scenario: 消费者提供自定义 RPC Bridge

Given 消费者提供一个自定义 RPC 上下文桥
When 应用启动并执行 RPC 调用
Then 容器中只有该自定义 Bridge 生效
And 默认 MDC Bridge 不被创建

### Scenario: Micrometer 类存在但没有自定义 Web Trace 组件

Given 应用类路径包含 Micrometer Tracer
And 消费者没有提供自定义 Web Trace 组件
When 一个 Web 请求完成
Then 响应头仍包含合法 traceId
And 不依赖不存在的外部 Starter 接管该能力

### Scenario: 常见客户端 HTTP 错误

Given 请求触发入参方法校验失败、缺少必需请求头或资源不存在
When 全局异常处理生成响应
Then HTTP 状态分别属于 400 或 404
And 响应继续使用统一错误结构

### Scenario: HTTP 内容协商或上传失败

Given 请求触发不可接受响应类型、不支持的请求媒体类型或上传超限
When 全局异常处理生成响应
Then HTTP 状态分别为 406、415 或 413
And 未知服务端异常仍返回 500

### Scenario: Spring MVC 服务端契约错误

Given 控制器返回值方法校验失败或处理器声明的路径变量无法解析
When 全局异常处理生成响应
Then HTTP 状态为 500
And 不把服务端映射或返回值契约错误伪装成客户端 400

### Scenario: 业务异常兼容

Given 业务逻辑抛出现有业务异常
When 全局异常处理生成响应
Then HTTP 状态仍为 200
And 原有业务错误码和消息保持不变

### Scenario: Nacos 密文动态刷新成功

Given 应用当前配置 Bean 读取明文 `old-value`
And 远程属性源更新为可解密得到 `new-value` 的新密文
When 环境变更事件完成
Then Environment 返回 `new-value`
And 配置 Bean 返回 `new-value`

### Scenario: Nacos 刷新使用错误密钥

Given 远程属性源更新为无法使用当前密钥解密的密文
When 环境变更事件处理该属性
Then 刷新明确失败
And Environment 与配置 Bean 保持刷新前的明文
And 日志不包含密文、密钥或解密后的明文

### Scenario: Nacos 现有刷新顺序已经正确

Given 真实刷新集成测试在当前实现上通过
When 本治理项进入实施
Then 不修改运行时刷新实现
And 仅保留该集成测试作为回归证据

### Scenario: 下游使用 JUnit Suite

Given 下游项目仅以 test scope 引入测试 Starter
When 下游编译并运行一个 JUnit Suite
Then Suite API 编译成功
And Suite Engine 发现并执行套件中的测试

### Scenario: 容器依赖清理不改变消费边界

Given 下游项目没有显式选择容器测试模块
When 升级测试 Starter 并解析消费依赖树
Then 消费依赖树仍不包含 Testcontainers
And 下游显式添加具体容器模块时仍可使用平台管理的版本

### Scenario: 消费者替换默认自动装配 Bean

Given 消费者提供 Web、访问日志或 MyBatis 的约定替换 Bean
When 应用上下文启动
Then 对应 Starter 默认 Bean 回退
And 每类能力只有一个有效实例

## Behavior: 文档与依赖维护自动化

### Scenario: 项目事实与文档一致

Given 根 POM、Starter 聚合 POM 和活跃版本目录是当前代码事实
When 文档健康检查执行
Then 版本号、Starter 数量与清单、Reactor 模块数量和活跃版本入口全部一致
And 检查退出码为 0

### Scenario: 人工文档发生事实漂移

Given 任一文档写入错误版本号、错误 Starter 数量或失效内部链接
When 文档健康检查执行
Then 检查退出码非 0
And 输出包含不一致文件和期望事实

### Scenario: 文档检查发现差异

Given 文档健康检查发现 1 项或更多差异
When CI 报告失败
Then 人工文档内容保持原样
And 工具不自动覆盖任何 Markdown 文件

### Scenario: 常规依赖更新

Given 同一周存在多个 GitHub Actions minor/patch 更新
When 依赖机器人创建更新请求
Then 这些更新合并到一个 Actions 分组请求
And Maven 更新继续按现有领域分组

### Scenario: 重大依赖更新

Given 存在一个 major 版本更新
When 依赖机器人创建更新请求
Then 该更新不混入 minor/patch 分组
And 更新频率仍保持每周

### Scenario: 企业级宽 BOM

Given BOM 管理一个未被当前 Starter 消费的依赖
When 消费者或维护者查阅依赖支持范围
Then 该依赖标记为“仅管理”而不是“已验证”
And 依赖继续保留在 BOM 中

## Behavior: 2.x 公共兼容性保持

### Scenario: Serializable 数据继续使用公共响应

Given 现有消费者的响应数据实现 `Serializable`
When 消费者升级到 v2.1.2
Then 现有源码无需修改即可编译
And JSON 响应语义保持不变

### Scenario: 非 Serializable 数据尝试使用公共响应

Given 消费者的数据类型没有实现 `Serializable`
When 消费者在 v2.1.2 使用现有公共响应泛型
Then 编译边界与 v2.1.1 保持一致
And 本版本不引入静默适配或数据丢弃的新路径

### Scenario: 后续主版本评估

Given 后续 3.0 版本重新评估公共响应泛型
When 设计者查阅本治理结论
Then 可以识别 `Serializable` 上界为候选破坏性变更
And v2.1.2 不包含该变更的实现任务

## Constraints

- 逻辑版本固定为 `2.1.2`，目标目录固定为 `docs/active/v2.1.2/project-governance/`。
- 冻结范围固定为 20 项：GOV-001 至 GOV-020；新增项必须重新确认收益和版本影响。
- 当前项目事实基线为 Java 17、Spring Boot 3.3.13、10 个 Starter、15 个 Maven Reactor 模块。
- 本地预检与 CI 固定使用 Java 17、Node.js 22 和 lockfile 锁定的 Markdown 工具链。
- CI 中每次变更的完整 Reactor 构建次数为 1；Failsafe XML 报告和 JaCoCo XML 报告数量都必须大于 0。
- CI 不使用 `paths`/`paths-ignore` 跳过 required check；核心 Build Job 不读取发布密钥。
- 文档事实检查、Workflow 静态检查、Markdown lint，以及覆盖已跟踪与新建治理文件的 Git whitespace
  check，允许错误数均为 0。
- v2.1.2 关闭时未验证 P0/P1 数量必须为 0；P2 必须完成或记录 Owner、理由和目标版本。
- 每个进入“已验证”的 GOV 项必须记录指向 Task AC 的定向验证证据，并共同引用 T12 AC2
  作为版本级门禁证据；“已关闭”和“延期”分别记录决策证据或 Owner、原因、目标版本。
