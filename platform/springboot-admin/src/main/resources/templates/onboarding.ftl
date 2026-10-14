<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>MySQL To Hive Onboarding</title>
    <link rel="stylesheet" href="/admin.css">
  </head>
  <body>
    <header>
      <h1>MySQL To Hive Onboarding</h1>
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
        <h2>接入新表</h2>
        <form method="post" action="/onboarding">
          <div class="grid">
            <div><label>Database</label><input name="databaseName" value="${request.databaseName}"></div>
            <div><label>Table</label><input name="tableName" value="${request.tableName}"></div>
            <div><label>DBA Metadata</label><input name="dbaMetadataPath" value="${request.dbaMetadataPath}"></div>
            <div><label>Primary Keys</label><input name="primaryKeys" value="${request.primaryKeys}"></div>
            <div><label>Version Column</label><input name="versionColumn" value="${request.versionColumn}"></div>
            <div><label>Partition Column</label><input name="partitionColumn" value="${request.partitionColumn}"></div>
          </div>
          <div class="actions"><button type="submit">Execute Onboarding</button></div>
        </form>
      </section>
      <section>
        <h2>执行计划</h2>
        <ol>
          <#list plan as item>
          <li>${item}</li>
          </#list>
        </ol>
      </section>
      <#if error??>
      <section>
        <h2>提交失败</h2>
        <pre class="bad">${error?html}</pre>
      </section>
      </#if>
      <#if execution??>
      <section>
        <h2>执行状态</h2>
        <p id="onboardingStatus">Execution #${execution.id}: ${execution.status}</p>
        <div class="actions">
          <button id="verifyButton" type="button" class="secondary" onclick="verifyOnboardedTable(this)" disabled>接入后验收</button>
          <button id="cancelButton" type="button" class="warn" onclick="cancelOnboarding(this)">取消</button>
          <a href="/api/tasks/executions/${execution.id}/log" target="_blank">查看日志</a>
          <a href="/tasks">任务中心</a>
        </div>
        <pre id="onboardingLog"></pre>
        <pre id="onboardingVerifyResult"></pre>
      </section>
      </#if>
    </main>
    <script>
      function verifyOnboardedTable(button) {
        var previous = button.textContent;
        var body = new URLSearchParams();
        body.set("databaseName", document.querySelector("[name='databaseName']").value);
        body.set("tableName", document.querySelector("[name='tableName']").value);
        body.set("bizDt", "");
        body.set("startDt", "");
        body.set("endDt", "");
        body.set("dryRun", "false");
        button.disabled = true;
        button.textContent = "Running";
        document.getElementById("onboardingVerifyResult").textContent = "running onboarding verify ...";
        fetch("/api/table-ops/onboarding-verify", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
          body: body
        })
          .then(function (response) { return response.json(); })
          .then(function (data) {
            if (data.id) {
              document.getElementById("onboardingVerifyResult").textContent =
                "submitted verification execution #" + data.id + ", status=" + data.status + "\nOpen Tasks for progress and logs.";
            } else {
              document.getElementById("onboardingVerifyResult").textContent = data.error || data.output || JSON.stringify(data);
            }
          })
          .catch(function (error) {
            document.getElementById("onboardingVerifyResult").textContent = String(error);
          })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      <#if execution??>
      var onboardingExecutionId = ${execution.id?c};
      var onboardingTimer = null;

      function terminal(status) {
        return ["SUCCESS", "FAILED", "TIMEOUT", "CANCELLED"].indexOf(status) >= 0;
      }

      function refreshOnboarding() {
        Promise.all([
          fetch("/api/tasks/executions/" + onboardingExecutionId, { cache: "no-store" }).then(function (response) { return response.json(); }),
          fetch("/api/tasks/executions/" + onboardingExecutionId + "/log", { cache: "no-store" }).then(function (response) { return response.text(); })
        ]).then(function (values) {
          var execution = values[0];
          document.getElementById("onboardingStatus").textContent =
            "Execution #" + execution.id + ": " + execution.status + (execution.errorMessage ? " - " + execution.errorMessage : "");
          document.getElementById("onboardingLog").textContent = values[1] || execution.outputExcerpt || "";
          document.getElementById("verifyButton").disabled = execution.status !== "SUCCESS";
          document.getElementById("cancelButton").disabled = terminal(execution.status);
          if (terminal(execution.status) && onboardingTimer) {
            clearInterval(onboardingTimer);
          }
        }).catch(function (error) {
          document.getElementById("onboardingStatus").textContent = String(error);
        });
      }

      function cancelOnboarding(button) {
        button.disabled = true;
        fetch("/api/tasks/executions/" + onboardingExecutionId + "/cancel", { method: "POST" })
          .then(refreshOnboarding)
          .catch(function (error) { document.getElementById("onboardingStatus").textContent = String(error); });
      }

      refreshOnboarding();
      onboardingTimer = setInterval(refreshOnboarding, 2000);
      </#if>
    </script>
  </body>
</html>
