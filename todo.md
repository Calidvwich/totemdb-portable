# Totem 移动数据库实验 TODO

> 依据上游仓库 `MarchSeventh258/totem_mobile` 的最新版 `实验任务书.md`（提交 `6eb1f54`）整理。

## 0. 开发准备

- [x] 使用 Android Studio 打开当前项目并完成 Gradle 同步。
- [x] 确认 Android SDK、JDK 和 Gradle 环境可用。
- [x] 在 Android 模拟器或 Android 真机上成功编译、安装并启动原始应用。
- [x] 确认 `MainActivity.java` 的 SQL 输入框和结果展示区域能够正常工作。
- [x] 运行现有基础 SQL，确认修改前的 `CREATE CLASS`、`INSERT`、`SELECT`、`UPDATE`、`DELETE` 可用。
- [x] 阅读 `CreateDeputyClassImpl.java`，定位 SelectDeputy 和 GroupDeputy 的创建逻辑。
- [x] 阅读 `InsertImpl.java`，定位源元组插入、代理传播和 `BiPointer` 创建逻辑。
- [x] 阅读 `DeleteImpl.java`，定位源元组及代理元组删除迁移逻辑。
- [x] 阅读 `UpdateImpl.java`，定位源元组及代理元组更新迁移逻辑。
- [x] 阅读 `SelectImpl.java`、`GroupBy.java` 和 `Having.java`，确认查询执行阶段与数据结构。
- [x] 阅读代理类系统表，确认 `DeputyTable`、`DeputyRuleTable`、`SwitchingTable`、`BiPointerTable` 的用途。

## 1. 非严格 SelectDeputy

### 1.1 创建与模式识别

- [x] 创建 SelectDeputy 时检查其 `SELECT` 是否包含 `WHERE` 子句。
- [x] 将包含 `WHERE` 的 SelectDeputy 识别为严格模式。
- [x] 将不包含 `WHERE` 的 SelectDeputy 识别为非严格模式。
- [x] 选择合适的系统表字段或规则表示方式，持久化严格/非严格模式信息。
- [x] 确保数据库重新加载后仍能正确识别代理类模式。
- [x] 创建非严格 SelectDeputy 时建立类定义和属性映射。
- [x] 创建非严格 SelectDeputy 时不复制源类已有数据，代理类初始为空。
- [x] 保持严格 SelectDeputy 创建时按 `WHERE` 自动筛选数据的现有行为。

### 1.2 显式 INSERT

- [x] 确认解析器可以解析 `INSERT INTO source VALUES (...) INTO deputyClass`。
- [x] 在 `InsertImpl.java` 中读取可选的目标代理类名称。
- [x] 未指定第二个 `INTO` 时，只向源类插入元组。
- [x] 未指定第二个 `INTO` 时，不向任何非严格 SelectDeputy 自动传播。
- [x] 指定第二个 `INTO` 时，先完成源元组插入。
- [x] 校验目标代理类存在。
- [x] 校验目标类是 SelectDeputy。
- [x] 校验目标代理类的源类与当前插入的源类一致。
- [x] 校验目标代理类为非严格模式。
- [x] 对严格 SelectDeputy 使用显式 `INTO` 时返回明确错误。
- [x] 对非同源代理类使用显式 `INTO` 时返回明确错误。
- [x] 根据 `SwitchingTable` 将源元组属性映射到代理元组属性。
- [x] 将映射后的元组插入指定非严格 SelectDeputy。
- [x] 为源元组和代理元组创建正确的双向指针。
- [x] 确保插入失败时不会遗留半完成的代理元组或双向指针。
- [x] 保持严格 SelectDeputy 原有的规则驱动插入行为不变。

### 1.3 DELETE 更新迁移

- [x] 删除源元组前查找其关联的非严格 SelectDeputy 指针。
- [x] 删除关联源元组时同步删除对应代理元组。
- [x] 同步清理源元组与代理元组之间的 `BiPointer` 条目。
- [x] 删除未关联非严格代理类的源元组时，不修改代理类数据。
- [x] 确保严格 SelectDeputy 的删除迁移行为不受影响。

### 1.4 UPDATE 更新迁移

- [x] 更新源元组时查找其关联的非严格 SelectDeputy 指针。
- [x] 根据 `SwitchingTable` 找出需要同步的代理属性。
- [x] 更新关联源元组时同步更新代理元组的对应属性值。
- [x] 更新未关联非严格代理类的源元组时，不修改代理类数据。
- [x] 确保严格 SelectDeputy 的更新迁移行为不受影响。

### 1.5 任务一验收

- [x] 创建无 `WHERE` 的 `GoodStudent` 后确认代理类初始为空。
- [x] 插入 Alice 时不指定代理类，确认 `GoodStudent` 只显示表头、没有数据行。
- [x] 使用 `INTO GoodStudent` 插入 Bob，确认代理类结果为 `Bob | 85`。
- [x] 创建有 `WHERE score > 80` 的严格代理类。
- [x] 对严格代理类执行显式 `INTO`，确认操作报错。
- [x] 从 `Teacher` 向 `Student` 的代理类显式插入，确认操作报错。
- [x] 插入 Alice 和 Charlie 到非严格代理类，再删除 Alice，确认只剩 Charlie。
- [x] 删除未关联代理类的 Bob，确认 Charlie 不受影响。
- [x] 更新关联代理类的 Alice 分数为 95，确认代理元组同步为 `Alice | 95`。
- [x] 更新未关联代理类的 Bob，确认代理类仍为 `Alice | 95`。

## 2. GroupDeputy HAVING

### 2.1 HAVING 表达式执行

