package com.yggdrasil.labs.mybatis.log;

/** 慢 SQL 的包内有限文法模板器，不输出原始标识符或值。 */
final class SlowSqlTextSanitizer {

    private static final int MAX_INPUT_LENGTH = 65536;
    private static final int MAX_OUTPUT_LENGTH = 4096;
    private static final int MAX_DEPTH = 32;
    private static final String TRUNCATED_SUFFIX = " [truncated]";
    private static final String[] KEYWORDS = {
        "SELECT",
        "INSERT",
        "INTO",
        "VALUES",
        "UPDATE",
        "SET",
        "DELETE",
        "FROM",
        "WHERE",
        "AND",
        "OR",
        "NOT",
        "IS",
        "IN",
        "LIKE",
        "BETWEEN",
        "EXISTS",
        "DISTINCT",
        "AS",
        "JOIN",
        "LEFT",
        "RIGHT",
        "INNER",
        "OUTER",
        "ON",
        "GROUP",
        "BY",
        "HAVING",
        "ORDER",
        "ASC",
        "DESC",
        "LIMIT",
        "OFFSET",
        "FETCH",
        "FIRST",
        "NEXT",
        "ROWS",
        "ONLY",
        "UNION",
        "ALL",
        "CASE",
        "WHEN",
        "THEN",
        "ELSE",
        "END"
    };
    private static final Result UNAVAILABLE = new Result("<omitted>", "UNAVAILABLE");
    private static final Result TOO_LONG = new Result("<omitted>", "TOO_LONG");
    private static final Result UNSUPPORTED = new Result("<omitted>", "UNSUPPORTED");

    private SlowSqlTextSanitizer() {}

    static Result sanitize(String sql) {
        if (sql == null) {
            return UNAVAILABLE;
        }
        if (sql.length() > MAX_INPUT_LENGTH) {
            return TOO_LONG;
        }
        if (sql.isBlank()) {
            return UNAVAILABLE;
        }
        Parser parser = new Parser(sql);
        if (parser.current.type() == Type.END) {
            return UNAVAILABLE;
        }
        return parser.statement() ? new Result(parser.shape.finish(), "SHAPE") : UNSUPPORTED;
    }

