# Mimir Boot Starter Nacos

基于 Spring Cloud Alibaba Nacos Config 的配置加密脱敏启动器，提供开箱即用的配置加密解密功能。

## 用途

在 Spring Cloud Alibaba Nacos Config 基础上增强配置安全能力：

- ✅ **配置加密脱敏**：支持 `ENC(encrypted_value)` 格式的配置值自动解密
- ✅ **自动解密处理**：应用启动时和配置刷新时自动处理加密配置
- ✅ **多种加密算法**：支持 AES 等对称加密算法
- ✅ **工具类支持**：提供密钥生成、加密解密等便捷工具
- ✅ **动态刷新支持**：配置动态刷新时自动重新解密

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-starter-nacos</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**注意**：需要同时引入 `spring-cloud-starter-alibaba-nacos-config` 依赖（已自动传递）。

### 基础配置

#### 1. Nacos 配置中心配置

在 `bootstrap.yml` 或 `application.yml` 中配置 Nacos：

```yaml
spring:
  application:
    name: your-application-name
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        file-extension: yaml
        namespace: dev
        group: DEFAULT_GROUP
```

#### 2. 配置加密密钥

在 `application.yml` 中配置加密密钥：

```yaml
mimir:
  nacos:
    encrypt:
      enabled: true              # 是否启用配置加密脱敏功能，默认 true
      key: YOUR_BASE64_KEY       # Base64 编码的加密密钥（必填）
      algorithm: AES             # 加密算法，默认 AES
      prefix: ENC               # 加密前缀，默认 ENC
```

#### 3. 在 Nacos 配置中心使用加密值

在 Nacos 配置中心，使用 `ENC(encrypted_value)` 格式配置敏感信息：

```yaml
# 示例：在 Nacos 配置中心的配置内容
database:
  password: ENC(xxxxxxxx)        # 加密的数据库密码
  username: admin

api:
  secret-key: ENC(yyyyyyyy)      # 加密的 API 密钥

redis:
  password: ENC(zzzzzzzz)        # 加密的 Redis 密码
```

应用启动时会自动检测并解密所有 `ENC(...)` 格式的配置值。

## 核心功能

### 1. 自动配置解密

Starter 会在应用启动早期阶段（`ApplicationEnvironmentPreparedEvent`）自动处理配置解密：

```yaml
# Nacos 中的配置（加密后）
app:
  secret: ENC(encrypted_value_here)
  api-key: ENC(another_encrypted_value)

# 应用运行时（自动解密后）
# app.secret = "decrypted_plaintext"
# app.api-key = "another_decrypted_value"
```

### 2. 配置动态刷新支持

支持 Spring Cloud 配置刷新功能，配置变更时自动重新解密：

```java
@RestController
@RefreshScope  // Spring Cloud 配置刷新
public class ConfigController {
    
    @Value("${app.secret}")
    private String secret;  // 配置刷新时会自动重新解密
    
    @GetMapping("/secret")
    public String getSecret() {
        return secret;  // 始终返回解密后的值
    }
}
```

### 3. 加密工具类

提供便捷的工具类用于生成密钥和加密配置值：

```java
import com.yggdrasil.labs.nacos.util.NacosEncryptUtil;

// 1. 生成加密密钥
String key = NacosEncryptUtil.generateKey();
System.out.println("密钥: " + key);  // Base64 编码的密钥

// 2. 加密配置值
String plaintext = "my-secret-password";
String encrypted = NacosEncryptUtil.encrypt(plaintext, key);
System.out.println("加密后: " + encrypted);

// 3. 生成 ENC() 格式（用于 Nacos 配置）
String encValue = NacosEncryptUtil.wrapWithEnc(encrypted);
System.out.println("配置值: " + encValue);  // 输出: ENC(encrypted_value)
```

### 4. 自定义加密前缀

支持自定义加密前缀，默认使用 `ENC`：

```yaml
mimir:
  nacos:
    encrypt:
      prefix: SECRET  # 自定义前缀
```

配置示例：
```yaml
# Nacos 中的配置
password: SECRET(encrypted_value)  # 使用自定义前缀
```

## 配置参数

所有配置项前缀为 `mimir.nacos.encrypt`：

