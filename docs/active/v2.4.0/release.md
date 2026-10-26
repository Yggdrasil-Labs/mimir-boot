---
version: v2.4.0
status: in-progress
branch: main
created: 2026-10-23
released: null
updated: 2026-10-26
---

# v2.4.0 版本记录

## 版本目标

围绕现有模块丰富可供其他项目复用的基础能力，不新增模块。通过逐模块讨论收敛候选需求，优先考虑使用方接入体验、稳定性与向后兼容。

## 当前阶段

当前处于头脑风暴阶段，统一记录在[版本级 Brainstorm](./brainstorm.md)。Parent 的编译目标兼容、注解处理器接入、构建产物按需控制、测试与质量检查开关统一、私有仓库发布接入五项策略已逐项确认，其中三项经 2026-10-24 至 2026-10-25 的代码对照复核后调整：注解处理器不默认配置处理器路径，保留 classpath 处理器发现，构建 JDK 为 21 及以上时显式开启 `proc=full`（D-41）；构建产物沿用默认生成源码包与 Javadoc，通过原生 `maven.source.skip`、`maven.javadoc.skip` 关闭，交付为使用说明与消费者验证（D-42）；私有仓库发布保留 Central 默认地址与默认跳过部署，不新增属性入口，接入方用 `distributionManagement` 覆盖或原生 `alt*DeploymentRepository` 接入，交付为使用说明与示例（D-43）。P-03、P-05 不计为新增能力。测试与质量检查保留现有 Profile 的职责和默认行为。详细设计及版本最终交付范围尚未确定。

当前 BOM 三项策略已逐项确认：同时纳入 `lombok-mapstruct-binding` 和 `poi-ooxml` 的版本管理；保留已有验证，本轮聚焦新增和实际变更的组合；v2.4.0 保持 Boot 3.3.13、Cloud 2023.0.6、Alibaba 2023.0.3.4，框架升级及其专项兼容性评估暂缓。

Common 五项方向已逐项确认：新增独立错误码定义接口，供内置 `ErrorCode` 与业务枚举实现；提供分页数据逐条转换入口，返回新分页结果并保留分页信息；批量操作结果汇总总数、成功数和失败数，保留全部逐项结果，可携带成功数据或错误码/消息；无需总数的分页结果经价值复核后暂缓，三个内部业务项目均无滚动列表场景，接口层使用 COLA 的 `MultiResponse` 表达无总数列表（D-61）；R 的 JSON 成功状态只保留 success，Java 继续保留 isSuccess()/isFail()，JSON fail 字段使用方的迁移及序列化接入范围留到详细设计。多字段排序、整页列表转换、游标式分页及 R 数据映射本轮未纳入；具体接口、边界行为与 MyBatis 分页适配仍待后续收敛。经输出类型复核，三个内部业务项目的接口层使用 COLA Response，未引用 Mimir Common 的任何类型；用户确认分页逐条转换保留并增加 COLA 分页响应支持（D-62），批量操作结果与 R 的 success 收敛保留（D-63、D-64）。COLA 支持形式已确认：Common 以 optional 方式依赖 `cola-component-dto`，转换放在独立工具类；BOM 只管理该坐标并对齐 5.0.0；超出 int 范围时抛异常；`PageResult` 与 MyBatis `IPage` 均可转为 COLA `PageResponse`，不另提供 `MultiResponse` 转换（D-66 至 D-69）。

Log 已确认统一默认日志方案和业务任务 MDC 传播两项方向，优先降低业务项目维护和配置成本，要求开箱即用、风格统一。容器日志采集与服务器文件落盘由框架提供统一预设，部署侧通过公共模板指定，未指定时保留既有输出行为，复用已有文本输出体系。经复核校准（D-44），默认 Logback XML 已随 Starter 生效，本轮补齐容器输出预设，以及滚动、保留、队列等少量属性入口，使调整常见参数时无需复制 XML。新预设的生产 root 默认 INFO，第三方降噪直接复用现有 prod 清单（去掉重复项），保留 logging.level 显式覆盖；默认启用 password、token、secret、api_key 四组已有脱敏规则，允许覆盖或关闭，其他规则按需选择。MDC 传播优先适配 Spring Boot 默认执行器，自建执行器提供统一入口；已选择传播提交时的全部 MDC 字段快照，完成或异常后恢复执行线程原上下文。具体装配与兼容组合待详细设计验证。访问日志采样、分路径慢阈值及完整 JSON 日志输出尚未纳入，版本最终交付范围尚未定稿。

Exception 已选择同时纳入“标准 HTTP 异常状态及协议头补齐”“统一校验错误明细”两项方向，复用现有响应工厂和默认 R。HTTP 异常覆盖选择同时支持 Spring 标准 HTTP 异常及业务自定义异常类型上的 @ResponseStatus，保持既有 Mimir 业务异常约定；校验明细选择默认保留现有格式，通过显式配置启用结构化明细。具体字段及异常优先级等留到详细设计。Common 的错误码接口与已有自定义响应工厂不重复计为新增能力；本轮讨论中采纳的 R success/fail 收敛归入 Common，JSON 字段迁移与校验明细模式独立处理。