- [x] 实现 `Having.java`，使其能够接收并执行任务书中的 HAVING 条件。
- [x] 复用现有表达式或 `WHERE` 比较能力，避免重复实现相同运算逻辑。
- [x] 支持对分组后的聚合结果执行比较。
- [x] 至少支持任务书使用的 `AVG(column)`。
- [x] 至少支持任务书使用的 `>` 和 `>=` 比较运算符。
- [x] 正确处理整数和平均值等数值类型比较。
- [x] 对不支持或非法的 HAVING 表达式返回明确错误。

### 2.2 普通 SELECT 中的 HAVING

- [x] 在 `SelectImpl.java` 中接入 HAVING 过滤。
- [x] 确保 HAVING 在 `GROUP BY` 聚合完成后执行。
- [x] 确保 HAVING 在 `ORDER BY` 排序前执行。
- [x] 确保没有 HAVING 的原有 `GROUP BY` 查询行为不变。
- [x] 执行 `HAVING AVG(salary) > 5000`，确认 Sales 被过滤，HR 和 IT 保留。
- [x] 执行 `HAVING AVG(salary) >= 3000`，确认 HR、IT、Sales 三个部门都保留。

### 2.3 创建 GroupDeputy

- [x] 创建 GroupDeputy 时解析并保存完整的 HAVING 规则。
- [x] 在创建代理元组前对分组结果执行 HAVING 过滤。
- [x] 只向 GroupDeputy 插入满足 HAVING 的分组。
- [x] 只为满足 HAVING 的分组创建对应 `BiPointer` 条目。
- [x] 不为被 HAVING 过滤的分组创建代理元组或指针。
- [x] 确保没有 HAVING 的 GroupDeputy 创建行为保持不变。
- [x] 使用 `HAVING AVG(salary) > 5000` 创建 `HighSalaryDept`。
- [x] 查询 `HighSalaryDept`，确认只有 HR 和 IT，不包含 Sales。

### 2.4 INSERT 更新迁移

- [x] 源类插入数据后确定受影响的分组。
- [x] 重新计算受影响分组的 `AVG`、`COUNT` 等代理属性。
- [x] 分组始终满足 HAVING 时，更新已有代理元组的聚合值。
- [x] 分组由不满足变为满足 HAVING 时，创建代理元组。
- [x] 新建代理元组时同步创建正确的 `BiPointer` 条目。
- [x] 分组仍不满足 HAVING 时，不创建代理元组。
- [x] 向 IT 插入工资 10000 的员工，确认 `avg_sal` 更新为 9000、`cnt` 更新为 3。
- [x] 向 Sales 插入工资 9000 的员工，确认平均值变为 6000 并出现 Sales 代理元组。

### 2.5 DELETE 更新迁移

- [x] 源类删除数据后确定受影响的分组。
- [x] 重新计算受影响分组的聚合属性。
- [x] 删除后分组仍满足 HAVING 时，更新已有代理元组。
- [x] 删除后分组不再满足 HAVING 时，删除对应代理元组。
- [x] 删除代理元组时同步清理相关 `BiPointer` 条目。
- [x] 分组删除至无源元组时，删除对应代理元组和所有相关指针。
- [x] 删除 IT 中 id=4 的员工，确认 IT 仍存在且聚合值、计数更新。
- [x] 删除 Sales 中新增的高工资员工，确认平均值降低后 Sales 代理元组消失。

### 2.6 UPDATE 更新迁移

- [x] 源类更新数据后确定更新前和更新后的受影响分组。
- [x] 分组键未变化时重新计算该分组聚合属性。
- [x] 分组键变化时分别重新计算旧分组和新分组。
- [x] 更新后分组始终满足 HAVING 时，更新已有代理元组。
- [x] 更新后分组由满足变为不满足 HAVING 时，删除代理元组和相关指针。
- [x] 更新后分组由不满足变为满足 HAVING 时，创建代理元组和相关指针。
- [x] 更新工资后确认持续满足的分组聚合值会更新。
- [x] 将 HR 的 id=2 工资更新为 3000，确认平均值变为 4000 后 HR 消失。
- [x] 将 HR 两名员工工资更新为 9000，确认 HR 平均值变为 9000 后重新出现。

### 2.7 任务二回归与边界

- [x] 验证 HAVING 阈值等于边界时 `>=` 能正确保留分组。
- [x] 验证 HAVING 阈值等于边界时 `>` 能正确过滤分组。
- [x] 验证一个分组只有一条源元组时聚合与 HAVING 判断正确。
- [x] 验证删除一个分组最后一条源元组时不会遗留代理数据。
- [x] 验证一次操作影响多个源元组时，每个分组只产生正确的最终代理状态。
- [x] 验证 GroupDeputy 的代理元组与 `BiPointer` 始终一致。
- [x] 回归无 HAVING 的普通 `GROUP BY` 查询。
- [x] 回归无 HAVING 的 GroupDeputy 创建与更新迁移。

## 3. Android 演示与交付

- [x] 在 Android Studio 中成功编译项目。
- [x] 将应用安装到 Android 模拟器或 Android 真机。
- [x] 在 Android 前端依次执行任务一的全部任务书示例。
- [x] 在 Android 前端依次执行任务二的全部任务书示例。
- [x] 确认 SQL 查询结果在界面中完整、清晰地显示。
- [x] 确认非法显式代理插入在界面中显示明确错误。
- [x] 保存任务一关键执行结果和截图。
- [x] 保存任务二关键执行结果和截图。
- [x] 整理任务一的设计思路、修改文件和核心代码。
- [x] 整理任务二的设计思路、修改文件和核心代码。
- [x] 在实验报告中说明 AI 工具的使用方式和所占比重。
- [x] 检查提交内容不包含 `references/`、`tmp/`、本地配置和构建产物。
- [x] 最终确认任务书中的全部示例均通过。
- [x] 提交完整项目源码和实验报告。
