package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.common.constant.CommonConstants;

/** SQL 文本敏感赋值脱敏；词法扫描和参数对象遍历互相独立。 */
final class SqlTextMasker {
    private SqlTextMasker() {}

    static String mask(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        StringBuilder masked = new StringBuilder(sql.length());
        for (int index = 0; index < sql.length(); ) {
            if (Lexemes.startsLineComment(sql, index)) {
                index = appendLineComment(sql, masked, index);
            } else if (Lexemes.startsBlockComment(sql, index)) {
                index = appendBlockComment(sql, masked, index);
            } else if (sql.charAt(index) == '\'') {
                int end = Quotes.quotedEnd(sql, index, '\'');
                masked.append(sql, index, end);
                index = end;
            } else if (Lexemes.isIdentifierStart(sql.charAt(index))) {
                index = appendIdentifier(sql, masked, index);
            } else {
                masked.append(sql.charAt(index));
                index++;
            }
        }
        return masked.toString();
    }

    private static int appendLineComment(String sql, StringBuilder masked, int index) {
        int end = sql.indexOf('\n', index);
        end = end < 0 ? sql.length() : end;
        masked.append(sql, index, end);
        return end;
    }

    private static int appendBlockComment(String sql, StringBuilder masked, int index) {
        int end = sql.indexOf("*/", index + 2);
        end = end < 0 ? sql.length() : end + 2;
        if (Lexemes.isExecutableBlockComment(sql, index) && end >= index + 4) {
            int contentStart = Lexemes.executableCommentContentStart(sql, index, end - 2);
            masked.append(sql, index, contentStart)
                    .append(mask(sql.substring(contentStart, end - 2)))
                    .append("*/");
        } else {
            masked.append(sql, index, end);
        }
        return end;
    }

    private static int appendIdentifier(String sql, StringBuilder masked, int index) {
        int end = Lexemes.identifierEnd(sql, index);
        int equalsIndex = Lexemes.skipSqlTrivia(sql, end);
        int assignmentEnd = Lexemes.assignmentOperatorEnd(sql, equalsIndex);
        if (assignmentEnd <= equalsIndex) {
            masked.append(sql, index, end);
            return end;
        }
        int valueStart = Lexemes.skipSqlTrivia(sql, assignmentEnd);
        int valueEnd = new ValueScanner(sql, valueStart).scan();
        String identifier = sql.substring(index, end);
        if (valueStart < valueEnd
                && SqlParameterMasker.isSensitiveParameterName(
                        Quotes.unquoteSqlIdentifier(identifier))) {
            masked.append(sql, index, valueStart).append(CommonConstants.MASKED);
            return sensitiveValueEnd(sql, valueStart, valueEnd);
        }
        // 不跳过非敏感表达式，继续识别其中嵌套的敏感比较或赋值。
        masked.append(sql, index, valueStart);
        return valueStart;
    }

    private static int sensitiveValueEnd(String sql, int start, int fallbackEnd) {
        char first = sql.charAt(start);
        if (first == '\'') {
            return Quotes.quotedEnd(sql, start, first);
        }
        if (first == '"') {
            return Quotes.quotedEndWithBackslashEscapes(sql, start, first);
        }
        if (first == '$') {
            int dollarQuoteEnd = Quotes.dollarQuotedEnd(sql, start);
            if (dollarQuoteEnd > start) {
                return dollarQuoteEnd;
            }
        }
        return fallbackEnd;
    }

    /** 单一游标扫描赋值右侧，注释和引号优先于括号及终止关键字。 */
    private static final class ValueScanner {
        private final String sql;
        private int index;
        private int parenthesesDepth;

        ValueScanner(String sql, int start) {
            this.sql = sql;
            this.index = start;
        }

        int scan() {
            while (index < sql.length()) {
                if (skipFragment()) {
                    continue;
                }
                char value = sql.charAt(index);
                if (value == '(') {
                    parenthesesDepth++;
                } else if (value == ')') {
                    if (parenthesesDepth == 0) {
                        break;
                    }
                    parenthesesDepth--;
                } else if (isTerminator(value)) {
                    break;
                }
                index++;
            }
            return index;
        }

