package com.yggdrasil.labs.test.arch;

import java.util.Arrays;

/** 完整包名的校验与 ArchUnit 包标识转换。 */
final class ArchPackages {
    private ArchPackages() {}

    /**
     * 校验完整包名并在错误消息中保留调用参数名。
     *
     * @param value 待校验的包名
     * @param parameter 对外参数名
     * @return 校验后的原始包名
     * @throws IllegalArgumentException 包名为空或无效时抛出
     */
    static String requirePackage(String value, String parameter) {
        if (value == null
                || !value.matches(
                        "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*+")) {
            throw new IllegalArgumentException(parameter + " 必须是非空的完整包名");
        }
        return value;
    }

    /**
     * 将完整包名转换为包含全部子包的 ArchUnit 包标识。
     *
     * @param value 完整包名
     * @param parameter 对外参数名
     * @return 包名及其子包标识
     */
    static String pattern(String value, String parameter) {
        return requirePackage(value, parameter) + "..";
    }

    /**
     * 校验并转换一组完整包名。
     *
     * @param values 待转换的包名列表
     * @param parameter 对外参数名
     * @param allowEmpty 是否允许空列表
     * @return 对应的 ArchUnit 包标识列表
     * @throws IllegalArgumentException 列表为 null、不允许为空或包名无效时抛出
     */
    static String[] patterns(String[] values, String parameter, boolean allowEmpty) {
        if (values == null || (!allowEmpty && values.length == 0)) {
            throw new IllegalArgumentException(parameter + " 必须包含完整包名");
        }
        return Arrays.stream(values).map(value -> pattern(value, parameter)).toArray(String[]::new);
    }

    /**
     * 判断候选包是否为指定包或其子包，保持包名段的边界。
     *
     * @param packageName 完整父包名
     * @param candidate 候选完整包名
     * @return 候选包属于指定包时返回 true
     */
    static boolean contains(String packageName, String candidate) {
        return candidate.equals(packageName) || candidate.startsWith(packageName + ".");
    }
}