复核同时记录了 8 项详细设计约束和 4 项新增候选（B-04、C-08、C-09、E-03），约束已并入 brainstorm 各模块的已确认方向，候选见其“暂缓、撤销与未采纳”一节；这些候选尚未纳入本轮范围。

Web 已核对现有 JSON、CORS、请求标识、MDC 和响应增强能力；范围已选定（D-45）：纳入“JSON 与 MVC 参数日期格式统一接入”“大整数 JSON 输出策略”两项方向，“回传已有 requestId”保留候选；大整数策略按复核约束只在“超出 JavaScript 安全整数范围时转换”和“按字段显式启用”之间选择。日期格式统一接入已确认默认启用、可关闭，Mimir 格式只作为 `spring.mvc.format.*` 的默认值，`@DateTimeFormat` 与业务显式配置优先，ISO 回退保留（D-46、D-47）；大整数输出确认为 Long/long 与 BigInteger 超出 JavaScript 安全整数范围时才输出字符串，默认关闭、显式开启（D-48、D-49）。Web 本轮主要方向已确认，具体设计待后续收敛。已有能力及 C-07 的序列化衔接不重复计为新增需求。

MyBatis 模块讨论（Web 已收口，D-50）：范围已选定（D-51）：纳入拦截器顺序编排（使多租户、动态表名排在分页之前）、无总数分页的持久层适配（承接 Common 的无总数分页结果）、分页转换时映射（承接 Common 的分页逐条转换）、慢 SQL 记录四项方向；防全表更新与删除、单页上限、审计人类型可配置未纳入。对照三个内部业务项目做价值复核后：拦截器顺序编排移出本轮，属于业务接入方使用多租户组件时的考虑（D-58）；无总数分页暂缓，三个项目均无该场景，本轮只在 README 说明单独 count 或只取前 N 条时使用 `searchCount=false`，待有真实场景时与 Common 的无总数分页一并重新评估（D-59）；分页转换时映射经输出类型复核后暂缓，其输出类型 `PageResult` 当前没有使用方（D-65）；原定的转换契约（null 元素不交给转换函数并在结果中保留，转换异常原样抛出，D-55）继续用于 Common 的分页逐条转换；慢 SQL 记录确认默认开启、阈值可调可关闭，以独立 Logger 按 WARN 输出，现有 JSON SQL 日志不变（D-56、D-57）。随后按 D-69 改为提供 `IPage` 加转换函数输出 COLA `PageResponse` 的入口。MyBatis 本轮保留慢 SQL 记录、`IPage` 到 COLA `PageResponse` 的转换入口及 `searchCount=false` 使用说明，具体设计待后续收敛；MyBatis 已收口（D-70）。

MyBatis Processor 已在三个内部业务项目中使用（共 24 个实体），均依赖编译器从 classpath 发现处理器，佐证了注解处理器保留 classpath 发现的选择（D-41）。用户确认功能保持现状，本轮只做两项小修：支持的源版本跟随编译器，纠正 README 中生成冲突的说明（D-71），Processor 已收口（D-72）。

Nacos 在三个内部业务项目中仅由 valhalla-auth 接入，使用配置导入与刷新，未见加密值，也未发现依赖配置删除语义的业务路径。用户确认 Nacos Starter 本轮保持现状，并将核对结果写入[技术债台账](../tech-debt-tracker.md) TD-047 作为消费者清单起点，TD-047 仍为待规划（D-73），Nacos 已收口（D-74）。

RPC Core 在三个内部业务项目中仅由 valhalla-project 经 Dubbo Starter 间接使用，未自定义 Hook 或 Bridge，也未调用已废弃的旧入口。用户确认功能保持现状，本轮将 TD-013、TD-023 对应旧入口改标为 `@Deprecated(forRemoval = true)`，在 README 与升级说明中写明迁移方式并计划在下一个主版本删除（D-75），RPC Core 已收口（D-76）。

Feign 在三个内部业务项目中没有使用方。Spring Cloud OpenFeign 自 2022.0.0 起被官方视为功能完结，建议迁移到 Spring HTTP Service Clients。用户确认 Feign Starter 本轮保持现状，“RPC Core 对接 Spring HTTP Service Client”记为未采纳候选，待出现内部 HTTP RPC 场景时再评估（D-77），Feign 已收口（D-78）。

Dubbo 在三个内部业务项目中仅由 valhalla-auth 作为 Provider 使用，以 COLA Response 返回业务错误，内部仓库中没有 Consumer。用户确认 Dubbo Starter 本轮保持现状（D-79），Dubbo 已收口（D-80）。

Test Starter 在三个内部业务项目中均以 test 范围引入，但测试代码未引用 Mimir 的任何测试 API，只用于聚合测试依赖；Starter 附带的 `application-test.yml` 会被接入方自己的同名文件覆盖。用户确认 Test Starter 本轮保持现状（D-81）。

至此全部现有模块已按顺序讨论完毕。各方向尚未形成需求规格、详细设计或实施计划，版本最终交付范围待整体收敛后定稿。

文档当前记录于 main，尚未建立实施分支，也未调整 POM 版本。规划版本不代表已开始编码或发布。

## 交付状态

尚无实施交付、发布记录或实施验收结果。本轮仅整理讨论内容与文档导航；已有技术债继续由[技术债台账](../tech-debt-tracker.md)维护。