    private static String keyword(String sql, int start, int end) {
        for (String candidate : KEYWORDS) {
            if (asciiEquals(sql, start, end, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static int skipLineComment(String sql, int index) {
        // 无分隔符的双减号存在方言歧义，不能吞掉其后的语句。
        if (index < sql.length() && !isLineCommentSeparator(sql.charAt(index))) {
            return -1;
        }
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\n') {
                return index + 1;
            }
            if (current == '\r') {
                return index + 1 < sql.length() && sql.charAt(index + 1) == '\n' ? index + 2 : -1;
            }
            // NUL、其他控制字符和 Unicode 行分隔符的终止语义不作方言猜测。
            if ((Character.isISOControl(current) && current != '\t')
                    || current == '\u2028'
                    || current == '\u2029') {
                return -1;
            }
            index++;
        }
        return index;
    }

    private static int skipBlockComment(String sql, int start) {
        int index = start + 2;
        if (index < sql.length()
                && (sql.charAt(index) == '!'
                        || ((sql.charAt(index) == 'M'
                                        || sql.charAt(index) == 'm'
                                        || sql.charAt(index) == 'T'
                                        || sql.charAt(index) == 't')
                                && index + 1 < sql.length()
                                && sql.charAt(index + 1) == '!'))) {
            return -1;
        }
        while (index < sql.length()) {
            if (startsWith(sql, index, '/', '*')) {
                return -1;
            }
            if (startsWith(sql, index, '*', '/')) {
                return index + 2;
            }
            index++;
        }
        return -1;
    }

    private static int skipString(String sql, int start) {
        if (startsTripleQuote(sql, start)) {
            return -1;
        }
        int index = start + 1;
        while (index < sql.length()) {
            // 连续三个引号可能属于其他方言的多行字符串，尾部同样不能猜测。
            if (startsTripleQuote(sql, index)) {
                return -1;
            }
            char current = sql.charAt(index);
            index++;
            if (current == '\\') {
                return -1;
            }
            if (current == '\'') {
                if (index < sql.length() && sql.charAt(index) == '\'') {
                    index++;
                } else {
                    return index;
                }
            }
        }
        return -1;
    }

    private static boolean startsTripleQuote(String sql, int index) {
        return startsWith(sql, index, '\'', '\'')
                && index + 2 < sql.length()
                && sql.charAt(index + 2) == '\'';
    }

    private static boolean asciiEquals(String sql, int start, int end, String candidate) {
        if (candidate.length() != end - start) {
            return false;
        }
        for (int offset = 0; offset < candidate.length(); offset++) {
            char current = sql.charAt(start + offset);
            if (current >= 'a' && current <= 'z') {
                current -= 'a' - 'A';
            }
            if (current != candidate.charAt(offset)) {
                return false;
            }
        }
        return true;
    }

    private static String operator(String sql, int start, int end) {
        if (end - start == 1 && isOperator(sql.charAt(start))) {
            return String.valueOf(sql.charAt(start));
        }
        if (end - start == 2) {
            return switch (sql.substring(start, end)) {
                case "<=" -> "<=";
                case ">=" -> ">=";
                case "<>" -> "<>";
                case "!=" -> "!=";
                default -> null;
            };
        }
        return null;
    }

    private static boolean startsWith(String sql, int index, char first, char second) {
        return index + 1 < sql.length()
                && sql.charAt(index) == first
                && sql.charAt(index + 1) == second;
    }

    private static boolean startsComment(String sql, int index) {
        return startsWith(sql, index, '-', '-') || startsWith(sql, index, '/', '*');
    }

    private static boolean isWhitespace(char value) {
        return value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\f';
    }

    private static boolean isLineCommentSeparator(char value) {
        return value <= ' ' || value == '\u007f';
    }

    private static boolean isIdentifierStart(char value) {
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z') || value == '_';
    }

    private static boolean isIdentifierPart(char value) {
        return isIdentifierStart(value) || isDigit(value);
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static boolean isOperator(char value) {
        return value == '='
                || value == '<'
                || value == '>'
                || value == '!'
                || value == '+'
                || value == '-'
                || value == '*'
                || value == '/'
                || value == '%';
    }

    /** 数值字面量的扫描边界；不接受不完整指数或相邻标识符。 */
    private static final class Numbers {
        private Numbers() {}

        static int skipNumber(String sql, int start) {
            int index = skipDigits(sql, start);
            if (index < sql.length() && sql.charAt(index) == '.') {
                index = skipDigits(sql, index + 1);
            }
            index = skipExponent(sql, index);
            if (index < 0) {
                return -1;
            }
            if (index < sql.length()
                    && (isIdentifierPart(sql.charAt(index)) || sql.charAt(index) == '.')) {
                return -1;
            }
            return index;
        }

        private static int skipDigits(String sql, int start) {
            int index = start;
            while (index < sql.length() && isDigit(sql.charAt(index))) {
                index++;
            }
            return index;
        }

        private static int skipExponent(String sql, int index) {
            if (index >= sql.length() || (sql.charAt(index) != 'e' && sql.charAt(index) != 'E')) {
                return index;
            }
            index++;
            if (index < sql.length() && (sql.charAt(index) == '+' || sql.charAt(index) == '-')) {
                index++;
            }
            int end = skipDigits(sql, index);
            return end == index ? -1 : end;
        }
    }

    private enum Type {
        WORD,
        IDENTIFIER,
        INTEGER,
        NUMBER,
        STRING,
        BOOLEAN,
        NULL,
        PARAMETER,
        SYMBOL,
        END,
        INVALID
    }

    /** text 只包含固定常量，原始标识符和字面值不离开词法扫描器。 */
    private record Token(Type type, String text) {}

    private static final Token END = new Token(Type.END, "");
    private static final Token INVALID = new Token(Type.INVALID, "");

    private static final class Lexer {
        private final String sql;
        private int index;

        Lexer(String sql) {
            this.sql = sql;
        }

        Token next() {
            if (!skipTrivia()) {
                return INVALID;
            }
            if (index == sql.length()) {
                return END;
            }
            char current = sql.charAt(index);
            if (isIdentifierStart(current)) {
                return word();
            }
            if (current == '\'') {
                index = skipString(sql, index);
                return index < 0 ? INVALID : new Token(Type.STRING, "?");
            }
            if (isDigit(current)
                    || (current == '.'
                            && index + 1 < sql.length()
                            && isDigit(sql.charAt(index + 1)))) {
                return number();
            }
            if (isOperator(current)) {
                return symbol();
            }
            index++;
            return punctuation(current);
        }

        private boolean skipTrivia() {
            while (index < sql.length()) {
                if (isWhitespace(sql.charAt(index))) {
                    index++;
                } else if (startsWith(sql, index, '-', '-')) {
                    index = skipLineComment(sql, index + 2);
                } else if (startsWith(sql, index, '/', '*')) {
                    index = skipBlockComment(sql, index);
                } else {
                    return true;
                }
                if (index < 0) {
                    return false;
                }
            }
            return true;
        }

        private Token word() {
            int start = index;
            index++;
            while (index < sql.length() && isIdentifierPart(sql.charAt(index))) {
                index++;
            }
            if (index < sql.length() && sql.charAt(index) == '\'') {
                return INVALID;
            }
            String word = keyword(sql, start, index);
            if (word != null) {
                return new Token(Type.WORD, word);
            }
            if (asciiEquals(sql, start, index, "NULL")) {
                return new Token(Type.NULL, "?");
            }
            if (asciiEquals(sql, start, index, "TRUE") || asciiEquals(sql, start, index, "FALSE")) {
                return new Token(Type.BOOLEAN, "?");
            }
            return new Token(Type.IDENTIFIER, "?");
        }

        private Token number() {
            int start = index;
            index = Numbers.skipNumber(sql, index);
            if (index < 0) {
                return INVALID;
            }
            Type type = Type.INTEGER;
            for (int offset = start; offset < index; offset++) {
                if (!isDigit(sql.charAt(offset))) {
                    type = Type.NUMBER;
                    break;
                }
            }
            return new Token(type, "?");
        }

        private Token symbol() {
            int start = index;
            index++;
            while (index < sql.length()
                    && isOperator(sql.charAt(index))
                    && !startsComment(sql, index)) {
                index++;
            }
            String symbol = operator(sql, start, index);
            return symbol == null ? INVALID : new Token(Type.SYMBOL, symbol);
        }

        private Token punctuation(char current) {
            return switch (current) {
                case '?' -> new Token(Type.PARAMETER, "?");
                case '(' -> new Token(Type.SYMBOL, "(");
                case ')' -> new Token(Type.SYMBOL, ")");
                case ',' -> new Token(Type.SYMBOL, ",");
                case '.' -> new Token(Type.SYMBOL, ".");
                case ';' -> new Token(Type.SYMBOL, ";");
                default -> INVALID;
            };
        }
    }

    /** 有限正向文法：单一游标、不回溯，只有完整消费到 EOF 才能输出模板。 */
    private static final class Parser {
        private final Lexer lexer;
        private final Shape shape = new Shape();
        private Token current;

        private final QuerySyntax queries = new QuerySyntax();
        private final ExpressionSyntax expressions = new ExpressionSyntax();
        private final ValueSyntax values = new ValueSyntax();

        Parser(String sql) {
            lexer = new Lexer(sql);
            current = lexer.next();
        }

        boolean statement() {
            boolean valid;
            if (is("SELECT")) {
                valid = query(0);
            } else if (take("INSERT")) {
                valid = insert();
            } else if (take("UPDATE")) {
                valid = name() && alias() && take("SET") && assignments(0) && writeTail();
            } else if (take("DELETE")) {
                valid = take("FROM") && name() && alias() && writeTail();
            } else {
                return false;
            }
            if (!valid) {
                return false;
            }
            take(";");
            return current.type() == Type.END;
        }

        private boolean insert() {
            if (!take("INTO") || !name()) {
                return false;
            }
            boolean columns = take("(");
            if (columns && !insertColumns()) {
                return false;
            }
            if (take("VALUES")) {
                do {
                    if (!take("(") || !expressions(1) || !take(")")) {
                        return false;
                    }
                } while (take(","));
                return true;
            }
            if (!columns && take("SET")) {
                return assignments(0);
            }
            return query(0);
        }

        private boolean insertColumns() {
            if (!identifier()) {
                return false;
            }
            while (take(",")) {
                if (!identifier()) {
                    return false;
                }
            }
            return take(")");
        }

        private boolean query(int depth) {
            return queries.query(depth);
        }

        private boolean order(int depth) {
            return queries.order(depth);
        }

        private boolean expressions(int depth) {
            return expressions.expressions(depth);
        }

        private boolean expression(int depth) {
            return expressions.expression(depth);
        }

        private boolean sum(int depth) {
            return values.sum(depth);
        }

        private boolean limit() {
            return queries.limit();
        }

        private boolean assignments(int depth) {
            do {
                if (!name() || !take("=") || !expression(depth)) {
                    return false;
                }
            } while (take(","));
            return true;
        }

        private boolean writeTail() {
            if (take("WHERE") && !expression(0)) {
                return false;
            }
            return order(0) && (!take("LIMIT") || limit());
        }

        private boolean alias() {
            return !take("AS") || identifier();
        }

        private boolean name() {
            if (!identifier()) {
                return false;
            }
            while (take(".")) {
                if (!identifier()) {
                    return false;
                }
            }
            return true;
        }

        private boolean identifier() {
            if (current.type() != Type.IDENTIFIER) {
                return false;
            }
            advance();
            return true;
        }

        private boolean count() {
            if (current.type() != Type.INTEGER && current.type() != Type.PARAMETER) {
                return false;
            }
            advance();
            return true;
        }

        private boolean is(String token) {
            return current.text().equals(token);
        }

        private boolean take(String token) {
            if (!is(token)) {
                return false;
            }
            advance();
            return true;
        }

        private void advance() {
            if (!is(";")) {
                shape.append(current.text());
            }
            current = lexer.next();
        }

        /** SELECT、连接、排序与分页语法。共享外层唯一游标与深度上限。 */
        private final class QuerySyntax {
            private boolean query(int depth) {
                if (!select(depth)) {
                    return false;
                }
                while (take("UNION")) {
                    take("ALL");
                    if (!select(depth)) {
                        return false;
                    }
                }
                if (!order(depth)) {
                    return false;
                }
                if (take("LIMIT")) {
                    return limit();
                }
                if (take("OFFSET")) {
                    if (!count()) {
                        return false;
                    }
                    take("ROWS");
                }
                return !take("FETCH")
                        || ((take("FIRST") || take("NEXT"))
                                && count()
                                && take("ROWS")
                                && take("ONLY"));
            }

            private boolean select(int depth) {
                if (!take("SELECT")) {
                    return false;
                }
                if (!take("DISTINCT")) {
                    take("ALL");
                }
                return selectItems(depth) && from(depth) && predicates(depth);
            }

            private boolean selectItems(int depth) {
                do {
                    if (!take("*") && (!expression(depth) || !alias())) {
                        return false;
                    }
                } while (take(","));
                return true;
            }

            private boolean from(int depth) {
                if (!take("FROM")) {
                    return true;
                }
                if (!table(depth)) {
                    return false;
                }
                while (true) {
                    if (take(",")) {
                        if (!table(depth)) {
                            return false;
                        }
                    } else if (isJoin()) {
                        if (!join(depth)) {
                            return false;
                        }
                    } else {
                        return true;
                    }
                }
            }

            private boolean isJoin() {
                return is("JOIN") || is("LEFT") || is("RIGHT") || is("INNER");
            }

            private boolean join(int depth) {
                if (take("LEFT") || take("RIGHT")) {
                    take("OUTER");
                } else {
                    take("INNER");
                }
                return take("JOIN") && table(depth) && take("ON") && expression(depth);
            }

            private boolean predicates(int depth) {
                if (take("WHERE") && !expression(depth)) {
                    return false;
                }
                if (take("GROUP") && (!take("BY") || !expressions(depth))) {
                    return false;
                }
                return !take("HAVING") || expression(depth);
            }

            private boolean table(int depth) {
                if (take("(")) {
                    if (depth >= MAX_DEPTH || !query(depth + 1) || !take(")")) {
                        return false;
                    }
                } else if (!name()) {
                    return false;
                }
                return alias();
            }

            private boolean order(int depth) {
                if (!take("ORDER")) {
                    return true;
                }
                if (!take("BY")) {
                    return false;
                }
                do {
                    if (!expression(depth)) {
                        return false;
                    }
                    if (!take("ASC")) {
                        take("DESC");
                    }
                } while (take(","));
                return true;
            }

            private boolean limit() {
                if (!count()) {
                    return false;
                }
                return !(take(",") || take("OFFSET")) || count();
            }
        }

        /** 布尔组合和比较语法。共享外层唯一游标与深度上限。 */
        private final class ExpressionSyntax {
            private boolean expressions(int depth) {
                if (!expression(depth)) {
                    return false;
                }
                while (take(",")) {
                    if (!expression(depth)) {
                        return false;
                    }
                }
                return true;
            }

            private boolean expression(int depth) {
                if (!and(depth)) {
                    return false;
                }
                while (take("OR")) {
                    if (!and(depth)) {
                        return false;
                    }
                }
                return true;
            }

            private boolean and(int depth) {
                if (!not(depth)) {
                    return false;
                }
                while (take("AND")) {
                    if (!not(depth)) {
                        return false;
                    }
                }
                return true;
            }

            private boolean not(int depth) {
                while (is("NOT")) {
                    if (depth >= MAX_DEPTH) {
                        return false;
                    }
                    depth++;
                    advance();
                }
                return comparison(depth);
            }

            private boolean comparison(int depth) {
                if (!sum(depth)) {
                    return false;
                }
                if (isComparisonOperator()) {
                    advance();
                    return sum(depth);
                }
                if (take("IS")) {
                    take("NOT");
                    if (current.type() != Type.NULL && current.type() != Type.BOOLEAN) {
                        return false;
                    }
                    advance();
                    return true;
                }
                boolean negated = take("NOT");
                if (take("IN")) {
                    if (depth >= MAX_DEPTH || !take("(")) {
                        return false;
                    }
                    return (is("SELECT") ? query(depth + 1) : expressions(depth + 1)) && take(")");
                }
                if (take("BETWEEN")) {
                    return sum(depth) && take("AND") && sum(depth);
                }
                if (take("LIKE")) {
                    return sum(depth);
                }
                return !negated;
            }

            private boolean isComparisonOperator() {
                return is("=") || is("<") || is(">") || is("<=") || is(">=") || is("<>")
                        || is("!=");
            }
        }

        /** 算术、函数调用、CASE 与嵌套值语法。共享外层唯一游标与深度上限。 */
        private final class ValueSyntax {
            private boolean sum(int depth) {
                if (!product(depth)) {
                    return false;
                }
                while (is("+") || is("-")) {
                    advance();
                    if (!product(depth)) {
                        return false;
                    }
                }
                return true;
            }

            private boolean product(int depth) {
                if (!unary(depth)) {
                    return false;
                }
                while (is("*") || is("/") || is("%")) {
                    advance();
                    if (!unary(depth)) {
                        return false;
                    }
                }
                return true;
            }

            private boolean unary(int depth) {
                while (is("+") || is("-") || is("!")) {
                    if (depth >= MAX_DEPTH) {
                        return false;
                    }
                    depth++;
                    advance();
                }
                return primary(depth);
            }

            private boolean primary(int depth) {
                if (isLiteral()) {
                    advance();
                    return true;
                }
                if (take("(")) {
                    return depth < MAX_DEPTH
                            && (is("SELECT") ? query(depth + 1) : expression(depth + 1))
                            && take(")");
                }
                if (take("EXISTS")) {
                    return depth < MAX_DEPTH && take("(") && query(depth + 1) && take(")");
                }
                if (take("CASE")) {
                    return depth < MAX_DEPTH && caseBody(depth + 1);
                }
                if (current.type() == Type.IDENTIFIER) {
                    if (!name()) {
                        return false;
                    }
                    return !take("(") || (depth < MAX_DEPTH && arguments(depth + 1));
                }
                if (take("LEFT") || take("RIGHT")) {
                    return depth < MAX_DEPTH && take("(") && arguments(depth + 1);
                }
                return false;
            }

            private boolean isLiteral() {
                return current.type() == Type.INTEGER
                        || current.type() == Type.NUMBER
                        || current.type() == Type.STRING
                        || current.type() == Type.NULL
                        || current.type() == Type.BOOLEAN
                        || current.type() == Type.PARAMETER;
            }

            private boolean arguments(int depth) {
                if (take(")")) {
                    return true;
                }
                boolean modifier = take("DISTINCT") || take("ALL");
                if (is("*")) {
                    return !modifier && take("*") && take(")");
                }
                return expressions(depth) && take(")");
            }

            private boolean caseBody(int depth) {
                if (!is("WHEN") && !expression(depth)) {
                    return false;
                }
                if (!take("WHEN")) {
                    return false;
                }
                do {
                    if (!expression(depth) || !take("THEN") || !expression(depth)) {
                        return false;
                    }
                } while (take("WHEN"));
                if (take("ELSE") && !expression(depth)) {
                    return false;
                }
                return take("END");
            }
        }
    }

    /** 截断仅影响输出缓冲区，词法与文法仍继续消费完整输入。 */
    private static final class Shape {
        private final StringBuilder output = new StringBuilder(MAX_OUTPUT_LENGTH);
        private int suffixBoundary;
        private boolean truncated;

        void append(String token) {
            if (truncated) {
                return;
            }
            int nextLength = output.length() + (output.isEmpty() ? 0 : 1) + token.length();
            if (nextLength > MAX_OUTPUT_LENGTH) {
                truncated = true;
                return;
            }
            if (!output.isEmpty()) {
                output.append(' ');
            }
            output.append(token);
            if (output.length() <= MAX_OUTPUT_LENGTH - TRUNCATED_SUFFIX.length()) {
                suffixBoundary = output.length();
            }
        }

        String finish() {
            if (truncated) {
                output.setLength(suffixBoundary);
                output.append(TRUNCATED_SUFFIX);
            }
            return output.toString();
        }
    }

    record Result(String sql, String status) {}
}
