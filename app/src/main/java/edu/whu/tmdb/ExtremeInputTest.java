package edu.whu.tmdb;

import java.util.ArrayList;
import java.util.List;

import edu.whu.tmdb.query.operations.utils.MemConnect;
import edu.whu.tmdb.query.operations.utils.SelectResult;
import edu.whu.tmdb.storage.memory.Tuple;
import edu.whu.tmdb.util.DbOperation;

/**
 * Extreme and hostile input smoke tests.
 *
 * These cases focus on crash resistance and state integrity around malformed SQL,
 * SQL-looking string payloads, boundary values, and repeated deputy migrations.
 */
public class ExtremeInputTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Totem extreme / hostile input tests");
        System.out.println("========================================\n");

        DbOperation.resetDB();

        testMalformedSqlDoesNotCrash();
        testSqlLikeStringPayloadsRemainData();
        testBoundaryValuesAndLongStrings();
        testRejectedDeputyTargetsDoNotCorruptState();
        testBulkGroupDeputyMigration();

        System.out.println("\n========================================");
        System.out.println("  " + passed + " passed, " + failed + " failed");
        System.out.println("========================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testMalformedSqlDoesNotCrash() {
        section("Malformed and hostile syntax");

        String[] hostileSql = {
            "",
            "   ",
            ";",
            ";;;;",
            "SELECT FROM;",
            "SELECT * FROM MissingTable;",
            "INSERT INTO MissingTable VALUES (1);",
            "CREATE CLASS Broken (id INT, name);",
            "UPDATE MissingTable SET x = 1 WHERE id = 1;",
            "DELETE FROM MissingTable WHERE id = 1;",
            "DROP CLASS MissingTable;",
            "CREATE CLASS A (id INT); CREATE CLASS B (id INT);",
            "INSERT INTO Student VALUES (1, 'x'); -- comment tail",
            "SELECT dept, AVG(salary) FROM Employee GROUP BY dept HAVING AVG(salary) >>> 1;"
        };

        for (String sql : hostileSql) {
            assertNoCrash("no crash: " + printable(sql), sql);
        }
        assertKnownParserHardError(
            "unterminated string reports parser hard error",
            "SELECT * FROM Student WHERE name = 'unterminated");
    }

    private static void testSqlLikeStringPayloadsRemainData() {
        section("SQL-looking payloads stored as strings");

        exec("CREATE CLASS PayloadBox (id INT, payload STRING);");
        exec("INSERT INTO PayloadBox VALUES (1, 'DROP TABLE PayloadBox; --');");
        exec("INSERT INTO PayloadBox VALUES (2, 'UNION SELECT password FROM users');");
        exec("INSERT INTO PayloadBox VALUES (3, '<script>alert(1)</script>');");
        exec("INSERT INTO PayloadBox VALUES (4, 'Robert''); DROP CLASS PayloadBox; --');");

        assertTupleContains("table survives SQL-looking string payload", "PayloadBox", 1,
            "DROP TABLE PayloadBox; --");
        assertTupleContains("union text remains data", "PayloadBox", 1,
            "UNION SELECT password FROM users");
        assertTupleContains("script text remains data", "PayloadBox", 1,
            "<script>alert(1)</script>");
        assertTupleContains("escaped quote payload remains data", "PayloadBox", 1,
            "Robert'); DROP CLASS PayloadBox; --");
        String result = resultString("SELECT * FROM PayloadBox;");
        assertContains("payload table still queryable", result, "PayloadBox");
    }

    private static void testBoundaryValuesAndLongStrings() {
        section("Boundary values and long strings");

        exec("CREATE CLASS BoundaryBox (id INT, label STRING, score INT);");
        exec("INSERT INTO BoundaryBox VALUES (1, '', 0);");
        exec("INSERT INTO BoundaryBox VALUES (2, 'negative-min', -2147483648);");
        exec("INSERT INTO BoundaryBox VALUES (3, 'positive-max', 2147483647);");

        StringBuilder longLabel = new StringBuilder();
        for (int i = 0; i < 2048; i++) {
            longLabel.append((char) ('a' + (i % 26)));
        }
        exec("INSERT INTO BoundaryBox VALUES (4, '" + longLabel + "', 42);");

        String result = resultString("SELECT * FROM BoundaryBox;");
        assertContains("minimum int preserved", result, "-2147483648");
        assertContains("maximum int preserved", result, "2147483647");
        assertTupleValueContains("long string prefix preserved", "BoundaryBox", 1, longLabel.substring(0, 80));
        assertContains("empty string row present", result, "|1");
    }

    private static void testRejectedDeputyTargetsDoNotCorruptState() {
        section("Rejected deputy target attacks");

        exec("CREATE CLASS SourceBox (id INT, name STRING, score INT);");
        exec("CREATE SELECTDEPUTY ManualBox AS SELECT name, score FROM SourceBox;");
        exec("CREATE SELECTDEPUTY StrictBox AS SELECT name, score FROM SourceBox WHERE score > 80;");
        exec("CREATE CLASS PlainBox (id INT, name STRING, score INT);");

        int beforeSource = tupleCount("SourceBox");
        int beforeManual = tupleCount("ManualBox");
        int beforeStrict = tupleCount("StrictBox");
        int beforePlain = tupleCount("PlainBox");

        assertRejectedWithoutCrash("reject strict explicit deputy target",
            "INSERT INTO SourceBox VALUES (1, 'strict-attack', 99) INTO StrictBox;");
        assertRejectedWithoutCrash("reject non-deputy target",
            "INSERT INTO SourceBox VALUES (2, 'plain-attack', 99) INTO PlainBox;");
        assertRejectedWithoutCrash("reject missing deputy target",
            "INSERT INTO SourceBox VALUES (3, 'missing-attack', 99) INTO DoesNotExist;");

        assertEquals("SourceBox unchanged after rejected attacks", beforeSource, tupleCount("SourceBox"));
        assertEquals("ManualBox unchanged after rejected attacks", beforeManual, tupleCount("ManualBox"));
        assertEquals("StrictBox unchanged after rejected attacks", beforeStrict, tupleCount("StrictBox"));
        assertEquals("PlainBox unchanged after rejected attacks", beforePlain, tupleCount("PlainBox"));

        exec("INSERT INTO SourceBox VALUES (4, 'valid-manual', 100) INTO ManualBox;");
        assertEquals("valid non-strict explicit insert updates source", beforeSource + 1, tupleCount("SourceBox"));
        assertEquals("valid non-strict explicit insert updates deputy", beforeManual + 1, tupleCount("ManualBox"));
    }

    private static void testBulkGroupDeputyMigration() {
        section("Bulk GroupDeputy migration stress");

        exec("CREATE CLASS StressEmployee (id INT, dept STRING, salary INT);");
        for (int i = 1; i <= 120; i++) {
            String dept = "D" + (i % 6);
            int salary = 1000 + (i % 10) * 500;
            exec("INSERT INTO StressEmployee VALUES (" + i + ", '" + dept + "', " + salary + ");");
        }

        exec("CREATE GROUPDEPUTY StressHighDept AS " +
            "SELECT dept, AVG(salary) AS avg_sal, COUNT(*) AS cnt " +
            "FROM StressEmployee GROUP BY dept HAVING AVG(salary) >= 3000;");

        assertNoDanglingPointers("initial stress deputy pointers", "StressEmployee", "StressHighDept");

        for (int i = 1; i <= 40; i++) {
            exec("UPDATE StressEmployee SET salary = 9000 WHERE id = " + i + ";");
        }
        for (int i = 41; i <= 80; i++) {
            exec("DELETE FROM StressEmployee WHERE id = " + i + ";");
        }
        for (int i = 121; i <= 180; i++) {
            exec("INSERT INTO StressEmployee VALUES (" + i + ", 'D" + (i % 6) + "', 7000);");
        }

        assertNoDanglingPointers("post-mutation stress deputy pointers", "StressEmployee", "StressHighDept");
        String result = resultString("SELECT * FROM StressHighDept ORDER BY dept;");
        assertContains("stress deputy remains queryable", result, "StressHighDept");
        assertContains("stress deputy has D0 group", result, "D0");
    }

    private static void section(String name) {
        System.out.println("\n--- " + name + " ---");
    }

    private static void exec(String sql) {
        try {
            Main.execute(sql);
            System.out.println("  [OK] " + printable(sql));
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + printable(sql));
            System.out.println("         " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
        }
    }

    private static void assertNoCrash(String label, String sql) {
        try {
            Main.execute(sql);
            System.out.println("  [PASS] " + label);
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + label);
            System.out.println("         " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
        }
    }

    private static void assertRejectedWithoutCrash(String label, String sql) {
        try {
            Main.execute(sql);
            System.out.println("  [FAIL] " + label);
            System.out.println("         expected rejection, but statement was accepted");
            failed++;
        } catch (Exception e) {
            System.out.println("  [PASS] " + label + " -> " + e.getMessage());
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + label);
            System.out.println("         hard failure: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
        }
    }

    private static void assertKnownParserHardError(String label, String sql) {
        try {
            Main.execute(sql);
            System.out.println("  [FAIL] " + label);
            System.out.println("         expected parser hard error");
            failed++;
        } catch (net.sf.jsqlparser.parser.TokenMgrError e) {
            System.out.println("  [PASS] " + label + " -> " + e.getClass().getSimpleName());
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + label);
            System.out.println("         unexpected: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
        }
    }

    private static String resultString(String sql) {
        try {
            SelectResult result = Main.execute(sql);
            if (result == null) {
                return "";
            }
            return DbOperation.getResultString(result);
        } catch (Throwable t) {
            System.out.println("  [FAIL] result query crashed: " + printable(sql));
            System.out.println("         " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
            return "";
        }
    }

    private static void assertContains(String label, String text, String needle) {
        if (text != null && text.contains(needle)) {
            System.out.println("  [PASS] " + label);
            passed++;
        } else {
            System.out.println("  [FAIL] " + label);
            System.out.println("         missing: " + needle);
            failed++;
        }
    }

    private static void assertTupleContains(String label, String className, int columnIndex, String expected) {
        try {
            int classId = MemConnect.getClassId(className);
            for (Tuple tuple : MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance())
                    .getTupleList(classId).tuplelist) {
                if (tuple.tuple.length > columnIndex
                        && expected.equals(String.valueOf(tuple.tuple[columnIndex]))) {
                    System.out.println("  [PASS] " + label);
                    passed++;
                    return;
                }
            }
            System.out.println("  [FAIL] " + label);
            System.out.println("         expected tuple value: " + expected);
            printColumnValues(className, columnIndex);
            failed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + label);
            System.out.println("         " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
        }
    }

    private static void printColumnValues(String className, int columnIndex) throws Exception {
        int classId = MemConnect.getClassId(className);
        for (Tuple tuple : MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance())
                .getTupleList(classId).tuplelist) {
            if (tuple.tuple.length > columnIndex) {
                System.out.println("         actual tuple value: " + tuple.tuple[columnIndex]);
            }
        }
    }

    private static void assertTupleValueContains(
        String label,
        String className,
        int columnIndex,
        String expectedFragment
    ) {
        try {
            int classId = MemConnect.getClassId(className);
            for (Tuple tuple : MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance())
                    .getTupleList(classId).tuplelist) {
                if (tuple.tuple.length > columnIndex
                        && String.valueOf(tuple.tuple[columnIndex]).contains(expectedFragment)) {
                    System.out.println("  [PASS] " + label);
                    passed++;
                    return;
                }
            }
            System.out.println("  [FAIL] " + label);
            System.out.println("         expected tuple fragment: " + expectedFragment);
            failed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + label);
            System.out.println("         " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected == actual) {
            System.out.println("  [PASS] " + label + " = " + actual);
            passed++;
        } else {
            System.out.println("  [FAIL] " + label);
            System.out.println("         expected: " + expected);
            System.out.println("         actual:   " + actual);
            failed++;
        }
    }

    private static int tupleCount(String className) {
        try {
            int classId = MemConnect.getClassId(className);
            return MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance())
                .getTupleList(classId).tuplelist.size();
        } catch (Throwable t) {
            System.out.println("  [FAIL] tuple count failed for " + className);
            System.out.println("         " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
            return -1;
        }
    }

    private static void assertNoDanglingPointers(String label, String sourceClassName, String deputyClassName) {
        try {
            int sourceClassId = MemConnect.getClassId(sourceClassName);
            int deputyClassId = MemConnect.getClassId(deputyClassName);
            List<Integer> sourceTupleIds = tupleIds(sourceClassId);
            List<Integer> deputyTupleIds = tupleIds(deputyClassId);

            for (edu.whu.tmdb.storage.memory.SystemTable.BiPointerTableItem item
                    : MemConnect.getBiPointerTableList()) {
                if (item.classid != sourceClassId || item.deputyid != deputyClassId) {
                    continue;
                }
                if (!sourceTupleIds.contains(item.objectid)
                        || !deputyTupleIds.contains(item.deputyobjectid)) {
                    System.out.println("  [FAIL] " + label);
                    System.out.println("         stale pointer: " + item.objectid + " -> " + item.deputyobjectid);
                    failed++;
                    return;
                }
            }
            System.out.println("  [PASS] " + label);
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + label);
            System.out.println("         " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
        }
    }

    private static List<Integer> tupleIds(int classId) throws Exception {
        List<Integer> ids = new ArrayList<>();
        for (Tuple tuple : MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance())
                .getTupleList(classId).tuplelist) {
            ids.add(tuple.tupleId);
        }
        return ids;
    }

    private static String printable(String sql) {
        if (sql == null) {
            return "null";
        }
        String oneLine = sql.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 100 ? oneLine : oneLine.substring(0, 100) + "...";
    }
}
