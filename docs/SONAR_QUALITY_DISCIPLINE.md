---
updated: 2026-09-16
---

# SonarCloud 质量纪律

本文档定义 Mimir Boot 对 SonarCloud 新代码质量门禁的工作纪律。它补充
[`RELIABILITY.md`](./RELIABILITY.md) 的可靠性原则，不替代 Maven、测试或代码审查。

## 1. 适用范围与原则

- 以 SonarCloud 的 **New Code** 为判断范围；当前仓库的新代码周期以相对上个发布版本的改动为准。
- 质量门禁看新代码，项目总览中的历史 Bug、漏洞和代码异味不能与本次变更混为一谈。
- 不得为了通过门禁而降低阈值、扩大 `sonar.*.exclusions`、关闭规则或用无理由的抑制注解掩盖问题。
- Sonar 规则严重度是风险信号，不自动覆盖公共 API、配置绑定和兼容性约束；涉及这些边界的修复必须先确认迁移策略。

## 2. 当前质量门禁

CI 的单一 Build Job 与本地共用统一完整入口：
`bash scripts/engineering.sh quality --mode full --source worktree`。该入口包含 Java 子检查
`bash scripts/engineering.sh java`；Java 子检查先执行 `./mvnw -B -Pci clean verify`，再核验
测试与 JaCoCo 报告。只有在基础构建和报告核验均通过后，且 `RUN_SONAR=true`、
`SONAR_TOKEN`、`SONAR_ORGANIZATION`、`SONAR_PROJECT_KEY` 均由环境提供时，才独立执行
`./mvnw -B -Pci sonar:sonar` 并等待 Quality Gate。Sonar 不是 `clean verify` 的同一次 Maven
invocation；本地 full 默认 `RUN_SONAR=false`，因此只等价 CI 基础检查，不包含 Sonar。
CI 仅在 push 到 `main`/`develop` 且三个凭据均配置时启用该阶段；其他事件只跳过分析。
新代码必须满足下列条件：

| 指标 | 要求 | 处理原则 |
|---|---:|---|
| 可靠性评级 | A | 任一新 Bug 都必须在同一变更中修复或获得明确的规则处置决定。 |
| 安全评级 | A | 新漏洞不得带入；涉及鉴权、输入、密钥或日志时必须额外做安全审查。 |
| 可维护性评级 | A | 新代码异味应优先随改动消除；不能安全消除时登记有 owner 的技术债。 |
| 新代码覆盖率 | >= 80% | 用行为测试覆盖新增分支和异常路径；不得以排除路径代替测试。 |
| 新代码重复率 | <= 3% | 提取共享逻辑或测试夹具，避免复制粘贴。 |
| 安全热点审查率 | 100% | 每个新热点必须完成安全判断，不能仅标记为已审查。 |

分析触发条件以 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) 为准，完整入口及 Java
子检查以 [`scripts/engineering.sh`](../scripts/engineering.sh) 为准；本地 JaCoCo 配置位于
[`mimir-boot-parent/pom.xml`](../mimir-boot-parent/pom.xml)，与上表 Sonar 新代码门禁要求分别维护。
修改阈值、分析范围或上报方式属于
构建治理变更，必须说明影响并完成完整验证。

## 3. 推送前检查清单

每次准备推送可能触发 Sonar 分析的改动前，按以下顺序检查：

1. 先阅读 `git diff`，识别新增的异常路径、反射代码、配置绑定、废弃 API 和测试断言。
2. 使用仓库目标运行时执行完整门禁（默认不上传 Sonar）：

   ```bash
   mise exec java@17 -- bash scripts/engineering.sh quality --mode full --source worktree
   ```

3. 若已确认目标 Sonar 项目、分支和上传授权，并需要复现 CI 的 Sonar 阶段，先由受控环境提供三个环境变量，再使用相同完整入口：

   ```bash
   RUN_SONAR=true \
   SONAR_TOKEN="${SONAR_TOKEN:?请通过受控环境提供}" \
   SONAR_ORGANIZATION="${SONAR_ORGANIZATION:?请通过受控环境提供}" \
   SONAR_PROJECT_KEY="${SONAR_PROJECT_KEY:?请通过受控环境提供}" \
   mise exec java@17 -- bash scripts/engineering.sh quality --mode full --source worktree
   ```

   该命令会先完成 `clean verify` 和报告核验，再独立运行 `sonar:sonar` 并等待 Quality Gate；文档不记录任何凭据值。
4. 运行 `git diff --check`，并在完整报告中确认 Spotless、测试和 JaCoCo 报告核验结果；`./mvnw -Pci clean verify` 是 Java 子检查的 Maven 基础步骤，不能替代完整入口的报告核验。
5. 对已知 Sonar 规则逐项确认：没有未使用导入、嵌套三元表达式、无意义的 `throws`、或会掩盖异常来源的断言 lambda。
6. 若改动包含新分支、错误处理或配置解析，确认 JaCoCo 报告已经覆盖成功和失败路径，而不仅是 happy path。

本地 `verify` 不能代替 SonarCloud 分析。不要在未明确分支参数和凭证用途的情况下从本地向主分支上传 Sonar 分析；以具备上述运行条件的受控 CI 分析结果为准，PR 事件本身不执行 Sonar 分析。

## 4. 常见问题的处置

| 问题类型 | 默认处置 |
|---|---|
| 新 Bug / 可靠性评级非 A | 阻断推送；补充复现测试后修复。 |
| 新漏洞或未审查热点 | 阻断推送；按 [`SECURITY.md`](./SECURITY.md) 处理。 |
| 未使用导入、嵌套三元、冗余 `throws` | 在当前变更中直接修复并运行受影响模块测试。 |
| JUnit 异常断言中存在多个可能抛异常的调用 | 先构造被测值，再让 assertion lambda 只保留一个可能抛异常的调用。 |
| 反射中通过类名字符串比较类型 | 用实际 `Class` 对象或 `instanceof` / 可赋值关系比较。 |
| `@Deprecated` 兼容 API | 保留兼容语义，补齐 `@deprecated` Javadoc，并在技术债中记录移除版本和 owner；不得仅为消除异味直接删除。 |
| 配置属性、公开常量或方法名冲突 | 先评估配置绑定与二进制兼容性；需要改名时提供弃用别名和迁移说明。 |
| 泛型 `Exception` | 优先使用领域异常或库的具体异常；若属于公开 API，先评估调用方兼容性。 |

## 5. CI 失败后的闭环

1. 以 SonarCloud issue 的规则、文件和行号定位，不以总览指标猜测原因。
2. 判断该 issue 是真实缺陷、可安全重构，还是需要兼容性/安全决策的治理项。
3. 修复真实缺陷或安全问题时，先补充失败复现，再运行受影响模块测试和完整 `verify`。
4. 对不能立即移除的兼容性项，在 [`active/tech-debt-tracker.md`](./active/tech-debt-tracker.md) 记录原因、owner、目标版本和复查条件；不要静默忽略。
5. 仅在新的 CI Sonar 分析显示 Quality Gate 通过后，才视为该次质量门禁闭环。

## 6. 责任边界

- 改动作者负责在推送前完成本地验证，并处理由本次改动引入的新代码问题。
- 审查者负责识别把 Sonar 规则误用于公共兼容性边界的修复，并要求明确迁移方案。
- 维护者负责保持 JaCoCo 报告路径、Sonar 项目配置与 CI 命令一致；发现漂移应优先修复链路，不得通过全局排除掩盖。
