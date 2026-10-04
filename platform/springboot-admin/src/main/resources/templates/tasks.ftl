<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>Task Config</title>
    <link rel="stylesheet" href="/admin.css">
  </head>
  <body>
    <header>
      <h1>Task Config</h1>
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
        <h2>新增调度任务</h2>
        <#if message??><p class="ok">${message}</p></#if>
        <form method="post" action="/tasks">
          <div class="grid">
            <div><label>Name</label><input name="taskName" value="${task.taskName}"></div>
            <div><label>Type</label><input name="taskType" value="${task.taskType}"></div>
            <div><label>Schedule</label><input name="schedule" value="${task.schedule}"></div>
          </div>
          <p><label>Command</label><input name="command" value="${task.command}"></p>
          <div class="actions"><button type="submit">Save Task</button></div>
        </form>
      </section>
      <section>
        <h2>已配置任务</h2>
        <table>
          <tr><th>Name</th><th>Type</th><th>Command</th><th>Schedule</th><th>Action</th></tr>
          <#list tasks as task>
          <tr>
            <td>${task.taskName}</td>
            <td>${task.taskType}</td>
            <td class="path">${task.command}</td>
            <td>${task.schedule}</td>
            <td><button type="button" onclick="runTask('${task.taskName?js_string}', this)">Run</button></td>
          </tr>
          </#list>
        </table>
        <pre id="taskRunResult"></pre>
      </section>
      <section>
        <h2>任务执行历史</h2>
        <table id="taskExecutionTable">
          <tr><th>Time</th><th>Name</th><th>Status</th><th>Exit</th><th>Duration</th><th>Output</th><th>Action</th></tr>
          <#list executions as item>
          <tr>
            <td>${item.createdAt!""}</td>
            <td>${item.taskName!""}</td>
            <td>${item.status!""}</td>
            <td>${item.exitCode!""}</td>
            <td>${item.durationMs!""} ms</td>
            <td><pre class="inline-log">${(item.outputExcerpt!"")?html}</pre></td>
            <td>
              <button type="button" class="secondary" onclick="showExecution(${item.id}, this)">Detail</button>
              <button type="button" class="secondary" onclick="showExecutionLog(${item.id}, this)">Log</button>
              <button type="button" class="secondary" onclick="showExecutionContext(${item.id}, this)">Context</button>
              <#if item.status == "PENDING" || item.status == "RUNNING">
              <button type="button" class="warn" onclick="cancelExecution(${item.id}, this)">Cancel</button>
              <#else>
              <button type="button" class="warn" onclick="rerunExecution(${item.id}, this)">Re-run</button>
              </#if>
            </td>
          </tr>
          </#list>
          <#if executions?size == 0>
          <tr><td colspan="7" class="muted">no task executions</td></tr>
          </#if>
        </table>
        <pre id="taskExecutionDetail"></pre>
      </section>
      <section>
        <h2>ODS Merge 状态</h2>
        <table id="mergeStatusTable">
          <tr><th>Updated</th><th>Table</th><th>DT</th><th>Run ID</th><th>Status</th><th>Rows</th><th>Audit</th></tr>
          <#list mergeStatuses as item>
          <tr>
            <td>${item.updatedAt!""}</td>
            <td>${item.sourceDatabase!""}.${item.sourceTable!""}</td>
            <td>${item.processDt!""}</td>
            <td>${item.runId!""}</td>
            <td>${item.status!""}</td>
            <td>binlog=${item.binlogRows!0}, old=${item.oldRows!0}, out=${item.outputRows!0}</td>
            <td class="path">${item.auditPath!""}</td>
          </tr>
          </#list>
          <#if mergeStatuses?size == 0>
          <tr><td colspan="7" class="muted">no merge status</td></tr>
          </#if>
        </table>
      </section>
    </main>
    <script>
      function escapeHtml(value) {
        return String(value || "")
          .replace(/&/g, "&amp;")
          .replace(/</g, "&lt;")
          .replace(/>/g, "&gt;")
          .replace(/"/g, "&quot;")
          .replace(/'/g, "&#039;");
      }

      function runTask(taskName, button) {
        var previous = button.textContent;
        var result = document.getElementById("taskRunResult");
        button.disabled = true;
        button.textContent = "Running";
        result.textContent = "running " + taskName + " ...";
        fetch("/api/tasks/run/" + encodeURIComponent(taskName), { method: "POST" })
          .then(readJson)
          .then(function (data) {
            result.textContent = "submitted execution " + data.id + ", status=" + data.status;
            refreshTaskExecutions();
            refreshMergeStatus();
          })
          .catch(function (error) {
            result.textContent = String(error);
          })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      function refreshTaskExecutions() {
        fetch("/api/tasks/executions", { cache: "no-store" })
          .then(function (response) { return response.json(); })
          .then(function (items) {
            var rows = (items || []).map(function (item) {
              return "<tr>"
                + "<td>" + escapeHtml(item.createdAt) + "</td>"
                + "<td>" + escapeHtml(item.taskName) + "</td>"
                + "<td>" + escapeHtml(item.status) + "</td>"
                + "<td>" + escapeHtml(item.exitCode) + "</td>"
                + "<td>" + escapeHtml(item.durationMs) + " ms</td>"
                + "<td><pre class=\"inline-log\">" + escapeHtml(item.outputExcerpt) + "</pre></td>"
                + "<td>"
                + "<button type=\"button\" class=\"secondary\" onclick=\"showExecution(" + Number(item.id) + ", this)\">Detail</button> "
                + "<button type=\"button\" class=\"secondary\" onclick=\"showExecutionLog(" + Number(item.id) + ", this)\">Log</button> "
                + "<button type=\"button\" class=\"secondary\" onclick=\"showExecutionContext(" + Number(item.id) + ", this)\">Context</button> "
                + ((item.status === "PENDING" || item.status === "RUNNING")
                  ? "<button type=\"button\" class=\"warn\" onclick=\"cancelExecution(" + Number(item.id) + ", this)\">Cancel</button>"
                  : "<button type=\"button\" class=\"warn\" onclick=\"rerunExecution(" + Number(item.id) + ", this)\">Re-run</button>")
                + "</td>"
                + "</tr>";
            }).join("");
            if (!rows) {
              rows = '<tr><td colspan="7" class="muted">no task executions</td></tr>';
            }
            document.getElementById("taskExecutionTable").innerHTML =
              "<tr><th>Time</th><th>Name</th><th>Status</th><th>Exit</th><th>Duration</th><th>Output</th><th>Action</th></tr>" + rows;
          });
      }

      function showExecution(id, button) {
        var previous = button.textContent;
        button.disabled = true;
        button.textContent = "Loading";
        fetch("/api/tasks/executions/" + encodeURIComponent(id), { cache: "no-store" })
          .then(function (response) { return response.json(); })
          .then(function (item) {
            document.getElementById("taskExecutionDetail").textContent =
              "id=" + item.id + "\n"
              + "task=" + item.taskName + "\n"
              + "status=" + item.status + ", exitCode=" + item.exitCode + ", durationMs=" + item.durationMs + "\n"
              + "triggeredBy=" + (item.triggeredBy || "") + ", startedAt=" + (item.startedAt || "") + ", finishedAt=" + (item.finishedAt || "") + "\n"
              + "logPath=" + (item.logPath || "") + "\n"
              + "command=" + item.command + "\n\n"
              + (item.errorMessage || "") + "\n\n" + (item.outputExcerpt || "");
          })
          .catch(function (error) {
            document.getElementById("taskExecutionDetail").textContent = String(error);
          })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      function rerunExecution(id, button) {
        var previous = button.textContent;
        var result = document.getElementById("taskRunResult");
        button.disabled = true;
        button.textContent = "Submitting";
        result.textContent = "submitting rerun for execution " + id + " ...";
        fetch("/api/tasks/executions/" + encodeURIComponent(id) + "/rerun", { method: "POST" })
          .then(readJson)
          .then(function (data) {
            result.textContent = "submitted execution " + data.id + ", status=" + data.status;
            refreshTaskExecutions();
            refreshMergeStatus();
          })
          .catch(function (error) {
            result.textContent = String(error);
          })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      function cancelExecution(id, button) {
        var previous = button.textContent;
        var result = document.getElementById("taskRunResult");
        button.disabled = true;
        button.textContent = "Cancelling";
        fetch("/api/tasks/executions/" + encodeURIComponent(id) + "/cancel", { method: "POST" })
          .then(readJson)
          .then(function (data) {
            result.textContent = "execution " + id + ", status=" + data.status;
            refreshTaskExecutions();
          })
          .catch(function (error) { result.textContent = String(error); })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      function showExecutionLog(id, button) {
        var previous = button.textContent;
        button.disabled = true;
        button.textContent = "Loading";
        fetch("/api/tasks/executions/" + encodeURIComponent(id) + "/log", { cache: "no-store" })
          .then(function (response) {
            if (!response.ok) throw new Error("HTTP " + response.status);
            return response.text();
          })
          .then(function (value) {
            document.getElementById("taskExecutionDetail").textContent = value || "no log output";
          })
          .catch(function (error) {
            document.getElementById("taskExecutionDetail").textContent = String(error);
          })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      function readJson(response) {
        return response.json().then(function (data) {
          if (!response.ok) throw new Error(data.error || ("HTTP " + response.status));
          return data;
        });
      }

      function showExecutionContext(id, button) {
        var previous = button.textContent;
        button.disabled = true;
        button.textContent = "Loading";
        fetch("/api/tasks/executions/" + encodeURIComponent(id) + "/context", { cache: "no-store" })
          .then(function (response) { return response.json(); })
          .then(function (data) {
            var text = Object.keys(data || {}).map(function (key) {
              return "===== " + key + " =====\n" + (data[key] || "");
            }).join("\n\n");
            document.getElementById("taskExecutionDetail").textContent = text || "no context";
          })
          .catch(function (error) {
            document.getElementById("taskExecutionDetail").textContent = String(error);
          })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      function refreshMergeStatus() {
        fetch("/api/tasks/merge-status", { cache: "no-store" })
          .then(function (response) { return response.json(); })
          .then(function (items) {
            var rows = (items || []).map(function (item) {
              return "<tr>"
                + "<td>" + escapeHtml(item.updatedAt) + "</td>"
                + "<td>" + escapeHtml(item.sourceDatabase) + "." + escapeHtml(item.sourceTable) + "</td>"
                + "<td>" + escapeHtml(item.processDt) + "</td>"
                + "<td>" + escapeHtml(item.runId) + "</td>"
                + "<td>" + escapeHtml(item.status) + "</td>"
                + "<td>binlog=" + escapeHtml(item.binlogRows) + ", old=" + escapeHtml(item.oldRows) + ", out=" + escapeHtml(item.outputRows) + "</td>"
                + "<td class=\"path\">" + escapeHtml(item.auditPath) + "</td>"
                + "</tr>";
            }).join("");
            if (!rows) {
              rows = '<tr><td colspan="7" class="muted">no merge status</td></tr>';
            }
            document.getElementById("mergeStatusTable").innerHTML =
              "<tr><th>Updated</th><th>Table</th><th>DT</th><th>Run ID</th><th>Status</th><th>Rows</th><th>Audit</th></tr>" + rows;
          });
      }

      setInterval(refreshTaskExecutions, 5000);
      setInterval(refreshMergeStatus, 5000);
    </script>
  </body>
</html>
