<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>Replay</title>
    <link rel="stylesheet" href="/admin.css">
  </head>
  <body>
    <header>
      <h1>Replay</h1>
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
        <h2>Maxwell Bootstrap Replay</h2>
        <p>Executes a full source MySQL snapshot. Start and end are audit labels only.</p>
        <form method="post" action="/replay">
          <div class="grid">
            <div><label>Database</label><input name="databaseName" value="${request.databaseName}"></div>
            <div><label>Table</label><input name="tableName" value="${request.tableName}"></div>
            <div><label>Start</label><input name="startTime" value="${request.startTime}"></div>
            <div><label>End</label><input name="endTime" value="${request.endTime}"></div>
          </div>
          <div class="actions"><button type="submit">Execute Full Replay</button></div>
        </form>
        <#if error??><pre>${error?html}</pre></#if>
        <#if command??>
        <pre>${command?html}</pre>
        </#if>
        <#if execution??>
        <pre>submitted execution ${execution.id}, status=${execution.status}</pre>
        </#if>
      </section>
      <section>
        <h2>Recent Replay Runs</h2>
        <table id="replayTable">
          <thead><tr><th>ID</th><th>Execution</th><th>Source</th><th>Status</th><th>Created</th><th>Action</th></tr></thead>
          <tbody>
          <#list records as item>
            <tr>
              <td>${item.id}</td>
              <td>${item.executionId!""}</td>
              <td>${item.databaseName?html}.${item.tableName?html}</td>
              <td>${item.status?html}</td>
              <td>${item.createdAt?html}</td>
              <td>
                <#if item.executionId??>
                <button type="button" class="secondary" onclick="showReplayLog(${item.executionId})">Log</button>
                <#if item.status == "PENDING" || item.status == "RUNNING">
                <button type="button" class="warn" onclick="cancelReplay(${item.executionId})">Cancel</button>
                </#if>
                </#if>
              </td>
            </tr>
          </#list>
          </tbody>
        </table>
      </section>
    </main>
    <pre id="replayLog"></pre>
    <script>
      function escapeHtml(value) {
        return String(value == null ? "" : value)
          .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
          .replace(/"/g, "&quot;").replace(/'/g, "&#039;");
      }

      function refreshReplays() {
        fetch("/api/replay/records", { cache: "no-store" })
          .then(function (response) { return response.json(); })
          .then(function (items) {
            var rows = (items || []).map(function (item) {
              var actions = "";
              if (item.executionId) {
                actions += "<button type=\"button\" class=\"secondary\" onclick=\"showReplayLog(" + Number(item.executionId) + ")\">Log</button> ";
                if (item.status === "PENDING" || item.status === "RUNNING") {
                  actions += "<button type=\"button\" class=\"warn\" onclick=\"cancelReplay(" + Number(item.executionId) + ")\">Cancel</button>";
                }
              }
              return "<tr><td>" + Number(item.id) + "</td><td>" + escapeHtml(item.executionId)
                + "</td><td>" + escapeHtml(item.databaseName) + "." + escapeHtml(item.tableName)
                + "</td><td>" + escapeHtml(item.status) + "</td><td>" + escapeHtml(item.createdAt)
                + "</td><td>" + actions + "</td></tr>";
            }).join("");
            document.getElementById("replayTable").innerHTML =
              "<thead><tr><th>ID</th><th>Execution</th><th>Source</th><th>Status</th><th>Created</th><th>Action</th></tr></thead><tbody>"
              + rows + "</tbody>";
          });
      }

      function showReplayLog(executionId) {
        fetch("/api/tasks/executions/" + executionId + "/log", { cache: "no-store" })
          .then(function (response) { return response.text(); })
          .then(function (value) { document.getElementById("replayLog").textContent = value || "no log output"; });
      }

      function cancelReplay(executionId) {
        fetch("/api/tasks/executions/" + executionId + "/cancel", { method: "POST" })
          .then(function () { refreshReplays(); });
      }

      setInterval(refreshReplays, 5000);
    </script>
  </body>
</html>
