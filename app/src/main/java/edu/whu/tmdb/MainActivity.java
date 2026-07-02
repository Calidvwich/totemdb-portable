package edu.whu.tmdb;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.whu.tmdb.R;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "tmdb_ui";
    private static final String KEY_HISTORY = "sql_history";
    private static final String HISTORY_SEPARATOR = "\u001E";
    private static final int MAX_HISTORY_SIZE = 10;

    private static final String TASK_ONE_EXAMPLE =
            "resetdb\n" +
            "CREATE CLASS Student (id INT, name STRING, score INT);\n" +
            "CREATE SELECTDEPUTY GoodStudent AS SELECT name, score FROM Student;\n" +
            "INSERT INTO Student VALUES (1, 'Alice', 90);\n" +
            "SELECT * FROM GoodStudent;\n" +
            "INSERT INTO Student VALUES (2, 'Bob', 85) INTO GoodStudent;\n" +
            "SELECT * FROM GoodStudent;\n" +
            "UPDATE Student SET score = 95 WHERE id = 2;\n" +
            "SELECT * FROM GoodStudent;\n" +
            "DELETE FROM Student WHERE id = 2;\n" +
            "SELECT * FROM GoodStudent;";

    private static final String TASK_TWO_EXAMPLE =
            "resetdb\n" +
            "CREATE CLASS Employee (id INT, dept STRING, salary INT);\n" +
            "INSERT INTO Employee VALUES (1, 'HR', 5000), (2, 'HR', 6000), (3, 'IT', 8000), (4, 'IT', 9000), (5, 'Sales', 3000);\n" +
            "CREATE GROUPDEPUTY HighSalaryDept AS SELECT dept, AVG(salary) AS avg_sal, COUNT(*) AS cnt FROM Employee GROUP BY dept HAVING AVG(salary) > 5000;\n" +
            "SELECT * FROM HighSalaryDept;\n" +
            "INSERT INTO Employee VALUES (6, 'Sales', 9000);\n" +
            "SELECT * FROM HighSalaryDept;\n" +
            "UPDATE Employee SET salary = 3000 WHERE id = 2;\n" +
            "SELECT * FROM HighSalaryDept;";

    private EditText etCmd;
    private LinearLayout resultContainer;
    private ScrollView scrollView;
    private SharedPreferences preferences;
    private StringBuilder exportBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etCmd = findViewById(R.id.etCmd);
        resultContainer = findViewById(R.id.resultContainer);
        scrollView = findViewById(R.id.scrollView);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        scrollView.setFillViewport(true);

        Button btExecute = findViewById(R.id.btExecute);
        Button btClear = findViewById(R.id.btClear);
        Button btHistory = findViewById(R.id.btHistory);
        Button btTask1 = findViewById(R.id.btTask1);
        Button btTask2 = findViewById(R.id.btTask2);
        Button btExport = findViewById(R.id.btExport);

        btExecute.setOnClickListener(v -> {
            String sqlCommand = etCmd.getText().toString().trim();
            if (sqlCommand.isEmpty()) {
                Toast.makeText(this, "请输入SQL命令", Toast.LENGTH_SHORT).show();
                return;
            }

            saveSqlHistory(sqlCommand);
            String[] rawResults = Main.execute_UI(sqlCommand);
            exportBuffer.append("SQL:\n").append(sqlCommand).append("\n\n");

            for (String rawResult : rawResults) {
                String displayResult = enhanceResultMessage(rawResult);
                addResultText(displayResult);
                exportBuffer.append(displayResult).append("\n\n");
            }

            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            etCmd.setText("");
        });

        btClear.setOnClickListener(v -> {
            resultContainer.removeAllViews();
            exportBuffer.setLength(0);
        });
        btHistory.setOnClickListener(v -> showSqlHistory());
        btTask1.setOnClickListener(v -> loadExample(TASK_ONE_EXAMPLE, "已加载任务一验收示例"));
        btTask2.setOnClickListener(v -> loadExample(TASK_TWO_EXAMPLE, "已加载任务二验收示例"));
        btExport.setOnClickListener(v -> exportResults());
    }

    private void addResultText(String rawResult) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        String escaped = rawResult
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace(" ", "&nbsp;")
                .replace("\n", "<br>");
        String htmlResult = "<pre><tt>" + escaped + "</tt></pre>";
        tv.setText(Html.fromHtml(htmlResult, Html.FROM_HTML_MODE_LEGACY));

        tv.setTextSize(14);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(rawResult.startsWith("执行失败") ? 0xFFB00020 : 0xFF1A1A1A);

        tv.setPadding(4, 4, 4, 4);
        resultContainer.addView(tv);
    }

    private String enhanceResultMessage(String rawResult) {
        if (rawResult == null || rawResult.trim().isEmpty()) {
            return "";
        }
        if (!rawResult.startsWith("Error:")) {
            return rawResult;
        }

        String message = rawResult.substring("Error:".length()).trim();
        String suggestion;
        if (message.contains("strict deputy")) {
            suggestion = "严格 SelectDeputy 由 WHERE 规则自动维护，不能使用显式 INTO。";
        } else if (message.contains("not a non-strict deputy")) {
            suggestion = "请确认目标代理类存在、属于当前源类，并且是无 WHERE 的非严格 SelectDeputy。";
        } else if (message.contains("does not exist")) {
            suggestion = "请先创建对应的类或代理类，再执行当前语句。";
        } else if (message.toLowerCase().contains("syntax")) {
            suggestion = "请检查 SQL 关键字、括号、引号和分号。";
        } else {
            suggestion = "请检查 SQL 语句、类名、属性名和代理类关系。";
        }
        return "执行失败: " + message + "\n建议: " + suggestion;
    }

    private void saveSqlHistory(String sqlCommand) {
        List<String> history = loadSqlHistory();
        history.remove(sqlCommand);
        history.add(0, sqlCommand);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
        preferences.edit().putString(KEY_HISTORY, String.join(HISTORY_SEPARATOR, history)).apply();
    }

    private List<String> loadSqlHistory() {
        String raw = preferences.getString(KEY_HISTORY, "");
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(raw.split(HISTORY_SEPARATOR, -1)));
    }

    private void showSqlHistory() {
        List<String> history = loadSqlHistory();
        if (history.isEmpty()) {
            Toast.makeText(this, "暂无 SQL 执行历史", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[history.size()];
        for (int i = 0; i < history.size(); i++) {
            String sql = history.get(i).replace("\n", " ");
            labels[i] = sql.length() > 72 ? sql.substring(0, 72) + "..." : sql;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择历史 SQL")
                .setItems(labels, (dialog, which) -> {
                    etCmd.setText(history.get(which));
                    etCmd.setSelection(etCmd.getText().length());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadExample(String sql, String toastText) {
        etCmd.setText(sql);
        etCmd.setSelection(etCmd.getText().length());
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show();
    }

    private void exportResults() {
        String text = exportBuffer.toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "暂无可导出的执行结果", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Totem SQL Results", text));
        Toast.makeText(this, "执行结果已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }
}
