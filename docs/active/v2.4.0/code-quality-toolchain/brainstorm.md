---
version: v2.4.0
status: draft
created: 2026-11-02
updated: 2026-11-02
---

# 代码质量工具链方案大纲

> D-102～D-109 为 2026-11-02 用户确认的决定；2026-11-06 以 D-110 确认独立插件入口并开始实施。方案选择不表示已验收，交付以实际证据为准。

产物导航：[行为规格](./spec.md) · [详细设计](./design.md) · [静态实施计划](./plan.md) · [版本入口](../brainstorm.md) · [架构 RFC](../../../design-docs/arch-code-quality-toolchain.md)

## 需求摘要（sdd-ready）

为本仓库和下游业务服务提供本地开发阶段即可运行的代码质量检查，覆盖注释、命名、坏味道、重复代码与依赖方向，与既有 Spotless、JaCoCo 组成一套定位不重叠的工具链。

| 编号 | 需求 | 期望结果 |
|---|---|---|
| Q-01 | 注释与命名检查 | PMD 检查公开类型与方法的 Javadoc 存在性、Javadoc 位置与命名；不检查格式与 Javadoc 标签内容 |
| Q-02 | 坏味道检查 | PMD 精选规则集报告潜在错误与复杂度问题 |
| Q-03 | 重复代码检查 | CPD 报告超过阈值的重复片段 |
| Q-04 | 依赖方向检查 | ArchUnit 规则库：包循环、模块间禁止引用、通用分层与 COLA 预设；本仓库以测试落地 |
| Q-05 | 下游接入 | 继承 Parent 自动获得；每个工具独立开关，默认只报告；仅导入 BOM 的下游可自行接入 |
| Q-06 | 统一定位 | Spotless、JaCoCo、Enforcer、Sonar 与新工具共用一张定位表，规则不重叠，既有语义不变 |

## 已确认决定

| 编号 | 决定 |
|---|---|
| D-102 | 代码质量工具链并入 v2.4.0 作为第十项需求；作为 D-02 的唯一例外，允许新增规则资源模块 `mimir-boot-code-quality` |
| D-103 | 引入 PMD、CPD、ArchUnit（原含 Checkstyle，经 D-109 修订）；不引入 `dependency:analyze`，不扩展 Enforcer 模块方向规则，不开启 Javadoc doclint；SpotBugs、Error Prone 暂缓 |
| D-104 | 本仓库严格执行，违规阻断构建；下游每个工具独立开关，默认只报告 |
| D-105 | Spotless、JaCoCo 纳入统一治理但不合并实现：配置位置、开关与失败语义不变，不进入规则 jar，不新增聚合开关 |
| D-106 | 每个质量维度只有一个负责工具，定位矩阵见[架构 RFC](../../../design-docs/arch-code-quality-toolchain.md#定位矩阵) |
| D-107 | ArchUnit 规则库放在 `mimir-boot-starter-test`，提供通用分层与 COLA 预设；注释规则只要求公开类型与方法有 Javadoc，不强制 `@author` |
| D-108 | 开关保持插件原生属性；已自行配置 PMD 的下游受影响，作为向后兼容的显式例外写入升级说明并给出恢复配置（规格 CQ-10），不改用 Mimir 私有开关 |
| D-109 | 不引入 Checkstyle：注释与命名改由 PMD 选定规则负责（`CommentRequired`、`DanglingJavadoc` 与 codestyle 命名规则），修订 D-103；接受不检查 Javadoc 标签与签名一致性等缺口（2026-11-02 用户确认） |

## 本轮推荐（非用户逐项确认）

接入形态按 D-110 更新为[独立插件入口](./design.md#分叉配置问题与接入方案)，规则制品来源与官方实现版本须配对，Mimir 输出使用 `target/mimir-quality/`。

- PMD/CPD 使用 `maven-pmd-plugin` 3.28.0 自带的 PMD 7.17.0，不单独覆盖；`CommentRequired` 用 `violationSuppressXPath` 排除构造器与非公开可见性，以满足 D-107；本仓库让 PMD 处理错误也阻断构建。
- ArchUnit 1.5.1 核心库，以 optional 方式进入 starter-test，不引入 `archunit-junit5`。
- CPD `minimumTokens=100`，不检查测试源码。
- 不改 Git hook；本地入口为 `./mvnw -Pprecheck verify`。
- 发布 Parent 的规则 jar 版本通过 Flatten 属性插值固化，详见[设计](./design.md#发布-parent-的规则-jar-版本)。
