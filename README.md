# Totem 移动数据库内核实验说明

实验交付材料：

- [实验报告](docs/实验报告.md)
- [Android 模拟器演示记录](docs/演示记录.md)

本仓库用于整理 Totem 移动数据库内核实验的开发要求、实现范围和验收清单。参考资料来自 `references` 目录中的任务书和系统介绍 PDF；该目录已加入 `.gitignore`，不随代码提交。

## 项目背景

Totem 是一个运行在 Android 平台上的移动数据库系统，使用 Java 编写，核心模型是对象代理模型。除普通源类外，系统可以定义代理类，由代理规则从源类派生数据，并通过 `BiPointerTable` 和 `SwitchingTable` 维护源类与代理类之间的依赖关系。

源类发生 `INSERT`、`UPDATE`、`DELETE` 时，系统需要通过更新迁移机制同步维护相关代理类。实验重点是补全 SelectDeputy 和 GroupDeputy 在特定场景下的代理维护能力。

## 开发环境

- Android Studio
- Android SDK API 35 或更高版本
- Android 模拟器或 Android 真机
- Java / Gradle 项目环境

参考项目地址：

```text
https://github.com/MarchSeventh258/totem_mobile
```

## 参考项目结构

```text
totem_mobile/
app/src/main/java/edu/whu/tmdb/
├── query/                     # 查询执行层
├── query/operations/impl/     # SQL 语句实现
├── storage/                   # LSM-Tree 存储引擎
├── memory/SystemTable/        # 系统表定义
├── level/                     # SSTable / Compaction
├── cache/                     # 多级缓存
├── Log/                       # Write-Ahead Log
└── MainActivity.java          # Android 前端界面
app/libs/                      # 第三方 JAR，如 JSqlParser
```

## 已有能力概览

- 支持 `CREATE CLASS`、`DROP CLASS`、`INSERT`、`DELETE`、`UPDATE`、`SELECT`。
- `SELECT` 支持 `WHERE`、多种 `JOIN`、`GROUP BY`、`ORDER BY`、`LIMIT`。
- 支持 `UNION`、`INTERSECT`、`EXCEPT` 等集合操作。
- 支持 `AVG`、`MIN`、`MAX`、`COUNT`、`SUM` 等聚合函数。
- 存储层基于 LSM-Tree、SSTable、Zone Map、Bloom Filter、B-Tree 索引、LRU 缓存和 WAL。
- 代理类包括 `SelectDeputy`、`JoinDeputy`、`UnionDeputy`、`GroupDeputy` 等类型。

## 实验任务一：实现非严格 SelectDeputy

当前系统已有严格 SelectDeputy：创建时必须在 `WHERE` 子句中给出代理规则，系统根据规则自动判断源元组是否属于代理类。

本任务要求新增非严格 SelectDeputy：创建代理类时不指定 `WHERE` 条件，代理类初始为空；元组是否进入代理类不再由系统自动判断，而是在 `INSERT` 时由用户通过 `INTO deputyClass` 显式声明。

示例：

```sql
CREATE CLASS Student (id INT, name STRING, score INT);

CREATE SELECTDEPUTY GoodStudent AS
SELECT name, score FROM Student;

INSERT INTO Student VALUES (1, 'Alice', 90) INTO GoodStudent;
INSERT INTO Student VALUES (2, 'Bob', 50);

SELECT * FROM Student;
SELECT * FROM GoodStudent;
```

期望结果：`Student` 包含 Alice 和 Bob，`GoodStudent` 只包含 Alice。

实现要求：

- 创建 SelectDeputy 时，如果 `SELECT` 语句不包含 `WHERE`，识别为非严格模式。
- 非严格 SelectDeputy 创建时不从源类填充历史数据，初始结果为空。
- `INSERT ... INTO deputyClass` 只允许用于该源类的非严格 SelectDeputy。
- 未指定 `INTO deputyClass` 时，只插入源类，不自动传播到非严格代理类。
- 对严格 SelectDeputy 使用 `INSERT ... INTO deputyClass` 应报错。
- 对非同源代理类使用 `INSERT ... INTO deputyClass` 应报错。
- 删除源元组时，如果该元组关联了非严格代理元组，应同步删除代理元组。
- 更新源元组时，如果该元组关联了非严格代理元组，应同步更新代理类中的对应属性值。
- 严格 SelectDeputy 的既有行为保持不变。

可能涉及文件：

- `CreateDeputyClassImpl.java`
- `InsertImpl.java`
- `DeleteImpl.java`
- `UpdateImpl.java`

## 实验任务二：为 GroupDeputy 实现 HAVING 支持

GroupDeputy 用于按 `GROUP BY` 对源类数据聚合并生成代理元组。当前系统对 `GROUP BY` 有支持，但 `HAVING` 条件未真正参与过滤，导致创建代理类和更新迁移时不满足条件的分组也可能被保留。

本任务要求在普通 `SELECT` 查询、GroupDeputy 创建和源类更新迁移三个环节中实现 `HAVING` 判断。

示例：

```sql
CREATE CLASS Employee (id INT, dept STRING, salary INT);

INSERT INTO Employee VALUES
(1, 'HR', 5000), (2, 'HR', 6000),
(3, 'IT', 8000), (4, 'IT', 9000),
(5, 'Sales', 3000);

CREATE GROUPDEPUTY HighSalaryDept AS
SELECT dept, AVG(salary) as avg_sal, COUNT(*) as cnt
FROM Employee
GROUP BY dept
HAVING AVG(salary) > 5500;

SELECT * FROM HighSalaryDept;
```

期望结果：`Sales` 分组被过滤，`HR` 和 `IT` 被保留。

实现要求：

- 普通 `SELECT` 中，`HAVING` 过滤发生在 `GROUP BY` 聚合之后、`ORDER BY` 排序之前。
- 创建 GroupDeputy 时，应先按 `HAVING` 过滤分组，再创建代理元组和双向指针。
- 被 `HAVING` 过滤掉的分组不应写入代理类，也不应创建 `BiPointer`。
- 源类数据变化后，更新迁移机制应重新计算对应分组并检查 `HAVING`。
- 分组仍满足 `HAVING` 时，更新代理元组的聚合值。
- 分组由不满足变为满足 `HAVING` 时，新建代理元组和指针。
- 分组由满足变为不满足 `HAVING` 时，删除代理元组和指针。
- 需要覆盖 `INSERT`、`DELETE`、`UPDATE` 三类迁移场景。

可能涉及文件：

- `Having.java`
- `SelectImpl.java`
- `CreateDeputyClassImpl.java`
- `InsertImpl.java`
- `DeleteImpl.java`
- `UpdateImpl.java`

## 演示与验收

演示必须在 Android Studio 模拟器或 Android 真机上运行。项目提供的 `MainActivity.java` 已包含 SQL 输入框和结果展示区域，可以直接使用，也可以根据需要优化界面。

演示时需要在 Android 界面中依次输入 SQL 语句，展示两个任务的功能效果。每个任务至少通过任务书中的全部示例；在此基础上支持更丰富的 `HAVING` 表达式或边界情况可以作为加分项。

实验报告需要包含：

- 设计思路：说明对每个任务的理解和实现方案。
- 核心代码：列出关键代码片段或伪代码，说明修改了哪些文件、增加了哪些关键逻辑。
- AI 使用情况：说明是否使用 AI 工具辅助开发、使用方式和所占比例。
- 演示截图或运行结果：展示关键 SQL 的执行结果。

评分权重：

- 任务一演示：40%
- 任务二演示：40%
- 实验报告：20%

代码查重发现抄袭时，相关任务记 0 分。
