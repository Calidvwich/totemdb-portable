# Totem 实验验收 SQL

本文档用于 Android 前端 SQL 输入框的手动验收。建议每次复制一条 SQL 执行；多行 `INSERT` 可以作为一条语句整体复制。若本机数据库中已经存在同名类，请先重启到干净数据环境，或把本文中的类名统一加后缀后再执行。

文中的 `showb` / `show BiPointerTable` 是可选观察点，用于查看源元组和代理元组之间的双向指针。如果当前前端版本不显示系统表，可跳过该观察点，以 `SELECT` 结果为主要验收依据。

## 1. 非严格 SelectDeputy 基础验收

### SQL

```sql
CREATE CLASS StudentNS (id INT, name STRING, score INT);
```

预期结果：创建源类成功。

```sql
CREATE SELECTDEPUTY GoodStudentNS AS
SELECT name, score FROM StudentNS;
```

预期结果：创建无 `WHERE` 的非严格 SelectDeputy 成功，代理类暂不复制历史数据。

```sql
SELECT * FROM GoodStudentNS;
```

预期结果：只显示表头，代理类初始为空。

```sql
INSERT INTO StudentNS VALUES (1, 'Alice', 90);
```

预期结果：只插入源类，不自动进入非严格代理类。

```sql
SELECT * FROM GoodStudentNS;
```

预期结果：仍为空。

```sql
INSERT INTO StudentNS VALUES (2, 'Bob', 85) INTO GoodStudentNS;
```

预期结果：Bob 同时写入源类和 `GoodStudentNS`，并建立 BiPointer。

```sql
SELECT * FROM StudentNS;
```

预期结果：源类包含 Alice 和 Bob。

```sql
SELECT * FROM GoodStudentNS;
```

预期结果：代理类只包含 `Bob | 85`。

```sql
showb
```

预期结果：可选观察点。应能看到 `StudentNS` 中 Bob 对应 `GoodStudentNS` 代理元组的 BiPointer；Alice 不应有指向 `GoodStudentNS` 的 BiPointer。

## 2. 非严格 SelectDeputy 错误场景

### SQL

```sql
CREATE CLASS StudentErr (id INT, name STRING, score INT);
```

```sql
CREATE SELECTDEPUTY ManualErr AS
SELECT name, score FROM StudentErr;
```

```sql
CREATE SELECTDEPUTY StrictErr AS
SELECT name, score FROM StudentErr WHERE score > 80;
```

预期结果：`ManualErr` 为非严格代理，`StrictErr` 为严格代理。

```sql
INSERT INTO StudentErr VALUES (1, 'StrictRejected', 99) INTO StrictErr;
```

预期结果：应报错，提示不允许对严格 SelectDeputy 使用显式 `INTO`。报错后源类不应新增该元组。

```sql
SELECT * FROM StudentErr;
```

预期结果：仍为空，说明失败场景未污染源类。

```sql
SELECT * FROM StrictErr;
```

预期结果：仍为空。

```sql
CREATE CLASS TeacherErr (id INT, name STRING, subject STRING);
```

```sql
INSERT INTO TeacherErr VALUES (1, 'Smith', 'Math') INTO ManualErr;
```

预期结果：应报错，提示 `ManualErr` 不是 `TeacherErr` 的非严格代理类。报错后 `TeacherErr` 不应新增该元组。

```sql
SELECT * FROM TeacherErr;
```

预期结果：仍为空。

```sql
SELECT * FROM ManualErr;
```

预期结果：仍为空，说明错误写入没有污染非严格代理类。

```sql
showb
```

预期结果：可选观察点。上述失败场景不应产生指向 `StrictErr` 或 `ManualErr` 的新增 BiPointer。

## 3. 非严格 SelectDeputy DELETE / UPDATE 迁移

### SQL

```sql
CREATE CLASS StudentMove (id INT, name STRING, score INT);
```

```sql
CREATE SELECTDEPUTY ManualMove AS
SELECT name, score FROM StudentMove;
```

```sql
INSERT INTO StudentMove VALUES (1, 'Alice', 90) INTO ManualMove;
```

```sql
INSERT INTO StudentMove VALUES (2, 'Bob', 50);
```

```sql
INSERT INTO StudentMove VALUES (3, 'Charlie', 85) INTO ManualMove;
```

```sql
INSERT INTO StudentMove VALUES (4, 'Diana', 70);
```

预期结果：Alice 和 Charlie 进入代理类，Bob 和 Diana 只在源类中。

```sql
SELECT * FROM ManualMove;
```

预期结果：包含 `Alice | 90`、`Charlie | 85`。

```sql
DELETE FROM StudentMove WHERE id = 1;
```

```sql
SELECT * FROM ManualMove;
```

预期结果：Alice 被同步删除，只剩 `Charlie | 85`。

```sql
DELETE FROM StudentMove WHERE id = 2;
```

```sql
SELECT * FROM ManualMove;
```

预期结果：删除未关联代理的 Bob 后，代理类不受影响，仍为 `Charlie | 85`。

