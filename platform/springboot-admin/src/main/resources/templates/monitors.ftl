<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>Monitors</title>
    <link rel="stylesheet" href="/admin.css">
  </head>
  <body>
    <header>
      <h1>Monitors</h1>
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
        <h2>监控项</h2>
        <div class="actions">
          <button type="button" onclick="runMonitorSuite(this)">Run Monitor Suite</button>
          <button type="button" class="secondary" onclick="sendTestAlert(this)">Send Test Alert</button>
        </div>
        <pre id="monitorRunResult"></pre>
        <ul>
          <#list items as item>
          <li>${item}</li>
          </#list>
        </ul>
      </section>
      <section>
        <h2>Latest Results</h2>
        <table>
          <tr>
            <th>Type</th>
            <th>Table</th>
            <th>Status</th>
            <th>Message</th>
            <th>Metric</th>
            <th>Created At</th>
          </tr>
          <#list results as result>
          <tr>
            <td>${result.monitorType}</td>
            <td>${result.databaseName}.${result.tableName}</td>
            <td><#if result.status == "OK"><span class="ok">${result.status}</span><#else><span class="bad">${result.status}</span></#if></td>
            <td>${result.message!""}</td>
            <td>${result.metricValue!""}</td>
            <td>${result.createdAt}</td>
          </tr>
          </#list>
        </table>
      </section>
    </main>
    <script>
      function runMonitorSuite(button) {
        runAction(button, "/api/actions/monitor-suite", "running monitor suite ...");
      }

      function sendTestAlert(button) {
        runAction(button, "/api/actions/test-alert", "sending test alert ...");
      }

      function runAction(button, url, runningText) {
        var previous = button.textContent;
        var result = document.getElementById("monitorRunResult");
        button.disabled = true;
        button.textContent = "Running";
        result.textContent = runningText;
        fetch(url, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: "{}"
        })
          .then(function (response) {
            return response.json().then(function (data) {
              if (!response.ok) throw new Error(data.error || JSON.stringify(data));
              return data;
            });
          })
          .then(function (data) {
            if (data.id) {
              result.textContent = "submitted execution #" + data.id + ", status=" + data.status + "\nlog=/api/tasks/executions/" + data.id + "/log";
              pollExecution(data.id, result);
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

      function pollExecution(id, result) {
        var timer = setInterval(function () {
          fetch("/api/tasks/executions/" + id, { cache: "no-store" })
            .then(function (response) { return response.json(); })
            .then(function (execution) {
              result.textContent = "execution #" + id + "\nstatus=" + execution.status
                + (execution.errorMessage ? "\nerror=" + execution.errorMessage : "")
                + "\nlog=/api/tasks/executions/" + id + "/log";
              if (["SUCCESS", "FAILED", "TIMEOUT", "CANCELLED"].indexOf(execution.status) >= 0) {
                clearInterval(timer);
              }
            });
        }, 2000);
      }
    </script>
  </body>
</html>
