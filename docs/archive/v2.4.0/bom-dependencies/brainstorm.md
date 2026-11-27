---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# BOM 配套依赖方案大纲

## 本轮推荐与 SDD 产物

2026-10-29 用户要求停止逐项提问，并补齐每项需求的 SDD。本文保留前期讨论过程；未逐项确认的细节已由本轮推荐方案收敛到 [spec](./spec.md)、[design](./design.md)、[plan](./plan.md)，推荐不追记为历史用户决定。以下“候选”“待讨论”“后续”等措辞描述此前状态，当前推荐以 SDD 为准；文档不代表实现或验收完成。

本稿承接[版本级 brainstorm](../brainstorm.md)的 B-01 至 B-03，讨论新增托管项的落点和验证边界。新增版本选择、消费示例及验证组织方式已由 SDD 推荐收敛（binding 0.2.0、`poi-ooxml` 复用 `poi.version`、COLA DTO 5.0.0），本稿保留探索过程。

## 需求范围

| 需求 | 版本级决策 | 期望结果 |
|---|---|---|
| B-01 配套依赖 | D-16、D-67 | 托管 Lombok 与 MapStruct 联用所需 binding、POI 的 OOXML 组件、COLA DTO |
| B-02 组合验证 | D-17 | 保留已有验证，本轮增加与实际改动相关的消费者组合 |
| B-03 框架基线 | D-18 | 保持现有 Spring Boot、Cloud、Alibaba 基线 |

已确认不导入 `cola-components-bom`，不扩展 JWT、Retrofit 与 OkHttp 专项联用验证，也不把未采纳的 B-04 安全补丁属性覆盖能力带入。

## 基于既有决定的方案收敛

按 D-16、D-17、D-18、D-67 及 D-86，BOM 继续提供可由下游选择和覆盖的版本基线。以下是既有决定的落实方向，不新增能力范围：

- 仅在 `dependencyManagement` 补齐三个坐标；下游显式声明需要的依赖，不因继承 Parent 或 import BOM 自动获得这些组件。
- `poi-ooxml` 复用已有 `poi.version`，COLA DTO 沿用已确认的 5.0.0；binding 0.2.0 保留为技术候选，待核对兼容依据与实际组合结果，不标为已验证版本。
- 继承 Parent 与仅 import BOM 都保留。后者不继承构建插件或 profile；覆盖依赖版本时采用下游显式 `dependencyManagement` 等 Maven 原生方式，不承诺 import 方能以同名属性覆盖 BOM 内部版本。
- 使用说明优先共用依赖示例，并列两种接入方式的差异；验证复用同一组最小行为样例，分别消费发布 Parent 与发布 BOM。
- 框架基线保持不变；新增组件的版本依据、许可证和依赖组合属于后续技术核对，不能用范围确认代替。

上述方向可以承接到后续详细设计；若版本兼容验证要求改变框架基线、扩大托管范围或强制新增运行依赖，再单独提出取舍。

## 已核对的代码事实

入口为 [BOM POM](../../../../mimir-boot-bom/pom.xml)、[BOM README](../../../../mimir-boot-bom/README.md)、[根 POM](../../../../pom.xml)。本轮做了静态核对，未解析消费者依赖树或执行组件测试。

- BOM 已管理 `mapstruct`、`mapstruct-processor` 与 `lombok`，对应本地源码版本分别为 1.6.3、1.6.3、1.18.48。
- 已管理 `org.apache.poi:poi`，使用 `poi.version=5.5.1`；当前没有显式管理 `poi-ooxml`。
- 当前没有显式管理 `lombok-mapstruct-binding`、`cola-component-dto`。
- Spring Boot 基线来自根 POM；BOM 当前使用 3.3.13、2023.0.6、2023.0.3.4 三组框架版本。
- Parent 通过 import 引入 Mimir BOM；独立消费者也可以只 import BOM。
- 本地缓存可见 binding 0.2.0 与 COLA DTO 5.0.0 制品；存在缓存仅说明可供进一步核对，不证明组合兼容。

## 候选方案一：显式管理三个坐标

建议在现有 `dependencyManagement` 分组中补齐坐标，不添加 BOM 自身的运行依赖。

