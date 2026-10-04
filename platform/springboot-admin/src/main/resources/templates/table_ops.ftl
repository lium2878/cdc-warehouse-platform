<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>Table Ops</title>
    <link rel="stylesheet" href="/admin.css">
  </head>
  <body>
    <header>
      <h1>Table Ops</h1>
      <nav>
        <a href="/">Dashboard</a>
        <a href="/realtime">Realtime</a>
        <a href="/logs">Logs</a>
        <a href="/tasks">Task Config</a>
        <a href="/table-ops">Table Ops</a>
        <a href="/onboarding">Onboarding</a>
        <a href="/replay">Replay</a>
        <a href="/monitors">Monitors</a>
        <a href="/rules">Rules</a>
        <a class="logout" href="/logout">Logout</a>
      </nav>
    </header>
    <main>
      <section>
        <h2>表级运维</h2>
        <form id="tableOpsForm">
          <div class="grid">
            <div><label>Database</label><input name="databaseName" value="${request.databaseName}"></div>
            <div><label>Table</label><input name="tableName" value="${request.tableName}"></div>
            <div><label>Biz DT</label><input name="bizDt" value="${request.bizDt}"></div>
            <div><label>Start DT</label><input name="startDt" value="${request.startDt}"></div>
            <div><label>End DT</label><input name="endDt" value="${request.endDt}"></div>
            <div><label>Dry Run</label><input type="checkbox" name="dryRun" value="true" <#if request.dryRun?? && request.dryRun>checked</#if>></div>
          </div>
          <div class="actions">
            <button type="button" onclick="runTableOps('/api/table-ops/check-lineage', this)">链路检查</button>
            <button type="button" class="secondary" onclick="runTableOps('/api/table-ops/consistency', this)">一致性检查</button>
            <button type="button" class="warn" onclick="runTableOps('/api/table-ops/backfill', this)">补数</button>
            <button type="button" class="warn" onclick="runTableOps('/api/table-ops/onboarding-verify', this)">新表验收</button>
          </div>
        </form>
        <pre id="tableOpsResult"></pre>
        <p><a href="/tasks">查看任务状态和实时日志</a></p>
      </section>

      <section>
        <h2>已接入表</h2>
        <table>
          <tr><th>Database</th><th>Table</th><th>ODS</th><th>PK</th><th>Partition</th><th>Action</th></tr>
          <#list tables as table>
          <tr>
            <td>${table.databaseName}</td>
            <td>${table.tableName}</td>
            <td>${table.odsTable!""}</td>
            <td><#if table.primaryKeys??>${table.primaryKeys?join(",")}</#if></td>
            <td>${table.partitionColumn!""}</td>
            <td><button type="button" onclick="fillTable('${table.databaseName?js_string}', '${table.tableName?js_string}')">Use</button></td>
          </tr>
          </#list>
        </table>
      </section>
    </main>
    <script>
      function fillTable(databaseName, tableName) {
        document.querySelector("[name='databaseName']").value = databaseName;
        document.querySelector("[name='tableName']").value = tableName;
      }

      function runTableOps(url, button) {
        var form = document.getElementById("tableOpsForm");
        var body = new URLSearchParams(new FormData(form));
        if (!body.has("dryRun")) {
          body.set("dryRun", "false");
        }
        var result = document.getElementById("tableOpsResult");
        var previous = button.textContent;
        button.disabled = true;
        button.textContent = "Submitting";
        result.textContent = "submitting " + url + " ...";
        fetch(url, {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
          body: body
        })
          .then(function (response) {
            return response.json().then(function (data) {
              if (!response.ok) throw new Error(data.error || ("HTTP " + response.status));
              return data;
            });
          })
          .then(function (data) {
            if (data.id) {
              result.textContent = "submitted execution " + data.id + ", status=" + data.status;
            } else {
              result.textContent = "exitCode=" + data.exitCode + "\n" + (data.output || "");
            }
          })
          .catch(function (error) {
            result.textContent = String(error);
          })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }
    </script>
  </body>
</html>
