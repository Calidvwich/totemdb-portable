package edu.whu.tmdb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.whu.tmdb.query.operations.utils.SelectResult;
import edu.whu.tmdb.query.operations.impl.CreateDeputyClassImpl;
import edu.whu.tmdb.query.operations.utils.MemConnect;
import edu.whu.tmdb.storage.memory.SystemTable.DeputyRuleTableItem;
import edu.whu.tmdb.storage.memory.SystemTable.DeputyTableItem;
import edu.whu.tmdb.util.DbOperation;

/**
 * 回归测试 —— 逐条执行 SQL，逐行对比实际输出与期望输出。
 */
public class RegressionTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Totem 数据库回归测试");
        System.out.println("========================================\n");

        DbOperation.resetDB();

        testBasicDDL();
        testBasicDML();
        testSelectWhere();
        testAggregateFunctions();
        testGroupBy();
        testHaving();
        testGroupDeputyHavingCreation();
        testGroupDeputyHavingMigration();
        testGroupDeputyHavingBoundaries();
        testGroupDeputyWithoutHavingMigration();
        testOrderBy();
        testJoin();
        testUnionIntersectExcept();
        testSelectDeputy();
        testJoinDeputy();
        testUnionDeputy();
        testGroupDeputy();
        testCrossClassQuery();
        testUpdateMigration();
        testNonStrictSelectDeputyCreation();

        System.out.println("========================================");
        System.out.println("  " + passed + " passed, " + failed + " failed");
        System.out.println("========================================");
        if (failed > 0) System.exit(1);
    }

    // ==================== 基础 DDL ====================

    static void testBasicDDL() {
        section("Basic DDL");
        ddl("CREATE TABLE Student (id INT, name STRING, score INT);");
        ddl("CREATE TABLE Course (id INT, title STRING, credit INT);");
        check("select * from Student;",
            "Student\n" +
            "|id                  |name                |score               |\n");
        ddl("DROP TABLE Course;");
        exec("select * from Course;");
        ddl("CREATE TABLE Course (id INT, title STRING, credit INT);");
    }

    // ==================== 基础 DML ====================

    static void testBasicDML() {
        section("Basic DML");
        ddl("INSERT INTO Student VALUES (1, 'Alice', 85);");
        ddl("INSERT INTO Student VALUES (2, 'Bob', 72);");
        ddl("INSERT INTO Student VALUES (3, 'Charlie', 90);");
        check("select * from Student;",
            "Student\n" +
            "|id                  |name                |score               |\n" +
            "|1                   |Alice               |85                  |\n" +
            "|2                   |Bob                 |72                  |\n" +
            "|3                   |Charlie             |90                  |\n");
        ddl("UPDATE Student SET score = 88 WHERE id = 1;");
        check("select * from Student WHERE id = 1;",
            "Student\n" +
            "|id                  |name                |score               |\n" +
            "|1                   |Alice               |88                  |\n");
        ddl("DELETE FROM Student WHERE id = 3;");
        check("select * from Student;",
            "Student\n" +
            "|id                  |name                |score               |\n" +
            "|1                   |Alice               |88                  |\n" +
            "|2                   |Bob                 |72                  |\n");
    }

    // ==================== SELECT WHERE ====================

    static void testSelectWhere() {
        section("SELECT WHERE");
        ddl("DELETE FROM Student WHERE id > 0;");
        ddl("INSERT INTO Student VALUES (1, 'Alice', 85);");
        ddl("INSERT INTO Student VALUES (2, 'Bob', 72);");
        ddl("INSERT INTO Student VALUES (3, 'Charlie', 90);");
        ddl("INSERT INTO Student VALUES (4, 'Diana', 60);");
        check("select * from Student WHERE score > 80;",
            "Student\n" +
            "|id                  |name                |score               |\n" +
            "|3                   |Charlie             |90                  |\n" +
            "|1                   |Alice               |85                  |\n");
        check("select * from Student WHERE score >= 85 AND name = 'Alice';",
            "Student\n" +
            "|id                  |name                |score               |\n" +
            "|1                   |Alice               |85                  |\n");
    }

    // ==================== 聚合函数 ====================

    static void testAggregateFunctions() {
        section("Aggregate Functions (with GROUP BY)");
        check("select score, COUNT(id), AVG(score), SUM(score), MIN(score), MAX(score) from Student GROUP BY score;",
            "Student\n" +
            "|score               |COUNT(id)           |AVG(score)          |SUM(score)          |MIN(score)          |MAX(score)          |\n" +
            "|90                  |1.00                |90.00               |90.00               |90.00               |90.00               |\n" +
            "|60                  |1.00                |60.00               |60.00               |60.00               |60.00               |\n" +
            "|72                  |1.00                |72.00               |72.00               |72.00               |72.00               |\n" +
            "|85                  |1.00                |85.00               |85.00               |85.00               |85.00               |\n");
    }

    // ==================== GROUP BY ====================

    static void testGroupBy() {
        section("GROUP BY");
        ddl("DELETE FROM Student WHERE id > 0;");
        ddl("INSERT INTO Student VALUES (1, 'Alice', 85);");
        ddl("INSERT INTO Student VALUES (2, 'Bob', 72);");
        ddl("INSERT INTO Student VALUES (3, 'Charlie', 90);");
        ddl("INSERT INTO Student VALUES (4, 'Diana', 60);");
        ddl("INSERT INTO Student VALUES (5, 'Eve', 88);");
        check("select score, COUNT(id) from Student GROUP BY score;",
            "Student\n" +
            "|score               |COUNT(id)           |\n" +
            "|88                  |1.00                |\n" +
            "|90                  |1.00                |\n" +
            "|60                  |1.00                |\n" +
            "|72                  |1.00                |\n" +
            "|85                  |1.00                |\n");
    }

    // ==================== HAVING ====================

    static void testHaving() {
        section("HAVING");
        ddl("CREATE CLASS Employee (id INT, dept STRING, salary INT);");
        ddl("INSERT INTO Employee VALUES " +
            "(1, 'HR', 5000), (2, 'HR', 6000), " +
            "(3, 'IT', 8000), (4, 'IT', 9000), " +
            "(5, 'Sales', 3000);");

        check("SELECT dept, AVG(salary) FROM Employee " +
                "GROUP BY dept HAVING AVG(salary) > 5000 ORDER BY dept;",
            "Employee\n" +
            "|dept                |AVG(salary)         |\n" +
            "|HR                  |5500.00             |\n" +
            "|IT                  |8500.00             |\n");

        check("SELECT dept, AVG(salary) FROM Employee " +
                "GROUP BY dept HAVING AVG(salary) >= 3000 ORDER BY dept;",
            "Employee\n" +
            "|dept                |AVG(salary)         |\n" +
            "|HR                  |5500.00             |\n" +
            "|IT                  |8500.00             |\n" +
            "|Sales               |3000.00             |\n");

        check("SELECT dept, AVG(salary) AS avg_sal FROM Employee " +
                "GROUP BY dept HAVING avg_sal > 5000 ORDER BY dept;",
            "Employee\n" +
            "|dept                |avg_sal             |\n" +
            "|HR                  |5500.00             |\n" +
            "|IT                  |8500.00             |\n");

        check("SELECT dept, AVG(salary) FROM Employee " +
                "GROUP BY dept HAVING AVG(salary) > 5000 ORDER BY dept DESC LIMIT 1;",
            "Employee\n" +
            "|dept                |AVG(salary)         |\n" +
            "|IT                  |8500.00             |\n");
    }

    static void testGroupDeputyHavingCreation() {
        section("GroupDeputy HAVING creation");
        createDeputy("CREATE GROUPDEPUTY HighSalaryDept AS " +
            "SELECT dept, AVG(salary) as avg_sal, COUNT(*) as cnt " +
            "FROM Employee GROUP BY dept HAVING AVG(salary) > 5000;");

        checkRowsUnordered("SELECT * FROM HighSalaryDept;",
            "HighSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|HR                  |5500.00             |2.00                |\n" +
            "|IT                  |8500.00             |2.00                |\n");
        checkBiPointer("Employee", 1, "HighSalaryDept");
        checkBiPointer("Employee", 2, "HighSalaryDept");
        checkBiPointer("Employee", 3, "HighSalaryDept");
        checkBiPointer("Employee", 4, "HighSalaryDept");
        checkNoBiPointer("Employee", 5, "HighSalaryDept");
        checkBiPointerCount("Employee", "HighSalaryDept", 4);
        checkDeputyRuleContains("HighSalaryDept", "HAVING AVG(salary) > 5000");
    }

    static void testGroupDeputyHavingMigration() {
        section("GroupDeputy HAVING migration");

        ddl("INSERT INTO Employee VALUES (6, 'IT', 10000);");
        checkRowsUnordered("SELECT * FROM HighSalaryDept;",
            "HighSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|HR                  |5500.00             |2.00                |\n" +
            "|IT                  |9000.00             |3.00                |\n");
        checkBiPointerCount("Employee", "HighSalaryDept", 5);

        ddl("INSERT INTO Employee VALUES (7, 'Sales', 9000);");
        checkRowsUnordered("SELECT * FROM HighSalaryDept;",
            "HighSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|HR                  |5500.00             |2.00                |\n" +
            "|IT                  |9000.00             |3.00                |\n" +
            "|Sales               |6000.00             |2.00                |\n");
        checkBiPointer("Employee", 5, "HighSalaryDept");
        checkBiPointer("Employee", 7, "HighSalaryDept");
        checkBiPointerCount("Employee", "HighSalaryDept", 7);

        ddl("DELETE FROM Employee WHERE id = 4;");
        checkRowsUnordered("SELECT * FROM HighSalaryDept;",
            "HighSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|HR                  |5500.00             |2.00                |\n" +
            "|IT                  |9000.00             |2.00                |\n" +
            "|Sales               |6000.00             |2.00                |\n");
        checkBiPointerCount("Employee", "HighSalaryDept", 6);

        ddl("DELETE FROM Employee WHERE id = 7;");
        checkRowsUnordered("SELECT * FROM HighSalaryDept;",
            "HighSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|HR                  |5500.00             |2.00                |\n" +
            "|IT                  |9000.00             |2.00                |\n");
        checkNoBiPointer("Employee", 5, "HighSalaryDept");
        checkBiPointerCount("Employee", "HighSalaryDept", 4);

        ddl("UPDATE Employee SET salary = 3000 WHERE id = 2;");
        check("SELECT * FROM HighSalaryDept ORDER BY dept;",
            "HighSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|IT                  |9000.00             |2.00                |\n");
        checkNoBiPointer("Employee", 1, "HighSalaryDept");
        checkNoBiPointer("Employee", 2, "HighSalaryDept");
        checkBiPointerCount("Employee", "HighSalaryDept", 2);

        ddl("UPDATE Employee SET salary = 9000 WHERE id = 1;");
        ddl("UPDATE Employee SET salary = 9000 WHERE id = 2;");
        checkRowsUnordered("SELECT * FROM HighSalaryDept;",
            "HighSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|HR                  |9000.00             |2.00                |\n" +
            "|IT                  |9000.00             |2.00                |\n");
        checkBiPointer("Employee", 1, "HighSalaryDept");
        checkBiPointer("Employee", 2, "HighSalaryDept");
        checkBiPointerCount("Employee", "HighSalaryDept", 4);

        ddl("UPDATE Employee SET dept = 'HR' WHERE id = 5;");
        checkRowsUnordered("SELECT * FROM HighSalaryDept;",
            "HighSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|HR                  |7000.00             |3.00                |\n" +
            "|IT                  |9000.00             |2.00                |\n");
        checkBiPointer("Employee", 5, "HighSalaryDept");
        checkBiPointerCount("Employee", "HighSalaryDept", 5);

        ddl("UPDATE Employee SET dept = 'Sales' WHERE id = 5;");
        checkRowsUnordered("SELECT * FROM HighSalaryDept;",
            "HighSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|HR                  |9000.00             |2.00                |\n" +
            "|IT                  |9000.00             |2.00                |\n");
        checkNoBiPointer("Employee", 5, "HighSalaryDept");
        checkBiPointerCount("Employee", "HighSalaryDept", 4);
    }

    static void testGroupDeputyHavingBoundaries() {
        section("GroupDeputy HAVING boundaries");
        ddl("CREATE CLASS BoundaryEmployee (id INT, dept STRING, salary INT);");
        ddl("INSERT INTO BoundaryEmployee VALUES " +
            "(1, 'Equal', 5000), (2, 'Above', 6000), (3, 'Solo', 7000);");
        createDeputy("CREATE GROUPDEPUTY SalaryAtLeast AS " +
            "SELECT dept, AVG(salary) AS avg_sal, COUNT(*) AS cnt " +
            "FROM BoundaryEmployee GROUP BY dept HAVING AVG(salary) >= 5000;");
        createDeputy("CREATE GROUPDEPUTY SalaryAbove AS " +
            "SELECT dept, AVG(salary) AS avg_sal, COUNT(*) AS cnt " +
            "FROM BoundaryEmployee GROUP BY dept HAVING AVG(salary) > 5000;");

        checkRowsUnordered("SELECT * FROM SalaryAtLeast;",
            "SalaryAtLeast\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|Equal               |5000.00             |1.00                |\n" +
            "|Above               |6000.00             |1.00                |\n" +
            "|Solo                |7000.00             |1.00                |\n");
        checkRowsUnordered("SELECT * FROM SalaryAbove;",
            "SalaryAbove\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|Above               |6000.00             |1.00                |\n" +
            "|Solo                |7000.00             |1.00                |\n");
        checkBiPointer("BoundaryEmployee", 1, "SalaryAtLeast");
        checkNoBiPointer("BoundaryEmployee", 1, "SalaryAbove");

        ddl("DELETE FROM BoundaryEmployee WHERE id = 3;");
        checkNoGroup("SalaryAtLeast", "Solo");
        checkNoGroup("SalaryAbove", "Solo");
        checkGroupDeputyConsistency("BoundaryEmployee", "SalaryAtLeast");
        checkGroupDeputyConsistency("BoundaryEmployee", "SalaryAbove");

        ddl("INSERT INTO BoundaryEmployee VALUES (4, 'Left', 4000), (5, 'Right', 4000);");
        ddl("UPDATE BoundaryEmployee SET salary = 6000 WHERE salary = 4000;");
        checkRowsUnordered("SELECT * FROM SalaryAbove;",
            "SalaryAbove\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|Above               |6000.00             |1.00                |\n" +
            "|Left                |6000.00             |1.00                |\n" +
            "|Right               |6000.00             |1.00                |\n");
        checkBiPointer("BoundaryEmployee", 4, "SalaryAbove");
        checkBiPointer("BoundaryEmployee", 5, "SalaryAbove");
        checkGroupDeputyConsistency("BoundaryEmployee", "SalaryAtLeast");
        checkGroupDeputyConsistency("BoundaryEmployee", "SalaryAbove");
    }

    static void testGroupDeputyWithoutHavingMigration() {
        section("GroupDeputy without HAVING migration");
        ddl("CREATE CLASS PlainEmployee (id INT, dept STRING, salary INT);");
        ddl("INSERT INTO PlainEmployee VALUES (1, 'A', 1000), (2, 'A', 3000), (3, 'B', 5000);");
        createDeputy("CREATE GROUPDEPUTY AllSalaryDept AS " +
            "SELECT dept, AVG(salary) AS avg_sal, COUNT(*) AS cnt " +
            "FROM PlainEmployee GROUP BY dept;");

        checkRowsUnordered("SELECT * FROM AllSalaryDept;",
            "AllSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|A                   |2000.00             |2.00                |\n" +
            "|B                   |5000.00             |1.00                |\n");
        ddl("INSERT INTO PlainEmployee VALUES (4, 'B', 7000);");
        ddl("UPDATE PlainEmployee SET salary = 5000 WHERE id = 1;");
        ddl("DELETE FROM PlainEmployee WHERE id = 3;");
        checkRowsUnordered("SELECT * FROM AllSalaryDept;",
            "AllSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|A                   |4000.00             |2.00                |\n" +
            "|B                   |7000.00             |1.00                |\n");
        ddl("DELETE FROM PlainEmployee WHERE id = 4;");
        check("SELECT * FROM AllSalaryDept ORDER BY dept;",
            "AllSalaryDept\n" +
            "|dept                |avg_sal             |cnt                 |\n" +
            "|A                   |4000.00             |2.00                |\n");
        checkGroupDeputyConsistency("PlainEmployee", "AllSalaryDept");
    }

    // ==================== ORDER BY ====================

    static void testOrderBy() {
        section("ORDER BY");
        check("select * from Student ORDER BY score;",
            "Student\n" +
            "|id                  |name                |score               |\n" +
            "|4                   |Diana               |60                  |\n" +
            "|2                   |Bob                 |72                  |\n" +
            "|1                   |Alice               |85                  |\n" +
            "|5                   |Eve                 |88                  |\n" +
            "|3                   |Charlie             |90                  |\n");
        check("select * from Student ORDER BY score DESC;",
            "Student\n" +
            "|id                  |name                |score               |\n" +
            "|3                   |Charlie             |90                  |\n" +
            "|5                   |Eve                 |88                  |\n" +
            "|1                   |Alice               |85                  |\n" +
            "|2                   |Bob                 |72                  |\n" +
            "|4                   |Diana               |60                  |\n");
    }

    // ==================== JOIN ====================

    static void testJoin() {
        section("JOIN");
        ddl("INSERT INTO Course VALUES (1, 'Math', 4);");
        ddl("INSERT INTO Course VALUES (2, 'English', 3);");
        ddl("INSERT INTO Course VALUES (3, 'Physics', 5);");
        ddl("CREATE TABLE Enroll (studentId INT, courseId INT);");
        ddl("INSERT INTO Enroll VALUES (1, 1);");
        ddl("INSERT INTO Enroll VALUES (1, 2);");
        ddl("INSERT INTO Enroll VALUES (2, 1);");
        check("select Enroll.studentId, Course.title from Enroll INNER JOIN Course ON Enroll.courseId = Course.id;",
            "Enroll\n" +
            "|Enroll.studentId    |Course.title        |\n" +
            "|1                   |Math                |\n" +
            "|1                   |English             |\n" +
            "|2                   |Math                |\n");
        check("select Enroll.studentId, Course.title from Enroll LEFT JOIN Course ON Enroll.courseId = Course.id;",
            "Enroll\n" +
            "|Enroll.studentId    |Course.title        |\n" +
            "|1                   |Math                |\n" +
            "|2                   |Math                |\n" +
            "|1                   |English             |\n");
        ddl("DROP TABLE Enroll;");
    }

    // ==================== UNION / INTERSECT / EXCEPT ====================

    static void testUnionIntersectExcept() {
        section("UNION / INTERSECT / EXCEPT");
        ddl("CREATE TABLE T1 (x INT);");
        ddl("CREATE TABLE T2 (x INT);");
        ddl("INSERT INTO T1 VALUES (1);");
        ddl("INSERT INTO T1 VALUES (2);");
        ddl("INSERT INTO T1 VALUES (3);");
        ddl("INSERT INTO T2 VALUES (2);");
        ddl("INSERT INTO T2 VALUES (3);");
        ddl("INSERT INTO T2 VALUES (4);");
        check("select * from T1;",
            "T1\n" +
            "|x                   |\n" +
            "|1                   |\n" +
            "|2                   |\n" +
            "|3                   |\n");
        check("select * from T2;",
            "T2\n" +
            "|x                   |\n" +
            "|2                   |\n" +
            "|3                   |\n" +
            "|4                   |\n");
        check("select * from T1 UNION select * from T2;",
            "T1\n" +
            "|x                   |\n" +
            "|1                   |\n" +
            "|2                   |\n" +
            "|3                   |\n" +
            "|4                   |\n");
        check("select * from T1 INTERSECT select * from T2;",
            "T1\n" +
            "|x                   |\n" +
            "|2                   |\n" +
            "|3                   |\n");
        check("select * from T1 EXCEPT select * from T2;",
            "T1\n" +
            "|x                   |\n" +
            "|1                   |\n");
        ddl("DROP TABLE T1;");
        ddl("DROP TABLE T2;");
    }

    // ==================== SelectDeputy (严格) ====================

    static void testSelectDeputy() {
        section("SelectDeputy (strict)");
        ddl("DELETE FROM Student WHERE id > 0;");
        ddl("INSERT INTO Student VALUES (1, 'Alice', 85);");
        ddl("INSERT INTO Student VALUES (2, 'Bob', 72);");
        ddl("INSERT INTO Student VALUES (3, 'Charlie', 90);");
        ddl("INSERT INTO Student VALUES (4, 'Diana', 60);");
        ddl("CREATE SELECTDEPUTY GoodStudent AS SELECT name, score FROM Student WHERE score > 80;");
        check("select * from GoodStudent;",
            "GoodStudent\n" +
            "|name                |score               |\n" +
            "|Charlie             |90                  |\n" +
            "|Alice               |85                  |\n");
    }

    // ==================== SelectDeputy (非严格创建) ====================

    static void testNonStrictSelectDeputyCreation() {
        section("SelectDeputy (non-strict creation)");
        ddl("CREATE SELECTDEPUTY ManualStudent AS SELECT name, score FROM Student;");
        check("select * from ManualStudent;",
            "ManualStudent\n" +
            "|name                |score               |\n");
        checkDeputyMode("ManualStudent", CreateDeputyClassImpl.SELECT_DEPUTY_NON_STRICT_MODE);
        checkDeputyMode("GoodStudent", CreateDeputyClassImpl.SELECT_DEPUTY_STRICT_MODE);
        checkDeputyModesAfterReload();

        ddl("INSERT INTO Student VALUES (10, 'ManualOnly', 40);");
        check("select * from ManualStudent;",
            "ManualStudent\n" +
            "|name                |score               |\n");

        ddl("INSERT INTO Student VALUES (11, 'Bob', 85) INTO ManualStudent;");
        check("select * from ManualStudent;",
            "ManualStudent\n" +
            "|name                |score               |\n" +
            "|Bob                 |85                  |\n");
        checkBiPointer("Student", 11, "ManualStudent");

        expectError(
            "INSERT INTO Student VALUES (12, 'StrictRejected', 99) INTO GoodStudent;",
            "explicit INTO is not allowed for strict deputy GoodStudent"
        );
        check("select * from Student WHERE id = 12;",
            "Student\n" +
            "|id                  |name                |score               |\n");

        ddl("CREATE CLASS Teacher (id INT, name STRING, subject STRING);");
        expectError(
            "INSERT INTO Teacher VALUES (1, 'Smith', 'Math') INTO ManualStudent;",
            "ManualStudent is not a non-strict deputy of the source class"
        );
        check("select * from Teacher;",
            "Teacher\n" +
            "|id                  |name                |subject             |\n");

        ddl("INSERT INTO Student VALUES (13, 'Charlie', 85) INTO ManualStudent;");
        ddl("DELETE FROM Student WHERE id = 11;");
        check("select * from ManualStudent;",
            "ManualStudent\n" +
            "|name                |score               |\n" +
            "|Charlie             |85                  |\n");
        checkNoDanglingBiPointer("Student", "ManualStudent");

        ddl("DELETE FROM Student WHERE id = 10;");
        check("select * from ManualStudent;",
            "ManualStudent\n" +
            "|name                |score               |\n" +
            "|Charlie             |85                  |\n");

        ddl("UPDATE Student SET score = 95 WHERE id = 13;");
        check("select * from ManualStudent;",
            "ManualStudent\n" +
            "|name                |score               |\n" +
            "|Charlie             |95                  |\n");

        ddl("UPDATE Student SET id = 15 WHERE id = 13;");
        check("select * from ManualStudent;",
            "ManualStudent\n" +
            "|name                |score               |\n" +
            "|Charlie             |95                  |\n");

        ddl("INSERT INTO Student VALUES (14, 'Unlinked', 50);");
        ddl("UPDATE Student SET score = 60 WHERE id = 14;");
        check("select * from ManualStudent;",
            "ManualStudent\n" +
            "|name                |score               |\n" +
            "|Charlie             |95                  |\n");
    }

    // ==================== JoinDeputy ====================

    static void testJoinDeputy() {
        section("JoinDeputy");
        ddl("CREATE TABLE Singer (id INT, name STRING, sex STRING, age INT, nationality STRING, company STRING);");
        ddl("CREATE TABLE Song (id INT, name STRING, singer STRING, date INT);");
        ddl("INSERT INTO Singer VALUES (0, 'TaylorSwift', 'F', 36, 'America', 'ATVMusic');");
        ddl("INSERT INTO Singer VALUES (1, 'EdSheeran', 'M', 33, 'UK', 'WarnerMusic');");
        ddl("INSERT INTO Singer VALUES (2, 'JayChou', 'M', 45, 'China', 'JVR');");
        ddl("INSERT INTO Song VALUES (0, 'Red', 'TaylorSwift', 2012);");
        ddl("INSERT INTO Song VALUES (1, '1989', 'TaylorSwift', 2014);");
        ddl("INSERT INTO Song VALUES (2, 'Divide', 'EdSheeran', 2017);");
        ddl("CREATE JOINDEPUTY singer_song AS " +
            "SELECT Song.name, Song.date, Song.singer, Singer.sex, Singer.age, Singer.nationality, Singer.company " +
            "FROM Song, Singer WHERE Song.singer = Singer.name;");
        check("select * from singer_song;",
            "singer_song\n" +
            "|Song.name           |Song.date           |Song.singer         |Singer.sex          |Singer.age          |Singer.nationality  |Singer.company      |\n" +
            "|Red                 |2012                |TaylorSwift         |F                   |36                  |America             |ATVMusic            |\n" +
            "|1989                |2014                |TaylorSwift         |F                   |36                  |America             |ATVMusic            |\n" +
            "|Divide              |2017                |EdSheeran           |M                   |33                  |UK                  |WarnerMusic         |\n");
    }

    // ==================== UnionDeputy ====================

    static void testUnionDeputy() {
        section("UnionDeputy");
        ddl("CREATE TABLE Photo (id INT, name STRING, singer STRING, date INT);");
        ddl("INSERT INTO Photo VALUES (0, 'Fearless', 'TaylorSwift', 2008);");
        ddl("INSERT INTO Photo VALUES (1, 'HeadAboveWater', 'AvrilLavigne', 2022);");
        ddl("CREATE UNIONDEPUTY song_photo AS " +
            "SELECT name, singer, date FROM Song UNION SELECT name, singer, date FROM Photo;");
        check("select * from song_photo;",
            "song_photo\n" +
            "|name                |singer              |date                |\n" +
            "|Red                 |TaylorSwift         |2012                |\n" +
            "|1989                |TaylorSwift         |2014                |\n" +
            "|Divide              |EdSheeran           |2017                |\n" +
            "|Fearless            |TaylorSwift         |2008                |\n" +
            "|HeadAboveWater      |AvrilLavigne        |2022                |\n");
    }

    // ==================== GroupDeputy ====================

    static void testGroupDeputy() {
        section("GroupDeputy");
        ddl("CREATE GROUPDEPUTY year_songnumber AS " +
            "SELECT date, COUNT(id) as song_count FROM Song GROUP BY date;");
        check("select * from year_songnumber;",
            "year_songnumber\n" +
            "|date                |song_count          |\n" +
            "|2017                |1.00                |\n" +
            "|2014                |1.00                |\n" +
            "|2012                |1.00                |\n");
    }

    // ==================== 跨类查询 ====================

    static void testCrossClassQuery() {
        section("Cross-class Query");
        checkRowsUnordered("SELECT Singer -> singer_song FROM Singer;",
            "singer_song\n" +
            "|Song.name           |Song.date           |Song.singer         |Singer.sex          |Singer.age          |Singer.nationality  |Singer.company      |\n" +
            "|Red                 |2012                |TaylorSwift         |F                   |36                  |America             |ATVMusic            |\n" +
            "|Divide              |2017                |EdSheeran           |M                   |33                  |UK                  |WarnerMusic         |\n" +
            "|1989                |2014                |TaylorSwift         |F                   |36                  |America             |ATVMusic            |\n");
        checkRowsUnordered("SELECT Singer -> singer_song.name FROM Singer;",
            "singer_song\n" +
            "|name                |\n" +
            "|Red                 |\n" +
            "|Divide              |\n" +
            "|1989                |\n");
        checkRowsUnordered("SELECT Singer{name = 'TaylorSwift'} -> singer_song.name FROM Singer;",
            "singer_song\n" +
            "|name                |\n" +
            "|Red                 |\n" +
            "|1989                |\n");
        checkRowsUnordered("SELECT Singer -> singer_song{date >= 2014} -> Song.name FROM Singer;",
            "Song\n" +
            "|name                |\n" +
            "|Divide              |\n" +
            "|1989                |\n");
    }

    // ==================== 更新迁移 ====================

    static void testUpdateMigration() {
        section("Update Migration");
        ddl("INSERT INTO Student VALUES (5, 'Frank', 95);");
        check("select * from GoodStudent;",
            "GoodStudent\n" +
            "|name                |score               |\n" +
            "|Charlie             |90                  |\n" +
            "|Alice               |85                  |\n" +
            "|Frank               |95                  |\n");
        ddl("UPDATE Student SET score = 55 WHERE id = 1;");
        // 已知限制: UPDATE 不重新评估 SelectDeputy 的 WHERE
        check("select * from GoodStudent;",
            "GoodStudent\n" +
            "|name                |score               |\n" +
            "|Charlie             |90                  |\n" +
            "|Alice               |55                  |\n" +
            "|Frank               |95                  |\n");
        ddl("DELETE FROM Student WHERE id = 5;");
        check("select * from GoodStudent;",
            "GoodStudent\n" +
            "|name                |score               |\n" +
            "|Charlie             |90                  |\n" +
            "|Alice               |55                  |\n");
    }

    // ==================== 辅助方法 ====================

    static void section(String name) {
        System.out.println("--- " + name + " ---");
    }

    static void ddl(String sql) {
        try {
            Main.execute(sql);
        } catch (Exception e) {
            System.out.println("  [FAIL] " + sql);
            System.out.println("         " + e.getMessage());
            failed++;
        }
    }

    static void exec(String sql) {
        try { Main.execute(sql); } catch (Exception e) {}
    }

    static void expectError(String sql, String expectedMessage) {
        try {
            Main.execute(sql);
            System.out.println("  [FAIL] " + sql);
            System.out.println("         expected error: " + expectedMessage);
            failed++;
        } catch (Exception e) {
            if (expectedMessage.equals(e.getMessage())) {
                System.out.println("  [PASS] " + sql);
                passed++;
            } else {
                System.out.println("  [FAIL] " + sql);
                System.out.println("         expected error: " + expectedMessage);
                System.out.println("         actual error:   " + e.getMessage());
                failed++;
            }
        }
    }

    static void createDeputy(String sql) {
        try {
            net.sf.jsqlparser.statement.Statement statement =
                net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
            new CreateDeputyClassImpl().createDeputyClass(statement);
        } catch (Exception e) {
            System.out.println("  [FAIL] " + sql);
            e.printStackTrace(System.out);
            failed++;
        }
    }

    static void check(String sql, String expected) {
        String actual;
        try {
            SelectResult r = Main.execute(sql);
            if (r == null) {
                actual = "(null)";
            } else {
                actual = DbOperation.getResultString(r);
            }
        } catch (Exception e) {
            actual = "Error: " + e.getMessage();
        }

        if (actual.equals(expected)) {
            System.out.println("  [PASS] " + sql);
            passed++;
        } else {
            System.out.println("  [FAIL] " + sql);
            System.out.println("  --- expected ---");
            for (String line : expected.split("\n")) {
                System.out.println("  " + line);
            }
            System.out.println("  --- actual ---");
            for (String line : actual.split("\n")) {
                System.out.println("  " + line);
            }
            System.out.println("  ---");
            failed++;
        }
    }

    static void checkRowsUnordered(String sql, String expected) {
        String actual;
        try {
            SelectResult r = Main.execute(sql);
            actual = r == null ? "(null)" : DbOperation.getResultString(r);
        } catch (Exception e) {
            actual = "Error: " + e.getMessage();
        }

        List<String> expectedLines = new ArrayList<>(Arrays.asList(expected.split("\n")));
        List<String> actualLines = new ArrayList<>(Arrays.asList(actual.split("\n")));
        boolean matches = expectedLines.size() >= 2
            && actualLines.size() >= 2
            && expectedLines.get(0).equals(actualLines.get(0))
            && expectedLines.get(1).equals(actualLines.get(1));
        if (matches) {
            expectedLines = expectedLines.subList(2, expectedLines.size());
            actualLines = actualLines.subList(2, actualLines.size());
            expectedLines.sort(String::compareTo);
            actualLines.sort(String::compareTo);
            matches = expectedLines.equals(actualLines);
        }

        if (matches) {
            System.out.println("  [PASS] " + sql);
            passed++;
        } else {
            System.out.println("  [FAIL] " + sql);
            System.out.println("  --- expected ---");
            System.out.print(expected);
            System.out.println("  --- actual ---");
            System.out.print(actual);
            System.out.println("  ---");
            failed++;
        }
    }

    static void checkDeputyMode(String deputyClassName, String expectedMode) {
        int deputyClassId;
        try {
            deputyClassId = MemConnect.getClassId(deputyClassName);
        } catch (Exception e) {
            System.out.println("  [FAIL] " + deputyClassName + " mode");
            System.out.println("         " + e.getMessage());
            failed++;
            return;
        }
        String actualMode = "";
        for (DeputyTableItem deputy : MemConnect.getDeputyTableList()) {
            if (deputy.deputyid != deputyClassId) {
                continue;
            }
            for (DeputyRuleTableItem rule : MemConnect.getDeputyRuleTableList()) {
                if (rule.ruleid == deputy.ruleid && rule.deputyrule.length > 2) {
                    actualMode = rule.deputyrule[2];
                    break;
                }
            }
            break;
        }

        if (expectedMode.equals(actualMode)) {
            System.out.println("  [PASS] " + deputyClassName + " mode = " + expectedMode);
            passed++;
        } else {
            System.out.println("  [FAIL] " + deputyClassName + " mode");
            System.out.println("         expected: " + expectedMode);
            System.out.println("         actual:   " + actualMode);
            failed++;
        }
    }

    static void checkDeputyModesAfterReload() {
        try {
            edu.whu.tmdb.query.Transaction.getInstance().SaveAll();
            MemConnect.getDeputyRuleTable().clear();
            edu.whu.tmdb.storage.memory.MemManager.getInstance().loadDeputyRuleTable();

            checkDeputyMode("ManualStudent", CreateDeputyClassImpl.SELECT_DEPUTY_NON_STRICT_MODE);
            checkDeputyMode("GoodStudent", CreateDeputyClassImpl.SELECT_DEPUTY_STRICT_MODE);
        } catch (Exception e) {
            System.out.println("  [FAIL] reload SelectDeputy modes");
            System.out.println("         " + e.getMessage());
            failed++;
        }
    }

    static void checkBiPointer(String sourceClassName, int sourceObjectId, String deputyClassName) {
        try {
            int sourceClassId = MemConnect.getClassId(sourceClassName);
            int deputyClassId = MemConnect.getClassId(deputyClassName);
            int sourceTupleId = -1;
            for (edu.whu.tmdb.storage.memory.Tuple tuple
                    : MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance())
                        .getTupleList(sourceClassId).tuplelist) {
                if (tuple.tuple.length > 0
                        && String.valueOf(sourceObjectId).equals(String.valueOf(tuple.tuple[0]))) {
                    sourceTupleId = tuple.tupleId;
                    break;
                }
            }
            for (edu.whu.tmdb.storage.memory.SystemTable.BiPointerTableItem item
                    : MemConnect.getBiPointerTableList()) {
                if (item.classid == sourceClassId
                        && item.deputyid == deputyClassId
                        && item.objectid == sourceTupleId) {
                    System.out.println("  [PASS] BiPointer " + sourceClassName + " -> " + deputyClassName);
                    passed++;
                    return;
                }
            }
            System.out.println("  [FAIL] BiPointer " + sourceClassName + " -> " + deputyClassName);
            failed++;
        } catch (Exception e) {
            System.out.println("  [FAIL] BiPointer " + sourceClassName + " -> " + deputyClassName);
            System.out.println("         " + e.getMessage());
            failed++;
        }
    }

    static void checkNoBiPointer(String sourceClassName, int sourceObjectId, String deputyClassName) {
        try {
            int sourceClassId = MemConnect.getClassId(sourceClassName);
            int deputyClassId = MemConnect.getClassId(deputyClassName);
            int sourceTupleId = findTupleId(sourceClassId, sourceObjectId);
            for (edu.whu.tmdb.storage.memory.SystemTable.BiPointerTableItem item
                    : MemConnect.getBiPointerTableList()) {
                if (item.classid == sourceClassId
                        && item.deputyid == deputyClassId
                        && item.objectid == sourceTupleId) {
                    System.out.println("  [FAIL] unexpected BiPointer " + sourceClassName + " -> " + deputyClassName);
                    failed++;
                    return;
                }
            }
            System.out.println("  [PASS] no BiPointer " + sourceClassName + " -> " + deputyClassName);
            passed++;
        } catch (Exception e) {
            System.out.println("  [FAIL] no BiPointer " + sourceClassName + " -> " + deputyClassName);
            System.out.println("         " + e.getMessage());
            failed++;
        }
    }

    static void checkBiPointerCount(String sourceClassName, String deputyClassName, int expected) {
        try {
            int sourceClassId = MemConnect.getClassId(sourceClassName);
            int deputyClassId = MemConnect.getClassId(deputyClassName);
            int actual = 0;
            for (edu.whu.tmdb.storage.memory.SystemTable.BiPointerTableItem item
                    : MemConnect.getBiPointerTableList()) {
                if (item.classid == sourceClassId && item.deputyid == deputyClassId) {
                    actual++;
                }
            }
            checkValue("BiPointer count " + sourceClassName + " -> " + deputyClassName,
                expected, actual);
        } catch (Exception e) {
            System.out.println("  [FAIL] BiPointer count " + sourceClassName + " -> " + deputyClassName);
            System.out.println("         " + e.getMessage());
            failed++;
        }
    }

    static void checkNoGroup(String deputyClassName, Object groupValue) {
        try {
            int deputyClassId = MemConnect.getClassId(deputyClassName);
            for (edu.whu.tmdb.storage.memory.Tuple tuple
                    : MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance())
                        .getTupleList(deputyClassId).tuplelist) {
                if (tuple.tuple.length > 0 && groupValue.equals(tuple.tuple[0])) {
                    System.out.println("  [FAIL] unexpected group " + groupValue + " in " + deputyClassName);
                    failed++;
                    return;
                }
            }
            System.out.println("  [PASS] no group " + groupValue + " in " + deputyClassName);
            passed++;
        } catch (Exception e) {
            System.out.println("  [FAIL] no group " + groupValue + " in " + deputyClassName);
            System.out.println("         " + e.getMessage());
            failed++;
        }
    }

    static void checkGroupDeputyConsistency(String sourceClassName, String deputyClassName) {
        try {
            int sourceClassId = MemConnect.getClassId(sourceClassName);
            int deputyClassId = MemConnect.getClassId(deputyClassName);
            java.util.Set<Integer> sourceTupleIds = new java.util.HashSet<>();
            java.util.Set<Integer> deputyTupleIds = new java.util.HashSet<>();
            java.util.Set<Integer> pointedDeputyIds = new java.util.HashSet<>();

            MemConnect connect =
                MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance());
            for (edu.whu.tmdb.storage.memory.Tuple tuple : connect.getTupleList(sourceClassId).tuplelist) {
                sourceTupleIds.add(tuple.tupleId);
            }
            for (edu.whu.tmdb.storage.memory.Tuple tuple : connect.getTupleList(deputyClassId).tuplelist) {
                deputyTupleIds.add(tuple.tupleId);
            }
            for (edu.whu.tmdb.storage.memory.SystemTable.BiPointerTableItem item
                    : MemConnect.getBiPointerTableList()) {
                if (item.classid != sourceClassId || item.deputyid != deputyClassId) {
                    continue;
                }
                if (!sourceTupleIds.contains(item.objectid)
                        || !deputyTupleIds.contains(item.deputyobjectid)) {
                    System.out.println("  [FAIL] inconsistent GroupDeputy pointers " +
                        sourceClassName + " -> " + deputyClassName);
                    failed++;
                    return;
                }
                pointedDeputyIds.add(item.deputyobjectid);
            }
            if (!pointedDeputyIds.equals(deputyTupleIds)) {
                System.out.println("  [FAIL] orphan GroupDeputy tuple in " + deputyClassName);
                failed++;
                return;
            }
            System.out.println("  [PASS] GroupDeputy pointers consistent " +
                sourceClassName + " -> " + deputyClassName);
            passed++;
        } catch (Exception e) {
            System.out.println("  [FAIL] GroupDeputy pointers consistent " +
                sourceClassName + " -> " + deputyClassName);
            System.out.println("         " + e.getMessage());
            failed++;
        }
    }

    static void checkDeputyRuleContains(String deputyClassName, String expectedFragment) {
        try {
            int deputyClassId = MemConnect.getClassId(deputyClassName);
            for (DeputyTableItem deputy : MemConnect.getDeputyTableList()) {
                if (deputy.deputyid != deputyClassId) {
                    continue;
                }
                for (DeputyRuleTableItem rule : MemConnect.getDeputyRuleTableList()) {
                    if (rule.ruleid == deputy.ruleid
                            && rule.deputyrule.length > 0
                            && rule.deputyrule[0].contains(expectedFragment)) {
                        System.out.println("  [PASS] " + deputyClassName + " rule contains HAVING");
                        passed++;
                        return;
                    }
                }
            }
            System.out.println("  [FAIL] " + deputyClassName + " rule contains HAVING");
            failed++;
        } catch (Exception e) {
            System.out.println("  [FAIL] " + deputyClassName + " rule contains HAVING");
            System.out.println("         " + e.getMessage());
            failed++;
        }
    }

    static int findTupleId(int classId, int logicalId) throws Exception {
        for (edu.whu.tmdb.storage.memory.Tuple tuple
                : MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance())
                    .getTupleList(classId).tuplelist) {
            if (tuple.tuple.length > 0
                    && String.valueOf(logicalId).equals(String.valueOf(tuple.tuple[0]))) {
                return tuple.tupleId;
            }
        }
        throw new Exception("source tuple not found: " + logicalId);
    }

    static void checkValue(String label, int expected, int actual) {
        if (expected == actual) {
            System.out.println("  [PASS] " + label + " = " + expected);
            passed++;
        } else {
            System.out.println("  [FAIL] " + label);
            System.out.println("         expected: " + expected);
            System.out.println("         actual:   " + actual);
            failed++;
        }
    }

    static void checkNoDanglingBiPointer(String sourceClassName, String deputyClassName) {
        try {
            int sourceClassId = MemConnect.getClassId(sourceClassName);
            int deputyClassId = MemConnect.getClassId(deputyClassName);
            for (edu.whu.tmdb.storage.memory.SystemTable.BiPointerTableItem item
                    : MemConnect.getBiPointerTableList()) {
                if (item.classid != sourceClassId || item.deputyid != deputyClassId) {
                    continue;
                }
                edu.whu.tmdb.storage.memory.Tuple sourceTuple =
                    MemConnect.getInstance(edu.whu.tmdb.storage.memory.MemManager.getInstance())
                        .GetTuple(item.objectid);
                if (sourceTuple == null) {
                    System.out.println("  [FAIL] stale BiPointer " + sourceClassName + " -> " + deputyClassName);
                    failed++;
                    return;
                }
            }
            System.out.println("  [PASS] no stale BiPointer " + sourceClassName + " -> " + deputyClassName);
            passed++;
        } catch (Exception e) {
            System.out.println("  [FAIL] no stale BiPointer " + sourceClassName + " -> " + deputyClassName);
            System.out.println("         " + e.getMessage());
            failed++;
        }
    }
}