        private boolean skipFragment() {
            char value = sql.charAt(index);
            if (value == '$') {
                int end = Quotes.dollarQuotedEnd(sql, index);
                if (end > index) {
                    index = end;
                    return true;
                }
            }
            if (value == '\'' || value == '"') {
                index = Quotes.quotedEnd(sql, index, value);
                return true;
            }
            if (Lexemes.startsBlockComment(sql, index)) {
                int end = sql.indexOf("*/", index + 2);
                index = end < 0 ? sql.length() : end + 2;
                return true;
            }
            if (Lexemes.startsLineComment(sql, index)) {
                int end = sql.indexOf('\n', index);
                index = end < 0 ? sql.length() : end + 1;
                return true;
            }
            return false;
        }

        private boolean isTerminator(char value) {
            if (parenthesesDepth != 0) {
                return false;
            }
            if (value == ',' || value == ';') {
                return true;
            }
            return Character.isWhitespace(value)
                    && Lexemes.startsSqlValueTerminator(sql, Lexemes.skipWhitespace(sql, index));
        }
    }

    /** 引号、方括号与美元定界符的原有转义边界。 */
    private static final class Quotes {
        private Quotes() {}

        private static int quotedEnd(String sql, int start, char quote) {
            int index = start + 1;
            while (index < sql.length()) {
                if (sql.charAt(index) == quote) {
                    if (quote == '\''
                            && Quotes.hasOddNumberOfPrecedingBackslashes(sql, start, index)) {
                        index++;
                        continue;
                    }
                    if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                        index += 2;
                        continue;
                    }
                    return index + 1;
                }
                index++;
            }
            return sql.length();
        }

