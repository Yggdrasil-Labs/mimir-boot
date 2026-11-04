package com.yggdrasil.labs.mybatis.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class SlowSqlTextSanitizerTest {

    private static final String KEYWORDS =
            "SELECT INSERT INTO VALUES UPDATE SET DELETE FROM WHERE AND OR NOT IS IN LIKE"
                    + " BETWEEN EXISTS DISTINCT AS JOIN LEFT RIGHT INNER OUTER ON GROUP BY HAVING"
                    + " ORDER ASC DESC LIMIT OFFSET FETCH FIRST NEXT ROWS ONLY UNION ALL CASE WHEN"
                    + " THEN ELSE END";
    private static final Set<String> ALLOWED_WORDS = Set.of(KEYWORDS.split(" "));
    private static final Set<String> ALLOWED_SYMBOLS =
            Set.of(
                    "?", "(", ")", ",", ".", "=", "<", ">", "!", "+", "-", "*", "/", "%", "<=",
                    ">=", "<>", "!=");

    @Test
    void shouldRejectUnknownTrailingGrammarAndBareAliasesWithoutACommandBlacklist() {
        for (String sql :
                List.of(
                        "SELECT 1 KILL 123",
                        "SELECT 1 CLOSE private_cursor",
                        "SELECT 1 DEALLOCATE private_cursor",
                        "SELECT 1 REVERT",
                        "SELECT a private_alias FROM t",
                        "SELECT a FROM t private_alias",
                        "SELECT WHERE",
                        "INSERT private_identifier",
                        "UPDATE t value=1",
                        "DELETE t",
                        "SELECT a FROM WHERE",
                        "SELECT 1 AS SELECT")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
        assertShape(
                "SELECT a AS private_alias FROM t AS private_table", "SELECT ? AS ? FROM ? AS ?");
    }

    @Test
    void shouldAllowOrdinaryFunctionsWithoutTreatingTheirNamesAsCommands() {
        assertShape(
                "SELECT REPLACE(name, 'secret', 'masked'), IF(flag=1, 'yes', 'no') FROM t",
                "SELECT ? ( ? , ? , ? ) , ? ( ? = ? , ? , ? ) FROM ?");
    }

    @Test
    void shouldBoundAllExpressionNestingAt32WithoutLimitingIterativeLists() {
        assertShape(
                "SELECT " + "(".repeat(32) + "1" + ")".repeat(32),
                "SELECT " + "( ".repeat(32) + "?" + " )".repeat(32));
        assertOmitted("SELECT " + "(".repeat(33) + "1" + ")".repeat(33), "UNSUPPORTED");
        assertOmitted("SELECT " + "NOT ".repeat(33) + "1=1", "UNSUPPORTED");
        assertOmitted("SELECT " + "- ".repeat(33) + "1", "UNSUPPORTED");
        assertOmitted("SELECT " + "fn(".repeat(33) + "1" + ")".repeat(33), "UNSUPPORTED");
    }

    @Test
    void shouldShareOneDepthLimitAcrossFunctionsCasesNotAndSubqueries() {
        for (int depth : List.of(32, 33)) {
            String status = depth == 32 ? "SHAPE" : "UNSUPPORTED";
            List<String> queries =
                    List.of(
                            "SELECT " + "fn(".repeat(depth) + "1" + ")".repeat(depth),
                            "SELECT " + "NOT ".repeat(depth) + "1=1",
                            "SELECT " + "- ".repeat(depth) + "1",
                            "SELECT " + "(SELECT ".repeat(depth) + "1" + ")".repeat(depth),
                            "SELECT "
                                    + "CASE WHEN 1=1 THEN ".repeat(depth)
                                    + "1"
                                    + " END".repeat(depth),
                            "SELECT NOT ("
                                    + "fn(".repeat(depth - 2)
                                    + "1"
                                    + ")".repeat(depth - 2)
                                    + ")");
            for (String sql : queries) {
                var result = SlowSqlTextSanitizer.sanitize(sql);
                assertEquals(status, result.status());
                if (depth == 32) {
                    assertSafeVocabulary(result.sql());
                } else {
                    assertEquals("<omitted>", result.sql());
                }
            }
        }
        assertOmitted("SELECT " + "(".repeat(5000) + "1" + ")".repeat(5000), "UNSUPPORTED");
        assertOmitted("SELECT " + "NOT ".repeat(5000) + "1", "UNSUPPORTED");
    }

    @Test
    void shouldRejectValuesKeywordsAndIdentifiersInTheWrongGrammarPositions() {
        for (String sql :
                List.of(
                        "SELECT 'secret' FROM 123",
                        "SELECT a AS 1",
                        "SELECT a AS NULL",
                        "SELECT a FROM NULL",
                        "INSERT INTO t('secret') VALUES(1)",
                        "SELECT a FROM t ORDER secret",
                        "SELECT a FROM t WHERE x=1 WHERE y=2",
                        "SELECT 1, FROM t",
                        "SELECT AS a",
                        "SELECT 1 INTO private_table",
                        "SELECT 1 FETCH NEXT FROM private_cursor",
                        "SELECT 1 AS private_alias KILL 123",
                        "SELECT (1,2)",
                        "SELECT a IN ()",
                        "SELECT 1 +",
                        "SELECT a NOT private_value",
                        "SELECT a FROM t AS x AS y",
                        "SELECT * FROM t JOIN u",
                        "SELECT a AS x AS y",
                        "SELECT a LIMIT 1.5")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
        assertShape(
                "SELECT a FROM t AS private_alias WHERE x IS NOT NULL",
                "SELECT ? FROM ? AS ? WHERE ? IS NOT ?");
    }

    @Test
    void shouldRequireWritableTableNamesAndRestrictAsteriskPositions() {
        for (String sql :
                List.of(
                        "UPDATE (SELECT 1) SET a=1",
                        "DELETE FROM (SELECT 1)",
                        "SELECT * AS private_alias",
                        "SELECT COUNT(DISTINCT *) FROM t",
                        "SELECT COUNT(ALL *) FROM t")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
        assertShape(
                "SELECT COUNT(DISTINCT a), COUNT(*) FROM t",
                "SELECT ? ( DISTINCT ? ) , ? ( * ) FROM ?");
    }

    @Test
    void shouldValidateMoreThanOneThousandIndependentUnknownTails() {
        Random random = new Random(20261104L);
        String expected =
                "SELECT ? ( ? , ? , ? ) AS ? FROM ? AS ? WHERE ? IN ( ? , ? )"
                        + " AND ? IS ? ORDER BY ? DESC LIMIT ?";
        for (int index = 0; index < 1200; index++) {
            String sql =
                    "SELECT REPLACE(private_column, 'private_"
                            + random.nextLong()
                            + "', 'masked') AS private_alias FROM private_table_"
                            + index
                            + " AS t WHERE flag IN(1,2) AND nullable IS NULL ORDER BY"
                            + " private_column DESC LIMIT 10";
            assertShape(sql, expected);
            assertOmitted(sql + " rogue_command_" + index + "(123)", "UNSUPPORTED");
            assertOmitted(sql + " KILL " + index, "UNSUPPORTED");
            assertOmitted(sql + " CLOSE cursor_" + index, "UNSUPPORTED");
        }
    }

    @Test
    void shouldKeepBinaryOperatorsAndLargeValidListsIterativeAfterTruncation() {
        String sql = "SELECT " + "1+".repeat(10000) + "1";
        var result = SlowSqlTextSanitizer.sanitize(sql);
        assertEquals("SHAPE", result.status());
        assertTrue(result.sql().endsWith(" [truncated]"));
        assertSafeVocabulary(result.sql());
        assertOmitted(sql + " CLOSE private_cursor", "UNSUPPORTED");
        assertOmitted(sql + " KILL 123", "UNSUPPORTED");
        assertOmitted(sql + " private_alias", "UNSUPPORTED");
        assertOmitted(sql + " WHERE", "UNSUPPORTED");
    }

    @Test
    void shouldReplaceEveryIdentifierAndValueWithPlaceholders() {
        SlowSqlTextSanitizer.Result result =
                SlowSqlTextSanitizer.sanitize(
                        "select private_column from private_table where secret_key ="
                                + " 'private-value' and amount = 123456");

        assertEquals("SHAPE", result.status());
        assertEquals("SELECT ? FROM ? WHERE ? = ? AND ? = ?", result.sql());
    }

    @Test
    void shouldSupportOnlyTheFourDmlStartingKeywords() {
        assertShape("SELECT * FROM private_table", "SELECT * FROM ?");
        assertShape(
                "INSERT INTO private_table(secret) VALUES('hidden')",
                "INSERT INTO ? ( ? ) VALUES ( ? )");
        assertShape("UPDATE private_table SET secret = 42", "UPDATE ? SET ? = ?");
        assertShape("DELETE FROM private_table WHERE secret = ?", "DELETE FROM ? WHERE ? = ?");
        for (String sql :
                List.of(
                        "WITH x AS (SELECT 1) SELECT * FROM x",
                        "CALL hidden()",
                        "EXEC hidden",
                        "CREATE TABLE hidden(id int)",
                        "(SELECT 1)",
                        "SELECTOR hidden",
                        "123 SELECT hidden",
                        "? SELECT hidden")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
    }

    @Test
    void shouldPreserveExactlyTheAsciiKeywordWhitelist() {
        Set<String> observed = new java.util.HashSet<>();
        List<String> queries =
                List.of(
                        "SELECT DISTINCT a AS x FROM t AS y LEFT OUTER JOIN u AS z ON y.id=z.id"
                            + " RIGHT JOIN v ON z.id=v.id INNER JOIN w ON v.id=w.id WHERE a NOT"
                            + " IN(1,2) AND b IS NOT NULL OR c NOT LIKE 'hidden' GROUP BY a HAVING"
                            + " COUNT(*)>1 ORDER BY a ASC,b DESC LIMIT 10 OFFSET 20",
                        "SELECT CASE WHEN a BETWEEN 1 AND 2 THEN b ELSE c END FROM t"
                                + " WHERE EXISTS(SELECT 1 FROM u) UNION ALL SELECT a FROM t"
                                + " OFFSET 1 ROWS FETCH NEXT 2 ROWS ONLY",
                        "SELECT a FROM t FETCH FIRST 2 ROWS ONLY",
                        "INSERT INTO t(a) VALUES(1),(2)",
                        "UPDATE t SET a=1",
                        "DELETE FROM t");
        for (String sql : queries) {
            var result = SlowSqlTextSanitizer.sanitize(sql.toLowerCase(Locale.ROOT));
            assertEquals("SHAPE", result.status());
            assertSafeVocabulary(result.sql());
            for (String token : result.sql().split(" ")) {
                if (ALLOWED_WORDS.contains(token)) {
                    observed.add(token);
                }
            }
        }
        assertEquals(ALLOWED_WORDS, observed);
        assertShape(
                "SeLeCt DISTINCT a FrOm b wHeRe a BeTwEeN 1 AnD 2",
                "SELECT DISTINCT ? FROM ? WHERE ? BETWEEN ? AND ?");
        assertShape(
                "SELECT SELECTOR, _select, select1, xSELECT, ABS(1), NOW() FROM t",
                "SELECT ? , ? , ? , ? , ? ( ? ) , ? ( ) FROM ?");
    }

    @Test
    void shouldMaskBooleanAndNullTokensAtEveryPositionAndCase() {
        for (String value :
                List.of(
                        "TRUE", "true", "TrUe", "FALSE", "false", "FaLsE", "NULL", "null",
                        "NuLl")) {
            assertShape(
                    "INSERT INTO hidden(flag) VALUES(" + value + ")",
                    "INSERT INTO ? ( ? ) VALUES ( ? )");
            assertShape(
                    "SELECT " + value + " FROM hidden WHERE flag IS " + value,
                    "SELECT ? FROM ? WHERE ? IS ?");
            assertShape("UPDATE hidden SET flag=" + value, "UPDATE ? SET ? = ?");
        }
    }

    @Test
    void shouldMaskCompleteDecimalAndScientificNumericTokens() {
        assertShape(
                "SELECT 0, 012345, 123456789012345678901234567890, 12.50, .75, 3.,"
                        + " 1e9, 2E+12, 3.14e-9, -42, +7 FROM hidden",
                "SELECT ? , ? , ? , ? , ? , ? , ? , ? , ? , - ? , + ? FROM ?");
        for (String value : List.of("1e", "1e+", "1.2.3", "1hidden", "0xDEADBEEF", "0b101")) {
            assertOmitted("SELECT " + value, "UNSUPPORTED");
        }
    }

    @Test
    void shouldMaskStandardEscapedStringsWithoutInterpretingTheirContents() {
        assertShape(
                "SELECT '', 'hidden''secret', 'a;--b/*c*/',"
                        + " '中文\n秘密\t\"quoted\"', 'literal?()' FROM hidden",
                "SELECT ? , ? , ? , ? , ? FROM ?");
    }

    @Test
    void shouldOmitBackslashAndUnterminatedStrings() {
        for (String sql :
                List.of(
                        "SELECT 'hidden\\secret'",
                        "SELECT 'hidden\\'rest'",
                        "SELECT 'hidden",
                        "SELECT 'hidden''",
                        "SELECT 'ok' 'unterminated")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
    }

    @Test
    void shouldOmitOracleAlternativeQuotesRatherThanEmitTheirLiteralKeywords() {
        assertOmitted("SELECT q'!alpha'WHERE'omega!' FROM dual", "UNSUPPORTED");
        assertOmitted("SELECT Q'[alpha'UNION'omega]' FROM dual", "UNSUPPORTED");
        assertOmitted("SELECT nq'{alpha'ORDER'omega}' FROM dual", "UNSUPPORTED");
        assertOmitted("SELECT NQ'<alpha'FROM'omega>' FROM dual", "UNSUPPORTED");
    }

    @Test
    void shouldOmitOtherAdjacentStringPrefixesInsteadOfAssumingStandardQuotes() {
        for (String prefix :
                List.of(
                        "n",
                        "N",
                        "e",
                        "E",
                        "b",
                        "B",
                        "x",
                        "X",
                        "r",
                        "R",
                        "br",
                        "Rb",
                        "_utf8",
                        "_utf8mb4",
                        "_binary",
                        "arbitrary_prefix")) {
            assertOmitted("SELECT " + prefix + "'hidden' FROM private_table", "UNSUPPORTED");
        }
        assertShape("SELECT 'hidden' FROM private_table", "SELECT ? FROM ?");
        assertShape("SELECT prefix_column, 'hidden' FROM private_table", "SELECT ? , ? FROM ?");
    }

    @Test
    void shouldOmitTripleQuotesAtTheStartMiddleOrEndOfAString() {
        for (String sql :
                List.of(
                        "SELECT '''alpha'WHERE'omega'''",
                        "SELECT '''alpha'''",
                        "SELECT ''''",
                        "SELECT 'alpha'''",
                        "SELECT 'alpha'''WHERE'omega'",
                        "SELECT 'alpha''''WHERE''''omega'",
                        "SELECT 'safe', '''alpha'UNION'omega'''",
                        "SELECT '''alpha\nWHERE omega'''")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
        for (int quoteCount = 3; quoteCount <= 8; quoteCount++) {
            String quotes = "'".repeat(quoteCount);
            assertOmitted("SELECT " + quotes + "alpha" + quotes, "UNSUPPORTED");
        }
        assertShape("SELECT 'alpha''WHERE''omega'", "SELECT ?");
        assertShape("SELECT 'safe' /* '''hidden''' */ -- '''hidden'''", "SELECT ?");
    }

    @Test
    void shouldDiscardCommentsAndNormalizeWhitespace() {
        assertShape(
                "-- leading-secret\r\n/*hidden*/\tselect/**/secret_column\nfrom"
                        + " hidden_table where secret_column=/*value-secret*/1 -- tail-secret",
                "SELECT ? FROM ? WHERE ? = ?");
        assertShape(
                "SELECT a=-- secret\n1, b+/*secret*/2; /*trailing*/ -- tail",
                "SELECT ? = ? , ? + ?");
        assertShape("SELECT a/* hidden Unicode 秘密 and ' and \\ */FROM b", "SELECT ? FROM ?");
    }

    @Test
    void shouldOmitAmbiguousDoubleMinusWithoutACommentSeparator() {
        for (String sql :
                List.of(
                        "SELECT 1 --2; SELECT 3",
                        "SELECT 1--2; SELECT 3",
                        "SELECT a=--2; SELECT 3",
                        "SELECT 1;--2; SELECT 3",
                        "--hidden\nSELECT 1",
                        "SELECT 1 --hidden",
                        "SELECT 1 ---2",
                        "SELECT 1 --\u00a0hidden")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
        assertShape("SELECT 1 - -2", "SELECT ? - - ?");
    }

    @Test
    void shouldAcceptDoubleMinusWithAnUnambiguousSeparatorOrEndOfInput() {
        for (String separator : List.of(" ", "\t", "\n", "\r\n")) {
            String suffix = separator.contains("\n") ? "" : "hidden";
            assertShape("SELECT 1 --" + separator + suffix, "SELECT ?");
        }
        assertShape("SELECT 1 --", "SELECT ?");
        assertShape("SELECT 1; --", "SELECT ?");
        assertOmitted("--", "UNAVAILABLE");
    }

    @Test
    void shouldOmitAmbiguousControlOrLineSeparatorsInsideLineComments() {
        for (String sql :
                List.of(
                        "SELECT 1 -- secret\rWHERE 2=3",
                        "SELECT 1 -- secret\r",
                        "SELECT 1 --\r",
                        "-- secret\rSELECT 1",
                        "SELECT 1; -- secret\rWHERE 2=3",
                        "SELECT 1 -- secret\r\r\nWHERE 2=3")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
        for (String separator :
                List.of(
                        "\u0000", "\u0001", "\u000b", "\f", "\u001b", "\u007f", "\u0085", "\u2028",
                        "\u2029")) {
            assertOmitted("SELECT 1 -- hidden" + separator + "WHERE 2=3", "UNSUPPORTED");
            assertOmitted("SELECT 1 -- hidden" + separator + "; SELECT 2", "UNSUPPORTED");
            assertOmitted("SELECT 1 --" + separator + "hidden", "UNSUPPORTED");
        }
        assertShape("SELECT 1 -- 中文秘密\t注释\r\nWHERE 2=3", "SELECT ? WHERE ? = ?");
        assertShape("SELECT 1 -- 中文秘密\t注释\nWHERE 2=3", "SELECT ? WHERE ? = ?");
        assertShape("SELECT 1; -- 中文秘密\r\n", "SELECT ?");
    }

    @Test
    void shouldOmitExecutableNestedAndUnterminatedBlockComments() {
        for (String comment :
                List.of(
                        "/*!hidden*/",
                        "/*M!hidden*/",
                        "/*m!hidden*/",
                        "/*hidden /* nested */ comment*/",
                        "/*hidden")) {
            assertOmitted("SELECT 1 " + comment, "UNSUPPORTED");
            assertOmitted(comment + " SELECT 1", "UNSUPPORTED");
            assertOmitted("SELECT 1; " + comment, "UNSUPPORTED");
        }
    }

    @Test
    void shouldOmitTiDbExecutableCommentsWithoutExposingTheirContents() {
        for (String comment :
                List.of(
                        "/*T! secret_value*/",
                        "/*T![feature_id] secret_value*/",
                        "/*T! secret_value",
                        "/*T![feature_id] secret_value")) {
            SlowSqlTextSanitizer.Result result =
                    SlowSqlTextSanitizer.sanitize("SELECT 1 " + comment);
            assertEquals("UNSUPPORTED", result.status());
            assertEquals("<omitted>", result.sql());
            assertFalse(result.sql().contains("secret_value"));
            assertFalse(result.sql().contains("feature_id"));
        }
    }

    @Test
    void shouldOmitQuotedIdentifiersDollarQuotesJdbcEscapesAndNonAsciiTokens() {
        for (String value :
                List.of(
                        "\"hidden\"",
                        "`hidden`",
                        "[hidden]",
                        "$$hidden$$",
                        "$tag$hidden$tag$",
                        "{fn hidden()}",
                        "中文秘密",
                        "selec\u0131t",
                        "table$hidden",
                        "hidden\u0000value",
                        "hidden\u001bvalue",
                        "a\u00a0FROM b")) {
            assertOmitted("SELECT " + value, "UNSUPPORTED");
        }
    }

    @Test
    void shouldPreserveOnlyWhitelistedSymbolsAndOperators() {
        assertShape(
                "SELECT ?, (a), a.b, a=b, a<b, a>b, !a, a+b, a-b, a*b, a/b, a%b,"
                        + " a<=b, a>=b, a<>b, a!=b FROM hidden",
                "SELECT ? , ( ? ) , ? . ? , ? = ? , ? < ? , ? > ? , ! ? , ? + ? , ? - ? ,"
                        + " ? * ? , ? / ? , ? % ? , ? <= ? , ? >= ? , ? <> ? , ? != ? FROM ?");
    }

    @Test
    void shouldOmitUnsupportedOperatorsInsteadOfSplittingThemIntoSafeSymbols() {
        for (String operator :
                List.of(
                        "::", "||", "&&", "->", "->>", "@>", "#>", "<=>", "==", "**", "//", "%%",
                        "<<", ">>", "!~", "~", "^", ":", "$", "&", "|")) {
            assertOmitted("SELECT a" + operator + "b FROM hidden", "UNSUPPORTED");
        }
    }

    @Test
    void shouldAllowOneTrailingSemicolonButRejectAdditionalStatementsOrSemicolons() {
        assertShape("SELECT 1;", "SELECT ?");
        assertShape("SELECT ';'; -- hidden\n /* hidden */", "SELECT ?");
        for (String sql :
                List.of(
                        "SELECT 1; SELECT 2",
                        "SELECT 1;;",
                        "SELECT 1; hidden",
                        ";SELECT 1",
                        "SELECT 1;/*hidden*/;")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
    }

    @Test
    void shouldOmitRepeatedTopLevelDmlEvenWithoutSemicolons() {
        for (String sql :
                List.of(
                        "SELECT 1 SELECT 2",
                        "SELECT 1 UPDATE hidden SET value=2",
                        "SELECT 1 DELETE FROM hidden",
                        "SELECT 1 INSERT INTO hidden VALUES(2)",
                        "UPDATE hidden SET value=1 UPDATE hidden SET value=2",
                        "UPDATE hidden SET value=1 SELECT 2",
                        "DELETE FROM hidden DELETE FROM hidden",
                        "INSERT INTO hidden VALUES(1) SELECT 2",
                        "INSERT INTO hidden SELECT 1 SELECT 2",
                        "SELECT (SELECT 1) SELECT 2",
                        "SELECT 1 UNION SELECT 2 SELECT 3",
                        "SELECT 1 UNION ALL SELECT 2 DELETE FROM hidden")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
    }

    @Test
    void shouldRetainSubqueriesUnionSelectAndInsertSelectWithinOneStatement() {
        assertShape(
                "SELECT (SELECT 1) FROM hidden WHERE EXISTS (SELECT 2 FROM private_table)",
                "SELECT ( SELECT ? ) FROM ? WHERE EXISTS ( SELECT ? FROM ? )");
        assertShape(
                "SELECT 1 UNION SELECT 2 UNION ALL SELECT 3",
                "SELECT ? UNION SELECT ? UNION ALL SELECT ?");
        assertShape(
                "INSERT INTO hidden(value) SELECT value FROM private_table UNION ALL SELECT 2",
                "INSERT INTO ? ( ? ) SELECT ? FROM ? UNION ALL SELECT ?");
        assertShape(
                "INSERT INTO hidden(value) VALUES((SELECT 1))",
                "INSERT INTO ? ( ? ) VALUES ( ( SELECT ? ) )");
        assertShape(
                "UPDATE hidden SET value=(SELECT 1 FROM private_table)",
                "UPDATE ? SET ? = ( SELECT ? FROM ? )");
        assertShape(
                "DELETE FROM hidden WHERE EXISTS (SELECT 1 FROM private_table)",
                "DELETE FROM ? WHERE EXISTS ( SELECT ? FROM ? )");
        assertShape(
                "SELECT 1 UNION /* hidden */ ALL -- hidden\nSELECT 2",
                "SELECT ? UNION ALL SELECT ?");
    }

    @Test
    void shouldOmitAmbiguousUnionSequencesAndUnbalancedParentheses() {
        for (String sql :
                List.of(
                        "SELECT 1 UNION",
                        "SELECT 1 UNION ALL",
                        "SELECT 1 UNION UPDATE hidden SET value=2",
                        "SELECT 1 UNION ALL ALL SELECT 2",
                        "SELECT 1 UNION private_identifier SELECT 2",
                        "UPDATE hidden SET value=1 UNION SELECT 2",
                        "INSERT INTO hidden VALUES(1) UNION SELECT 2",
                        "SELECT ((SELECT 1)",
                        "SELECT 1)")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
    }

    @Test
    void shouldOmitKnownNonDmlStatementsBeforeMaskingTheirOriginalTokens() {
        for (String sql :
                List.of(
                        "SELECT 1 PRINT 'hidden'",
                        "SELECT 1 WAITFOR DELAY '00:00:00'",
                        "SELECT 1 EXEC hidden",
                        "SELECT 1 EXECUTE hidden",
                        "SELECT 1 CALL hidden()",
                        "SELECT 1 DECLARE hidden int",
                        "SELECT 1 CREATE TABLE hidden(id int)",
                        "SELECT 1 DROP TABLE hidden",
                        "SELECT 1 BEGIN PRINT 'hidden' END",
                        "SELECT 1 COMMIT",
                        "SELECT 1 RETURN",
                        "SELECT 1 RAISERROR ('hidden',16,1)",
                        "SELECT 1 pRiNt 'hidden'",
                        "SELECT 1 /* hidden */ print /* hidden */ 'hidden'",
                        "SELECT 1 -- hidden\nWAITFOR DELAY '00:00:00'")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
        for (String word :
                List.of(
                        "EXEC",
                        "CALL",
                        "DECLARE",
                        "PRINT",
                        "WAITFOR",
                        "REPLACE",
                        "MERGE",
                        "CREATE",
                        "ALTER",
                        "DROP",
                        "TRUNCATE",
                        "GRANT",
                        "REVOKE",
                        "DENY",
                        "COMMIT",
                        "ROLLBACK",
                        "SAVEPOINT",
                        "BEGIN",
                        "DO",
                        "IF",
                        "WHILE",
                        "RETURN",
                        "THROW",
                        "RAISERROR",
                        "GOTO",
                        "BREAK",
                        "CONTINUE",
                        "USE",
                        "DBCC",
                        "BACKUP",
                        "RESTORE",
                        "CHECKPOINT",
                        "SHUTDOWN",
                        "RECONFIGURE",
                        "BULK",
                        "LOCK",
                        "UNLOCK")) {
            assertOmitted("SELECT 1 " + word.toLowerCase(Locale.ROOT) + " hidden", "UNSUPPORTED");
        }
        assertShape(
                "SELECT waitfor_delay, printable, execute_id FROM private_table",
                "SELECT ? , ? , ? FROM ?");
        assertShape("SELECT 'PRINT WAITFOR EXEC' /* DECLARE */ -- CALL", "SELECT ?");
    }

    @Test
    void shouldOmitTopLevelSetStatementsOutsideTheInitialDmlAssignment() {
        for (String sql :
                List.of(
                        "SELECT 1 SET NOCOUNT ON",
                        "DELETE FROM hidden SET NOCOUNT ON",
                        "UPDATE hidden SET value=1 SET NOCOUNT ON",
                        "INSERT INTO hidden VALUES(1) SET value=2")) {
            assertOmitted(sql, "UNSUPPORTED");
        }
        assertShape("UPDATE hidden SET value=1, another=2", "UPDATE ? SET ? = ? , ? = ?");
        assertShape("INSERT INTO hidden SET value=1", "INSERT INTO ? SET ? = ?");
    }

    @Test
    void shouldReportUnavailableWithoutEchoingEmptyOrCommentOnlyInputs() {
        assertOmitted(null, "UNAVAILABLE");
        for (String value : List.of("", " \t\n\r\f", "\u2003", "-- hidden", "/*hidden*/")) {
            assertOmitted(value, "UNAVAILABLE");
        }
    }

    @Test
    void shouldCheckTheOriginalInputLengthBeforeScanningOrTruncating() {
        String sql = "SELECT '" + "s".repeat(65527) + "'";
        assertEquals(65536, sql.length());
        assertShape(sql, "SELECT ?");
        assertOmitted(sql + "x", "TOO_LONG");
        assertOmitted(" ".repeat(65536), "UNAVAILABLE");
        assertOmitted(" ".repeat(65537), "TOO_LONG");
        assertOmitted("\"" + "s".repeat(65536), "TOO_LONG");
    }

    @Test
    void shouldRetainAnExact4096CharacterTemplateWithoutTruncating() {
        String sql = "SELECT " + "1,".repeat(1022) + "1";
        SlowSqlTextSanitizer.Result result = SlowSqlTextSanitizer.sanitize(sql);

        assertEquals("SHAPE", result.status());
        assertEquals(4096, result.sql().length());
        assertFalse(result.sql().contains("[truncated]"));
        assertSafeVocabulary(result.sql());
    }

    @Test
    void shouldBoundA4097CharacterTemplateWithTheFixedSuffixAtATokenBoundary() {
        SlowSqlTextSanitizer.Result result =
                SlowSqlTextSanitizer.sanitize("SELECT DISTINCT " + "1,".repeat(1020) + "1");

        assertEquals("SHAPE", result.status());
        assertTrue(result.sql().endsWith(" [truncated]"));
        assertTrue(result.sql().length() <= 4096);
        assertSafeVocabulary(result.sql());

        result =
                SlowSqlTextSanitizer.sanitize(
                        "SELECT " + "CASE WHEN 1=1 THEN 2 ELSE 3 END,".repeat(1000) + "1");
        assertEquals("SHAPE", result.status());
        assertTrue(result.sql().endsWith(" [truncated]"));
        assertTrue(result.sql().length() <= 4096);
        assertSafeVocabulary(result.sql());
    }

    @Test
    void shouldContinueValidatingAfterTheTemplateLimitAndOmitUnsafeTails() {
        String prefix = "SELECT " + "1,".repeat(3000);
        for (String tail :
                List.of(
                        "'hidden\\secret'",
                        "/*unterminated",
                        "/*!hidden*/",
                        "\"hidden\"",
                        "$hidden$",
                        "a->b",
                        ";DELETE FROM hidden",
                        "q'!alpha'WHERE'omega!'",
                        "NQ'[alpha'UNION'omega]'",
                        "_utf8'secret'",
                        "--2; SELECT 3",
                        "'''alpha'WHERE'omega'''",
                        "'alpha'''",
                        "SELECT 2",
                        "UPDATE hidden SET value=2",
                        "-- hidden\rWHERE 2=3",
                        "-- hidden\u0000; SELECT 2",
                        "-- hidden\u0085WHERE 2=3",
                        "-- hidden\u2028WHERE 2=3",
                        "-- hidden\u2029WHERE 2=3",
                        "PRINT 'hidden'",
                        "WAITFOR DELAY '00:00:00'",
                        "EXEC hidden",
                        "SET NOCOUNT ON",
                        "中文秘密")) {
            assertOmitted(prefix + tail, "UNSUPPORTED");
        }
        SlowSqlTextSanitizer.Result result =
                SlowSqlTextSanitizer.sanitize(prefix + "'hidden-secret'");
        assertEquals("SHAPE", result.status());
        assertTrue(result.sql().endsWith(" [truncated]"));
        assertFalse(result.sql().contains("hidden"));
        assertFalse(result.sql().contains("private"));
    }

    @Test
    void shouldNeverEmitRawFragmentsForDeterministicAdversarialInputs() {
        Random random = new Random(20261104L);
        List<String> fragments =
                List.of(
                        "private_secret",
                        "'sensitive''value'",
                        "123e-45",
                        "/*comment-secret*/",
                        "-- line-secret\n",
                        "--2; SELECT 3",
                        "q'!alpha'WHERE'omega!'",
                        "'''alpha'WHERE'omega'''",
                        "TRUE",
                        "null",
                        "SELECT",
                        "FROM",
                        "WHERE",
                        "=",
                        "<=",
                        "?",
                        "(",
                        ")",
                        ",",
                        " ",
                        "\n",
                        "'",
                        "\\",
                        "\"",
                        "\u0000",
                        "\u001b",
                        "中文",
                        "\ud800",
                        "->",
                        "::",
                        ";",
                        "/*!secret*/");
        for (int iteration = 0; iteration < 1000; iteration++) {
            StringBuilder input = new StringBuilder("SELECT ");
            for (int count = random.nextInt(100); count > 0; count--) {
                input.append(fragments.get(random.nextInt(fragments.size()))).append(' ');
            }
            SlowSqlTextSanitizer.Result result = SlowSqlTextSanitizer.sanitize(input.toString());
            if ("SHAPE".equals(result.status())) {
                assertSafeVocabulary(result.sql());
            } else {
                assertEquals("UNSUPPORTED", result.status());
                assertEquals("<omitted>", result.sql());
            }
        }
    }

    @Test
    void shouldKeepConcurrentCallsIndependent() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<SlowSqlTextSanitizer.Result>> calls = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                int callIndex = index;
                calls.add(
                        () ->
                                SlowSqlTextSanitizer.sanitize(
                                        callIndex % 2 == 0
                                                ? "SELECT 'secret" + callIndex + "' FROM hidden"
                                                : "SELECT \"secret" + callIndex + "\""));
            }
            var results = executor.invokeAll(calls);
            for (int index = 0; index < results.size(); index++) {
                SlowSqlTextSanitizer.Result result = results.get(index).get();
                assertEquals(index % 2 == 0 ? "SHAPE" : "UNSUPPORTED", result.status());
                assertEquals(index % 2 == 0 ? "SELECT ? FROM ?" : "<omitted>", result.sql());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertShape(String sql, String expected) {
        SlowSqlTextSanitizer.Result result = SlowSqlTextSanitizer.sanitize(sql);
        assertEquals("SHAPE", result.status());
        assertEquals(expected, result.sql());
        assertSafeVocabulary(result.sql());
    }

    private static void assertOmitted(String sql, String status) {
        SlowSqlTextSanitizer.Result result = SlowSqlTextSanitizer.sanitize(sql);
        assertEquals(status, result.status());
        assertEquals("<omitted>", result.sql());
    }

    private static void assertSafeVocabulary(String shape) {
        assertTrue(shape.length() <= 4096);
        assertTrue(shape.chars().noneMatch(Character::isISOControl));
        String tokens = shape.replace(" [truncated]", "");
        for (String token : tokens.split(" ")) {
            assertTrue(
                    ALLOWED_WORDS.contains(token) || ALLOWED_SYMBOLS.contains(token),
                    "模板只允许固定词表中的完整 token");
        }
    }
}
