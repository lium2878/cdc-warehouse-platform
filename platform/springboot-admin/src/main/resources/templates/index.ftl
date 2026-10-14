<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>CDC Warehouse Admin</title>
    <link rel="stylesheet" href="/admin.css">
  </head>
  <body>
    <header>
      <h1>CDC Warehouse Admin</h1>
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
        <h2>运行状态</h2>
        <div class="status-grid" id="serviceStatuses">
          <#list dashboard.serviceStatuses as service>
          <div class="status-card">
            <strong>${service.name}</strong>
            <span><span class="badge <#if service.running>badge-ok<#else>badge-bad</#if>"><#if service.running>UP<#else>DOWN</#if></span></span>
            <span>${service.status?html}</span>
            <span class="muted">${service.ports?html}</span>
          </div>
          </#list>
        </div>
      </section>

      <section>
        <h2>运维操作</h2>
        <div class="action-panel">
          <div>
            <label>Biz DT</label>
            <input id="actionBizDt" value="2026-07-07">
          </div>
          <div>
            <label>DS Mode</label>
            <select id="dsMode">
              <option value="--audit">audit</option>
              <option value="--dry-run">dry-run</option>
              <option value="--live">live</option>
            </select>
          </div>
          <button type="button" onclick="runAction('refresh-ops', this)">刷新快照</button>
          <button type="button" onclick="runAction('daily-merge', this)">跑每日 Merge</button>
          <button type="button" class="secondary" onclick="runAction('monitor-suite', this)">跑监控</button>
          <button type="button" class="secondary" onclick="runAction('verify-e2e-local', this)">本地 E2E 验收</button>
          <button type="button" class="secondary" onclick="runAction('verify-e2e-server', this)">服务器 E2E 验收</button>
          <button type="button" class="warn" onclick="runAction('ds-publish', this)">发布 DS</button>
        </div>
        <pre id="actionResult"></pre>
      </section>

      <section>
        <h2>操作审计</h2>
        <table id="actionAuditTable">
          <tr>
            <th>Time</th>
            <th>Action</th>
            <th>Operator</th>
            <th>Client IP</th>
            <th>Exit</th>
            <th>Duration</th>
          </tr>
          <tr><td colspan="6" class="muted">loading</td></tr>
        </table>
      </section>

      <section>
        <h2>已接入表</h2>
        <table>
          <tr>
            <th>Database</th>
            <th>Table</th>
            <th>ODS Binlog</th>
            <th>ODS Snapshot</th>
            <th>Primary Keys</th>
            <th>Version</th>
            <th>Partition</th>
            <th>Action</th>
          </tr>
          <#list tables as table>
          <tr>
            <td>${table.databaseName}</td>
            <td>${table.tableName}</td>
            <td>${table.odsBinlogTable!""}</td>
            <td>${table.odsTable!""}</td>
            <td><#if table.primaryKeys??>${table.primaryKeys?join(",")}</#if></td>
            <td>${table.versionColumn!""}</td>
            <td>${table.partitionColumn!""}</td>
            <td>
              <button type="button" onclick="showTableMetadata('${table.databaseName?js_string}', '${table.tableName?js_string}')">元数据</button>
              <button type="button" class="secondary" onclick="setHiveSql('select * from ods.${(table.odsTable!"")?js_string} limit 20')">查 ODS</button>
              <button type="button" class="warn" onclick="setHiveSql('msck repair table ods.${(table.odsTable!"")?js_string}')">修复分区</button>
            </td>
          </tr>
          </#list>
        </table>
        <pre id="tableMetadataResult"></pre>
      </section>

      <section>
        <h2>Hive 查询台</h2>
        <p class="muted">只允许 select/show/desc/describe/msck/use/explain。默认最多返回 100 行。</p>
        <textarea id="hiveSql">show databases</textarea>
        <div class="button-row">
          <button type="button" onclick="runHiveQuery(this)">执行查询</button>
          <button type="button" class="secondary" onclick="setHiveSql('show databases')">库列表</button>
          <button type="button" class="secondary" onclick="setHiveSql('show tables in ads')">ADS 表</button>
          <button type="button" class="secondary" onclick="setHiveSql('select * from ads.ads_comment_dashboard_1d where dt=\\'2026-07-07\\' limit 20')">查 ADS</button>
          <button type="button" class="warn" onclick="setHiveSql('msck repair table ods.ods_basiccomment_avatar_commentbatchsource_dic')">修复 ODS 分区</button>
          <button type="button" class="warn" onclick="setHiveSql('msck repair table ads.ads_comment_dashboard_1d')">修复 ADS 分区</button>
        </div>
        <pre id="hiveQueryMessage"></pre>
        <div class="query-result" id="hiveQueryResult"></div>
      </section>

      <section>
        <h2>数仓分层数据</h2>
        <p class="muted">来源优先 HDFS <span class="path">/warehouse</span>，无 HDFS 时回退本地 <span class="path">data/lake</span>。</p>
        <div id="warehouseLayers">
          <#list dashboard.warehouseLayers as layer>
          <div class="layer-block">
            <h3>${layer.layer} <span class="muted">${layer.tableCount} tables · ${layer.partitionCount} partitions</span></h3>
            <table>
              <tr>
                <th>Table</th>
                <th>Latest DT</th>
                <th>Rows</th>
                <th>Path</th>
                <th>Sample</th>
              </tr>
              <#list layer.tables as table>
              <tr>
                <td>${table.table}</td>
                <td>${table.latestPartition!""}</td>
                <td>${table.rowCount}</td>
                <td class="path">${table.latestPath!""}</td>
                <td><pre>${(table.sample!"")?html}</pre></td>
              </tr>
              </#list>
              <#if layer.tables?size == 0>
              <tr><td colspan="5" class="muted">no data</td></tr>
              </#if>
            </table>
          </div>
          </#list>
        </div>
      </section>

      <section>
        <h2>ODS / ODS Binlog 数据</h2>
        <#list dashboard.tableStorage as storage>
        <h3>${storage.databaseName}.${storage.tableName}</h3>
        <div class="grid-2">
          <div>
            <div class="path">${storage.odsBinlogPath}</div>
            <pre>${storage.odsBinlogSample?html}</pre>
          </div>
          <div>
            <div class="path">${storage.odsPath}</div>
            <pre>${storage.odsSample?html}</pre>
          </div>
        </div>
        </#list>
      </section>

      <section>
        <h2>HDFS / Hive</h2>
        <div class="grid-2">
          <div>
            <h3>HDFS /warehouse</h3>
            <pre id="hdfsWarehouseListing" data-follow="true">${dashboard.hdfsWarehouseListing?html}</pre>
          </div>
          <div>
            <h3>Hive Databases</h3>
            <pre id="hiveDatabases" data-follow="true">${dashboard.hiveDatabases?html}</pre>
          </div>
        </div>
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

      function nearBottom(el) {
        return el.scrollHeight - el.scrollTop - el.clientHeight < 24;
      }

      function setText(id, value) {
        var el = document.getElementById(id);
        if (!el) return;
        var previousTop = el.scrollTop;
        var follow = el.dataset.follow === "true" && nearBottom(el);
        el.textContent = value || "";
        if (follow) {
          el.scrollTop = el.scrollHeight;
        } else {
          el.scrollTop = previousTop;
        }
      }

      function renderServiceStatuses(services) {
        var root = document.getElementById("serviceStatuses");
        if (!root) return;
        root.innerHTML = (services || []).map(function (service) {
          var ok = service.running === true;
          return '<div class="status-card">'
            + '<strong>' + escapeHtml(service.name) + '</strong>'
            + '<span><span class="badge ' + (ok ? 'badge-ok' : 'badge-bad') + '">' + (ok ? 'UP' : 'DOWN') + '</span></span>'
            + '<span>' + escapeHtml(service.status) + '</span>'
            + '<span class="muted">' + escapeHtml(service.ports) + '</span>'
            + '</div>';
        }).join("");
      }

      function renderWarehouseLayers(layers) {
        var root = document.getElementById("warehouseLayers");
        if (!root) return;
        root.innerHTML = (layers || []).map(function (layer) {
          var rows = (layer.tables || []).map(function (table) {
            return '<tr>'
              + '<td>' + escapeHtml(table.table) + '</td>'
              + '<td>' + escapeHtml(table.latestPartition) + '</td>'
              + '<td>' + escapeHtml(table.rowCount) + '</td>'
              + '<td class="path">' + escapeHtml(table.latestPath) + '</td>'
              + '<td><pre>' + escapeHtml(table.sample) + '</pre></td>'
              + '</tr>';
          }).join("");
          if (!rows) {
            rows = '<tr><td colspan="5" class="muted">no data</td></tr>';
          }
          return '<div class="layer-block">'
            + '<h3>' + escapeHtml(layer.layer) + ' <span class="muted">' + escapeHtml(layer.tableCount) + ' tables · ' + escapeHtml(layer.partitionCount) + ' partitions</span></h3>'
            + '<table><tr><th>Table</th><th>Latest DT</th><th>Rows</th><th>Path</th><th>Sample</th></tr>'
            + rows
            + '</table></div>';
        }).join("");
      }

      var actionPollTimer = null;

      function pollAction(executionId) {
        if (actionPollTimer) clearInterval(actionPollTimer);
        function refresh() {
          fetch("/api/tasks/executions/" + executionId, { cache: "no-store" })
            .then(function (response) { return response.json(); })
            .then(function (execution) {
              document.getElementById("actionResult").textContent =
                "execution #" + execution.id + "\nstatus=" + execution.status
                + (execution.errorMessage ? "\nerror=" + execution.errorMessage : "")
                + "\nlog=/api/tasks/executions/" + execution.id + "/log\ntasks=/tasks";
              if (["SUCCESS", "FAILED", "TIMEOUT", "CANCELLED"].indexOf(execution.status) >= 0) {
                clearInterval(actionPollTimer);
                refreshDashboard();
                refreshActionAudits();
              }
            });
        }
        refresh();
        actionPollTimer = setInterval(refresh, 2000);
      }

      function runAction(action, button) {
        var payload = {
          bizDt: document.getElementById("actionBizDt").value,
          mode: document.getElementById("dsMode").value
        };
        var result = document.getElementById("actionResult");
        var previous = button.textContent;
        button.disabled = true;
        button.textContent = "Running";
        result.textContent = "running " + action + " ...";
        fetch("/api/actions/" + action, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        })
          .then(function (response) {
            return response.json().then(function (data) {
              if (!response.ok) throw new Error(data.error || JSON.stringify(data));
              return data;
            });
          })
          .then(function (data) {
            if (data.id) {
              pollAction(data.id);
            } else {
              result.textContent = "exitCode=" + data.exitCode + "\n" + (data.output || "");
            }
            refreshDashboard();
            refreshActionAudits();
          })
          .catch(function (error) {
            result.textContent = String(error);
          })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      function setHiveSql(sql) {
        document.getElementById("hiveSql").value = sql;
      }

      function showTableMetadata(databaseName, tableName) {
        var result = document.getElementById("tableMetadataResult");
        result.textContent = "loading " + databaseName + "." + tableName + " ...";
        fetch("/api/metadata/tables/" + encodeURIComponent(databaseName) + "/" + encodeURIComponent(tableName), { cache: "no-store" })
          .then(function (response) {
            if (!response.ok) throw new Error("metadata not found");
            return response.json();
          })
          .then(function (data) {
            result.textContent = JSON.stringify(data, null, 2);
          })
          .catch(function (error) {
            result.textContent = String(error);
          });
      }

      function renderHiveResult(data) {
        var message = document.getElementById("hiveQueryMessage");
        var result = document.getElementById("hiveQueryResult");
        message.textContent = "exitCode=" + data.exitCode + "\n" + (data.message || "");
        if (!data.columns || data.columns.length === 0) {
          result.innerHTML = "";
          return;
        }
        var header = data.columns.map(function (column) {
          return "<th>" + escapeHtml(column) + "</th>";
        }).join("");
        var rows = (data.rows || []).map(function (row) {
          return "<tr>" + row.map(function (cell) {
            return "<td>" + escapeHtml(cell) + "</td>";
          }).join("") + "</tr>";
        }).join("");
        result.innerHTML = "<table><tr>" + header + "</tr>" + rows + "</table>";
      }

      function runHiveQuery(button) {
        var previous = button.textContent;
        button.disabled = true;
        button.textContent = "Running";
        document.getElementById("hiveQueryMessage").textContent = "running hive query ...";
        fetch("/api/hive/query", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ sql: document.getElementById("hiveSql").value, limit: 100 })
        })
          .then(function (response) { return response.json(); })
          .then(renderHiveResult)
          .catch(function (error) {
            document.getElementById("hiveQueryMessage").textContent = String(error);
          })
          .finally(function () {
            button.disabled = false;
            button.textContent = previous;
          });
      }

      function refreshDashboard() {
        fetch("/api/dashboard", { cache: "no-store" })
          .then(function (response) { return response.json(); })
          .then(function (data) {
            setText("hdfsWarehouseListing", data.hdfsWarehouseListing);
            setText("hiveDatabases", data.hiveDatabases);
            renderServiceStatuses(data.serviceStatuses);
            renderWarehouseLayers(data.warehouseLayers);
            document.getElementById("refreshStatus").textContent = "updated";
          })
          .catch(function () {
            document.getElementById("refreshStatus").textContent = "refresh failed";
          });
      }

      function refreshActionAudits() {
        fetch("/api/actions/audits", { cache: "no-store" })
          .then(function (response) { return response.json(); })
          .then(function (audits) {
            var root = document.getElementById("actionAuditTable");
            if (!root) return;
            var rows = (audits || []).map(function (audit) {
              return "<tr>"
                + "<td>" + escapeHtml(audit.createdAt) + "</td>"
                + "<td>" + escapeHtml(audit.actionName) + "</td>"
                + "<td>" + escapeHtml(audit.operator) + "</td>"
                + "<td>" + escapeHtml(audit.clientIp) + "</td>"
                + "<td>" + escapeHtml(audit.exitCode) + "</td>"
                + "<td>" + escapeHtml(audit.durationMs) + " ms</td>"
                + "</tr>";
            }).join("");
            if (!rows) {
              rows = '<tr><td colspan="6" class="muted">no audit records</td></tr>';
            }
            root.innerHTML = "<tr><th>Time</th><th>Action</th><th>Operator</th><th>Client IP</th><th>Exit</th><th>Duration</th></tr>" + rows;
          })
          .catch(function () {
            var root = document.getElementById("actionAuditTable");
            if (root) {
              root.innerHTML = '<tr><th>Time</th><th>Action</th><th>Operator</th><th>Client IP</th><th>Exit</th><th>Duration</th></tr>'
                + '<tr><td colspan="6" class="muted">audit unavailable</td></tr>';
            }
          });
      }

      document.querySelectorAll("pre[data-follow='true']").forEach(function (el) {
        el.scrollTop = el.scrollHeight;
      });
      refreshActionAudits();
      setInterval(refreshDashboard, 5000);
      setInterval(refreshActionAudits, 5000);
    </script>
  </body>
</html>