```sql
UPDATE StudentMove SET score = 95 WHERE id = 3;
```

```sql
SELECT * FROM ManualMove;
```

预期结果：Charlie 的代理元组同步更新为 `Charlie | 95`。

```sql
UPDATE StudentMove SET score = 75 WHERE id = 4;
```

```sql
SELECT * FROM ManualMove;
```

预期结果：更新未关联代理的 Diana 后，代理类不受影响，仍为 `Charlie | 95`。

```sql
showb
```

预期结果：可选观察点。应只保留 Charlie 源元组到 `ManualMove` 代理元组的 BiPointer，不应保留已删除 Alice 的悬挂指针。

## 4. GroupDeputy HAVING 基础验收

### 普通 SELECT 中的 HAVING

```sql
CREATE CLASS EmployeeHaving (id INT, dept STRING, salary INT);
```

```sql
INSERT INTO EmployeeHaving VALUES
(1, 'HR', 5000), (2, 'HR', 6000),
(3, 'IT', 8000), (4, 'IT', 9000),
(5, 'Sales', 3000);
```

```sql
SELECT dept, AVG(salary) FROM EmployeeHaving
GROUP BY dept
HAVING AVG(salary) > 5000
ORDER BY dept;
```

预期结果：保留 HR 和 IT，过滤 Sales。

```sql
SELECT dept, AVG(salary) FROM EmployeeHaving
GROUP BY dept
HAVING AVG(salary) >= 3000
ORDER BY dept;
```

预期结果：HR、IT、Sales 三个分组都保留。

### 创建 GroupDeputy 时的 HAVING

```sql
CREATE GROUPDEPUTY HighSalaryHaving AS
SELECT dept, AVG(salary) AS avg_sal, COUNT(*) AS cnt
FROM EmployeeHaving
GROUP BY dept
HAVING AVG(salary) > 5000;
```

```sql
SELECT * FROM HighSalaryHaving ORDER BY dept;
```

预期结果：只包含 HR 和 IT；Sales 被 HAVING 过滤，不应出现在代理类中。

```sql
showb
```

预期结果：可选观察点。应存在 HR、IT 源元组到 `HighSalaryHaving` 的 BiPointer；Sales 源元组不应有指向该代理类的 BiPointer。

### 无 HAVING 的 GroupDeputy 回归

```sql
CREATE CLASS EmployeePlainGroup (id INT, dept STRING, salary INT);
```

```sql
INSERT INTO EmployeePlainGroup VALUES
(1, 'A', 1000), (2, 'A', 3000), (3, 'B', 5000);
```

```sql
CREATE GROUPDEPUTY AllSalaryPlain AS
SELECT dept, AVG(salary) AS avg_sal, COUNT(*) AS cnt
FROM EmployeePlainGroup
GROUP BY dept;
```

```sql
SELECT * FROM AllSalaryPlain ORDER BY dept;
```

预期结果：无 HAVING 时原有 GroupDeputy 行为保持，A 与 B 两个分组都出现。

## 5. GroupDeputy HAVING 迁移验收

### 初始化

```sql
CREATE CLASS EmployeeMig (id INT, dept STRING, salary INT);
```

```sql
INSERT INTO EmployeeMig VALUES
(1, 'HR', 5000), (2, 'HR', 6000),
(3, 'IT', 8000), (4, 'IT', 9000),
(5, 'Sales', 3000);
```

```sql
CREATE GROUPDEPUTY HighSalaryMig AS
SELECT dept, AVG(salary) AS avg_sal, COUNT(*) AS cnt
FROM EmployeeMig
GROUP BY dept
HAVING AVG(salary) > 5000;
```

```sql
SELECT * FROM HighSalaryMig ORDER BY dept;
```

预期结果：初始代理类包含 HR 和 IT，过滤 Sales。

### INSERT 后已有分组仍满足 HAVING

```sql
INSERT INTO EmployeeMig VALUES (6, 'IT', 10000);
```

```sql
SELECT * FROM HighSalaryMig ORDER BY dept;
```

预期结果：IT 仍满足 HAVING，聚合值更新为 `avg_sal = 9000.00`、`cnt = 3.00`；HR 不变。

### INSERT 后不满足变为满足

```sql
INSERT INTO EmployeeMig VALUES (7, 'Sales', 9000);
```

```sql
SELECT * FROM HighSalaryMig ORDER BY dept;
```

预期结果：Sales 平均工资变为 6000，满足 HAVING，应新增 Sales 代理元组。

### DELETE 后分组仍满足 HAVING

```sql
DELETE FROM EmployeeMig WHERE id = 4;
```

```sql
SELECT * FROM HighSalaryMig ORDER BY dept;
```

预期结果：IT 只剩 id=3 和 id=6，仍满足 HAVING，聚合值更新为 `avg_sal = 9000.00`、`cnt = 2.00`。

### DELETE 后满足变为不满足

```sql
DELETE FROM EmployeeMig WHERE id = 7;
```

