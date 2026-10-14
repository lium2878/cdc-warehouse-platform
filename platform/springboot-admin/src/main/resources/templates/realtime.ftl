<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>Realtime</title>
    <link rel="stylesheet" href="/admin.css">
  </head>
  <body>
    <header>
      <h1>Realtime Kudu / Impala</h1>
      <div class="refresh">Auto refresh: 5s <small id="refreshStatus"></small></div>
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
        <h2>Cluster</h2>
        <div class="status-grid" id="clusterStatus">
          <div class="status-card">
            <strong>Impala JDBC</strong>
            <span><span id="impalaBadge" class="badge <#if snapshot.impalaConnected>badge-ok<#else>badge-bad</#if>"><#if snapshot.impalaConnected>UP<#else>DOWN</#if></span></span>
            <span id="impalaMessage">${snapshot.message?html}</span>
          </div>
          <div class="status-card">
            <strong>Kudu Master UI</strong>
            <span><a href="${snapshot.kuduMasterUrl?html}" target="_blank">${snapshot.kuduMasterUrl?html}</a></span>
          </div>
          <div class="status-card">
            <strong>Impala UI</strong>
            <span><a href="${snapshot.impalaUrl?html}" target="_blank">${snapshot.impalaUrl?html}</a></span>
          </div>
        </div>
        <div class="actions">
          <button type="button" onclick="runKafkaToKudu(this)">Run Kafka -> Kudu Once</button>
          <button type="button" class="secondary" onclick="refreshRealtime()">Refresh</button>
        </div>
        <pre id="actionResult"></pre>
      </section>

      <section>
        <h2>Realtime Tables</h2>
        <div id="realtimeTables">
          <#include "realtime_tables.ftl">
        </div>
      </section>

      <section>
        <h2>Comment Analysis</h2>
        <div id="commentAnalysis" class="query-result"></div>
      </section>

      <section>
        <h2>Trade Analysis</h2>
        <div id="tradeAnalysis" class="query-result"></div>
      </section>

      <section>
        <h2>User Analysis</h2>
        <div id="userAnalysis" class="query-result"></div>
      </section>
    </main>
    <script>
      function escapeHtml(value) {
        return String(value == null ? "" : value)
          .replace(/&/g, "&amp;")
          .replace(/</g, "&lt;")
          .replace(/>/g, "&gt;")
          .replace(/"/g, "&quot;")
          .replace(/'/g, "&#039;");
      }

      function renderResult(id, data) {
        var root = document.getElementById(id);
        if (!root) return;
        if (!data || !data.columns || data.columns.length === 0) {
          root.innerHTML = '<pre>' + escapeHtml(data ? data.message : 'no data') + '</pre>';
          return;
        }
        var header = data.columns.map(function (column) { return '<th>' + escapeHtml(column) + '</th>'; }).join('');
        var rows = (data.rows || []).map(function (row) {
          return '<tr>' + row.map(function (cell) { return '<td>' + escapeHtml(cell) + '</td>'; }).join('') + '</tr>';
        }).join('');
        root.innerHTML = '<table><tr>' + header + '</tr>' + rows + '</table>';
      }

      function renderTables(tables) {
        var rows = (tables || []).map(function (table) {
          return '<tr><td>' + escapeHtml(table.name) + '</td><td>' + escapeHtml(table.rowCount) + '</td><td>' + escapeHtml(table.latestUpdateTime) + '</td></tr>';
        }).join('');
        if (!rows) rows = '<tr><td colspan="3" class="muted">no realtime tables</td></tr>';
        document.getElementById('realtimeTables').innerHTML = '<table><tr><th>Table</th><th>Rows</th><th>Latest Update</th></tr>' + rows + '</table>';
      }

      function renderSnapshot(data) {
        var ok = data.impalaConnected === true;
        var badge = document.getElementById('impalaBadge');
        badge.className = 'badge ' + (ok ? 'badge-ok' : 'badge-bad');
        badge.textContent = ok ? 'UP' : 'DOWN';
        document.getElementById('impalaMessage').textContent = data.message || '';
        renderTables(data.tables);
        renderResult('commentAnalysis', data.commentAnalysis);
        renderResult('tradeAnalysis', data.tradeAnalysis);
        renderResult('userAnalysis', data.userAnalysis);
        document.getElementById('refreshStatus').textContent = 'updated';
      }

      function refreshRealtime() {
        fetch('/api/realtime', { cache: 'no-store' })
          .then(function (response) { return response.json(); })
          .then(renderSnapshot)
          .catch(function (error) { document.getElementById('refreshStatus').textContent = String(error); });
      }

      function runKafkaToKudu(button) {
        var previous = button.textContent;
        button.disabled = true;
        button.textContent = 'Running';
        document.getElementById('actionResult').textContent = 'running Kafka -> Kudu batch ...';
        fetch('/api/realtime/kafka-to-kudu', { method: 'POST' })
          .then(function (response) {
            return response.json().then(function (data) {
              if (!response.ok) throw new Error(data.error || JSON.stringify(data));
              return data;
            });
          })
          .then(function (data) {
            document.getElementById('actionResult').textContent =
              'submitted execution #' + data.id + ', status=' + data.status + '\\nlog=/api/tasks/executions/' + data.id + '/log';
            pollRealtimeExecution(data.id);
          })
          .catch(function (error) { document.getElementById('actionResult').textContent = String(error); })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      function pollRealtimeExecution(id) {
        var timer = setInterval(function () {
          fetch('/api/tasks/executions/' + id, { cache: 'no-store' })
            .then(function (response) { return response.json(); })
            .then(function (execution) {
              document.getElementById('actionResult').textContent =
                'execution #' + id + '\\nstatus=' + execution.status
                + (execution.errorMessage ? '\\nerror=' + execution.errorMessage : '')
                + '\\nlog=/api/tasks/executions/' + id + '/log';
              if (["SUCCESS", "FAILED", "TIMEOUT", "CANCELLED"].indexOf(execution.status) >= 0) {
                clearInterval(timer);
                refreshRealtime();
              }
            });
        }, 2000);
      }

      refreshRealtime();
      setInterval(refreshRealtime, 5000);
    </script>
  </body>
</html>