        private static int quotedEndWithBackslashEscapes(String sql, int start, char quote) {
            int index = start + 1;
            while (index < sql.length()) {
                if (sql.charAt(index) == quote) {
                    if (Quotes.hasOddNumberOfPrecedingBackslashes(sql, start, index)) {
                        index++;
                        continue;
                    }
                    if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                        index += 2;
                        continue;
                    }
                    return index + 1;
                }
                index++;
            }
            return sql.length();
        }

        private static boolean hasOddNumberOfPrecedingBackslashes(
                String sql, int start, int index) {
            int count = 0;
            for (int cursor = index - 1; cursor > start && sql.charAt(cursor) == '\\'; cursor--) {
                count++;
            }
            return count % 2 != 0;
        }

        private static int dollarQuotedEnd(String sql, int start) {
            int delimiterEnd = sql.indexOf('$', start + 1);
            if (delimiterEnd < 0 || !Quotes.isDollarQuoteTag(sql, start + 1, delimiterEnd)) {
                return start;
            }
            String delimiter = sql.substring(start, delimiterEnd + 1);
            int valueEnd = sql.indexOf(delimiter, delimiterEnd + 1);
            return valueEnd < 0 ? sql.length() : valueEnd + delimiter.length();
        }

        private static boolean isDollarQuoteTag(String sql, int start, int end) {
            if (start == end) {
                return true;
            }
            char first = sql.charAt(start);
            if (!Character.isLetter(first) && first != '_') {
                return false;
            }
            for (int index = start + 1; index < end; index++) {
                char value = sql.charAt(index);
                if (!Character.isLetterOrDigit(value) && value != '_') {
                    return false;
                }
            }
            return true;
        }

        private static int bracketIdentifierEnd(String sql, int start) {
            int index = start + 1;
            while (index < sql.length()) {
                if (sql.charAt(index) == ']') {
                    if (index + 1 < sql.length() && sql.charAt(index + 1) == ']') {
                        index += 2;
                        continue;
                    }
                    return index + 1;
                }
                index++;
            }
            return sql.length();
        }

        private static String unquoteSqlIdentifier(String identifier) {
            if (identifier == null || identifier.length() < 2) {
                return identifier;
            }
            char first = identifier.charAt(0);
            char last = identifier.charAt(identifier.length() - 1);
            if ((first == '`' && last == '`')
                    || (first == '\"' && last == '\"')
                    || (first == '[' && last == ']')) {
                return identifier.substring(1, identifier.length() - 1);
            }
            return identifier;
        }
    }

    /** 标识符、注释、赋值和关键字的有限词法。 */
    private static final class Lexemes {
        private Lexemes() {}

        private static boolean startsLineComment(String sql, int index) {
            if (sql.startsWith("--", index)) {
                return true;
            }
            if (sql.charAt(index) != '#') {
                return false;
            }
            return index + 1 >= sql.length()
                    || (sql.charAt(index + 1) != '>' && sql.charAt(index + 1) != '-');
        }

        private static boolean startsBlockComment(String sql, int index) {
            return sql.startsWith("/*", index);
        }

        private static boolean isExecutableBlockComment(String sql, int index) {
            return sql.startsWith("/*!", index) || sql.startsWith("/*M!", index);
        }

        private static int executableCommentContentStart(String sql, int start, int contentEnd) {
            int index = start + (sql.startsWith("/*M!", start) ? 4 : 3);
            while (index < contentEnd && Character.isDigit(sql.charAt(index))) {
                index++;
            }
            return index;
        }

        private static boolean isIdentifierStart(char value) {
            return Character.isLetter(value) || value == '`' || value == '"' || value == '[';
        }

        private static int identifierEnd(String sql, int start) {
            char quote = sql.charAt(start);
            if (quote == '`' || quote == '"') {
                return Quotes.quotedEnd(sql, start, quote);
            }
            if (quote == '[') {
                return Quotes.bracketIdentifierEnd(sql, start);
            }
            int index = start + 1;
            while (index < sql.length()) {
                char value = sql.charAt(index);
                if (!Character.isLetterOrDigit(value) && value != '_' && value != '-') {
                    break;
                }
                index++;
            }
            return index;
        }

        private static int skipWhitespace(String sql, int start) {
            int index = start;
            while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
                index++;
            }
            return index;
        }

        private static int assignmentOperatorEnd(String sql, int start) {
            if (start >= sql.length()) {
                return start;
            }
            if (sql.charAt(start) == '=') {
                return start + 1;
            }
            if (start + 1 < sql.length()
                    && ((sql.charAt(start) == ':' && sql.charAt(start + 1) == '=')
                            || (sql.charAt(start) == '=' && sql.charAt(start + 1) == '>'))) {
                return start + 2;
            }
            return start;
        }

        private static boolean startsSqlValueTerminator(String sql, int index) {
            return Lexemes.startsSqlKeyword(sql, index, "AND")
                    || Lexemes.startsSqlKeyword(sql, index, "OR")
                    || Lexemes.startsSqlKeyword(sql, index, "WHERE")
                    || Lexemes.startsSqlKeyword(sql, index, "RETURNING")
                    || Lexemes.startsSqlKeyword(sql, index, "FROM")
                    || Lexemes.startsSqlKeyword(sql, index, "LIMIT")
                    || Lexemes.startsSqlKeyword(sql, index, "ORDER")
                    || Lexemes.startsSqlKeyword(sql, index, "GROUP")
                    || Lexemes.startsSqlKeyword(sql, index, "HAVING");
        }

        private static boolean startsSqlKeyword(String sql, int index, String keyword) {
            int end = index + keyword.length();
            return end <= sql.length()
                    && sql.regionMatches(true, index, keyword, 0, keyword.length())
                    && (end == sql.length() || !Lexemes.isSqlIdentifierPart(sql.charAt(end)));
        }

        private static boolean isSqlIdentifierPart(char value) {
            return Character.isLetterOrDigit(value) || value == '_' || value == '-';
        }

        private static int skipSqlTrivia(String sql, int start) {
            int index = start;
            boolean trivia = true;
            while (index < sql.length() && trivia) {
                if (Character.isWhitespace(sql.charAt(index))) {
                    index++;
                } else if (Lexemes.startsBlockComment(sql, index)) {
                    int end = sql.indexOf("*/", index + 2);
                    index = end < 0 ? sql.length() : end + 2;
                } else if (Lexemes.startsLineComment(sql, index)) {
                    int end = sql.indexOf('\n', index);
                    index = end < 0 ? sql.length() : end + 1;
                } else {
                    trivia = false;
                }
            }
            return index;
        }
    }
}