```yaml
mimir:
  nacos:
    encrypt:
      # 是否启用配置加密脱敏功能
      enabled: true
      
      # 加密密钥（Base64 编码），必填
      # 可以通过工具类生成：NacosEncryptUtil.generateKey()
      key: YOUR_BASE64_ENCODED_KEY
      
      # 加密算法，默认 AES
      algorithm: AES
      
      # 加密前缀，默认 ENC
      # 配置值格式：prefix(encrypted_value)
      prefix: ENC
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| `enabled` | Boolean | `true` | 否 | 是否启用配置加密脱敏功能 |
| `key` | String | - | 是 | Base64 编码的加密密钥，用于解密配置值 |
| `algorithm` | String | `AES` | 否 | 加密算法，当前支持 AES |
| `prefix` | String | `ENC` | 否 | 加密配置值的前缀，格式为 `prefix(encrypted_value)` |

## 使用示例

### 完整示例

#### 1. 生成密钥并配置

```java
import com.yggdrasil.labs.nacos.util.NacosEncryptUtil;

public class KeyGenerator {
    public static void main(String[] args) {
        // 生成密钥
        String key = NacosEncryptUtil.generateKey();
        System.out.println("请将此密钥配置到 application.yml:");
        System.out.println("mimir.nacos.encrypt.key: " + key);
    }
}
```

#### 2. 加密敏感配置

```java
import com.yggdrasil.labs.nacos.util.NacosEncryptUtil;

public class ConfigEncryptor {
    public static void main(String[] args) {
        String key = "YOUR_BASE64_KEY";  // 从配置文件中读取
        
        // 加密数据库密码
        String dbPassword = "MySecret123!";
        String encrypted = NacosEncryptUtil.encrypt(dbPassword, key);
        String encValue = NacosEncryptUtil.wrapWithEnc(encrypted);
        System.out.println("Nacos 配置值: " + encValue);
        // 输出: ENC(encrypted_base64_string)
    }
}
```

#### 3. 在 Nacos 中配置

登录 Nacos 控制台，创建或编辑配置文件（如 `application-dev.yaml`）：

```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: ENC(encrypted_password_here)  # 加密后的密码

# API 配置
app:
  api:
    secret-key: ENC(encrypted_secret_key_here)
    access-token: ENC(encrypted_token_here)

# 其他敏感配置
payment:
  merchant-id: ENC(encrypted_merchant_id)
  api-key: ENC(encrypted_api_key)
```

#### 4. 应用中使用

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConfig {
    
    @Value("${spring.datasource.password}")
    private String dbPassword;  // 自动解密后的值
    
    @Value("${app.api.secret-key}")
    private String apiSecretKey;  // 自动解密后的值
    
    public void connect() {
        // dbPassword 和 apiSecretKey 已经是解密后的明文
        System.out.println("数据库密码: " + dbPassword);
    }
}
```

### 配置刷新示例

```java
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RefreshScope  // 启用配置刷新
public class DynamicConfig {
    
    @Value("${app.api.secret-key}")
    private String apiSecretKey;
    
    public String getApiSecretKey() {
        // 配置刷新时，如果 Nacos 中的值更新了，
        // 会自动重新解密并更新此值
        return apiSecretKey;
    }
}
```

## 最佳实践

### 1. 密钥管理

**生产环境密钥安全建议**：

```yaml
# ❌ 不推荐：直接写在配置文件中
mimir:
  nacos:
    encrypt:
      key: base64_key_in_plaintext

# ✅ 推荐：使用环境变量
mimir:
  nacos:
    encrypt:
      key: ${NACOS_ENCRYPT_KEY}  # 从环境变量读取
```

或者使用密钥管理服务（如 Vault、KMS）：

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NacosEncryptConfig {
    
    @Bean
    public NacosEncryptProperties nacosEncryptProperties(
            @Value("${vault.nacos.encrypt.key}") String key) {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        properties.setKey(key);  // 从密钥管理服务获取
        return properties;
    }
}
```

### 2. 密钥生成

使用工具类生成密钥，并妥善保存：

```java
import com.yggdrasil.labs.nacos.util.NacosEncryptUtil;
import java.util.Base64;

public class SecureKeyGenerator {
    public static void main(String[] args) {
        // 生成密钥
        String key = NacosEncryptUtil.generateKey();
        
        // 保存到密钥管理服务或安全存储
        System.out.println("生成的密钥（Base64）: " + key);
        
        // 注意：生产环境请使用密钥管理服务，不要硬编码
    }
}
```

### 3. 配置加密流程

1. **生成密钥**：使用 `NacosEncryptUtil.generateKey()` 生成密钥
2. **配置密钥**：将密钥配置到应用配置中（建议使用环境变量）
3. **加密敏感值**：使用 `NacosEncryptUtil.encrypt()` 加密敏感配置
4. **配置到 Nacos**：将 `ENC(encrypted_value)` 格式的值配置到 Nacos
5. **应用自动解密**：应用启动时自动解密配置值

### 4. 混合使用加密和非加密配置

```yaml
# Nacos 配置示例
database:
  host: localhost              # 非敏感配置，不需要加密
  port: 3306                  # 非敏感配置，不需要加密
  username: root              # 非敏感配置，不需要加密
  password: ENC(xxxxxx)       # 敏感配置，使用加密

