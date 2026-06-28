# Changelog

## [3.0.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v2.1.0...v3.0.0) (2026-06-28)


### ⚠ BREAKING CHANGES

* **build:** 所有 Maven 模块的 groupId 从 com.yggdrasil.labs 变更为 io.github.yggdrasil-labs。使用旧 groupId 的项目需要更新依赖引用。已发布的版本保留旧 groupId，新版本使用新 groupId。
* 项目首次发布，建立企业级Maven基础框架

### ✨ Features

* access.log支持指定路径不记录访问日志，减少日志杂音 ([7b4d8a9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7b4d8a9cbec4a7b4e1d8ff61da1ed1d528ed888e))
* **ci:** 添加 markdownlint 配置文件 ([2b16f67](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2b16f67a6329194bf87f03d40e83a0ba64387ecd))
* **common:** 去掉jackson的依赖，保持common模块的纯净 ([deaa318](https://github.com/Yggdrasil-Labs/mimir-boot/commit/deaa318006a67492394b0e977c4d2e2beac8a9f0))
* **common:** 完善common模块 ([1a3c6e7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1a3c6e79c440643e3b590ba86db0e4ab048f0073))
* **config:** 新增nacos相关模块 ([20e28b5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/20e28b551a6c92818beec21ebbe5fed0e698d8de))
* **example:** 增加示例工程，便于调试 ([2fcaade](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2fcaadec6acddb10eb1a8b68659ff6393ece19f0))
* **exception:** 增加starter-exception [#52](https://github.com/Yggdrasil-Labs/mimir-boot/issues/52) ([1f67a7d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1f67a7d78f5b3fa53f76f966121de27bae7a86b3))
* **exception:** 异常处理器支持响应格式适配 ([6fe0b10](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6fe0b1079e95ca73ba8463514a2ed88ff18543b7))
* **log:** 防止日志伪造风险 ([ff05b35](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ff05b35d65f07d6b4d1411fc687d7cfcce1e3c0c))
* **pom:** add dependency for mimir-boot-starter-exception ([16ecf87](https://github.com/Yggdrasil-Labs/mimir-boot/commit/16ecf872c519b206a635f8917d4e170e80d1abe2))
* **pom:** 增加compile-test依赖管理 ([7326cae](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7326cae4c04b763812675e6d54953cf792b7694b))
* **pom:** 统一管理依赖 ([cd3c31d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cd3c31d21b937ed56b818b92f17fe05d720d450c))
* starter-test模块的能力增强 ([9a174ec](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9a174ecbd4a66adcb342bb7411d2f0b740b01387))
* **web:** 新增starter-web ([f117bd4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f117bd4feda130c1baf5a8de0c30931bf600e057))
* 优化logback配置 ([901e80d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/901e80dd60be8995655d8a5cacb8816fb8ad5e94))
* 初始化Mimir Boot企业级基础框架项目 ([42c8c5c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/42c8c5cb94a21f7f8d376f4e0e6a58a797894938))
* 加密功能默认关闭，支持参数控制显示开启 ([f07a48d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f07a48de851a2025b734626854cbdd98f68c0bf4))
* 增加mybatis相关starter。 ([d457872](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d457872cc1e9b5007026d84da5dcfd5c70592da0))
* 增加starter-test，提供统一的测试依赖管理、测试基类、测试工具 ([4e5c513](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4e5c5131ab4cffeb28d45f1e2390a7c08ccacb3f))
* 增加校验依赖 ([8a5a110](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8a5a110ad7cd7cb1def8f107e8ce90ce2fadce47))
* 如果实体类是包含DO后缀的，Mapper、Service的名字要去除DO ([3b036fc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3b036fc7a1db98c443b0ddf604335d156bd3ef3c))
* 支持默认扫描com.yggdrasil.labs.**.mapper包下的Mapper ([09e93f3](https://github.com/Yggdrasil-Labs/mimir-boot/commit/09e93f3fce6712ad80f2db4a6612da7b9fbcfbee))
* 新增 RPC治理模块 ([251dd38](https://github.com/Yggdrasil-Labs/mimir-boot/commit/251dd38bac6cca305a215346e9c99e42cb3c284b))
* 新增common模块，完善模块之间的依赖关系 ([6ab1e26](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6ab1e2667a919006a010e57980d12a01046e23a2))
* 新增log模块，提供默认的log配置 ([8f20b38](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8f20b3814bdd479fadc53a7344ccbd98863cb2dc))
* 新增日志内容清理工具，防止日志注入攻击。 ([f455f9c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f455f9c273ff4efdcb231553ee794d93dbd81f91))
* 添加Checkstyle配置文件以增强代码质量检查 ([c3ccb99](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c3ccb99beff18f7fbd05bffbaf1d9f1916aaeabb))
* 生成的Mapper加上@Mapper注解，非com.yggdrasil.labs.**.mapper目录也能自动扫描 ([c2d1780](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c2d1780205e149cfc892ddcdb8023a3322647ec2))
* 脱敏处理支持=与:匹配 ([f4890d4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f4890d461e8103fec344738da6cda915f5c23e98))
* 默认mybatis使用slf4j，不使用StdOutImpl ([194095c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/194095c47f7cbf1c0858122a4ad6cf89e97cad20))
* 默认扫描包 com.yggdrasil.labs.**.mapper ([96304b0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/96304b05341e1f0a89435cead4ff5189ebb6013f))


### 🐛 Bug Fixes

* Avoid expanding secrets in a run block. ([94d7cda](https://github.com/Yggdrasil-Labs/mimir-boot/commit/94d7cda551cf72b83c9291e6d9b9be050bf125ae))
* **ci:** 修复 markdownlint-cli2-action SHA 引用无效 ([3f62332](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3f6233261a52b485abe43562d5a3f1c9af9acf7b))
* **ci:** 修复 SonarCloud 覆盖率上报，合并 build 为单步 verify，添加 markdownlint ([641b9b9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/641b9b9b1e712f0a2a45f7dc1b8a81f9471f2310))
* CryptoUtils增加私有构造函数 ([e700b73](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e700b7381177c4d49d108cb3e60d20463bba9a10))
* **deps:** 降级 spring boot plugin version from 3.5.7 to 3.3.13 ([d9feb8b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d9feb8b1de1d7fe97e06fc5a3edb8a9d1429c63c))
* **exception:** 修复请求URL日志记录，避免潜在的空指针异常 ([9f7dba5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9f7dba54389ce16eae1ee1c9174b610546870923))
* MdcUtil增加私有实例方法，防止实例化 ([bdc54f1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/bdc54f15b2018a9f506e42201e56bf5a1b8df5fc))
* **nacos:** 修复 ConfigCryptoUtilsTest.testDecryptWithWrongKey 测试不稳定 ([c36ae93](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c36ae930902044154a29283f53ac534762f07779))
* **pom:** 引入test starter后，可以直接使用常用测试能力。close: [#226](https://github.com/Yggdrasil-Labs/mimir-boot/issues/226) ([20f033a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/20f033ad0267a5fc046bb1784a0aef28d99ff3d3))
* remove no use code ([e51d4ee](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e51d4eec771f4082c8bc8ebf66f00176f0e18bfe))
* Sonar找不到单元测试报告 ([7ccbbb8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7ccbbb8fca5a2353d9a263f70114eaa6ce6cffe2))
* Sonar找不到单元测试报告 ([1b67c5b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1b67c5b9d7608df031f477a651feac374fa6f3ef))
* spotless apply应该在maven的process-sources阶段 ([85020be](https://github.com/Yggdrasil-Labs/mimir-boot/commit/85020bef008461f556a2df68159094f34b779264))
* Springboot 3.X 应该使用mybatis-plus-spring-boot3-starter ([c0d1cda](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c0d1cda203a346b6aa684716b7aed9e5a6f3df35))
* SQL日志打印的参数是密文 ([4f84584](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4f8458459cf70a20cc8bc4dd4d8c3653d5d2576e))
* 优化边界处理场景 ([4a7dd2e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4a7dd2ed5ba9bb651041dde91dd85a4cc13885cc))
* 使用动词原形而不是过去式 ([b82dc0b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b82dc0b72533c5d8fe119bb039f868984a41126c))
* 使用自定义的系统异常类 ([447417f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/447417fcfc39b786ea00deb673ef06a233b2e484))
* 修复access.log不打印的问题 ([36a26cb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/36a26cb5ec9109e03ae9c908816329219448b3c9))
* 修复sonar cloud issue ([cf7fc2d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cf7fc2daf827075be9853d4dfea66f9d98ba11a3))
* 修复sql json不生效的问题 ([e31e17a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e31e17a41813ec36e27637f7e92cfee966b439b4))
* 修复未加载分页插件问题 ([f84df1c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f84df1cb56764d0cc28db572899ded69112f10d4))
* 修改access.log慢接口为3000ms ([504d8a0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/504d8a0550d3580ba5727a6a749bcaa0fa826bb1))
* 修改在mask复杂对象时堆栈溢出的问题 ([6b209ce](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6b209ce1344020536e22646c7db2791f749ea73e))
* 加载顺序问题导致MybatisPlusAutoConfiguration未能正确加载；修复部分配置前缀错误 ([f842b17](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f842b17df20df148c31dcc33c50f8188910cccb5))
* 压缩日志统一放在“年-月”目录下归档 ([e7e8de8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e7e8de818cd7a0668559962e7f4bbdd3bc4c70ea))
* 取消自动注册为全局处理器，只有显式使用才行 ([aa03d2d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/aa03d2d6ecd7126fd00be195e0fb7cf58438bc8e))
* 增加日志级别判断，减少不必要的计算 ([21cb04e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/21cb04e1b76116e2ad28d7ee4a398dbcf2647634))
* 复杂对象导致堆栈溢出的问题 ([1582782](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1582782aa9b9ba47176140e0db8bbc5c4de1c296))
* 局部变量名不与类成员变量名冲突 ([25c6d17](https://github.com/Yggdrasil-Labs/mimir-boot/commit/25c6d17f87e5c99590e2046b5f7cee7907b768d6))
* 日志注入防护 [#52](https://github.com/Yggdrasil-Labs/mimir-boot/issues/52) ([55f68ba](https://github.com/Yggdrasil-Labs/mimir-boot/commit/55f68ba7268378c73a3a36f5731a0ca641884556))
* 日志注入防护 [#52](https://github.com/Yggdrasil-Labs/mimir-boot/issues/52) ([a3692ec](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a3692ec3279935239abde129a9dff26afcafb14a))
* 日志注入防护 [#52](https://github.com/Yggdrasil-Labs/mimir-boot/issues/52) ([3709518](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3709518938515d42f35da59427b3a0e16e33cbc4))
* 日志注入防护 [#52](https://github.com/Yggdrasil-Labs/mimir-boot/issues/52) ([6d1f699](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6d1f699b90c0881a4e7f28519e0110de23e3b4e5))
* 日志注入防护 [#52](https://github.com/Yggdrasil-Labs/mimir-boot/issues/52) ([e1b939c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e1b939cde37e8f6b35af9c0e30c66659e2044a27))
* 私有化Util类的构造函数 ([b1f015d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b1f015d784820a2054f18c5927e6a22b49a9916e))
* 线程安全问题 ([74c0efa](https://github.com/Yggdrasil-Labs/mimir-boot/commit/74c0efa5937f259b9248d721a114e1169a3e525b))
* 统一参数前缀为mimir.boot ([1073b25](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1073b2546d0ba6768c6d083fe1f56155d812184c))
* 调整分支顺序，先长后短 ([25ff278](https://github.com/Yggdrasil-Labs/mimir-boot/commit/25ff27884a2a9e72dc6988dd9855ef56dd368960))


### 📝 Documentation

* **common:** 补齐 ErrorCode 枚举 Javadoc ([5720240](https://github.com/Yggdrasil-Labs/mimir-boot/commit/57202405d362d2656b430f42f3a2482dbbd9c446))
* **init:** 按 docs-init skill 最佳实践重建文档体系 ([ec0861a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ec0861a01d289c7bdbb9e9f499aa389957fede32))
* **plan:** 新增 quality-refinement 迭代方案 ([dfb2d38](https://github.com/Yggdrasil-Labs/mimir-boot/commit/dfb2d382de0c707bc4374797d791e3bebf147d1e))
* 充值CHANGELOG ([49f6d67](https://github.com/Yggdrasil-Labs/mimir-boot/commit/49f6d671fff62a97b21ab98921665b8fff0fe856))
* 增加dubbo、feign相关的模块规划 ([8f0c255](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8f0c255f2a4a55cefd7a97eb603159f8a1e28e21))
* 完善README ([f0c2efb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f0c2efb70a2d390945933b38b341b43d189254c4))
* 归档 exception-handler-adapter，清理技术债 ([fde926d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fde926d0f4223487b4b4c7760b936fb3c4e63560))
* 新增文档体系，包含 AGENTS、ARCHITECTURE、DESIGN、PLANS 等文件，重构文档结构以提升可导航性和可维护性 ([25e3563](https://github.com/Yggdrasil-Labs/mimir-boot/commit/25e3563905b50ab39e774a99a46a68856594e724))
* 更换徽章 ([814ee77](https://github.com/Yggdrasil-Labs/mimir-boot/commit/814ee77ca87c61dfe51c965410bd37d5a212c06e))
* 更新 README 文件，增加徽章和模块说明，调整代码块格式 ([25c2c0f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/25c2c0fa38070678c14e65e32c8b407487f88b98))
* 清理 README 中不存在的规划模块，改为未来方向段落 ([f9fa42e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f9fa42ec0adbfde69480ab05eb69f17dd6931138))
* 清空CHANGELOG ([7847f31](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7847f3170cbd0eb1f120969f5ce219305c5e48c1))
* 精简根 README 特性展示段，去重代码示例 ([a6988c0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a6988c0923eb93b3a221eec84498db701a627011))
* 给每个模块完善README ([aeba7a8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/aeba7a8e7cb792b6743e3ee204ba2fcf332d3a8c))
* 给每个模块补充description ([4896bad](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4896bad7969f2a691a4c60e1485511f9eb94b184))
* 给每个模块补充description ([786057e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/786057e2b58f530bb50e7d1004aab55f03caced1))
* 补充mimir-boot-parent的README ([9c3a65d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9c3a65d9681f77fc7e1935ee2b6113a5f4155585))
* 补充README ([8c0f176](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8c0f176de2f57a964f4ee70fa7b0c2fc3b8dee2e))
* 补充README ([e5750af](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e5750afbf9d8fedc736f7dd8c47c5764c2a2a870))


### ♻️ Code Refactoring

* always true ([448ef59](https://github.com/Yggdrasil-Labs/mimir-boot/commit/448ef59d2ab82cd8015e39acbf67f049bd7f04fa))
* fix Serializable Check. ([2cfa409](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2cfa4090532500258313ed15697dea0dd85d45f6))
* Function&lt;String, String&gt; 的特化版本，约束输入输出为同一类型 ([f977418](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f9774185dca7058a61df771b9fd13eeeaf53ba9d))
* Util类添加私有构造方法 ([7fcdb4c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7fcdb4c0273359e9f55420dce5909fe226880b0c))
* 仅用于测试，忽略这些问题 ([825d0a0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/825d0a028180515ae09e0285de89548f2bf0019e))
* 优化重复代码 ([4841287](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4841287aef09275779fdcbbc8f12d2da8ed26e80))
* 使用Common模块定义的异常 ([91267ff](https://github.com/Yggdrasil-Labs/mimir-boot/commit/91267ff6480502a2bb93184f1b933bf553f94bed))
* 使用lambda表达式 ([e84c444](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e84c4449dda786ab3d8ad8c1316f2d73bf80f12f))
* 使用Stream.toList() ([458c455](https://github.com/Yggdrasil-Labs/mimir-boot/commit/458c4555cf4b6a50f835dec4f85d95809191936b))
* 使用构造器注入 ([e8a53b9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e8a53b98562ba1bb9dedb31824d381d730b2b7af))
* 使用构造器注入，使用Optional表达required=false的语义 ([937f764](https://github.com/Yggdrasil-Labs/mimir-boot/commit/937f764692a826ad66f783326e340ab19e1a5289))
* 使用线程安全的类型，volatile是不够的 ([95b81b1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/95b81b12ff721aa94951d36839adfa807ca4b993))
* 关闭automaticAnalysis ([328dc45](https://github.com/Yggdrasil-Labs/mimir-boot/commit/328dc45e0adf1257e999b9d309ebd24c0498ec26))
* 删除所有的AllTests ([8b463ff](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8b463ff4b4ea2ee2fa4316f978cf9f25695f2833))
* 去掉RuntimeException ([0547a83](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0547a83062d93985b5dc3a8edbeb774a37b3dde2))
* 将重复的字符串提取为常量 ([7e2d25d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7e2d25d63d1e3dd8daf247d6946944cf56ca2b7d))
* 将重复的字符串提取为常量，公共的提取到common模块 ([a2c2b93](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a2c2b93440351053aeb8cca0a07e3619d46cf5ab))
* 局部变量避免与全局变量重名 ([a806a00](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a806a007ae1b46541c0503b550e3125f192b30d7))
* 开启ci automaticAnalysis ([2634f7c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2634f7cbfad2e12287673a86be4a140950af7f7b))
* 捕获Exception ([db5ec33](https://github.com/Yggdrasil-Labs/mimir-boot/commit/db5ec3322c37b97cfa9212c2bccf2bd69a39e238))
* 提取解析客户端真实IP的逻辑到common模块 ([e7a12e5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e7a12e5927043e9d6aba328e2b779253ff0d8b7b))
* 文本块代替字符串拼接，更具可读性 ([41642f2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/41642f27b4c1801d00c40185b5ed701268d6946b))
* 调整日志级别为DEBUG ([b51b16d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b51b16d539bc743a63e546e6fa06af10d032eb87))
* 通用脱敏占位固定4个* ([7de417b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7de417b1e6981686e81b2ba98c605c9e1e0b60d5))
* 通用脱敏占位固定4个* ([92207af](https://github.com/Yggdrasil-Labs/mimir-boot/commit/92207af57997d9ce3d213bed5a4fd71164280b7f))
* 避免实例方法写静态字段，存在线程安全问题，内存泄露风险。 ([74ebd25](https://github.com/Yggdrasil-Labs/mimir-boot/commit/74ebd252f0f4b9b5ab21a79e9fdcc0bc0255878a))
* 采用 AES/GCM/NoPadding ([9f96750](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9f9675055cebe0211cc83c1ab7ecfb80cf138a32))


### ✅ Tests

* exception模块基于mimir-boot-starter-test改造单元测试 ([f094f9c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f094f9c94ce579a431ce0cee6bb58bb8d2daaebd))
* **exception:** 补充必要的单元测试 ([edd9cc1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/edd9cc120b9dd6147c27ecbaa159a0bc465f556a))
* **exception:** 补齐 MimirExceptionHandler fallback 和 null-safe 分支覆盖 ([7728000](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7728000721f6c519fb8e3107148ae27e56f8c433))
* **IpUtils:** 为标头方法添加空值和奇数长度检查 ([4a92fec](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4a92fec5d0f19f2c3fe4eeb9058c3135ab293963))
* log模块基于mimir-boot-starter-test改造单元测试 ([da405a5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/da405a583176335ded0fb6e53f27683af445e56d))
* mybatis-processor模块基于mimir-boot-starter-test改造单元测试 ([5404f00](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5404f00bd8f1052616fe040c20a4bf1ac4cd04de))
* mybatis模块基于mimir-boot-starter-test改造单元测试 ([ada6490](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ada6490ab047fb334046027d710274949299f020))
* **mybatis:** 补充mybatis相关单元测试 ([e50ea87](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e50ea872bb8e58532286daef425eb55dd9998252))
* nacos模块基于mimir-boot-starter-test改造单元测试 ([92ed1fd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/92ed1fdba09cf7c319db86a6feb3f8f5caf9b1c9))
* nacos模块基于mimir-boot-starter-test改造单元测试 ([893a1fd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/893a1fdf8860b1dc9625e707e3cd1fa7ec5eb519))
* starter-test补充单元测试 ([5d98f64](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5d98f64b1a65f69abf1ce070f405ad17107b02e2))
* **web,exception:** 补充 AutoConfiguration 集成测试 ([497954c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/497954c850b9710a36ae0127e31ca7fd1aac7e1e))
* **web,exception:** 补齐 AutoConfiguration 集成测试覆盖 ([9635615](https://github.com/Yggdrasil-Labs/mimir-boot/commit/963561586ae07eda42f779d9055cb1136bfdac01))
* web模块基于mimir-boot-starter-test改造单元测试 ([b85832f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b85832f39008d7fabe1d59cdde36714a61c15a18))
* 优化单元测试的一些告警 ([23d1434](https://github.com/Yggdrasil-Labs/mimir-boot/commit/23d14342d4c3ab0f27c5eb55df6bd0fee30cf348))
* 优化单元测试的一些告警 ([88c637c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/88c637c37b7bd7a964e6d4c330a2f8fdeb90a412))
* 修复失败的单元测试 ([ab7c5e8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ab7c5e8b8d3599a51825ae2e098f89957bc0c957))
* 修改单元测试 ([29bdfc9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/29bdfc92b2c853f3975d4760a178c34cb4b61e6d))
* 简化单元测试 ([447578a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/447578af3450481cdaf314481e5238019063510a))
* 简化断言 ([d6f1b62](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d6f1b6202fa46c0d82c3c288ce8c91614aa4d95e))
* 至少添加一个断言 ([be89b48](https://github.com/Yggdrasil-Labs/mimir-boot/commit/be89b487daf10566805aee4b12d4caeabd98b737))
* 补充common模块的单元测试 ([a7c7d95](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a7c7d95862c0ae88e2e241bcb463345e9d4b9fee))
* 补充common模块的单元测试 ([88ad614](https://github.com/Yggdrasil-Labs/mimir-boot/commit/88ad6148a50c213f498b1af9aa02e4663f6a6fef))
* 补充dubbo、feign模块单元测试 ([445605a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/445605a126080358b9469c1487a7e79843fe47e6))
* 补充dubbo、feign模块单元测试 ([ad7c831](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ad7c831434ef0bc2849b5ec412babb8a592390b6))
* 补充IpUtils的单元测试 ([7363622](https://github.com/Yggdrasil-Labs/mimir-boot/commit/73636224e7e26b0e45e02482eaa3754fdad984f5))
* 补充LogMask单元测试 ([60868cb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/60868cbe3f0604c867596a0c7fa3dc7be0c8d6d5))
* 补充MybatisPlusLoggingConfiguration单元测试 ([52e40af](https://github.com/Yggdrasil-Labs/mimir-boot/commit/52e40af950ca2cdcca9961e02e1d986a74b52b4c))
* 补充New Code 单元测试 ([f347a6a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f347a6a00051863d2efe69b11fe9f96d68cb1eb0))
* 补充New Code 单元测试 ([473328f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/473328fb23ccbd639f55aa7707a4464d8fc308e2))
* 补充processor相关单元测试 ([dab4d7b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/dab4d7b85b865f041df096718c8769c01e518888))
* 补充rpc-core模块单元测试 ([0597625](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0597625d2535ed6314eeb6237222def26864d128))
* 补充testMdcContext的断言，每个单元测试至少要有一个断言 ([6165b0f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6165b0fcf47b7f27c7354304dc96c6bc9b3e2531))
* 补充单元测试 ([61a6e52](https://github.com/Yggdrasil-Labs/mimir-boot/commit/61a6e52598c19339bc183afdf5f1ae7ce7e99fe9))
* 补充单元测试 ([16f1e7c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/16f1e7c4a8e856c7c66177a2c4ec93cfc7c886e1))
* 补充单元测试 ([490d169](https://github.com/Yggdrasil-Labs/mimir-boot/commit/490d169440de97ce35d8401807747706d3380c0b))
* 补充单元测试 ([0d12a83](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0d12a832a2569d759b7b612544a4d0afbb477aab))
* 降低单元测试覆盖率指标到60% ([aec4b68](https://github.com/Yggdrasil-Labs/mimir-boot/commit/aec4b68b01503097346790c939aaab94c7ee5f94))


### 👷 Continuous Integration

* 🐛 Bug: 优化Release与Release Please工作流 ([cb80936](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cb80936b20538a2ea888f4d20f9d0ac816554dc6)), closes [#160](https://github.com/Yggdrasil-Labs/mimir-boot/issues/160)
* bootstrap-sha 指向第一个提交 ([b8fd87c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b8fd87c75829bb08ffb6377f07654682bf175365))
* **ci:** refactor ci action ([9e9c28d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9e9c28d4602eb127949f4a716293400407456ea4))
* **ci:** sonar 关闭ci的自动分析 ([0a30ffb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0a30ffbf65602c19a790a1f67f4c4b7e4c49a3f8))
* **ci:** sonar 启用自动分析 ([b2aed27](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b2aed27ae797d317bdefe360166209748b9577e7))
* **ci:** sonar 问题修复 ([079efbb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/079efbb46145b9c2e22bb4106ba060cf0b988d07))
* **ci:** SonarQube Maven插件自动发现测试报告 ([7b3c73c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7b3c73c97a644988f4d8888acb48664b6b28ef15))
* **ci:** 不让sonarcloud检查覆盖率 ([a08e59c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a08e59ca804b9002c6916e492c85c7af69e1addd))
* **ci:** 修复流水线报错 No plugin found for prefix 'sonar' ([947581a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/947581a08b3c7a6bb72bd59a796ad8f49327b8b3))
* **ci:** 参考官方demo，简化sonar analyze ([394f4f9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/394f4f943919cf4a65134fafe58ed9b6e573b2da))
* **ci:** 在根模块中添加sonar插件 ([6d91f72](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6d91f72d7105db7eb734c21d1bc3051bc91521b1))
* **ci:** 普通PR才进行质量检查 ([69b780e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/69b780e0b8e4543c6a882e43d68270b8de47487f))
* **ci:** 流水线问题修复 ([bbe2dbc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/bbe2dbc8af5dccc177da1bbb751f9085dc3f808e))
* **ci:** 流水线问题修复 ([8a07e99](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8a07e993b5b6f2790b5ea408516911315a2725a7))
* **ci:** 添加 JaCoCo 覆盖率报告路径配置 ([c481f0b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c481f0bc3abce7fcab42bfdde32c84ebc15e815b))
* **ci:** 添加 JaCoCo 覆盖率报告路径配置 ([ee691af](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ee691af3a180b99ee0e5b91475e598e4a91472d8))
* **create-tag:** 创建Tag时优先从.release-please-manifest.json中提取版本号 ([5957c50](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5957c505cf62930cd9fe2e43ea0700b9e4b2f2bb))
* **dependabot:** eviewers 和 assignees 都设置为 YoungerYang-Y ([818ce9b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/818ce9bb7b2c7bf9ebd3b2ef18b78f1dd62c77f8))
* **dependabot:** 每周六进行依赖更新扫描 ([744b9c2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/744b9c221f520d2367e7eba4683acfad13d22602))
* dependabot的PR不需要sonar分析；example跳过maven deploy ([5030ae0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5030ae035667bca2a283cb266beef4bba6602572))
* **deps:** bump actions/checkout from 4 to 5 ([684af77](https://github.com/Yggdrasil-Labs/mimir-boot/commit/684af77aafcdd62d66f9b4f09cc871eebb60a29b))
* **deps:** bump actions/checkout from 5 to 6 ([b721da2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b721da2308d260b4f6facf6e67b01ae67af905b4))
* **deps:** bump actions/checkout from 6.0.1 to 6.0.2 ([2e9483b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2e9483b6f20b93b36b98f3db71208f5113d2b721))
* **deps:** bump actions/checkout from 6.0.2 to 7.0.0 ([b00af14](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b00af14dc710939d96b11e48c890e5f74a1ff848))
* **deps:** bump actions/checkout from 6.0.2 to 7.0.0 ([60d7dcf](https://github.com/Yggdrasil-Labs/mimir-boot/commit/60d7dcf065e27679baf40d9a70f04a7cf206f319))
* **deps:** bump actions/github-script from 7.1.0 to 8.0.0 ([4e4b132](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4e4b13266e3dcc191cbf7b78f23e5e53287bc846))
* **deps:** bump actions/github-script from 8.0.0 to 9.0.0 ([5b1de62](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5b1de62ea7329ca03dc696bd627c5631e39c577a))
* **deps:** bump actions/setup-java from 4.8.0 to 5.1.0 ([6d4c1b6](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6d4c1b697c3812096cd94974c60e7c6c68465a70))
* **deps:** bump actions/setup-java from 5.1.0 to 5.2.0 ([98a9c30](https://github.com/Yggdrasil-Labs/mimir-boot/commit/98a9c302be6e53da284f59d4617aa4bad954f769))
* **deps:** bump actions/setup-java from 5.2.0 to 5.3.0 ([5aabb21](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5aabb2108541cf01210ee8c9af3ed49633b8b69d))
* **deps:** bump actions/setup-java from 5.2.0 to 5.3.0 ([351c67c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/351c67ce955d23bea76c99a5138cb2502340a3d4))
* **deps:** bump actions/upload-artifact from 4.6.2 to 5.0.0 ([94c4478](https://github.com/Yggdrasil-Labs/mimir-boot/commit/94c4478ab904a912b2b7d631db5b67750f1e8b64))
* **deps:** bump actions/upload-artifact from 5.0.0 to 6.0.0 ([ed9228a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ed9228afd292fd4ff1f68de9210e1528499b6686))
* **deps:** bump actions/upload-artifact from 6.0.0 to 7.0.0 ([5fdf19c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5fdf19c54501e4e80b1e62f8446a4d4a6890a7e5))
* **deps:** bump actions/upload-artifact from 7.0.0 to 7.0.1 ([b3cd6bf](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b3cd6bfdb09a632a552e1edcd52abd03a62782f0))
* **deps:** bump googleapis/release-please-action from 4.2.0 to 4.4.0 ([297d496](https://github.com/Yggdrasil-Labs/mimir-boot/commit/297d496d81386fa0a8df37347e568ededf5b8e26))
* **deps:** bump googleapis/release-please-action from 4.4.0 to 4.4.1 ([67c277f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/67c277f05f419a3b9c96669c10c6691f59042a13))
* **deps:** bump googleapis/release-please-action from 4.4.1 to 5.0.0 ([c6dc9a3](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c6dc9a3db6b03a668f37ebeccb95530f994e7862))
* **deps:** bump softprops/action-gh-release from 1 to 3 ([954248b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/954248bb3979f66a78ff2b0ba323f05d4cf20252))
* **deps:** bump softprops/action-gh-release from 3.0.0 to 3.0.1 ([3219168](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3219168192ebd33fdd26b9b6d83e1a4b42338d2e))
* **deps:** bump softprops/action-gh-release from 3.0.0 to 3.0.1 ([d465ffe](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d465ffe32aba19a23adcb016d16b16b9769a1566))
* **deps:** 优化 dependabot 规则，阻止不兼容的 major/minor 升级 ([ba07745](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ba07745cf7430740ea7c824f9481fa853497b01b))
* release please工作流使用预期的labels ([92e3928](https://github.com/Yggdrasil-Labs/mimir-boot/commit/92e3928794eda4ba00d367b87f0c9904428b4c0a))
* **release-please:** bootstrap-sha从v1.0.0开始计算 ([6877d17](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6877d17c98be4440185f9756c4460a56bb9bb590))
* **release-please:** release type使用sample，java在快照版本不会更新CHANGELOG ([39361e7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/39361e766808496345e246da1a0671a5722a0cb7))
* **release-please:** tag格式为v{version} ([0aa19ba](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0aa19ba0ef34f929a10e263ad4855138686463a6))
* **release:** ✨ Feature: Release note 的标题增加图标 ([cf726d1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cf726d1ef7e48dc655b985048bb61f47188684bb)), closes [#155](https://github.com/Yggdrasil-Labs/mimir-boot/issues/155)
* **release:** skip GPG signing during verification and improve key import logging ([18e9711](https://github.com/Yggdrasil-Labs/mimir-boot/commit/18e9711ef4c3f92c34d571ff2de7db5d0f066881))
* **release:** 允许手动触发release时也会执行update-dev-version ([9cac6f8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9cac6f8fa6ea49eba6edddb9d00ee69ddbd88bba))
* **release:** 合并更新dev version 与 更新bootstrap-sha 到同一个任务 ([c626631](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c6266311f4795a8335153443dd7be68a391ad6e7))
* **release:** 将包目标添加到 Maven 部署命令以确保在部署之前构建 JAR ([3776fce](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3776fce04429c9fbe15e581e6dd37c7648944343))
* **release:** 工作流支持手动执行发布 ([0dd2543](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0dd25439f6521047582aae6a5598a8ff86f235b6))
* **release:** 手动发布时使用对应tag版本代码发布 ([91b8553](https://github.com/Yggdrasil-Labs/mimir-boot/commit/91b8553dd28cd12cd4d969fc3af72cf7c21ad2cc))
* **release:** 更改验证步骤以使用包以避免 GPG 签名 ([8d2a70f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8d2a70f8efbdbe7d36a28e2fc7855fe291a84c5c))
* **release:** 更新 Maven 命令以使用 -Dmaven.test.skip=true 跳过测试 ([9925031](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9925031f03cb872642ef98db38ce2736c8dfcae6))
* 修复release please工作流异常 ([11ea2c4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/11ea2c4777b236b62948ab4fd64e46ca6601f713))
* 修复release please的pr合入以后没有自动打tag的问题 ([e407683](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e407683b099e7cb47ca8b116541499940f63943d))
* 修复release工作流权限问题，重置版本 ([94080b1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/94080b1d03c9c02702ce634be3c7f89ee41d57c6))
* 修复安全问题 ([503012f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/503012f50d400595df0b8413d3c2860ed91d4b8b))
* 修复工作流的告警 ([086df70](https://github.com/Yggdrasil-Labs/mimir-boot/commit/086df7080b78a28f4ba98abfb0b195f35cc87e7c))
* 修复工作流问题，重置版本 ([a77ef75](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a77ef75416c8d17409e55e88238a238d007abd1c))
* 修复工作流问题，重置版本 ([f3e0553](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f3e0553ee241b32c1e03cd8fde5d1472228bb085))
* 修复未能触发release的问题，重置版本 ([c1d5994](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c1d5994ad614eaa220c2fe18a93c86e7bbb9b8c0))
* 修复未能触发release的问题，重置版本 ([a2d7aeb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a2d7aeb075d6a880f7689693eac5c15cebd64717))
* 修复未能触发release的问题，重置版本 ([66fa958](https://github.com/Yggdrasil-Labs/mimir-boot/commit/66fa958e847e9af9200715c09d372a8acbfa8e57))
* 修复未能触发release的问题，重置版本 ([a867485](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a867485cf6e2eb8c4f78beb1adf16e5394d5b5ed))
* 修复流水线报错 ([c2a8d9a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c2a8d9aafdd61be4a84344f1af75650c0cc10222))
* 修复流水线报错 ([fa61ca0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fa61ca03537b5e625c70688678a8aad36c515b04))
* 修复流水线报错 ([a24f867](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a24f8671b533da41cf0059b8120c420ce2e95782))
* 修复流水线报错 ([356ccd9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/356ccd96706021f1a3d00b0c80c6df0a970371f8))
* 修复流水线报错 ([7f1b566](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7f1b566df3499f55240c3de3f5147720206bbcb8))
* 修复流水线报错 ([2ed735c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2ed735c49c8596840a9dcd9efa298ddebbe6d990))
* 修复流水线报错 ([2388ac4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2388ac43d5783ee19a1c4e5cc4b4690520d9701d))
* 修复流水线报错 ([0a49f78](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0a49f783171f9197287ad12c6d883911182e3a71))
* 修复版号问题 ([392def0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/392def0d83868db61ab02f7b46db8339c64dcee8))
* 修复版号问题 ([f6302d8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f6302d8a312fe4aa47cba5e1a4ccf67927ae25b7))
* 修改判断逻辑，避免误升级 ([8cb695e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8cb695e3e76d73f2516cf75a3471a9fb031f14b7))
* 删掉没用的配置 ([893ffe7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/893ffe7c5731b33dbe4aa9e34915019b4aa5db52))
* 发布前先构建jar包 ([2ce642e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2ce642ecc72aa6d66b009b788aaab5635fa49b2a))
* 排查example模块，重新发布 ([e6d16fc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e6d16fc0150b782120e15418a129b1253f87d0d9))
* 排除examples模块，不发布 ([9cbe07c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9cbe07ca2fe189f4ebaf694973a5ea5e704f02c3))
* 推送版本tag时，校验，构建，发布到Github Packages，更新版本号 ([8e5daac](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8e5daac6a4c0ca5ea9b6501e3604c51624fa354e))
* 更新版号为0.0.0以反映开发状态 ([3fc2e18](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3fc2e18909f63b3690166b0c51cdd622c22b83a9))
* 检测 Release PR合并后创建Tag ([8c7571f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8c7571f170095e26087e11aebb507a6169bf2a47))
* 添加mvnw的执行权限 ([3460a00](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3460a00bbcb7f48942778742439859c8db4bb931))
* 添加正确的label ([5b042ec](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5b042ecc8c909825d567e538d2f0f9dec75186e2))
* 版号从1.0.0开始，发布后，通过update-dev-version工作流更新pom中的版号为开发版本 ([77e662c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/77e662c619cf9a7459667f6187393b02fd6b7e12))
* 自动发布后自动更新bootstrap-sha指向最新的版本hash ([f213f76](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f213f76c6542e511ce3aed6a3fa867bcdca5214a))
* 补充发布到GitHub Packages ([2ce2b6e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2ce2b6e82e3231619b67cbbd491a922ddb2285bf))
* 解决工作流的安全告警 ([8cd3ad2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8cd3ad213c555b403cf7e0a0d719e59d562041a1))
* 解决工作流的安全告警 ([02b32d7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/02b32d7c96a3d084be10ae048b0213374330bb56))
* 解决工作流的安全告警 ([b23955a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b23955a6f65efd2d9581ef42085254b998931d84))
* 解决工作流的安全告警 ([9ed224c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9ed224cbed21f98cdf38ee1922d8f3c2e3dadef2))
* 解决工作流的安全告警 ([334b6c9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/334b6c9e29c3c7b435175e94d5def54dce9c573a))
* 解决工作流的安全告警 ([ade07f7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ade07f71efda75e960aff45ad4a22191833e1c2f))
* 让release-please自动计算下一个版号 ([a58e3fa](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a58e3fa756329f15893b89c80738e6ab3cd45846))
* 调整工作流 ([a4b89a5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a4b89a506d18bbb76e2b5ac0112e8f04439b4b3a))
* 调整工作流，重置版本 ([bb798ec](https://github.com/Yggdrasil-Labs/mimir-boot/commit/bb798ec61f7cf54f253676b764c1bfbff9c69ac4))
* 调整流水线更新代码版号的逻辑，重置版本 ([46aa29c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/46aa29ca4721405d22a667267f28a772d4ea2225))
* 重新指定版本计算的commit hash ([8b8abd4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8b8abd48dc12c25391c39833312fbd02e0be886f))


### 🔧 Miscellaneous Chores

* add CI profile to skip GPG signing in CI/CD environment ([e27d3d7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e27d3d7a95265178e62b55e5f74dc6e88dee365c))
* add project URLs to POM files and configure distribution management for Maven Central ([53f154a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/53f154a5541ce31805ab720533793ebe95f5a3e9))
* bom管理mybaits、web、nacos等依赖版本 ([46e957b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/46e957b15438d26c5406f5667be7502fb79b8115))
* **build:** 变更 Maven groupId 为 io.github.yggdrasil-labs ([db8ee4f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/db8ee4fde6f6c09379cb496190f127ad6acb5bd8))
* bump version to 1.0.1-SNAPSHOT for next development cycle ([62a801e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/62a801e55264207ff3a9c29e894125f2105408ba))
* bump version to 1.0.1-SNAPSHOT for next development cycle ([c46173f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c46173fdd0a37e709357d1bf2a5a173bdf61ecb3))
* bump version to 1.0.1-SNAPSHOT for next development cycle ([367fc21](https://github.com/Yggdrasil-Labs/mimir-boot/commit/367fc21b72227f534f7ea09f6e6a8a268f551e69))
* bump version to 1.0.1-SNAPSHOT for next development cycle ([2045067](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2045067480461d4127444b374488acbaee693fe9))
* bump version to 1.0.1-SNAPSHOT for next development cycle ([66c501f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/66c501f830e2ba345ffb20894aaa72c32a141a84))
* bump version to 1.0.2-SNAPSHOT for next development cycle ([7a26eb6](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7a26eb6f54c02ceaba6a0e7689c6a7c179e8a21e))
* bump version to 1.1.1-SNAPSHOT for next development cycle ([a2885af](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a2885affbbc13374c7ec483a9beed384530b330e))
* bump version to 1.2.1-SNAPSHOT for next development cycle ([a508c54](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a508c5402d4309b6eab9de390f48175dbd27e9c8))
* bump version to 1.4.1-SNAPSHOT for next development cycle ([c91ea96](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c91ea96ab7fbfcb356a1598c351c6b9849d62ada))
* bump version to 1.4.2-SNAPSHOT for next development cycle ([c01abcb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c01abcbf87d0ef31d0b2a985bf5c0a02757e3a6c))
* bump version to 1.4.3-SNAPSHOT for next development cycle ([11af2c5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/11af2c5f1a95df2ffc380634d24d42d43f48796e))
* bump version to 1.5.1-SNAPSHOT for next development cycle ([6cf8623](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6cf8623c3f76ca43449b9ddeeed688101cd531c1))
* bump version to 1.6.1-SNAPSHOT for next development cycle ([14225af](https://github.com/Yggdrasil-Labs/mimir-boot/commit/14225af1ab43c26579d758f71bda15a90a03777f))
* bump version to 2.0.1-SNAPSHOT for next development cycle ([1b47530](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1b47530a1a4f4f4417f473e16faf01ea32c3cf11))
* bump version to 2.0.2-SNAPSHOT for next development cycle ([a6dd244](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a6dd2442cb60cc10d9e17aec54212b29136f24aa))
* bump version to 2.0.5-SNAPSHOT for next development cycle ([dbe8f50](https://github.com/Yggdrasil-Labs/mimir-boot/commit/dbe8f50febc144f55af2029d3197e25b6047eaae))
* **ci:** 统一 workflow action 版本注释 ([8b6b569](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8b6b569f33c8a88cc1d7903dd463f431e4ae2144))
* **ci:** 调整dependabot的行为，commit前缀与labels约束 ([6b41897](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6b41897062679eb85ec962b55918cba9fd637dd5))
* **dependabot:** 限制机器人不要更新spring的大版本 ([62ce59a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/62ce59a3892bd0c9f1f2ecc306845ba406b597cb))
* **deps-dev:** bump org.springframework.boot:spring-boot-maven-plugin ([4590954](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4590954004cd67775123a952367757b39af21743))
* **deps-dev:** bump org.springframework.boot:spring-boot-maven-plugin from 3.5.7 to 3.5.15 in the maven-plugins group ([de370b4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/de370b4f38b694814aaccddce25130aa8057e99b))
* **deps:** bump ch.qos.logback:logback-classic from 1.4.14 to 1.5.20 ([d024122](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d02412246d94088d0ff6d58d21103158b7c28efd))
* **deps:** bump cn.hutool:hutool-all in the utilities group ([5db1486](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5db1486af645e161bbfa22d58e81423b9e078589))
* **deps:** bump com.alibaba.fastjson2:fastjson2 from 2.0.54 to 2.0.59 ([534b325](https://github.com/Yggdrasil-Labs/mimir-boot/commit/534b325e93242c14d93e0e4b503ab1c585fd4348))
* **deps:** bump com.alibaba.fastjson2:fastjson2 in the utilities group ([90e7ebb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/90e7ebb54cb0b9eda38c836a62567a432394071e))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([fd7e736](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fd7e736ff996e83889c606865b7201a606e9c8cc))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([c247705](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c24770597b148e06388db0cb46aa3c18f317f773))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([33b543e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/33b543e5e52fdb5db40323a1bec08cd14aad8672))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([4339fb9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4339fb93eac9543864e26b4c97b81f4f8078d9f1))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([d5a6dae](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d5a6dae8ddc0c2e440deb3d6b109e2c334ad58eb))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([70f4280](https://github.com/Yggdrasil-Labs/mimir-boot/commit/70f4280923c3952d7f40f0afed64d9ddd48d50df))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin from 3.5.1 to 3.7.0 ([33363be](https://github.com/Yggdrasil-Labs/mimir-boot/commit/33363be874826cbfc1441a9baee4804e447cf6f4))
* **deps:** bump com.fasterxml.jackson.core:jackson-databind ([5e0dec2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5e0dec211c5a46bd838db26c1f0ea98b99c62a3f))
* **deps:** bump com.google.guava:guava from 33.3.1-jre to 33.5.0-jre ([1f3be7d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1f3be7d793eaecf6890fd461539de6240be3a2ce))
* **deps:** bump com.google.guava:guava from 33.5.0-jre to 33.6.0-jre ([4928729](https://github.com/Yggdrasil-Labs/mimir-boot/commit/492872903f3e97ab07bd58de3a75982dec251756))
* **deps:** bump com.google.protobuf:protobuf-java ([39e070a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/39e070ab6d73e675e23c71a8cea2da652cca5531))
* **deps:** bump com.google.protobuf:protobuf-java ([e1007fe](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e1007fe80891b7b8ec28b9b706996d55a6dae660))
* **deps:** bump com.google.protobuf:protobuf-java ([00e7062](https://github.com/Yggdrasil-Labs/mimir-boot/commit/00e70629096f60eef899ec4aa81b1f501d9042b9))
* **deps:** bump com.google.protobuf:protobuf-java ([cc9754f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cc9754ff5ed122df4095b3bbc335ac9baf04d604))
* **deps:** bump com.google.protobuf:protobuf-java ([87a462c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/87a462c83f36b15355bfc4bc31ba4201cab5f124))
* **deps:** bump com.google.protobuf:protobuf-java ([b9a3904](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b9a390464d8a1c3dc98f3335281a6e6297310781))
* **deps:** bump com.google.protobuf:protobuf-java ([d0a16fd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d0a16fd6bf04bb34afa01e45cf00623ef0ebdf24))
* **deps:** bump com.google.protobuf:protobuf-java from 4.34.1 to 4.35.1 ([8596394](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8596394b8fa0c5dd847a7894531beab57fb3914a))
* **deps:** bump com.itextpdf:itext-core from 8.0.2 to 8.0.5 ([775eb62](https://github.com/Yggdrasil-Labs/mimir-boot/commit/775eb6210a88ac546cbf720f8574fd5ec6a36be9))
* **deps:** bump com.itextpdf:itext-core from 8.0.2 to 8.0.5 ([d227692](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d22769263036dc17dd418f99ed56df1ca4a70a50))
* **deps:** bump com.mysql:mysql-connector-j in /mimir-boot-bom ([aca32d0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/aca32d05c2f45f8205bc07641c5ede6c3baa7b14))
* **deps:** bump com.squareup.okhttp3:okhttp from 4.12.0 to 5.2.1 ([2fb1cd2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2fb1cd2a264d9a9f0a529c244aa0a8ba59d63043))
* **deps:** bump com.squareup.okhttp3:okhttp from 5.2.1 to 5.3.1 ([c0e9044](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c0e90445e2827e20cd234c7bb68f0e8606086e0d))
* **deps:** bump com.squareup.okhttp3:okhttp from 5.3.1 to 5.3.2 ([dff4636](https://github.com/Yggdrasil-Labs/mimir-boot/commit/dff4636eed1b7a6877cf3652c28f2dcf20b424fd))
* **deps:** bump com.squareup.okhttp3:okhttp from 5.3.2 to 5.4.0 ([37d6f9c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/37d6f9cb1767a90e5e78e4f1d0aee36b0e33c9de))
* **deps:** bump com.squareup.okhttp3:okhttp from 5.3.2 to 5.4.0 ([1d5ac3b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1d5ac3be3294695e860be0b89ce49c40e9159004))
* **deps:** bump com.squareup.retrofit2:retrofit from 2.11.0 to 3.0.0 ([0a8967e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0a8967e74c04f83948a59798254a45ec1a845f46))
* **deps:** bump com.xuxueli:xxl-job-core from 3.4.0 to 3.4.2 in the scheduling group ([9ddbcfe](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9ddbcfe7765dfa4e46cf3560dbac24c5e3043be9))
* **deps:** bump com.xuxueli:xxl-job-core in /mimir-boot-bom ([7fec278](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7fec278ceb09761374a2d23a204a98eb2e3e25f4))
* **deps:** bump com.xuxueli:xxl-job-core in the scheduling group ([837a6a4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/837a6a423772eec0bb8b1d718f76e2a6c6c3ac94))
* **deps:** bump com.xuxueli:xxl-job-core in the scheduling group ([fc4a385](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fc4a3855d21f0abd0272387e7dc6fb130e83acbf))
* **deps:** bump com.xuxueli:xxl-job-core in the scheduling group ([56be46e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/56be46e3d0ce741ca9648e4fbc83fc196d8e01fe))
* **deps:** bump com.xuxueli:xxl-job-core in the scheduling group ([b8de5d8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b8de5d8c0c1dba2c53f1f6596b5dbf47d5aa685b))
* **deps:** bump com.zaxxer:HikariCP from 5.1.0 to 7.0.2 ([f8e2b85](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f8e2b851569496c1b21fbc34d48f34be374d702f))
* **deps:** bump org.apache.commons:commons-lang3 from 3.19.0 to 3.20.0 ([16a77dd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/16a77dd69c8d5db31108f976d09fb64d9ca8e41c))
* **deps:** bump org.apache.commons:commons-lang3 in /mimir-boot-bom ([94a4bea](https://github.com/Yggdrasil-Labs/mimir-boot/commit/94a4bea7d70420af62f17d8ca2829acb21460a7e))
* **deps:** bump org.apache.commons:commons-lang3 in /mimir-boot-bom ([3e66104](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3e6610447a5693dcab862f40886e396fb272cf34))
* **deps:** bump org.apache.curator:curator-framework ([f5e80f9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f5e80f95364d79da28987967cb022fb36e350343))
* **deps:** bump org.apache.maven.plugins:maven-compiler-plugin ([abb0957](https://github.com/Yggdrasil-Labs/mimir-boot/commit/abb0957c86babc168e6bff9e17d31371dbc38d17))
* **deps:** bump org.apache.maven.plugins:maven-compiler-plugin ([e304f74](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e304f742e539e795523aef93214e820ecb1969e5))
* **deps:** bump org.apache.poi:poi from 5.2.5 to 5.4.1 ([d415337](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d4153374da4da02456fa01fdc70c287571b09cf6))
* **deps:** bump org.apache.poi:poi from 5.4.1 to 5.5.0 ([9b3a263](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9b3a2631e7426fc8f17844d6aa16cb26234ae571))
* **deps:** bump org.apache.poi:poi from 5.5.0 to 5.5.1 ([cea5928](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cea5928dc1a8bfba251102ec884c2f0a3800243d))
* **deps:** bump org.codehaus.mojo:versions-maven-plugin ([6c8a71b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6c8a71bfdc035b8169b42acecc1287910984de04))
* **deps:** bump org.codehaus.mojo:versions-maven-plugin ([4904a1f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4904a1f6903abf629d8736eff245e217f7e9d2d4))
* **deps:** bump org.jacoco:jacoco-maven-plugin from 0.8.12 to 0.8.14 ([9ee7b56](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9ee7b567a399a30592a36cbfcf3d14a62d7a1947))
* **deps:** bump org.mybatis:mybatis from 3.5.16 to 3.5.19 ([8660bc8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8660bc850063cb9e2f99ef53dc840a71368a984e))
* **deps:** bump org.postgresql:postgresql from 42.7.4 to 42.7.8 ([dc3b3c8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/dc3b3c82abf53e82ea7ea1f98be8fad06e85a372))
* **deps:** bump org.projectlombok:lombok in the utilities group ([648ccdd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/648ccdd9dcaa1a651e0cc003597a89bd4cddfe27))
* **deps:** bump org.redisson:redisson-spring-boot-starter ([20f095b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/20f095b2cd837cee8ee835aa3da661f270daaa4a))
* **deps:** bump org.sonarsource.scanner.maven:sonar-maven-plugin ([e27a109](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e27a1093580357f4ed4f0d7e53133cc3821c5aa4))
* **deps:** bump org.sonarsource.scanner.maven:sonar-maven-plugin ([75f1e9e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/75f1e9eaa14c743f52795df5d5cef76e3b114ed5))
* **deps:** bump org.sonarsource.scanner.maven:sonar-maven-plugin ([940b890](https://github.com/Yggdrasil-Labs/mimir-boot/commit/940b890519fc41da53f00973c9ae786e9c9fdf7c))
* **deps:** bump org.sonatype.central:central-publishing-maven-plugin ([04a1b90](https://github.com/Yggdrasil-Labs/mimir-boot/commit/04a1b90b3ce168b06d8bb9da20dd026ebc0a3c76))
* **deps:** bump org.sonatype.central:central-publishing-maven-plugin from 0.10.0 to 0.11.0 ([7bc0e00](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7bc0e002b47b22acf1c722dbe716b2c3d313ccad))
* **deps:** bump org.springdoc:springdoc-openapi-starter-webmvc-ui ([8c34357](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8c34357889c413dede81a49b5d900945494aeeee))
* **deps:** bump org.springdoc:springdoc-openapi-starter-webmvc-ui from 2.8.13 to 2.8.17 ([31c9aae](https://github.com/Yggdrasil-Labs/mimir-boot/commit/31c9aae47e441163d42140d38d6f9adae5f7be84))
* **deps:** bump org.springframework.boot:spring-boot-starter-data-jpa ([89b9891](https://github.com/Yggdrasil-Labs/mimir-boot/commit/89b98916499178965ee6046073aff1b3d7e9c5d6))
* **deps:** bump org.springframework.boot:spring-boot-starter-data-redis ([6fafee3](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6fafee31a11eef7f0f55d50c8d3bf9f8b4fb738c))
* **deps:** bump org.springframework.boot:spring-boot-starter-mail ([4a28004](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4a280048ee88167df7c226c570b8951e8be6fac4))
* **deps:** bump org.springframework.boot:spring-boot-starter-validation ([bd1a170](https://github.com/Yggdrasil-Labs/mimir-boot/commit/bd1a17002dd3eecdad66ea2f19fb436aeb77d6ce))
* **deps:** bump org.springframework.security:spring-security-core ([7fa6384](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7fa638414db59c778cbf2bc36301b9a728221f75))
* **deps:** bump slf4j.version from 2.0.16 to 2.0.17 ([bab9627](https://github.com/Yggdrasil-Labs/mimir-boot/commit/bab9627a3256fe4a8ff281535505482f10863ce5))
* **deps:** bump slf4j.version from 2.0.17 to 2.0.18 ([ad5b0c9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ad5b0c91fa466ba78ca710a37fff657aebe51913))
* **deps:** bump slf4j.version from 2.0.17 to 2.0.18 ([cd764ea](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cd764ea0d6761cf168891ddbdae67dac40e64845))
* **deps:** bump testcontainers.version from 1.20.5 to 1.21.3 ([0a675be](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0a675bec99ca18587eb8410a29705e2e54aa00bd))
* **deps:** bump the caching group with 2 updates ([e443ab5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e443ab5bdcc47fe6d16be3ff9beb01bf37b410b4))
* **deps:** bump the caching group with 2 updates ([5817a96](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5817a9644a563a89edd07111004c88f3db76211c))
* **deps:** bump the database group with 2 updates ([3546a68](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3546a68b22cf9100fdc2eadf7d4f9ba23135b2f0))
* **deps:** bump the maven-plugins group across 1 directory with 2 updates ([4bfa5de](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4bfa5de0744183b687ed26777bed923646304ff7))
* **deps:** bump the maven-plugins group across 1 directory with 3 updates ([9437da9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9437da962a0e8cb4e6d0663cd8898abf0d1b2bb0))
* **deps:** bump the maven-plugins group across 1 directory with 6 updates ([06abdcb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/06abdcb89643f3e01719088eac0fc9f44a32be61))
* **deps:** bump the maven-plugins group across 1 directory with 6 updates ([7989cc1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7989cc16ab695bb5e25836506c67d21f5a81b1ca))
* **deps:** bump the maven-plugins group with 13 updates ([56bf65c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/56bf65c58972ba31f4f30f3e1bc54c0a9fb3f865))
* **deps:** bump the maven-plugins group with 4 updates ([d813c29](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d813c29af8a0d50cb93b2c5f1eb5e8725b500308))
* **deps:** bump the testing group with 4 updates ([4041d6c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4041d6c6d8ab88847ca2320061097dff772dfa96))
* **deps:** bump the utilities group across 1 directory with 2 updates ([34e013e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/34e013e128bbc27b13398f5159128fbd7fbc0a8e))
* **deps:** bump the utilities group across 1 directory with 2 updates ([fbd59b9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fbd59b98548a49adae89977eeb1aa84ceacabc21))
* **deps:** bump the utilities group with 2 updates ([27fe840](https://github.com/Yggdrasil-Labs/mimir-boot/commit/27fe8406b7a3fc50c736add16222e46fc3081b58))
* **deps:** sync dependabot.yml ([591dc2b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/591dc2be4a82486d2ccd08f182f102045889a18c))
* **deps:** 使用官方mybatis-plus-bom管理mybatis相关依赖 ([8bd6002](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8bd600227761f231dc5bb46efda3f981ea5f6645))
* **deps:** 使用更符合Java项目的dependabot ([6d54de1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6d54de13f9198eef6878c0b3b16642cdaf785bd6))
* **deps:** 删除一些不需要的依赖声明 ([da1f0dc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/da1f0dc16663c7349393e502cf78c84eccd677e2))
* **deps:** 部分依赖升级 ([aa3bbc6](https://github.com/Yggdrasil-Labs/mimir-boot/commit/aa3bbc6b41fee4726908ad9b57798a5ae0816ba5))
* enhance CI configuration to ensure GPG signing is skipped during artifact verification ([80069bc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/80069bc0bea09abc24edc285aaaba2c64780eb09))
* **main:** release 1.4.0 ([b526ddd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b526ddd57189e37db2c77e8490e3fcbf55d7a26b))
* **main:** release 1.4.1 ([da0f8ca](https://github.com/Yggdrasil-Labs/mimir-boot/commit/da0f8ca5b352e13b6b31e16cea5ce2c4be8cab49))
* **main:** release 1.4.2 ([99a8a9e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/99a8a9e828ff769211960191d33136d714c54062))
* **main:** release 1.5.0 ([a2ae31d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a2ae31dd86528dbf5702c95f215debecfb2aca50))
* **main:** release 1.6.0 ([d97be4c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d97be4cd38d7b5f49f891b11a60baec0452a84c1))
* **main:** release 2.0.0 ([9bbc6d1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9bbc6d1942bfc570c30a67a86e72d771aad7faa0))
* **main:** release 2.0.1 ([42e3f4d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/42e3f4d2150dfe6ef2f8a69577668a6674ad1656))
* **main:** release 2.0.2 ([f1bd57a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f1bd57a3d1fb2c9b1367ef77c8cc93c84df0bcc1))
* **main:** release 2.0.3 ([1b47714](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1b47714ee5d40004ec6dae8a9619919b254d6fde))
* **main:** release 2.0.4 ([affa496](https://github.com/Yggdrasil-Labs/mimir-boot/commit/affa496c17dfa9d7f1751513701bfccb6d1bd47a))
* **main:** release 2.1.0 ([1d3531e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1d3531ef8c1a4a828d7bf4a527ac53a3c6225183))
* **main:** release mimir-boot 1.0.0 ([ab320a5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ab320a51e0ea97decdfc8c5f6e4ff764f606d9f2))
* **main:** release mimir-boot 1.0.0 ([7a695f8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7a695f89ba1a856fe8e5b0ef40643e050ae2641f))
* **main:** release mimir-boot 1.0.0 ([3d20699](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3d20699d0f11287d09be2ad551ec45b8f41cc7a1))
* **main:** release mimir-boot 1.0.0 ([6de41b9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6de41b92c04e12e4605f100c9d4fbadc5df39d12))
* **main:** release mimir-boot 1.0.0 ([c604ace](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c604ace78ab9f1568b96d36d06b801f12de8d798))
* **main:** release mimir-boot 1.0.0 ([3d99d20](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3d99d205b71e0fb5cadc439aeeb0901b04ba3579))
* **main:** release mimir-boot 1.0.0 ([bcd8b7b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/bcd8b7bfc919c296a29d5f172a6cbdd695ab8be6))
* **main:** release mimir-boot 1.0.0 ([6ec3a9f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6ec3a9fc6fac47d4f41179c69f5985e28e011e9d))
* **main:** release mimir-boot 1.0.0 ([7b036ab](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7b036abc68b5fec1a7c2353778cad4b68a2ac702))
* **main:** release mimir-boot 1.0.0 ([4d50390](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4d50390562de126b5723ea65b2edcb45fd3ace04))
* **main:** release mimir-boot 1.0.0 ([10aa32c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/10aa32c4c2ea6c05fd2272206d2c25eea5905c26))
* **main:** release mimir-boot 1.0.0 ([9d960a4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9d960a445acef556322f6df188f4b68b1f6e8feb))
* **main:** release mimir-boot 1.0.0 ([7d8c256](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7d8c256bb4c0d41bb9d31dd448f00f6e39bc7774))
* **main:** release mimir-boot 1.0.0 ([cb0827d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cb0827d920a4181ac407a2edbb6286044ce3c803))
* **main:** release mimir-boot 1.0.0 ([5a248ea](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5a248eafb32f76e7356bd241ecd178ebc2cb9044))
* **main:** release mimir-boot 1.0.0 ([d213ea1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d213ea101963da6019543bd9a66abfbc32c13e1e))
* **main:** release mimir-boot 1.0.0 ([f4396ba](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f4396bae98b07fd828a006577c4247d76217bbb0))
* **main:** release mimir-boot 1.1.0 ([a56594c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a56594cec7af3934d30d6ad28b768e245aafcd67))
* **main:** release mimir-boot 1.1.0 ([b54a651](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b54a6512680c5fa493b2e8e11822e0b17a946cf9))
* **main:** release mimir-boot 1.2.0 ([afa1f11](https://github.com/Yggdrasil-Labs/mimir-boot/commit/afa1f1139fcfb28bab5417701c71f27c100a0e9f))
* **main:** release mimir-boot 1.2.0 ([f2249c6](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f2249c60b5440d99a7ff2ae9cd5253991b9aa9b8))
* **main:** release mimir-boot 1.2.1 ([03b5cc5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/03b5cc592a500f3f42da037262b41b575f56fea6))
* **main:** release mimir-boot 1.3.0 ([785ad28](https://github.com/Yggdrasil-Labs/mimir-boot/commit/785ad282af39e65e4ad6d3e6fb0360ce0608511e))
* maven profiles的最佳实践 ([e090cc1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e090cc134a092b9398e417a90ab732e7fce0b318))
* **pom:** 优化Maven配置，继承parent的子项目默认不发布到Maven Central ([612d1c2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/612d1c200fd5056882bc153cba9ff0528ea59cb8))
* **pom:** 调整镜像仓库配置，去掉在spring boot dependencies中已管理的依赖 ([026afb8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/026afb8e66f3743514287833f4f844edb1fceb2e))
* update .gitignore to include AI-related directories ([ea81f6c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ea81f6c11c77cd8b05feb81af32b09343ee98a82))
* update bootstrap-sha to 03b5cc5 after release ([4d7afb2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4d7afb28cbe84c3656e83fc350102e1d3169c1b6))
* update Maven configuration for Central publishing and add source/javadoc plugins ([3be7290](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3be72904d8f63c8b371011545988ef7c182d3e18))
* update revision to 1.0.0 for release ([3111142](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3111142d9d62d3ea72dc5c9e43a98b79c8481f0a))
* update revision to 1.0.0 for release ([b279485](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b279485d35d2181456d7d2e26f9787da5a8eec6a))
* update revision to 1.0.0 for release ([4a80768](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4a807685b77531ea95f249f7b924d86d10521450))
* update revision to 1.0.0 for release ([c28fdd9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c28fdd96a1c19d2d2bc1cf2c58831bf1ab6bed34))
* 优化通过CI发布到Maven的工作流 ([c127237](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c12723753d847447da937a54529615f7cf981303))
* 使parent默认不发布，避免其他项目继承时会尝试发布到Maven Central ([7dd8fff](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7dd8fffd7316dc8ee87b94b0e49c74e9bdc5108d))
* 使用JDK17，与配套的Spring版本 ([ebdf5fe](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ebdf5fe4c38d9e02df1aad19131aa6bde7e9ec5c))
* 修改工作流顺序；修改版号 ([4ff3a54](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4ff3a54938cd3fb591be4359fd9674c7b68d8c4d))
* 回滚版号，重新发布 ([84c7d38](https://github.com/Yggdrasil-Labs/mimir-boot/commit/84c7d3811be373df8d87ec6f7dbce79cba5a5878))
* 回滚版本相关修改，准备重新发布 ([a7baa75](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a7baa751f126a2a52afcfafd5bca423315793074))
* 增加ci、release工作流 ([19fdaec](https://github.com/Yggdrasil-Labs/mimir-boot/commit/19fdaec1248795010f21ad7024c3dad04c191ce4))
* 支持发布到Maven central ([9374793](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9374793303728c623e79e2d0b7bb477414c5871f))
* 更新开发版本号 ([42384d3](https://github.com/Yggdrasil-Labs/mimir-boot/commit/42384d35ac79ede082a64eb22b3d2362810c0faa))
* 更新项目配置和依赖，清理一些没用的依赖 ([0f5dba4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0f5dba4927c698db3f9b72712d78b7be631e5ff6))
* 清理CHANGELOG.md并更新release-please配置中的bootstrap-sha ([3e86bc7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3e86bc72dfb16fbeb09a5b381885fe65242ab312))
* 清理examples相关内容 ([ffca964](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ffca96466694529346ab255c60570109f393022b))
* 移除.idea目录 ([69b1ecf](https://github.com/Yggdrasil-Labs/mimir-boot/commit/69b1ecfba6f5e1cf99a1ff561687a6b126d0a977))
* 移除examples模块 ([4e74ec0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4e74ec0405404dbb588cc5b9f6c97daa9bf8c7ac))
* 移除不必要的仓库设置，如果有需要，应该通过setting.xml设置 ([b5a9ec4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b5a9ec4fd483543bd8351bdcc89fc5dd6b94bb82))
* 编译时格式化，统一使用AOSP风格 ([d4a83d2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d4a83d29c96fb0b04d5c19cc56b5d5751c961ef7))
* 解决发布后的jar包中版本好未替换的问题 ([fa768e2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fa768e25910e8dd63c957bc88dedce27862fb27b))
* 调整log配置文件 ([2804a32](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2804a32df72608f5b9737dae09bf8131f04a3f1b))
* 配置 Maven GPG 插件以默认跳过签名 ([7f4423c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7f4423cea1705dd65baca2677fd23a5314f0a7f0))
* 重置版本号 ([b73f82b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b73f82bd37acff7560511ba2ef0702b1a8e0d878))
* 重置版本号 ([bd864ba](https://github.com/Yggdrasil-Labs/mimir-boot/commit/bd864bad2abf9b2a1fbb1c55614f9311f2cfc58f))
* 预发布1.0.0版本 ([c38dfc8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c38dfc892f0c9029a4f6862ed84efb3ed5b5f005))


### 💄 Code Style

* **docs:** 修复全项目 markdown lint 错误并配置 markdownlint-cli2 ([3273c8b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3273c8b4e59e0c5f311b136bc40781bc1f221973))

## [2.1.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v2.0.4...v2.1.0) (2026-06-28)


### ✨ Features

* **ci:** 添加 markdownlint 配置文件 ([2b16f67](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2b16f67a6329194bf87f03d40e83a0ba64387ecd))
* **exception:** 异常处理器支持响应格式适配 ([6fe0b10](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6fe0b1079e95ca73ba8463514a2ed88ff18543b7))


### 🐛 Bug Fixes

* **ci:** 修复 markdownlint-cli2-action SHA 引用无效 ([3f62332](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3f6233261a52b485abe43562d5a3f1c9af9acf7b))
* **ci:** 修复 SonarCloud 覆盖率上报，合并 build 为单步 verify，添加 markdownlint ([641b9b9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/641b9b9b1e712f0a2a45f7dc1b8a81f9471f2310))
* **nacos:** 修复 ConfigCryptoUtilsTest.testDecryptWithWrongKey 测试不稳定 ([c36ae93](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c36ae930902044154a29283f53ac534762f07779))
* **pom:** 引入test starter后，可以直接使用常用测试能力。close: [#226](https://github.com/Yggdrasil-Labs/mimir-boot/issues/226) ([20f033a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/20f033ad0267a5fc046bb1784a0aef28d99ff3d3))


### 📝 Documentation

* **common:** 补齐 ErrorCode 枚举 Javadoc ([5720240](https://github.com/Yggdrasil-Labs/mimir-boot/commit/57202405d362d2656b430f42f3a2482dbbd9c446))
* **init:** 按 docs-init skill 最佳实践重建文档体系 ([ec0861a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ec0861a01d289c7bdbb9e9f499aa389957fede32))
* **plan:** 新增 quality-refinement 迭代方案 ([dfb2d38](https://github.com/Yggdrasil-Labs/mimir-boot/commit/dfb2d382de0c707bc4374797d791e3bebf147d1e))
* 归档 exception-handler-adapter，清理技术债 ([fde926d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fde926d0f4223487b4b4c7760b936fb3c4e63560))
* 新增文档体系，包含 AGENTS、ARCHITECTURE、DESIGN、PLANS 等文件，重构文档结构以提升可导航性和可维护性 ([25e3563](https://github.com/Yggdrasil-Labs/mimir-boot/commit/25e3563905b50ab39e774a99a46a68856594e724))
* 清理 README 中不存在的规划模块，改为未来方向段落 ([f9fa42e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f9fa42ec0adbfde69480ab05eb69f17dd6931138))
* 精简根 README 特性展示段，去重代码示例 ([a6988c0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a6988c0923eb93b3a221eec84498db701a627011))


### ✅ Tests

* **exception:** 补齐 MimirExceptionHandler fallback 和 null-safe 分支覆盖 ([7728000](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7728000721f6c519fb8e3107148ae27e56f8c433))
* **web,exception:** 补充 AutoConfiguration 集成测试 ([497954c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/497954c850b9710a36ae0127e31ca7fd1aac7e1e))
* **web,exception:** 补齐 AutoConfiguration 集成测试覆盖 ([9635615](https://github.com/Yggdrasil-Labs/mimir-boot/commit/963561586ae07eda42f779d9055cb1136bfdac01))


### 👷 Continuous Integration

* **deps:** bump actions/checkout from 6.0.2 to 7.0.0 ([b00af14](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b00af14dc710939d96b11e48c890e5f74a1ff848))
* **deps:** bump actions/checkout from 6.0.2 to 7.0.0 ([60d7dcf](https://github.com/Yggdrasil-Labs/mimir-boot/commit/60d7dcf065e27679baf40d9a70f04a7cf206f319))
* **deps:** bump actions/github-script from 8.0.0 to 9.0.0 ([5b1de62](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5b1de62ea7329ca03dc696bd627c5631e39c577a))
* **deps:** bump actions/setup-java from 5.2.0 to 5.3.0 ([5aabb21](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5aabb2108541cf01210ee8c9af3ed49633b8b69d))
* **deps:** bump actions/setup-java from 5.2.0 to 5.3.0 ([351c67c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/351c67ce955d23bea76c99a5138cb2502340a3d4))
* **deps:** bump actions/upload-artifact from 6.0.0 to 7.0.0 ([5fdf19c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5fdf19c54501e4e80b1e62f8446a4d4a6890a7e5))
* **deps:** bump actions/upload-artifact from 7.0.0 to 7.0.1 ([b3cd6bf](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b3cd6bfdb09a632a552e1edcd52abd03a62782f0))
* **deps:** bump googleapis/release-please-action from 4.4.0 to 4.4.1 ([67c277f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/67c277f05f419a3b9c96669c10c6691f59042a13))
* **deps:** bump googleapis/release-please-action from 4.4.1 to 5.0.0 ([c6dc9a3](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c6dc9a3db6b03a668f37ebeccb95530f994e7862))
* **deps:** bump softprops/action-gh-release from 1 to 3 ([954248b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/954248bb3979f66a78ff2b0ba323f05d4cf20252))
* **deps:** bump softprops/action-gh-release from 3.0.0 to 3.0.1 ([3219168](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3219168192ebd33fdd26b9b6d83e1a4b42338d2e))
* **deps:** bump softprops/action-gh-release from 3.0.0 to 3.0.1 ([d465ffe](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d465ffe32aba19a23adcb016d16b16b9769a1566))
* **deps:** 优化 dependabot 规则，阻止不兼容的 major/minor 升级 ([ba07745](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ba07745cf7430740ea7c824f9481fa853497b01b))


### 🔧 Miscellaneous Chores

* bump version to 2.0.5-SNAPSHOT for next development cycle ([dbe8f50](https://github.com/Yggdrasil-Labs/mimir-boot/commit/dbe8f50febc144f55af2029d3197e25b6047eaae))
* **ci:** 统一 workflow action 版本注释 ([8b6b569](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8b6b569f33c8a88cc1d7903dd463f431e4ae2144))
* **deps-dev:** bump org.springframework.boot:spring-boot-maven-plugin ([4590954](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4590954004cd67775123a952367757b39af21743))
* **deps-dev:** bump org.springframework.boot:spring-boot-maven-plugin from 3.5.7 to 3.5.15 in the maven-plugins group ([de370b4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/de370b4f38b694814aaccddce25130aa8057e99b))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([fd7e736](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fd7e736ff996e83889c606865b7201a606e9c8cc))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([c247705](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c24770597b148e06388db0cb46aa3c18f317f773))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([33b543e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/33b543e5e52fdb5db40323a1bec08cd14aad8672))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin from 3.5.1 to 3.7.0 ([33363be](https://github.com/Yggdrasil-Labs/mimir-boot/commit/33363be874826cbfc1441a9baee4804e447cf6f4))
* **deps:** bump com.google.guava:guava from 33.5.0-jre to 33.6.0-jre ([4928729](https://github.com/Yggdrasil-Labs/mimir-boot/commit/492872903f3e97ab07bd58de3a75982dec251756))
* **deps:** bump com.google.protobuf:protobuf-java ([39e070a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/39e070ab6d73e675e23c71a8cea2da652cca5531))
* **deps:** bump com.google.protobuf:protobuf-java ([e1007fe](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e1007fe80891b7b8ec28b9b706996d55a6dae660))
* **deps:** bump com.google.protobuf:protobuf-java from 4.34.1 to 4.35.1 ([8596394](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8596394b8fa0c5dd847a7894531beab57fb3914a))
* **deps:** bump com.itextpdf:itext-core from 8.0.2 to 8.0.5 ([775eb62](https://github.com/Yggdrasil-Labs/mimir-boot/commit/775eb6210a88ac546cbf720f8574fd5ec6a36be9))
* **deps:** bump com.itextpdf:itext-core from 8.0.2 to 8.0.5 ([d227692](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d22769263036dc17dd418f99ed56df1ca4a70a50))
* **deps:** bump com.squareup.okhttp3:okhttp from 5.3.2 to 5.4.0 ([37d6f9c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/37d6f9cb1767a90e5e78e4f1d0aee36b0e33c9de))
* **deps:** bump com.squareup.okhttp3:okhttp from 5.3.2 to 5.4.0 ([1d5ac3b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1d5ac3be3294695e860be0b89ce49c40e9159004))
* **deps:** bump com.xuxueli:xxl-job-core from 3.4.0 to 3.4.2 in the scheduling group ([9ddbcfe](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9ddbcfe7765dfa4e46cf3560dbac24c5e3043be9))
* **deps:** bump com.xuxueli:xxl-job-core in the scheduling group ([837a6a4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/837a6a423772eec0bb8b1d718f76e2a6c6c3ac94))
* **deps:** bump com.xuxueli:xxl-job-core in the scheduling group ([fc4a385](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fc4a3855d21f0abd0272387e7dc6fb130e83acbf))
* **deps:** bump org.projectlombok:lombok in the utilities group ([648ccdd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/648ccdd9dcaa1a651e0cc003597a89bd4cddfe27))
* **deps:** bump org.sonarsource.scanner.maven:sonar-maven-plugin ([e27a109](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e27a1093580357f4ed4f0d7e53133cc3821c5aa4))
* **deps:** bump org.sonatype.central:central-publishing-maven-plugin ([04a1b90](https://github.com/Yggdrasil-Labs/mimir-boot/commit/04a1b90b3ce168b06d8bb9da20dd026ebc0a3c76))
* **deps:** bump org.sonatype.central:central-publishing-maven-plugin from 0.10.0 to 0.11.0 ([7bc0e00](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7bc0e002b47b22acf1c722dbe716b2c3d313ccad))
* **deps:** bump org.springdoc:springdoc-openapi-starter-webmvc-ui ([8c34357](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8c34357889c413dede81a49b5d900945494aeeee))
* **deps:** bump org.springdoc:springdoc-openapi-starter-webmvc-ui from 2.8.13 to 2.8.17 ([31c9aae](https://github.com/Yggdrasil-Labs/mimir-boot/commit/31c9aae47e441163d42140d38d6f9adae5f7be84))
* **deps:** bump slf4j.version from 2.0.17 to 2.0.18 ([ad5b0c9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ad5b0c91fa466ba78ca710a37fff657aebe51913))
* **deps:** bump slf4j.version from 2.0.17 to 2.0.18 ([cd764ea](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cd764ea0d6761cf168891ddbdae67dac40e64845))
* **deps:** bump the caching group with 2 updates ([e443ab5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e443ab5bdcc47fe6d16be3ff9beb01bf37b410b4))
* **deps:** bump the caching group with 2 updates ([5817a96](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5817a9644a563a89edd07111004c88f3db76211c))
* **deps:** bump the maven-plugins group across 1 directory with 6 updates ([06abdcb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/06abdcb89643f3e01719088eac0fc9f44a32be61))
* **deps:** bump the maven-plugins group across 1 directory with 6 updates ([7989cc1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7989cc16ab695bb5e25836506c67d21f5a81b1ca))
* **deps:** bump the utilities group across 1 directory with 2 updates ([34e013e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/34e013e128bbc27b13398f5159128fbd7fbc0a8e))
* **deps:** bump the utilities group across 1 directory with 2 updates ([fbd59b9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/fbd59b98548a49adae89977eeb1aa84ceacabc21))


### 💄 Code Style

* **docs:** 修复全项目 markdown lint 错误并配置 markdownlint-cli2 ([3273c8b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3273c8b4e59e0c5f311b136bc40781bc1f221973))

## [2.0.4](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v2.0.3...v2.0.4) (2026-03-23)


### ✅ Tests

* **IpUtils:** 为标头方法添加空值和奇数长度检查 ([4a92fec](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4a92fec5d0f19f2c3fe4eeb9058c3135ab293963))


### 👷 Continuous Integration

* **deps:** bump actions/checkout from 6.0.1 to 6.0.2 ([2e9483b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2e9483b6f20b93b36b98f3db71208f5113d2b721))
* **deps:** bump actions/setup-java from 5.1.0 to 5.2.0 ([98a9c30](https://github.com/Yggdrasil-Labs/mimir-boot/commit/98a9c302be6e53da284f59d4617aa4bad954f769))
* **release:** 允许手动触发release时也会执行update-dev-version ([9cac6f8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9cac6f8fa6ea49eba6edddb9d00ee69ddbd88bba))
* **release:** 将包目标添加到 Maven 部署命令以确保在部署之前构建 JAR ([3776fce](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3776fce04429c9fbe15e581e6dd37c7648944343))
* **release:** 更新 Maven 命令以使用 -Dmaven.test.skip=true 跳过测试 ([9925031](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9925031f03cb872642ef98db38ce2736c8dfcae6))


### 🔧 Miscellaneous Chores

* **deps:** bump com.alibaba.fastjson2:fastjson2 in the utilities group ([90e7ebb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/90e7ebb54cb0b9eda38c836a62567a432394071e))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([4339fb9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4339fb93eac9543864e26b4c97b81f4f8078d9f1))
* **deps:** bump com.google.protobuf:protobuf-java ([00e7062](https://github.com/Yggdrasil-Labs/mimir-boot/commit/00e70629096f60eef899ec4aa81b1f501d9042b9))
* **deps:** bump com.google.protobuf:protobuf-java ([cc9754f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cc9754ff5ed122df4095b3bbc335ac9baf04d604))
* **deps:** bump org.apache.maven.plugins:maven-compiler-plugin ([abb0957](https://github.com/Yggdrasil-Labs/mimir-boot/commit/abb0957c86babc168e6bff9e17d31371dbc38d17))
* **deps:** bump the maven-plugins group across 1 directory with 3 updates ([9437da9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9437da962a0e8cb4e6d0663cd8898abf0d1b2bb0))
* **deps:** bump the utilities group with 2 updates ([27fe840](https://github.com/Yggdrasil-Labs/mimir-boot/commit/27fe8406b7a3fc50c736add16222e46fc3081b58))
* **pom:** 优化Maven配置，继承parent的子项目默认不发布到Maven Central ([612d1c2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/612d1c200fd5056882bc153cba9ff0528ea59cb8))

## [2.0.3](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v2.0.2...v2.0.3) (2026-01-25)


### 👷 Continuous Integration

* **release:** skip GPG signing during verification and improve key import logging ([18e9711](https://github.com/Yggdrasil-Labs/mimir-boot/commit/18e9711ef4c3f92c34d571ff2de7db5d0f066881))
* **release:** 工作流支持手动执行发布 ([0dd2543](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0dd25439f6521047582aae6a5598a8ff86f235b6))
* **release:** 手动发布时使用对应tag版本代码发布 ([91b8553](https://github.com/Yggdrasil-Labs/mimir-boot/commit/91b8553dd28cd12cd4d969fc3af72cf7c21ad2cc))
* **release:** 更改验证步骤以使用包以避免 GPG 签名 ([8d2a70f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8d2a70f8efbdbe7d36a28e2fc7855fe291a84c5c))


### 🔧 Miscellaneous Chores

* 使parent默认不发布，避免其他项目继承时会尝试发布到Maven Central ([7dd8fff](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7dd8fffd7316dc8ee87b94b0e49c74e9bdc5108d))
* 修改工作流顺序；修改版号 ([4ff3a54](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4ff3a54938cd3fb591be4359fd9674c7b68d8c4d))

## [2.0.2](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v2.0.1...v2.0.2) (2026-01-24)


### 🔧 Miscellaneous Chores

* add CI profile to skip GPG signing in CI/CD environment ([e27d3d7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e27d3d7a95265178e62b55e5f74dc6e88dee365c))
* add project URLs to POM files and configure distribution management for Maven Central ([53f154a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/53f154a5541ce31805ab720533793ebe95f5a3e9))
* bump version to 2.0.2-SNAPSHOT for next development cycle ([a6dd244](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a6dd2442cb60cc10d9e17aec54212b29136f24aa))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([d5a6dae](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d5a6dae8ddc0c2e440deb3d6b109e2c334ad58eb))
* **deps:** bump the maven-plugins group with 4 updates ([d813c29](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d813c29af8a0d50cb93b2c5f1eb5e8725b500308))
* enhance CI configuration to ensure GPG signing is skipped during artifact verification ([80069bc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/80069bc0bea09abc24edc285aaaba2c64780eb09))
* update Maven configuration for Central publishing and add source/javadoc plugins ([3be7290](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3be72904d8f63c8b371011545988ef7c182d3e18))
* 优化通过CI发布到Maven的工作流 ([c127237](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c12723753d847447da937a54529615f7cf981303))
* 移除不必要的仓库设置，如果有需要，应该通过setting.xml设置 ([b5a9ec4](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b5a9ec4fd483543bd8351bdcc89fc5dd6b94bb82))

## [2.0.1](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v2.0.0...v2.0.1) (2026-01-20)


### 🐛 Bug Fixes

* Avoid expanding secrets in a run block. ([94d7cda](https://github.com/Yggdrasil-Labs/mimir-boot/commit/94d7cda551cf72b83c9291e6d9b9be050bf125ae))


### 🔧 Miscellaneous Chores

* bump version to 2.0.1-SNAPSHOT for next development cycle ([1b47530](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1b47530a1a4f4f4417f473e16faf01ea32c3cf11))
* 支持发布到Maven central ([9374793](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9374793303728c623e79e2d0b7bb477414c5871f))
* 配置 Maven GPG 插件以默认跳过签名 ([7f4423c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7f4423cea1705dd65baca2677fd23a5314f0a7f0))

## [2.0.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v1.6.0...v2.0.0) (2026-01-17)


### ⚠ BREAKING CHANGES

* **build:** 所有 Maven 模块的 groupId 从 com.yggdrasil.labs 变更为 io.github.yggdrasil-labs。使用旧 groupId 的项目需要更新依赖引用。已发布的版本保留旧 groupId，新版本使用新 groupId。

### 🔧 Miscellaneous Chores

* **build:** 变更 Maven groupId 为 io.github.yggdrasil-labs ([db8ee4f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/db8ee4fde6f6c09379cb496190f127ad6acb5bd8))
* bump version to 1.6.1-SNAPSHOT for next development cycle ([14225af](https://github.com/Yggdrasil-Labs/mimir-boot/commit/14225af1ab43c26579d758f71bda15a90a03777f))
* **deps:** 部分依赖升级 ([aa3bbc6](https://github.com/Yggdrasil-Labs/mimir-boot/commit/aa3bbc6b41fee4726908ad9b57798a5ae0816ba5))

## [1.6.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v1.5.0...v1.6.0) (2025-12-27)


### ✨ Features

* 新增 RPC治理模块 ([251dd38](https://github.com/Yggdrasil-Labs/mimir-boot/commit/251dd38bac6cca305a215346e9c99e42cb3c284b))


### 🐛 Bug Fixes

* 使用动词原形而不是过去式 ([b82dc0b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b82dc0b72533c5d8fe119bb039f868984a41126c))
* 修复sonar cloud issue ([cf7fc2d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cf7fc2daf827075be9853d4dfea66f9d98ba11a3))


### ✅ Tests

* 补充dubbo、feign模块单元测试 ([445605a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/445605a126080358b9469c1487a7e79843fe47e6))
* 补充dubbo、feign模块单元测试 ([ad7c831](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ad7c831434ef0bc2849b5ec412babb8a592390b6))
* 补充rpc-core模块单元测试 ([0597625](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0597625d2535ed6314eeb6237222def26864d128))
* 降低单元测试覆盖率指标到60% ([aec4b68](https://github.com/Yggdrasil-Labs/mimir-boot/commit/aec4b68b01503097346790c939aaab94c7ee5f94))


### 🔧 Miscellaneous Chores

* bump version to 1.5.1-SNAPSHOT for next development cycle ([6cf8623](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6cf8623c3f76ca43449b9ddeeed688101cd531c1))

## [1.5.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v1.4.2...v1.5.0) (2025-12-20)


### ✨ Features

* 加密功能默认关闭，支持参数控制显示开启 ([f07a48d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f07a48de851a2025b734626854cbdd98f68c0bf4))


### 🐛 Bug Fixes

* spotless apply应该在maven的process-sources阶段 ([85020be](https://github.com/Yggdrasil-Labs/mimir-boot/commit/85020bef008461f556a2df68159094f34b779264))
* SQL日志打印的参数是密文 ([4f84584](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4f8458459cf70a20cc8bc4dd4d8c3653d5d2576e))


### 🔧 Miscellaneous Chores

* bump version to 1.4.3-SNAPSHOT for next development cycle ([11af2c5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/11af2c5f1a95df2ffc380634d24d42d43f48796e))
* **deps:** bump the testing group with 4 updates ([4041d6c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4041d6c6d8ab88847ca2320061097dff772dfa96))
* 调整log配置文件 ([2804a32](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2804a32df72608f5b9737dae09bf8131f04a3f1b))

## [1.4.2](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v1.4.1...v1.4.2) (2025-12-15)


### 🐛 Bug Fixes

* 修改access.log慢接口为3000ms ([504d8a0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/504d8a0550d3580ba5727a6a749bcaa0fa826bb1))
* 修改在mask复杂对象时堆栈溢出的问题 ([6b209ce](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6b209ce1344020536e22646c7db2791f749ea73e))
* 取消自动注册为全局处理器，只有显式使用才行 ([aa03d2d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/aa03d2d6ecd7126fd00be195e0fb7cf58438bc8e))
* 复杂对象导致堆栈溢出的问题 ([1582782](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1582782aa9b9ba47176140e0db8bbc5c4de1c296))


### ♻️ Code Refactoring

* 调整日志级别为DEBUG ([b51b16d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b51b16d539bc743a63e546e6fa06af10d032eb87))


### 👷 Continuous Integration

* **deps:** bump actions/upload-artifact from 5.0.0 to 6.0.0 ([ed9228a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ed9228afd292fd4ff1f68de9210e1528499b6686))
* **deps:** bump googleapis/release-please-action from 4.2.0 to 4.4.0 ([297d496](https://github.com/Yggdrasil-Labs/mimir-boot/commit/297d496d81386fa0a8df37347e568ededf5b8e26))
* 修改判断逻辑，避免误升级 ([8cb695e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8cb695e3e76d73f2516cf75a3471a9fb031f14b7))


### 🔧 Miscellaneous Chores

* bump version to 1.4.2-SNAPSHOT for next development cycle ([c01abcb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c01abcbf87d0ef31d0b2a985bf5c0a02757e3a6c))

## [1.4.1](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v1.4.0...v1.4.1) (2025-12-14)


### 👷 Continuous Integration

* 补充发布到GitHub Packages ([2ce2b6e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2ce2b6e82e3231619b67cbbd491a922ddb2285bf))


### 🔧 Miscellaneous Chores

* bump version to 1.4.1-SNAPSHOT for next development cycle ([c91ea96](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c91ea96ab7fbfcb356a1598c351c6b9849d62ada))

## [1.4.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/v1.3.0...v1.4.0) (2025-12-14)


### ✨ Features

* access.log支持指定路径不记录访问日志，减少日志杂音 ([7b4d8a9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7b4d8a9cbec4a7b4e1d8ff61da1ed1d528ed888e))
* 默认mybatis使用slf4j，不使用StdOutImpl ([194095c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/194095c47f7cbf1c0858122a4ad6cf89e97cad20))


### 👷 Continuous Integration

* 🐛 Bug: 优化Release与Release Please工作流 ([cb80936](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cb80936b20538a2ea888f4d20f9d0ac816554dc6)), closes [#160](https://github.com/Yggdrasil-Labs/mimir-boot/issues/160)


### 🔧 Miscellaneous Chores

* 更新开发版本号 ([42384d3](https://github.com/Yggdrasil-Labs/mimir-boot/commit/42384d35ac79ede082a64eb22b3d2362810c0faa))

## [1.3.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/mimir-boot-v1.2.1...mimir-boot-v1.3.0) (2025-12-13)


### ✨ Features

* 增加校验依赖 ([8a5a110](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8a5a110ad7cd7cb1def8f107e8ce90ce2fadce47))


### 🐛 Bug Fixes

* 修复access.log不打印的问题 ([36a26cb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/36a26cb5ec9109e03ae9c908816329219448b3c9))
* 修复sql json不生效的问题 ([e31e17a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e31e17a41813ec36e27637f7e92cfee966b439b4))


### ♻️ Code Refactoring

* 使用lambda表达式 ([e84c444](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e84c4449dda786ab3d8ad8c1316f2d73bf80f12f))


### 👷 Continuous Integration

* **deps:** bump actions/github-script from 7.1.0 to 8.0.0 ([4e4b132](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4e4b13266e3dcc191cbf7b78f23e5e53287bc846))
* **deps:** bump actions/setup-java from 4.8.0 to 5.1.0 ([6d4c1b6](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6d4c1b697c3812096cd94974c60e7c6c68465a70))
* **deps:** bump actions/upload-artifact from 4.6.2 to 5.0.0 ([94c4478](https://github.com/Yggdrasil-Labs/mimir-boot/commit/94c4478ab904a912b2b7d631db5b67750f1e8b64))
* **release:** ✨ Feature: Release note 的标题增加图标 ([cf726d1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cf726d1ef7e48dc655b985048bb61f47188684bb)), closes [#155](https://github.com/Yggdrasil-Labs/mimir-boot/issues/155)
* **release:** 合并更新dev version 与 更新bootstrap-sha 到同一个任务 ([c626631](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c6266311f4795a8335153443dd7be68a391ad6e7))
* 删掉没用的配置 ([893ffe7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/893ffe7c5731b33dbe4aa9e34915019b4aa5db52))
* 解决工作流的安全告警 ([8cd3ad2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8cd3ad213c555b403cf7e0a0d719e59d562041a1))
* 解决工作流的安全告警 ([02b32d7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/02b32d7c96a3d084be10ae048b0213374330bb56))
* 解决工作流的安全告警 ([b23955a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b23955a6f65efd2d9581ef42085254b998931d84))
* 解决工作流的安全告警 ([9ed224c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9ed224cbed21f98cdf38ee1922d8f3c2e3dadef2))
* 解决工作流的安全告警 ([334b6c9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/334b6c9e29c3c7b435175e94d5def54dce9c573a))
* 解决工作流的安全告警 ([ade07f7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ade07f71efda75e960aff45ad4a22191833e1c2f))


### 🔧 Miscellaneous Chores

* **deps:** bump cn.hutool:hutool-all in the utilities group ([5db1486](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5db1486af645e161bbfa22d58e81423b9e078589))
* **deps:** bump com.google.protobuf:protobuf-java ([87a462c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/87a462c83f36b15355bfc4bc31ba4201cab5f124))
* **deps:** bump com.xuxueli:xxl-job-core in the scheduling group ([56be46e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/56be46e3d0ce741ca9648e4fbc83fc196d8e01fe))
* **deps:** bump org.apache.poi:poi from 5.5.0 to 5.5.1 ([cea5928](https://github.com/Yggdrasil-Labs/mimir-boot/commit/cea5928dc1a8bfba251102ec884c2f0a3800243d))
* **deps:** bump org.sonarsource.scanner.maven:sonar-maven-plugin ([75f1e9e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/75f1e9eaa14c743f52795df5d5cef76e3b114ed5))
* **deps:** bump the database group with 2 updates ([3546a68](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3546a68b22cf9100fdc2eadf7d4f9ba23135b2f0))
* update bootstrap-sha to 03b5cc5 after release ([4d7afb2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4d7afb28cbe84c3656e83fc350102e1d3169c1b6))
* 编译时格式化，统一使用AOSP风格 ([d4a83d2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/d4a83d29c96fb0b04d5c19cc56b5d5751c961ef7))

## [1.2.1](https://github.com/Yggdrasil-Labs/mimir-boot/compare/mimir-boot-v1.2.0...mimir-boot-v1.2.1) (2025-11-29)


### Bug Fixes

* 修复未加载分页插件问题 ([f84df1c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f84df1cb56764d0cc28db572899ded69112f10d4))
* 加载顺序问题导致MybatisPlusAutoConfiguration未能正确加载；修复部分配置前缀错误 ([f842b17](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f842b17df20df148c31dcc33c50f8188910cccb5))


### Documentation

* 增加dubbo、feign相关的模块规划 ([8f0c255](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8f0c255f2a4a55cefd7a97eb603159f8a1e28e21))
* 更换徽章 ([814ee77](https://github.com/Yggdrasil-Labs/mimir-boot/commit/814ee77ca87c61dfe51c965410bd37d5a212c06e))


### Tests

* 补充New Code 单元测试 ([f347a6a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f347a6a00051863d2efe69b11fe9f96d68cb1eb0))
* 补充New Code 单元测试 ([473328f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/473328fb23ccbd639f55aa7707a4464d8fc308e2))


### Continuous Integration

* **ci:** 不让sonarcloud检查覆盖率 ([a08e59c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a08e59ca804b9002c6916e492c85c7af69e1addd))
* **ci:** 修复流水线报错 No plugin found for prefix 'sonar' ([947581a](https://github.com/Yggdrasil-Labs/mimir-boot/commit/947581a08b3c7a6bb72bd59a796ad8f49327b8b3))
* **ci:** 在根模块中添加sonar插件 ([6d91f72](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6d91f72d7105db7eb734c21d1bc3051bc91521b1))
* **ci:** 流水线问题修复 ([bbe2dbc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/bbe2dbc8af5dccc177da1bbb751f9085dc3f808e))
* **ci:** 流水线问题修复 ([8a07e99](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8a07e993b5b6f2790b5ea408516911315a2725a7))
* **ci:** 添加 JaCoCo 覆盖率报告路径配置 ([c481f0b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c481f0bc3abce7fcab42bfdde32c84ebc15e815b))
* **ci:** 添加 JaCoCo 覆盖率报告路径配置 ([ee691af](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ee691af3a180b99ee0e5b91475e598e4a91472d8))
* **dependabot:** eviewers 和 assignees 都设置为 YoungerYang-Y ([818ce9b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/818ce9bb7b2c7bf9ebd3b2ef18b78f1dd62c77f8))
* **deps:** bump actions/checkout from 5 to 6 ([b721da2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b721da2308d260b4f6facf6e67b01ae67af905b4))
* 自动发布后自动更新bootstrap-sha指向最新的版本hash ([f213f76](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f213f76c6542e511ce3aed6a3fa867bcdca5214a))


### Miscellaneous Chores

* bump version to 1.2.1-SNAPSHOT for next development cycle ([a508c54](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a508c5402d4309b6eab9de390f48175dbd27e9c8))
* **deps:** bump com.xuxueli:xxl-job-core in the scheduling group ([b8de5d8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b8de5d8c0c1dba2c53f1f6596b5dbf47d5aa685b))
* **deps:** bump org.codehaus.mojo:versions-maven-plugin ([6c8a71b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6c8a71bfdc035b8169b42acecc1287910984de04))
* **deps:** bump org.sonarsource.scanner.maven:sonar-maven-plugin ([940b890](https://github.com/Yggdrasil-Labs/mimir-boot/commit/940b890519fc41da53f00973c9ae786e9c9fdf7c))
* update .gitignore to include AI-related directories ([ea81f6c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ea81f6c11c77cd8b05feb81af32b09343ee98a82))

## [1.2.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/mimir-boot-v1.1.0...mimir-boot-v1.2.0) (2025-11-22)


### Features

* starter-test模块的能力增强 ([9a174ec](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9a174ecbd4a66adcb342bb7411d2f0b740b01387))
* 增加starter-test，提供统一的测试依赖管理、测试基类、测试工具 ([4e5c513](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4e5c5131ab4cffeb28d45f1e2390a7c08ccacb3f))
* 如果实体类是包含DO后缀的，Mapper、Service的名字要去除DO ([3b036fc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3b036fc7a1db98c443b0ddf604335d156bd3ef3c))
* 支持默认扫描com.yggdrasil.labs.**.mapper包下的Mapper ([09e93f3](https://github.com/Yggdrasil-Labs/mimir-boot/commit/09e93f3fce6712ad80f2db4a6612da7b9fbcfbee))
* 生成的Mapper加上@Mapper注解，非com.yggdrasil.labs.**.mapper目录也能自动扫描 ([c2d1780](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c2d1780205e149cfc892ddcdb8023a3322647ec2))
* 默认扫描包 com.yggdrasil.labs.**.mapper ([96304b0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/96304b05341e1f0a89435cead4ff5189ebb6013f))


### Bug Fixes

* Sonar找不到单元测试报告 ([7ccbbb8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7ccbbb8fca5a2353d9a263f70114eaa6ce6cffe2))
* Sonar找不到单元测试报告 ([1b67c5b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1b67c5b9d7608df031f477a651feac374fa6f3ef))
* Springboot 3.X 应该使用mybatis-plus-spring-boot3-starter ([c0d1cda](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c0d1cda203a346b6aa684716b7aed9e5a6f3df35))
* 压缩日志统一放在“年-月”目录下归档 ([e7e8de8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e7e8de818cd7a0668559962e7f4bbdd3bc4c70ea))
* 统一参数前缀为mimir.boot ([1073b25](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1073b2546d0ba6768c6d083fe1f56155d812184c))


### Documentation

* 给每个模块完善README ([aeba7a8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/aeba7a8e7cb792b6743e3ee204ba2fcf332d3a8c))
* 给每个模块补充description ([4896bad](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4896bad7969f2a691a4c60e1485511f9eb94b184))
* 给每个模块补充description ([786057e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/786057e2b58f530bb50e7d1004aab55f03caced1))
* 补充mimir-boot-parent的README ([9c3a65d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9c3a65d9681f77fc7e1935ee2b6113a5f4155585))


### Code Refactoring

* 仅用于测试，忽略这些问题 ([825d0a0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/825d0a028180515ae09e0285de89548f2bf0019e))
* 关闭automaticAnalysis ([328dc45](https://github.com/Yggdrasil-Labs/mimir-boot/commit/328dc45e0adf1257e999b9d309ebd24c0498ec26))
* 开启ci automaticAnalysis ([2634f7c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2634f7cbfad2e12287673a86be4a140950af7f7b))


### Tests

* exception模块基于mimir-boot-starter-test改造单元测试 ([f094f9c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f094f9c94ce579a431ce0cee6bb58bb8d2daaebd))
* log模块基于mimir-boot-starter-test改造单元测试 ([da405a5](https://github.com/Yggdrasil-Labs/mimir-boot/commit/da405a583176335ded0fb6e53f27683af445e56d))
* mybatis-processor模块基于mimir-boot-starter-test改造单元测试 ([5404f00](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5404f00bd8f1052616fe040c20a4bf1ac4cd04de))
* mybatis模块基于mimir-boot-starter-test改造单元测试 ([ada6490](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ada6490ab047fb334046027d710274949299f020))
* nacos模块基于mimir-boot-starter-test改造单元测试 ([92ed1fd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/92ed1fdba09cf7c319db86a6feb3f8f5caf9b1c9))
* nacos模块基于mimir-boot-starter-test改造单元测试 ([893a1fd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/893a1fdf8860b1dc9625e707e3cd1fa7ec5eb519))
* starter-test补充单元测试 ([5d98f64](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5d98f64b1a65f69abf1ce070f405ad17107b02e2))
* web模块基于mimir-boot-starter-test改造单元测试 ([b85832f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b85832f39008d7fabe1d59cdde36714a61c15a18))


### Continuous Integration

* bootstrap-sha 指向第一个提交 ([b8fd87c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b8fd87c75829bb08ffb6377f07654682bf175365))
* **ci:** refactor ci action ([9e9c28d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9e9c28d4602eb127949f4a716293400407456ea4))
* **ci:** sonar 关闭ci的自动分析 ([0a30ffb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0a30ffbf65602c19a790a1f67f4c4b7e4c49a3f8))
* **ci:** sonar 启用自动分析 ([b2aed27](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b2aed27ae797d317bdefe360166209748b9577e7))
* **ci:** sonar 问题修复 ([079efbb](https://github.com/Yggdrasil-Labs/mimir-boot/commit/079efbb46145b9c2e22bb4106ba060cf0b988d07))
* **ci:** SonarQube Maven插件自动发现测试报告 ([7b3c73c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7b3c73c97a644988f4d8888acb48664b6b28ef15))
* **ci:** 参考官方demo，简化sonar analyze ([394f4f9](https://github.com/Yggdrasil-Labs/mimir-boot/commit/394f4f943919cf4a65134fafe58ed9b6e573b2da))
* **ci:** 普通PR才进行质量检查 ([69b780e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/69b780e0b8e4543c6a882e43d68270b8de47487f))
* **create-tag:** 创建Tag时优先从.release-please-manifest.json中提取版本号 ([5957c50](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5957c505cf62930cd9fe2e43ea0700b9e4b2f2bb))
* **dependabot:** 每周六进行依赖更新扫描 ([744b9c2](https://github.com/Yggdrasil-Labs/mimir-boot/commit/744b9c221f520d2367e7eba4683acfad13d22602))
* dependabot的PR不需要sonar分析；example跳过maven deploy ([5030ae0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5030ae035667bca2a283cb266beef4bba6602572))
* **deps:** bump actions/checkout from 4 to 5 ([684af77](https://github.com/Yggdrasil-Labs/mimir-boot/commit/684af77aafcdd62d66f9b4f09cc871eebb60a29b))
* **release-please:** bootstrap-sha从v1.0.0开始计算 ([6877d17](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6877d17c98be4440185f9756c4460a56bb9bb590))
* **release-please:** release type使用sample，java在快照版本不会更新CHANGELOG ([39361e7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/39361e766808496345e246da1a0671a5722a0cb7))
* **release-please:** tag格式为v{version} ([0aa19ba](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0aa19ba0ef34f929a10e263ad4855138686463a6))
* 让release-please自动计算下一个版号 ([a58e3fa](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a58e3fa756329f15893b89c80738e6ab3cd45846))


### Miscellaneous Chores

* bom管理mybaits、web、nacos等依赖版本 ([46e957b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/46e957b15438d26c5406f5667be7502fb79b8115))
* bump version to 1.0.1-SNAPSHOT for next development cycle ([62a801e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/62a801e55264207ff3a9c29e894125f2105408ba))
* bump version to 1.0.2-SNAPSHOT for next development cycle ([7a26eb6](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7a26eb6f54c02ceaba6a0e7689c6a7c179e8a21e))
* bump version to 1.1.1-SNAPSHOT for next development cycle ([a2885af](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a2885affbbc13374c7ec483a9beed384530b330e))
* **deps:** bump com.diffplug.spotless:spotless-maven-plugin ([70f4280](https://github.com/Yggdrasil-Labs/mimir-boot/commit/70f4280923c3952d7f40f0afed64d9ddd48d50df))
* **deps:** bump com.google.protobuf:protobuf-java ([b9a3904](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b9a390464d8a1c3dc98f3335281a6e6297310781))
* **deps:** bump com.squareup.okhttp3:okhttp from 5.2.1 to 5.3.1 ([c0e9044](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c0e90445e2827e20cd234c7bb68f0e8606086e0d))
* **deps:** bump com.squareup.okhttp3:okhttp from 5.3.1 to 5.3.2 ([dff4636](https://github.com/Yggdrasil-Labs/mimir-boot/commit/dff4636eed1b7a6877cf3652c28f2dcf20b424fd))
* **deps:** bump org.apache.commons:commons-lang3 from 3.19.0 to 3.20.0 ([16a77dd](https://github.com/Yggdrasil-Labs/mimir-boot/commit/16a77dd69c8d5db31108f976d09fb64d9ca8e41c))
* **deps:** bump org.apache.poi:poi from 5.4.1 to 5.5.0 ([9b3a263](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9b3a2631e7426fc8f17844d6aa16cb26234ae571))
* **deps:** bump the maven-plugins group across 1 directory with 2 updates ([4bfa5de](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4bfa5de0744183b687ed26777bed923646304ff7))
* **deps:** 使用官方mybatis-plus-bom管理mybatis相关依赖 ([8bd6002](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8bd600227761f231dc5bb46efda3f981ea5f6645))
* **main:** release mimir-boot 1.1.0 ([a56594c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a56594cec7af3934d30d6ad28b768e245aafcd67))
* **main:** release mimir-boot 1.1.0 ([b54a651](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b54a6512680c5fa493b2e8e11822e0b17a946cf9))
* **main:** release mimir-boot 1.2.0 ([f2249c6](https://github.com/Yggdrasil-Labs/mimir-boot/commit/f2249c60b5440d99a7ff2ae9cd5253991b9aa9b8))
* maven profiles的最佳实践 ([e090cc1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e090cc134a092b9398e417a90ab732e7fce0b318))
* 回滚版本相关修改，准备重新发布 ([a7baa75](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a7baa751f126a2a52afcfafd5bca423315793074))
* 清理examples相关内容 ([ffca964](https://github.com/Yggdrasil-Labs/mimir-boot/commit/ffca96466694529346ab255c60570109f393022b))
* 移除examples模块 ([4e74ec0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/4e74ec0405404dbb588cc5b9f6c97daa9bf8c7ac))

## [1.1.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/mimir-boot-v1.0.0...mimir-boot-v1.1.0) (2025-11-16)


### Features

* 如果实体类是包含DO后缀的，Mapper、Service的名字要去除DO ([3b036fc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3b036fc7a1db98c443b0ddf604335d156bd3ef3c))
* 支持默认扫描com.yggdrasil.labs.**.mapper包下的Mapper ([09e93f3](https://github.com/Yggdrasil-Labs/mimir-boot/commit/09e93f3fce6712ad80f2db4a6612da7b9fbcfbee))
* 生成的Mapper加上@Mapper注解，非com.yggdrasil.labs.**.mapper目录也能自动扫描 ([c2d1780](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c2d1780205e149cfc892ddcdb8023a3322647ec2))
* 默认扫描包 com.yggdrasil.labs.**.mapper ([96304b0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/96304b05341e1f0a89435cead4ff5189ebb6013f))


### Bug Fixes

* Springboot 3.X 应该使用mybatis-plus-spring-boot3-starter ([c0d1cda](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c0d1cda203a346b6aa684716b7aed9e5a6f3df35))
* 压缩日志统一放在“年-月”目录下归档 ([e7e8de8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e7e8de818cd7a0668559962e7f4bbdd3bc4c70ea))
* 统一参数前缀为mimir.boot ([1073b25](https://github.com/Yggdrasil-Labs/mimir-boot/commit/1073b2546d0ba6768c6d083fe1f56155d812184c))


### Documentation

* 补充mimir-boot-parent的README ([9c3a65d](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9c3a65d9681f77fc7e1935ee2b6113a5f4155585))


### Continuous Integration

* bootstrap-sha 指向第一个提交 ([b8fd87c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b8fd87c75829bb08ffb6377f07654682bf175365))
* **create-tag:** 创建Tag时优先从.release-please-manifest.json中提取版本号 ([5957c50](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5957c505cf62930cd9fe2e43ea0700b9e4b2f2bb))
* dependabot的PR不需要sonar分析；example跳过maven deploy ([5030ae0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5030ae035667bca2a283cb266beef4bba6602572))
* **release-please:** bootstrap-sha从v1.0.0开始计算 ([6877d17](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6877d17c98be4440185f9756c4460a56bb9bb590))
* **release-please:** release type使用sample，java在快照版本不会更新CHANGELOG ([39361e7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/39361e766808496345e246da1a0671a5722a0cb7))
* **release-please:** tag格式为v{version} ([0aa19ba](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0aa19ba0ef34f929a10e263ad4855138686463a6))
* 让release-please自动计算下一个版号 ([a58e3fa](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a58e3fa756329f15893b89c80738e6ab3cd45846))


### Miscellaneous Chores

* bom管理mybaits、web、nacos等依赖版本 ([46e957b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/46e957b15438d26c5406f5667be7502fb79b8115))
* bump version to 1.0.1-SNAPSHOT for next development cycle ([62a801e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/62a801e55264207ff3a9c29e894125f2105408ba))
* bump version to 1.0.2-SNAPSHOT for next development cycle ([7a26eb6](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7a26eb6f54c02ceaba6a0e7689c6a7c179e8a21e))
* **deps:** 使用官方mybatis-plus-bom管理mybatis相关依赖 ([8bd6002](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8bd600227761f231dc5bb46efda3f981ea5f6645))
* **main:** release mimir-boot 1.1.0 ([b54a651](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b54a6512680c5fa493b2e8e11822e0b17a946cf9))
* maven profiles的最佳实践 ([e090cc1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e090cc134a092b9398e417a90ab732e7fce0b318))
* 回滚版本相关修改，准备重新发布 ([a7baa75](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a7baa751f126a2a52afcfafd5bca423315793074))

## 1.0.0 (2025-11-09)


### Continuous Integration

* 发布前先构建jar包 ([2ce642e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/2ce642ecc72aa6d66b009b788aaab5635fa49b2a))
* 排查example模块，重新发布 ([e6d16fc](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e6d16fc0150b782120e15418a129b1253f87d0d9))
* 排除examples模块，不发布 ([9cbe07c](https://github.com/Yggdrasil-Labs/mimir-boot/commit/9cbe07ca2fe189f4ebaf694973a5ea5e704f02c3))


### Miscellaneous Chores

* bump version to 1.0.1-SNAPSHOT for next development cycle ([c46173f](https://github.com/Yggdrasil-Labs/mimir-boot/commit/c46173fdd0a37e709357d1bf2a5a173bdf61ecb3))
* **main:** release mimir-boot 1.0.0 ([7a695f8](https://github.com/Yggdrasil-Labs/mimir-boot/commit/7a695f89ba1a856fe8e5b0ef40643e050ae2641f))
* **main:** release mimir-boot 1.0.0 ([3d20699](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3d20699d0f11287d09be2ad551ec45b8f41cc7a1))
* update revision to 1.0.0 for release ([3111142](https://github.com/Yggdrasil-Labs/mimir-boot/commit/3111142d9d62d3ea72dc5c9e43a98b79c8481f0a))
* update revision to 1.0.0 for release ([b279485](https://github.com/Yggdrasil-Labs/mimir-boot/commit/b279485d35d2181456d7d2e26f9787da5a8eec6a))