| 坐标 | 候选版本来源 | 需要继续核对 |
|---|---|---|
| `org.projectlombok:lombok-mapstruct-binding` | 本地可见 0.2.0，作为候选而非已批准版本 | 与当前 Lombok、MapStruct 及 JDK 矩阵的联用结果 |
| `org.apache.poi:poi-ooxml` | 沿用现有 `poi.version` | `poi`、OOXML 组件及传递依赖的有效版本与读写能力 |
| `com.alibaba.cola:cola-component-dto` | D-67 已确认 5.0.0 | Common 与 MyBatis 可选依赖场景能否各自编译和运行 |

新增版本属性的命名延续 BOM 现有风格；binding 与 COLA DTO 是否各设属性，在详细设计时统一。消费者覆盖能力需要按接入方式解释，不能因为 BOM 内有属性就承诺 import 方能通过同名属性重写其内部版本。

选择显式坐标的理由是变更面可控：只对本轮需要的组件提供版本，避免引入其他 COLA 组件的版本约束。

可比较的替代方向是导入第三方完整 BOM：它有利于整套组件统一升级，但会扩大托管范围、增加既有依赖覆盖分析；COLA 完整 BOM 已被 D-67 排除，本稿不重开该选择。

## 候选方案二：消费者按需声明

建议将“托管版本”与“启用能力”分开写入使用说明。

- 只继承 Parent 或 import BOM，不会因此获得 POI、COLA DTO 或 binding 运行依赖。
- 使用 `.xlsx` 的业务显式声明 `poi-ooxml`；只用基础 POI 的业务继续按原方式声明。
- Common 与 MyBatis 的 COLA 转换由各自模块定义 optional 边界，BOM 不替它们决定类型暴露或自动装配行为。
- Lombok、MapStruct 及 binding 的编译期接入示例要与 Parent 的 classpath 发现方向一致，避免示例意外关闭其他处理器。
- 独立 import BOM 不继承 Parent 的编译插件、JDK profile 或质量门禁；示例须自行说明必要的编译器配置。

示例拟采用一套依赖声明配两种接入差异表，清楚标出编译插件的责任归属；相比维护两套完整示例，可以减少重复内容。若具体接入步骤存在无法共享的差异，再局部展开，不需要改变依赖按需声明的契约。

## 候选方案三：聚焦实际组合的验证

建议以两类独立消费者复用相同的最小行为样例，不扩展为全量依赖兼容平台。

| 消费方式 | 主要证明什么 | 不能据此证明什么 |
|---|---|---|
| 继承发布 Parent | BOM 版本托管与 Parent 编译规则共同生效 | 只 import BOM 的业务构建配置正确 |
| 独立 import 发布 BOM | 不依赖 Parent 也能解析新增版本并使用组件 | Parent 的 profile、Flatten 和质量门禁正确 |

每种接入先检查有效依赖管理与依赖树，再执行行为样例：

- Lombok 生成属性访问入口，MapStruct 映射这些属性；检查生成类并执行映射结果断言。
- 创建 `.xlsx` 文件或内存工作簿，写入并重新读取文本与数值；不能只证明依赖可以下载。
- 显式声明 COLA DTO 后编译基本响应使用代码；分页转换的溢出、空值和集合行为由 Common、MyBatis 自己验收。
- 未声明三个新增组件的最小消费者，不应因托管新增项而在运行 classpath 获得它们。

## 依赖与兼容边界

- [Parent 构建需求](../parent-build/brainstorm.md)负责 `release`、处理器发现和 profile；BOM 不新增构建 profile。
- Common、MyBatis 的 COLA 支持依赖 B-01 提供版本，但不要求其他 Starter 同步引入 COLA。
- POI 主组件与 OOXML 组件应保持版本一致；若传递依赖受其他 BOM 覆盖，先分析具体冲突，不自动升级整个依赖基线。
- 保持框架基线是既有决定，不代表已完成当前安全支持状态评估；升级与专项安全覆盖仍在本轮之外。
- 发布 BOM 消费者需要通过仓库解析本次产物，不能仅使用 reactor 内的源 POM 代替。

## 后续技术核对

优先核对 binding 候选版本与现有 Lombok、MapStruct 的兼容依据；处理器组合衔接 Parent 已列出的 JDK 17、21、25 验证方向，POI 与 COLA 聚焦自身实际变化，避免无依据扩大测试矩阵。新增依赖仍需按[开发规程](../../../engineering/development.md)核对版本、许可证与兼容依据；本稿尚未进行这些验证，也不声明任何组合已通过。

状态已与 SDD 同步为 draft。BOM 的范围无需重新选择，接下来可并行收敛 Common 的公共契约；BOM 技术核对若发现影响该契约的问题，再回到对应候选方案处理。
