---
id: mongodb-driver-alignment-spec
version: v2.3.0
status: draft
owner: YoungerYang-Y
created: 2026-09-22
updated: 2026-09-22
---

# MongoDB 驱动族兼容 — 需求规格

## Overview

修复 TD-040：接入方只使用本仓库发布 BOM 或 Parent、不自行覆盖 MongoDB 版本时，得到一致的驱动族，并能完成同步客户端和 Spring Data 的离线初始化/映射验证。本文是行为事实源；方案见 [设计](./design.md)，执行和证据见 [计划](./plan.md)。

本轮为 SDD 规划，未修改依赖、未复现运行故障、未验收实现。规划编号 v2.3.0 不改变根 revision，也不代表发布承诺。

## B1：默认消费获得一致驱动族

以下场景针对从隔离 Maven 仓库解析的本次候选发布制品；不是直接引用源码 POM，也不是依赖开发者本地旧制品。

- **S01 BOM-only**：Given 一个不继承本仓库 Parent、仅导入本仓库 BOM 的 Java 17 消费者；When 无版本声明引入同步 MongoDB 驱动；Then 最终解析的 sync、core、bson 均为 5.0.1，三者各只有 1 个选中版本。
- **S02 Parent**：Given 一个继承本仓库 Parent、无额外 BOM 和 MongoDB 版本覆盖的消费者；When 无版本声明引入同步驱动；Then 得到与 S01 相同的三项版本。
- **S03 驱动族边界**：Given 同 S01，并显式声明无版本的同步、响应式、legacy 驱动及 bson、bson-record-codec、bson-kotlin、Kotlin coroutine 驱动；When 解析依赖；Then 包括 core 在内的 8 个由当前 Boot 基线管理的坐标全部为 5.0.1，不混入 4.x。此场景只承诺版本解析，不承诺各语言/驱动运行时均已验证。
- **S04 负向验证**：Given 验证输入中人为将 sync 改为 4.11.5，其余 core/bson 为 5.0.1；When 执行版本一致性检查；Then 检查非零退出，报告失配坐标、实际版本及期望版本，不能误判为通过。接入方显式覆盖不属于默认兼容保证，不新增强制拦截业务构建的规则。

## B2：同步客户端初始化不再发生二进制链接错误

- **S05 正常初始化**：Given S01 的解析结果和不含凭据的本地 URI；When 创建同步客户端、取得数据库句柄并关闭；Then 客户端与数据库句柄非空，数据库名称为 `td040`，过程不抛缺类或缺方法错误。
- **S06 无服务端**：Given 没有可用 MongoDB 服务端；When 执行同一初始化/关闭场景且不发送数据库操作；Then 验证仍成功，不以后台监控连接失败日志作为测试失败，也不把成功解释为服务器可连接或 CRUD 已验证。
- **S07 非法配置**：Given URI 为 `not-a-mongodb-uri`；When 尝试创建客户端；Then 拒绝配置并抛参数校验异常，不转化为缺类、缺方法错误，不捕获后伪造成功。

## B3：Spring Data 离线消费链兼容

- **S08 自动配置**：Given 仅导入本仓库 BOM 并引入无版本的 MongoDB 数据访问 Starter；When 在无服务端环境创建最小应用上下文且禁用自动索引；Then 上下文无启动失败，同步客户端、数据库工厂、数据访问模板各有 1 个，数据库名为 `td040`；关闭上下文释放客户端。
- **S09 映射往返**：Given S08 的映射转换器；When 把仅含字符串 id=`sample-1`、name=`alice` 的样本转换成 BSON 文档再读回；Then 文档中 id 对应 `_id=sample-1`、`name=alice`，读回对象两个值与输入相同，过程不访问数据库。
- **S10 配置失败可见**：Given S08 的应用但 URI 改为 `not-a-mongodb-uri`；When 创建上下文；Then 上下文启动失败，异常链含参数校验错误而非二进制链接错误，测试必须观察到失败，不能仅检查进程退出码。

## Constraints

- 基线 Java 17、Spring Boot 3.3.13、Spring Data MongoDB 4.3.13；驱动族目标 5.0.1，是当前 Boot 基线，不追随最新版。
- 新增生产模块 0，新增应用运行时 API 0，Spring Boot/Spring Data 升级 0；不为未引入 MongoDB 的业务添加运行时依赖。
- 验证分 3 个独立消费者：BOM-only、Parent、Spring Data；族边界解析与失配负例复用 BOM-only 的隔离副本。
- 离线指不需要 MongoDB 服务端，不代表 Maven 不联网或客户端不会尝试后台连接。禁止连接真实业务数据库；无数据库写入、schema/index 变更。
- 没有经测量的延迟/吞吐 SLA。本轮仅消除依赖不一致造成的初始化链接错误，不承诺全部查询、事务、认证、TLS、服务器版本或响应式运行兼容。
- 不承诺 4.x API/ABI 无缝兼容。发布说明必须写明 sync 由 4.11.5 收敛至 5.0.1、重新编译/检查上游破坏性变更、覆盖版本需整族验证，不能静默包装为无影响升级。
- README 的“已验证”按现有 Reactor 直接消费定义不变；外部消费者 smoke 单独记录验证范围，不据此提升整个 MongoDB 驱动族支持等级。
- S01–S10、消费者门禁、完整质量检查及文档同步通过后才能关闭 TD-040；文档审查通过不等于实现完成。

## Self-Check

- [x] 3 个 Behavior 均至少 3 个 Given/When/Then 场景。
- [x] 明确解析、初始化、离线映射三种证据的能力边界。
- [x] 兼容性变化、错误路径和未验证范围可机械核对。