```sql
SELECT * FROM HighSalaryMig ORDER BY dept;
```

预期结果：Sales 只剩 3000，平均值不再满足 HAVING，Sales 代理元组应删除。

### UPDATE 后满足变为不满足

```sql
UPDATE EmployeeMig SET salary = 3000 WHERE id = 2;
```

```sql
SELECT * FROM HighSalaryMig ORDER BY dept;
```

预期结果：HR 平均工资变为 4000，不再满足 HAVING，HR 代理元组应删除。

### UPDATE 后不满足变为满足

```sql
UPDATE EmployeeMig SET salary = 9000 WHERE id = 1;
```

```sql
UPDATE EmployeeMig SET salary = 9000 WHERE id = 2;
```

```sql
SELECT * FROM HighSalaryMig ORDER BY dept;
```

预期结果：HR 平均工资变为 9000，重新满足 HAVING，应重新创建 HR 代理元组和 BiPointer。

### UPDATE 后始终满足

```sql
UPDATE EmployeeMig SET salary = 9500 WHERE id = 3;
```

```sql
SELECT * FROM HighSalaryMig ORDER BY dept;
```

预期结果：IT 始终满足 HAVING，代理元组保留，聚合值更新为 `avg_sal = 9750.00`、`cnt = 2.00`。

```sql
showb
```

预期结果：可选观察点。BiPointer 应与当前代理类中的 HR、IT 分组一致，不应包含已退出的 Sales 分组指针。

## 6. 边界验收

### AVG 等于阈值时的 `>` 与 `>=`

```sql
CREATE CLASS BoundaryAvg (id INT, dept STRING, salary INT);
```

```sql
INSERT INTO BoundaryAvg VALUES
(1, 'Equal', 5000), (2, 'Above', 6000), (3, 'Solo', 7000);
```

```sql
SELECT dept, AVG(salary) FROM BoundaryAvg
GROUP BY dept
HAVING AVG(salary) >= 5000
ORDER BY dept;
```

预期结果：Equal、Above、Solo 都保留。

```sql
SELECT dept, AVG(salary) FROM BoundaryAvg
GROUP BY dept
HAVING AVG(salary) > 5000
ORDER BY dept;
```

预期结果：Equal 被过滤，只保留 Above、Solo。

### COUNT(*) HAVING

```sql
CREATE CLASS CountHaving (id INT, dept STRING, salary INT);
```

```sql
INSERT INTO CountHaving VALUES
(1, 'A', 1000), (2, 'A', 2000), (3, 'B', 3000);
```

```sql
SELECT dept, COUNT(*) AS cnt FROM CountHaving
GROUP BY dept
HAVING COUNT(*) >= 2
ORDER BY dept;
```

预期结果：只保留 A，过滤只有一条数据的 B。

### HAVING 使用 SELECT 别名

```sql
CREATE CLASS AliasHaving (id INT, dept STRING, salary INT);
```

```sql
INSERT INTO AliasHaving VALUES
(1, 'HR', 5000), (2, 'HR', 6000),
(3, 'IT', 8000), (4, 'IT', 9000),
(5, 'Sales', 3000);
```

```sql
SELECT dept, AVG(salary) AS avg_sal FROM AliasHaving
GROUP BY dept
HAVING avg_sal > 5000
ORDER BY dept;
```

预期结果：保留 HR 和 IT，过滤 Sales。

### HAVING 引用未投影聚合表达式

```sql
SELECT dept FROM AliasHaving
GROUP BY dept
HAVING AVG(salary) > 5000
ORDER BY dept;
```

预期结果：保留 HR 和 IT，过滤 Sales。最终输出只包含 `dept` 一列，不应泄漏内部计算的 `AVG(salary)` 列。

```sql
SELECT dept FROM AliasHaving
GROUP BY dept
HAVING COUNT(*) >= 2
ORDER BY dept;
```

预期结果：保留 HR 和 IT，过滤只有一条数据的 Sales。当前已补充支持 HAVING 中未投影的 `AVG`、`COUNT`、`SUM`、`MIN`、`MAX` 等任务相关聚合表达式。

```sql
CREATE GROUPDEPUTY HiddenAliasDept AS
SELECT dept FROM AliasHaving
GROUP BY dept
HAVING AVG(salary) > 5000;
```

```sql
SELECT * FROM HiddenAliasDept ORDER BY dept;
```

预期结果：代理类只包含 `dept` 一列，结果为 HR 和 IT，不应多出 `AVG(salary)` 字段。

### 待验证 / 已知风险：GroupDeputy 分组键不是 SELECT 第一列

```sql
CREATE GROUPDEPUTY GroupKeyRisk AS
SELECT AVG(salary) AS avg_sal, dept
FROM AliasHaving
GROUP BY dept
HAVING AVG(salary) > 5000;
```

预期结果：该场景不是任务书主验收路径。当前同步逻辑以代理结果第一列作为分组键重建 BiPointer，分组键不是 SELECT 第一列时需要进一步验证，不能作为已确认支持项。
