# Changelog

## [1.1.0](https://github.com/Yggdrasil-Labs/mimir-boot/compare/mimir-boot-v1.0.0...mimir-boot-v1.1.0) (2025-11-14)


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
* dependabot的PR不需要sonar分析；example跳过maven deploy ([5030ae0](https://github.com/Yggdrasil-Labs/mimir-boot/commit/5030ae035667bca2a283cb266beef4bba6602572))
* **release-please:** bootstrap-sha从v1.0.0开始计算 ([6877d17](https://github.com/Yggdrasil-Labs/mimir-boot/commit/6877d17c98be4440185f9756c4460a56bb9bb590))
* **release-please:** release type使用sample，java在快照版本不会更新CHANGELOG ([39361e7](https://github.com/Yggdrasil-Labs/mimir-boot/commit/39361e766808496345e246da1a0671a5722a0cb7))
* **release-please:** tag格式为v{version} ([0aa19ba](https://github.com/Yggdrasil-Labs/mimir-boot/commit/0aa19ba0ef34f929a10e263ad4855138686463a6))
* 让release-please自动计算下一个版号 ([a58e3fa](https://github.com/Yggdrasil-Labs/mimir-boot/commit/a58e3fa756329f15893b89c80738e6ab3cd45846))


### Miscellaneous Chores

* bom管理mybaits、web、nacos等依赖版本 ([46e957b](https://github.com/Yggdrasil-Labs/mimir-boot/commit/46e957b15438d26c5406f5667be7502fb79b8115))
* bump version to 1.0.1-SNAPSHOT for next development cycle ([62a801e](https://github.com/Yggdrasil-Labs/mimir-boot/commit/62a801e55264207ff3a9c29e894125f2105408ba))
* **deps:** 使用官方mybatis-plus-bom管理mybatis相关依赖 ([8bd6002](https://github.com/Yggdrasil-Labs/mimir-boot/commit/8bd600227761f231dc5bb46efda3f981ea5f6645))
* maven profiles的最佳实践 ([e090cc1](https://github.com/Yggdrasil-Labs/mimir-boot/commit/e090cc134a092b9398e417a90ab732e7fce0b318))

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
