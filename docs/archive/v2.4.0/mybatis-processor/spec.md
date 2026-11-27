---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# MyBatis Processor 兼容需求规格

推荐规格，未实施；D-71 为已确认范围。[讨论](./brainstorm.md) · [设计](./design.md) · [计划](./plan.md)

## Behavior MP1：跟随编译器并保留生成规则

| Scenario | Given | When | Then |
|---|---|---|---|
| 正常生成 | 编译器开启处理，实体 UserDO 使用现有配置 | 编译实体 | 仍生成 UserMapper、UserService、UserServiceImpl，实体引用仍为 UserDO |
| 较新编译器 | 使用 JDK 21 或 25，目标 17 或该编译器支持的本版本 | clean 编译同一实体 | 无固定 RELEASE_17 引起的支持源版本落后告警，三类产物可编译 |
| 处理关闭 | 消费者显式关闭处理，已清理生成目录 | 编译不依赖生成类型的实体 | 不生成三类代码；引用生成类型时应编译失败 |
| 仅导入 BOM 且 JDK≥23（审查补充） | 消费者未继承 Parent、仅 import BOM，以 JDK 25 编译，处理器经 provided 进入 classpath | 分别在未设置与按 README 设置 `maven.compiler.proc=full`（或 `-proc:full`）后 clean 编译 | 未设置时 javac 默认不运行 classpath 发现的处理器，不生成三类代码；设置后三类产物生成并可编译 |

## Behavior MP2：冲突说明对应真实名称

| Scenario | Given | When | Then |
|---|---|---|---|
| 不冲突 | 同包 User、Order 采用同一组后缀 | 生成代码 | 生成六个不同全限定名 |
| 冲突 | 同包 UserDO、User 采用同一生成包和后缀 | 生成代码 | 重复全限定名导致编译错误，不覆盖或静默跳过 |
| 避让 | 对冲突实体之一分别调整三类产物包或后缀 | 再次 clean 编译 | 三类产物均不重名并能一起编译；只改 Mapper 不代表 Service 已避让 |

## Constraints

处理器制品最低运行要求仍为 Java 17；验证清单为 JDK 17、21、25，不承诺未来所有语言特性。保留现有 classpath 服务发现、provided 接入和模块自身关闭处理的配置。只改支持版本声明与冲突说明，不增生成能力。Parent P-02 的 `proc=full` 只作用于继承 Parent 的消费者；仅导入 BOM 的消费者在 JDK≥23 上须自行显式开启处理，本需求以 README 说明兑现，不改 BOM 或处理器发现机制（审查补充）。