api:
  base-url: https://api.example.com  # 非敏感配置
  secret-key: ENC(yyyyyy)            # 敏感配置，使用加密
```

### 5. 不同环境使用不同密钥

```yaml
# application-dev.yml
mimir:
  nacos:
    encrypt:
      key: ${DEV_ENCRYPT_KEY}

# application-prod.yml
mimir:
  nacos:
    encrypt:
      key: ${PROD_ENCRYPT_KEY}  # 生产环境使用不同的密钥
```

## 常见问题

### 问题1：配置未解密

**症状**：配置值仍然是 `ENC(...)` 格式，未自动解密

**可能原因**：
1. 未配置加密密钥或密钥配置错误
2. 功能被禁用（`mimir.nacos.encrypt.enabled: false`）
3. 配置值格式不正确

**解决方案**：

```yaml
# 检查配置
mimir:
  nacos:
    encrypt:
      enabled: true      # 确保已启用
      key: YOUR_KEY      # 确保密钥正确配置

# 检查配置值格式（Nacos 中）
password: ENC(encrypted_value)  # ✅ 正确格式
password: enc(encrypted_value)  # ✅ 大小写不敏感，也支持
password: ENC encrypted_value   # ❌ 错误格式，缺少括号
```

### 问题2：解密失败

**症状**：应用启动时报解密异常或配置值仍为加密格式

**可能原因**：
1. 使用了错误的密钥解密
2. 加密后的值格式不正确（不是 Base64）
3. 加密算法不匹配

**解决方案**：

```java
// 验证加密解密流程
String key = "YOUR_KEY";
String plaintext = "test";
String encrypted = NacosEncryptUtil.encrypt(plaintext, key);
String decrypted = NacosEncryptUtil.decrypt(encrypted, key);
System.out.println("原文: " + plaintext);
System.out.println("解密: " + decrypted);
// 应该输出相同的值
```

### 问题3：配置刷新后未重新解密

**症状**：配置刷新后，加密配置值未更新

**解决方案**：
1. 确认配置类使用了 `@RefreshScope` 注解
2. 检查日志中是否有配置变更事件的输出
3. 确认 `EnvironmentChangeEvent` 正常触发

```java
@RefreshScope  // 确保添加此注解
@Component
public class ConfigBean {
    @Value("${app.secret}")
    private String secret;
}
```

### 问题4：如何查看解密后的配置值

**调试方法**：

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigDebugger {
    
    @Value("${app.secret}")
    private String secret;
    
    @EventListener(ContextRefreshedEvent.class)
    public void debugConfig() {
        System.out.println("解密后的配置值: " + secret);
        // 注意：生产环境请移除此调试代码
    }
}
```

### 问题5：自定义加密前缀不生效

**症状**：配置了自定义前缀，但未识别

**解决方案**：
1. 确认前缀配置正确
2. 确认 Nacos 中的配置值使用新前缀

```yaml
# application.yml
mimir:
  nacos:
    encrypt:
      prefix: SECRET  # 自定义前缀

# Nacos 配置
password: SECRET(encrypted_value)  # 使用新前缀
```

## 技术实现

### 工作原理

1. **配置加载阶段**：在 `ApplicationEnvironmentPreparedEvent` 阶段，所有配置已加载完成
2. **配置扫描**：遍历所有配置属性源，查找包含 `ENC(...)` 格式的值
3. **自动解密**：提取加密内容，使用配置的密钥进行解密
4. **配置替换**：将解密后的值添加到环境配置的最高优先级位置
5. **动态刷新**：监听 `EnvironmentChangeEvent`，配置刷新时重新解密

### 安全说明

- **当前实现**：使用 AES/ECB 模式（默认），密钥长度 128 位
- **安全建议**：生产环境建议使用更安全的加密模式（如 AES-GCM）
- **密钥管理**：生产环境请使用密钥管理服务（Vault、KMS）动态获取密钥
- **密钥轮换**：定期轮换加密密钥，重新加密所有配置值

## 技术栈

- **Spring Cloud Alibaba Nacos**: 配置中心
- **Spring Boot**: 3.3.13+
- **Spring Cloud**: 2023.0.6+
- **Java**: 17+

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs

