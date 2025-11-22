# Changelog

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
