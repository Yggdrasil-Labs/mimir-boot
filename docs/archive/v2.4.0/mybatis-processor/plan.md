---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# MyBatis Processor 兼容实施计划

静态输入，未执行。[规格](./spec.md) · [设计](./design.md)

## 目标和范围

仅兑现 MP1/MP2；推荐 latestSupported，Java17制品基线不变。

1. Modify `mimir-boot-starters/mimir-boot-starter-mybatis-processor/src/main/java/com/yggdrasil/labs/mybatis/processor/AutoMybatisProcessor.java`：替换固定支持版本为查询方法；保留处理流程及错误诊断。
2. Modify 同模块 `src/test/java/com/yggdrasil/labs/mybatis/processor/AutoMybatisProcessorTest.java`：验证真实生成、冲突与避让；不修改服务发现文件或 POM 的 proc=none。
3. Modify 同模块 `README.md`：修正问题4并列出实测JDK，不把未来版本写成已支持验证。
4. Modify 同模块 `README.md`（审查补充）：在“问题1”编译插件示例（现仅提示不要设置 `proc:none`）及接入说明中补充，未继承 Parent、仅导入 BOM 的消费者在 JDK≥23 须设置 `<maven.compiler.proc>full</maven.compiler.proc>` 或 `-proc:full`，否则 javac 默认不运行 classpath 发现的处理器而静默不生成；继承 Parent 者由 P-02 覆盖。注明 `latestSupported()` 取决于实际运行 javac 的 JDK（fork/toolchain 场景）。审查补充（推荐）：示例把该设置放在 `<jdk>[23,)</jdk>`（或 `[21,)`）激活的 profile 内，不无条件设置，避免较旧 JDK 17 构建拒绝 `-proc:full`；说明 `maven.compiler.proc` 需 maven-compiler-plugin 支持 `proc=full`（本地核对 3.13.0～3.16.0 支持，更早版本最低要求待验证），旧插件改用 `compilerArgs` 传 `-proc:full`。审查补充（推荐）：注明 `<jdk>` profile 激活依据是运行 Maven 的 JVM 而非 toolchain/fork 实际 javac；使用 maven-toolchains 或 fork 指定不同 javac 的项目应按实际 javac 版本直接配置 `compilerArgs`（`-proc:full`）或 `maven.compiler.proc`，不依赖 jdk profile。

顺序为声明调整 → 现有编译测试及样例核对 → 与 Parent P-02 联合验收。文件均归本需求单一实施者，不与 Parent 同时改同一 POM。

## 验收

- `./mvnw -pl mimir-boot-starters/mimir-boot-starter-mybatis-processor -am test` 通过；原有生成规则无变化。
- 以最终发布形态的 JAR 和实际生成代码依赖，在 JDK17/21/25 上分别 clean 编译；较新 JDK 测目标17及该JDK目标。缺少JDK明确记录未验证。
- 两组正常及冲突示例符合 spec；provided/classpath 发现通过；显式关闭处理且清理输出后确实无生成文件。
- 验收矩阵增加“仅导入 BOM + JDK 25”消费者（审查补充）：未设置 proc 时无生成文件，按 README 设置 `proc=full` 后三产物生成并编译；与“继承 Parent + JDK 25”对照记录，实际 javac 版本写入结果。同一 README 示例 profile 在 JDK 17 下不激活且编译通过（审查补充）。
- 不新建 tools 专项脚本。临时消费者仅作验收输入，不作为新模块。

## 决策边界

测试组织可自行决定；不得顺带增加按实体选择生成、冲突跳过或修改类命名。公开注解属性与制品最低JDK变化属于范围变更。本稿未授权功能实施或发布。
