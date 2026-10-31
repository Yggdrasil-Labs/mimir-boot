---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# BOM 配套依赖实施计划

静态实施输入，尚未执行。[规格](./spec.md) · [设计](./design.md)

## 目标与关键边界

落实 B1/B2：只管理 binding、OOXML、COLA DTO 三坐标；版本与覆盖契约见设计。新增版本管理不等于新增运行依赖，传递版本变化仍需核对。实施不改变框架基线。

## 任务与依赖

| 任务 | 依赖 | 独占范围 |
|---|---|---|
| B-T1 核对并补齐管理 | 无 | mimir-boot-bom/pom.xml |
| B-T2 消费说明 | B-T1 | mimir-boot-bom/README.md |
| B-T3 消费者验收 | B-T1；继承路径依赖 Parent 发布形态 | 仓库外临时消费者；不新增或修改仓库内测试 |

### B-T1：版本及准入

Modify `mimir-boot-bom/pom.xml`：增加两个版本属性、三个 dependencyManagement 项，复用 poi.version；不改根框架版本。按 `docs/engineering/development.md` 核对来源、许可证、依赖树，binding 0.2.0 是本稿推荐版本。验收：有效 POM 与设计三行一致，其他受管坐标无非预期改动；不满足组合要求时修订具体版本方案，不擅自升级框架。

### B-T2：下游说明

Modify `mimir-boot-bom/README.md`：共用组件声明配继承/import 差异表；解释 import 不继承插件、同名属性不保证覆盖、消费者显式管理可覆盖；提供 `.xlsx` 与处理器配套接入。处理器接入须明确（审查补充）：只 import BOM 的非 Parent 消费者在 JDK≥23 上必须自行开启注解处理（`maven.compiler.proc=full` 或 `-proc:full`），推荐放入 `activation/jdk=[23,)` 的 profile 而非无条件设置；并注明 `proc=full` 需要较新的 maven-compiler-plugin（本地 3.13.0 起描述符已列出，更早最低版本以实施时核实为准）。验收：两种说明各能独立指导消费者，不把验证计划写成已通过。

### B-T3：有效产物与行为

复用 [Parent 计划 T3](../parent-build/plan.md#t3源码与发布前隔离消费者验收) 的仓库外临时工作根、生产者本地库、文件发布库与消费者本地库步骤及设计中的命令骨架，部署待验收 BOM/Parent，再由该文件仓库解析创建两个最小消费者；不得依赖源 POM 的 relativePath，不另建一套生产者流程。运行有效 POM/依赖树、无组件依赖反例、OOXML 消费者的 log4j-api/commons-io/commons-compress/xmlbeans 有效版本记录、工作簿读写、Lombok/MapStruct 映射、COLA 编译；处理器组合对齐 Parent 的 JDK 17/21/25；import 方未开启处理的编译失败负向只在 JDK 25 运行（JDK≥23 javac 默认不处理）；同一 import 消费者在 JDK 25 按 README 推荐 profile 开启 `proc=full` 后须编译成功并生成映射实现，作为正向对照。缺 JDK 或仓库失败明确标为未验证。Common/MyBatis 分页异常契约由其计划负责，不复制到 BOM。

集成通过条件：B1/B2 所有正常、边界、失败场景有结果；无自动新增运行依赖；显式覆盖有效；读写与生成代码可执行。模块 POM 检查可用 `./mvnw -pl mimir-boot-bom -am validate`，但不能替代独立消费者行为验收。

## 决策边界

允许自行组织样例与测试；不新增永久 tools 专项脚本、不扩展托管坐标/基线/发布策略。技术验证改变本稿契约时先回写设计再实施；本次写文档不授权部署、发布或提交。
