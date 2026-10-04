# 代码讲解手册

这份文档用于面试前快速熟悉代码。范围只包含项目自有代码、脚本、配置、SQL、模板和测试；不包含 `.git`、`.venv-kudu`、`platform/springboot-admin/.m2`、`platform/springboot-admin/target`、`docker/hadoop/dist`、`docker/hive/dist`、`data` 这类本地缓存、编译产物、下载的第三方组件或运行数据。

## 总体链路

项目目标是还原一个 CDC 数仓平台：

```text
MySQL -> Maxwell -> Kafka -> Spark Streaming -> HDFS ods_binlog
     -> Spark SQL ODS merge -> Hive ODS
     -> DIM/DWD/DWS/DWT/ADS -> BI 查询
     -> Kudu/Impala 实时查询链路
```

面试可以这样讲：

- 数据接入：Maxwell 采集 MySQL binlog 写入 Kafka，Spark Streaming 消费 Kafka 写 HDFS 的 `ods_binlog`。
- 离线合并：每天 Spark SQL 读取 T-1 binlog 和受影响 ODS 分区，按主键和版本开窗，过滤 delete，生成最新 ODS 快照。
- 数仓分层：SQL 文件覆盖 ODS、DIM、DWD、DWS、DWT、ADS。
- 平台能力：SpringBoot 管理元数据、任务、监控、日志、回放、Hive 查询、实时 Kudu/Impala 查询。
- 实时链路：Kafka binlog 经过脚本转换后 upsert 到 Kudu，通过 Impala 查询实时结果。

## 根目录文件

- `.env.example`：本地 Docker Compose 环境变量模板。面试说它用于覆盖 MySQL 密码、端口、profile 等部署参数。
- `.github/workflows/ci.yml`：GitHub Actions CI，负责跑 Python 单测、SpringBoot 编译、Shell 语法、Docker Compose 配置检查。
- `.gitignore`：忽略运行数据、虚拟环境、编译产物等。
- `README.md`：项目入口说明，包含架构、快速启动、部署入口、权限/告警入口、实时 Kudu/Impala 用法。
- `requirements.txt`：Python 依赖。核心是 `PyYAML`、`kafka-python`、`pyarrow`、`impyla`、`thrift-sasl`，PySpark 由容器或本机环境提供。

## `warehouse/onboarding.py`

平台侧新表接入辅助工具，负责根据 DBA 元数据生成内部元数据、Hive DDL 和 ODS merge SQL。

- `warehouse/onboarding.py`：根据 DBA 元数据生成项目需要的表元数据、Hive DDL 和 ODS merge SQL。面试重点：新增 MySQL 表接入数仓时，不手写所有 SQL，而是由元数据驱动生成。

## `configs`

配置加载层，区分 dev/prod。

- `configs/app-dev.yaml`：开发环境配置，指向本地 Docker HDFS、DS、告警 outbox 等默认值。
- `configs/app-prod.yaml`：生产环境配置，依赖环境变量注入 HDFS、WebHDFS、DS、进度目录、延迟阈值、告警通道等。
- `configs/kafka.yaml`：Kafka topic、bootstrap server 等配置。
- `configs/loader.py`：按 `ENVIRONMENT` 读取 `app-dev.yaml` 或 `app-prod.yaml`，同时解析 `${ENV_VAR}` 形式的环境变量。面试可以讲这是为了同一套代码支持本地和生产部署。

## `deploy`

服务器直接部署相关文件，不依赖 Kubernetes。

### `deploy/prod`

- `deploy/prod/admin.env.example`：SpringBoot 管理平台生产环境变量模板。
- `deploy/prod/jobs.env.example`：Spark/离线任务生产环境变量模板，包括 Kafka、HDFS、checkpoint、延迟阈值。
- `deploy/prod/run_admin.sh`：生产环境启动 SpringBoot Admin 的脚本。
- `deploy/prod/submit_daily_merge.sh`：生产环境手动提交每日 ODS merge 的兼容入口，内部调用统一任务入口。
- `deploy/run_job.sh`：Spark/Python 任务统一入口，本地和生产都通过它启动 `spark-streaming`、`daily-merge`、`layers`。

### `deploy/server`

这一组是 systemd 部署方案。

- `deploy/server/README.md`：服务器部署说明。
- `deploy/server/admin.env.example`：服务器版 Admin 环境变量模板。
- `deploy/server/jobs.env.example`：服务器版 Spark/任务环境变量模板。
- `deploy/server/nginx/cdc-warehouse.conf`：Nginx HTTPS 反代模板，包含 TLS、HSTS、安全 Header 和 IP 白名单示例。
- `deploy/server/cdc-admin.service`：SpringBoot Admin 的 systemd service。
- `deploy/server/cdc-spark-streaming.service`：Spark Streaming 长驻任务的 systemd service。
- `deploy/server/cdc-daily-merge.service`：每日 merge 的一次性 systemd service。
- `deploy/server/cdc-daily-merge.timer`：触发每日 merge 的 systemd timer。
- `deploy/server/cdc-ops-refresh.service`：定时刷新运行状态快照的 service。
- `deploy/server/control.sh`：统一控制脚本，封装 start/stop/status/logs/health/smoke/merge。
- `deploy/server/healthcheck.sh`：检查 Admin、Spark Streaming、HDFS、Hive、DS 等服务状态。
- `deploy/server/install.sh`：安装 systemd unit、复制代码和 env 模板。
- `deploy/server/preflight.sh`：部署前检查 Java、Python、Spark、配置项等。
- `deploy/server/prod_smoke.sh`：生产 smoke test，验证关键服务和 HDFS/Hive 读写。
- `deploy/server/refresh_ops_snapshot_server.sh`：服务器模式下采集状态、日志、容器或进程快照。
- `deploy/server/run_daily_merge.sh`：systemd 调用的每日 merge 入口。
- `deploy/server/run_ops_refresh_loop.sh`：循环刷新 ops 快照。
- `deploy/server/run_spark_streaming.sh`：生产 Spark Streaming 启动脚本，用 `spark-submit` 消费 Kafka 写 HDFS；支持 `startingOffsets`、`maxOffsetsPerTrigger`、坏数据落地目录和 trigger 周期。
- `deploy/server/uninstall.sh`：卸载 systemd unit。

## `docker`

本地真实组件部署，用 Docker Compose 起 MySQL、Maxwell、Kafka、HDFS、Hive、DolphinScheduler、SpringBoot、Kudu/Impala。

- `docker/docker-compose.yml`：主 compose。包含 MySQL、Kafka、Zookeeper、Maxwell、Admin、Spark Streaming、DolphinScheduler、ops-refresh。
- `docker/docker-compose.hive.yml`：HDFS NameNode/DataNode 和 HiveServer2。
- `docker/docker-compose.kudu.yml`：Kudu master/tserver 和 Impala，用于实时数仓联调。
- `docker/hadoop/conf/core-site.xml`：Hadoop core 配置，指定 HDFS 地址。
- `docker/hadoop/conf/hdfs-site.xml`：HDFS 存储目录、副本数、WebHDFS 等配置。
- `docker/hadoop/conf/mapred-site.xml`：MapReduce 配置，本地模式。
- `docker/hadoop/conf/yarn-site.xml`：YARN 配置占位，当前本地主要走 local Spark。
- `docker/hive/conf/hive-site.xml`：HiveServer2、metastore、warehouse 目录配置。
- `docker/maxwell/config.properties`：Maxwell 连接 MySQL、写 Kafka 的配置。
- `docker/mysql/init/00-dolphinscheduler.sql`：初始化 DolphinScheduler 需要的 MySQL 库/用户。
- `docker/mysql/init/01-platform.sql`：初始化管理平台元数据表、任务表、规则表、默认数据。
- `docker/mysql/init/02-business.sql`：初始化业务库和样例业务表。
- `docker/spark-streaming/Dockerfile`：Spark Streaming 容器镜像，安装 Python 依赖和 PySpark。
- `docker/springboot-admin/Dockerfile`：SpringBoot Admin 镜像，打包 Java 应用，并安装 Python 运行环境以便页面按钮触发脚本。

## `docs`

项目文档。面试前重点看 `architecture.md`、`binlog_merge.md`、`deployment_guide_zh.md`、`realtime_kudu_impala.md` 和本文档。

- `docs/architecture.md`：总体架构和目录结构说明。
- `docs/binlog_merge.md`：binlog merge 原理，讲清 `union all + row_number + delete 过滤`。
- `docs/deployment_guide.md`：英文部署文档。
- `docs/deployment_guide_zh.md`：中文部署文档，最适合面试前复盘部署流程。
- `docs/deployment_profiles.md`：dev/prod 配置差异说明。
- `docs/docker_runbook.md`：本地 Docker 运行手册。
- `docs/finebi_mapping.md`：ADS 到 FineBI 展示层的映射说明。
- `docs/metrics.md`：指标口径说明。
- `docs/production_checklist.md`：生产上线前检查清单。
- `docs/project_progress_zh.md`：项目进度、已完成和未完成事项。
- `docs/realtime_kudu_impala.md`：Kudu/Impala 实时数仓联调说明。
- `docs/springboot_mysql.md`：SpringBoot 接 MySQL 的说明。
- `docs/warehouse_layers.md`：数仓分层说明。
- `docs/code_walkthrough_zh.md`：本文档。

## `ingestion`

数据接入层。

### `ingestion/bootstrap`

- `ingestion/bootstrap/bootstrap_maxwell.properties`：Maxwell bootstrap 模式配置，用于全量重放。
- `ingestion/bootstrap/bootstrap_table.sh`：调用 Maxwell bootstrap 的 shell 入口。
- `ingestion/bootstrap/mysql_bootstrap.py`：不用 Maxwell 时，也可以从 MySQL 表导出全量数据并包装成 Maxwell `bootstrap-insert` 事件。面试重点：新增表接入前先做全量同步，避免 ODS 快照缺历史数据。

### `ingestion/kafka`

- `ingestion/kafka/kafka_topic_to_jsonl.py`：把 Kafka topic 导出为 JSONL。优先用 Docker 容器里的 `kafka-console-consumer`，没有 Docker 时用 `kafka-python`。它主要服务一次性调试、Kafka->Kudu micro batch 和本地验证。

## `metadata`

元数据和规则配置。核心思想是“元数据驱动建表、merge、接入、监控”。

### `metadata/dba`

DBA 提供的源表结构，用于字段监控和 onboarding。

- `metadata/dba/basiccomment.avatar_commentbatchsource.json`：评论批次来源表结构。
- `metadata/dba/trade.order_info.json`：订单表结构。
- `metadata/dba/user.user_info.json`：用户表结构。

### `metadata/tables`

平台内部使用的表元数据。

- `metadata/tables/basiccomment.avatar_commentbatchsource.json`：定义主键、版本列、分区列、字段列表和 ODS 表名。
- `metadata/tables/trade.order_info.json`：订单表内部元数据。
- `metadata/tables/user.user_info.json`：用户表内部元数据。

### `metadata/rules`

- `metadata/rules/sensitive_columns.json`：敏感字段字典，用于手机号、邮箱等脱敏。
- `metadata/rules/data_quality_rules.json`：数据质量规则。
- `metadata/rules/special_value_rules.json`：特殊值/非空/增长类规则。

### `metadata/lineage`

- `metadata/lineage/__init__.py`：包标记文件。
- `metadata/lineage/field_lineage.py`：字段级血缘分析，解析 SQL/配置形成上下游字段关系。面试可讲它用于影响分析：源字段变更时知道影响哪些下游表和指标。

## `monitors`

监控能力。它们既可以被命令行调用，也可以被 SpringBoot 页面触发。

- `monitors/delay_monitor.py`：读取同步进度，判断 Kafka/Spark 数据是否延迟。
- `monitors/field_alter_sql.py`：根据字段差异生成 Hive `ALTER TABLE ADD COLUMNS`。
- `monitors/field_monitor.py`：比较平台元数据和 DBA 元数据，发现新增/删除/类型变化。
- `monitors/field_monitor_job.py`：字段监控命令行入口。
- `monitors/notifier.py`：告警通知器，支持 `file/stdout/email/dingtalk/wechat/feishu`，通过 `ALERT_CHANNELS` 配置多通道发送，并把所有告警写入 outbox 审计。
- `monitors/null_rate_monitor.py`：检查关键字段空值率。
- `monitors/partition_monitor.py`：检查分区是否缺失。
- `monitors/plaintext_alert.py`：明文敏感数据告警模型。
- `monitors/result_store.py`：保存本地监控审计；配置管理库连接后同步写 `monitor_result`。
- `monitors/row_count_monitor.py`：源表和目标表行数校验。
- `monitors/run_monitor_suite.py`：配置驱动监控入口，扫描全部表元数据并用 Spark 检查本地/HDFS Parquet。
- `monitors/sensitive_text_monitor.py`：扫描落地数据，发现疑似明文敏感值。
- `monitors/special_value_monitor.py`：特殊值规则校验，例如不允许 `unknown`。
- `monitors/special_value_sql_builder.py`：生成特殊值校验 SQL。
- `monitors/table_update_monitor.py`：监控业务表最大更新时间，发现业务迁库/换表/停写。
- `monitors/table_update_monitor_job.py`：表更新时间监控命令行入口。

## `platform/springboot-admin`

SpringBoot 数据管理平台。面试可以按 MVC 讲：Controller 接 HTTP，Service 封装业务，Repository 访问 MySQL，Model 是 DTO/实体，Freemarker 是页面。

### 根文件

- `platform/springboot-admin/pom.xml`：Maven 配置，依赖 SpringBoot Web/JDBC/Freemarker/Security/MySQL/Swagger。
- `platform/springboot-admin/init_mysql.sh`：初始化平台 MySQL 的辅助脚本。

### `config`

- `WarehouseAdminApplication.java`：SpringBoot 启动类。
- `config/SwaggerConfig.java`：Swagger/OpenAPI 配置。
- `config/WarehouseProperties.java`：绑定 `warehouse.*` 配置，包括 auth、actions、mysql、validation。
- `config/TaskExecutorConfig.java`：配置后台任务线程池和心跳定时器；长任务不占用 HTTP 请求线程。

### `controller`

- `AuthController.java`：提供 `/api/auth/login`，返回 JWT。
- `LoginController.java`：页面登录和退出，写入 JWT cookie。
- `HiveController.java`：提供 Hive 查询接口。
- `LogController.java`：日志页面和日志 API。
- `MetadataController.java`：元数据查询/更新 API。
- `MonitorController.java`：监控页面和监控 API。
- `OnboardingController.java`：新表接入页面和 API。
- `PlatformActionController.java`：统一触发平台动作，例如 merge、监控、DS 发布；同时记录操作审计，包括操作人、客户端 IP、请求参数、退出码、输出摘要和耗时。
- `RealtimeController.java`：实时数仓页面和 Kudu/Impala API。
- `ReplayController.java`：数据回放页面和 API。
- `RuleController.java`：敏感规则/质量规则页面和 API。
- `TaskController.java`：任务配置和执行 API，包含异步提交、状态查询、实时日志、取消、重跑和 ODS merge 状态。
- `TableOpsController.java`：表级运维页面和 API，包含补数、链路检查、一致性检查和新表接入后验收。

### `model`

- `ActionRequest.java`：页面触发平台动作时的请求参数。
- `ActionAudit.java`：高风险平台动作的审计记录模型。
- `CommandResult.java`：脚本执行结果，包含 exitCode 和 output。
- `DashboardSnapshot.java`：首页聚合展示数据。
- `HiveQueryRequest.java`：Hive 查询请求。
- `HiveQueryResult.java`：Hive/Impala 查询结果。
- `LoginRequest.java`：登录请求。
- `MonitorResult.java`：监控结果实体。
- `OnboardRequest.java`：新表接入请求。
- `RealtimeSnapshot.java`：实时页聚合状态。
- `RealtimeTableView.java`：实时表展示模型。
- `ReplayRequest.java`：回放请求。
- `RuleRecord.java`：规则记录。
- `ServiceStatus.java`：服务状态卡片。
- `SparkTaskConfig.java`：Spark 任务配置。
- `TableOpsRequest.java`：表级运维请求参数，包括库表、业务日期、补数开始/结束日期。
- `TaskExecution.java`：任务执行模型，记录状态、触发人、超时、日志路径、心跳、父执行、开始/结束时间和结果。
- `MergeTaskStatus.java`：ODS merge 状态模型，从 merge audit JSON 同步到 MySQL。
- `TableMetadata.java`：表元数据实体。
- `TableStorageView.java`：表存储路径展示。
- `WarehouseLayerView.java`：数仓层级展示。
- `WarehouseTableView.java`：数仓表展示。

### `repository`

这些类用 `JdbcTemplate` 访问 MySQL。

- `MonitorResultRepository.java`：监控结果表 CRUD。
- `ActionAuditRepository.java`：操作审计表写入和最近记录查询。写审计时捕获数据库异常，避免审计失败影响真正的运维动作。
- `ReplayRepository.java`：回放记录表 CRUD。
- `RuleRepository.java`：规则表 CRUD。
- `TableMetadataRepository.java`：表元数据 CRUD。
- `TaskRepository.java`：任务配置 CRUD。
- `TaskExecutionRepository.java`：持久化任务生命周期；事务创建任务和防重锁，更新心跳、终态并释放锁。
- `MergeTaskStatusRepository.java`：ODS merge 状态写入和查询，用于排查某个分区是否 merge 成功。

### `security`

- `AuthUserService.java`：解析 `AUTH_USERS`，支持 `ADMIN/OPERATOR/VIEWER` 三类角色；未配置时兼容 `ADMIN_USER/ADMIN_PASS` 单管理员。
- `JwtAuthFilter.java`：从 Authorization header 或 cookie 取 JWT，解析 role，设置 Spring Security 上下文。
- `JwtTokenProvider.java`：生成和校验 JWT，token 内包含用户名和角色。
- `SecurityConfig.java`：安全白名单和 RBAC 鉴权规则。dev 可开放部分接口，prod 要登录；高风险操作如 DS 发布只允许 `ADMIN`。

### `service`

- `CommandExecutorService.java`：管理系统进程；日志直接落文件，正确执行超时和取消，并返回日志尾部摘要。
- `DashboardService.java`：组装首页数据，包括服务状态、Hive 表、日志摘要。
- `HiveQueryService.java`：通过 JDBC 查询 Hive/Impala。
- `MetadataService.java`：元数据业务逻辑，MySQL 不可用时 dev 可读 fallback JSON。
- `MonitorService.java`：触发并保存监控结果。
- `OnboardingService.java`：新表接入，调用 Python onboarding 脚本生成元数据和 SQL。
- `PlatformActionService.java`：页面按钮动作路由，例如 daily merge、monitor suite、DS publish、Kafka->Kudu、本地/服务器 E2E 验收。
- `MergeTaskStatusService.java`：扫描 `data/ops/merge_audit` 下的 merge 审计 JSON，并同步到 `merge_task_status` 表。
- `RealtimeService.java`：查询 Impala/Kudu 实时表、视图和连接状态。
- `ReplayService.java`：校验回放请求，执行全量 MySQL 快照重放并更新任务状态。
- `RuleService.java`：敏感规则读取/保存。
- `StartupValidationService.java`：启动时校验生产配置，避免默认密码、默认 secret、缺路径。
- `TaskConfigService.java`：任务配置读取/保存，将手动运行和历史重跑提交给异步执行服务。
- `TaskExecutionService.java`：任务状态机核心，负责提交、后台执行、防重复、心跳、超时、取消、重跑、日志读取和陈旧任务恢复。
- `TableOpsService.java`：表级运维逻辑。补数会执行 bootstrap 和 merge；链路检查会检查 MySQL、Kafka、HDFS、Hive；一致性检查会对比 MySQL 与 ODS 行数并写入监控结果；新表验收会对当前表执行 bootstrap、merge 和 ODS/Hive 检查。

### `resources`

- `application.yml`：通用配置。
- `application-dev.yml`：开发 profile，开放部分动作，允许 fallback。
- `application-prod.yml`：生产 profile，关闭 fallback，启用严格校验。
- `schema.sql`：SpringBoot 平台表结构，包括元数据、任务、任务历史、merge 状态、规则、监控、回放和操作审计表。
- `data.sql`：平台默认数据和默认任务。
- `static/admin.css`：管理平台样式。
- `templates/index.ftl`：首页 Dashboard，包含运行状态、运维操作、操作审计、Hive 查询和分层数据预览。
- `templates/login.ftl`：登录页。
- `templates/logs.ftl`：日志页。
- `templates/monitors.ftl`：监控页。
- `templates/onboarding.ftl`：新表接入页。
- `templates/realtime.ftl`：实时 Kudu/Impala 页。
- `templates/realtime_tables.ftl`：实时表格片段。
- `templates/replay.ftl`：回放页。
- `templates/rules.ftl`：规则页。
- `templates/tasks.ftl`：任务配置页，支持新增任务、手动运行、查看执行历史、查看失败输出、重跑历史命令和 ODS merge 状态。
- `templates/table_ops.ftl`：表级运维页，支持补数、链路检查、一致性检查、新表验收。

## `realtime`

实时数仓链路，核心是 Kudu/Impala。

### `realtime/impala`

- `bootstrap.py`：创建 realtime 数据库、Kudu 表、Impala 视图。
- `query.py`：Impala 查询客户端封装。
- `views/v_realtime_comment_analysis.sql`：评论批次实时分析视图。
- `views/v_realtime_trade_analysis.sql`：订单实时分析视图。
- `views/v_realtime_user_analysis.sql`：用户实时分析视图。

### `realtime/kudu`

- `kudu_client.py`：通过 Impala SQL 操作 Kudu 表，支持 create、upsert、delete、query。
- `ddl/kudu_avatar_commentbatchsource.sql`：评论批次 Kudu 表 DDL。
- `ddl/kudu_trade_order_info.sql`：订单 Kudu 表 DDL。
- `ddl/kudu_user_info.sql`：用户 Kudu 表 DDL。

## `warehouse/replay`

数据回放模块。面试可以说：用于数据丢失或首次镜像时，把指定时间段 binlog 重放到目标 topic/文件。

- `warehouse/replay/replay_plan.py`：定义回放计划，包括库表、开始结束时间、模式。
- `warehouse/replay/replay_runner.py`：执行回放，按计划过滤事件并写目标。
- `warehouse/replay/replay_sql_builder.py`：生成回放相关 SQL 或查询条件。

## `scripts`

项目运维和任务入口。面试时通常会被问“怎么跑”，这里就是答案。

- `bootstrap_mysql_table.py`：执行 MySQL 表全量 bootstrap，默认写 HDFS `ods_binlog`。
- `check_local_stack.sh`：检查本地 Docker 栈状态、Kafka、Hive、HDFS、Admin。
- `docker_down.sh`：停止本地 Docker 组件。
- `docker_seed_change.sh`：向 MySQL 样例业务表写一条变更，用于触发 Maxwell/Kafka。
- `docker_up.sh`：启动本地完整 Docker 栈。
- `download_hadoop_hive.sh`：下载本地 HDFS/Hive 容器需要的 Hadoop/Hive 二进制。
- `init_hdfs_hive.sh`：初始化 HDFS 目录和 Hive DDL。
- `kafka_to_jsonl.sh`：把 Kafka topic 导出为 JSONL。
- `local_smoke.sh`：本地全链路 smoke，启动组件并跑 HDFS/Hive E2E。
- `onboard_table.py`：命令行接入新表，生成元数据、Hive DDL、merge SQL。
- `publish_dolphinscheduler.py`：发布 DolphinScheduler 工作流，支持 audit/dry-run/live。
- `refresh_ops_snapshot.sh`：刷新运行状态快照，供日志/首页读取。
- `run_daily_ods_merge.sh`：每日 ODS merge 的本地兼容入口，内部转发到 `deploy/run_job.sh daily-merge`。
- `run_e2e_hdfs_pipeline.sh`：真实本地 E2E：MySQL 写入 -> Kafka -> HDFS -> merge -> Hive ADS。
- `run_local_kudu_impala_smoke.sh`：启动或验证 Kudu/Impala 实时链路。
- `run_realtime_kudu_smoke.py`：Kudu/Impala 实时写入和查询 smoke。
- `spark_sql_ods_merge_daily.py`：每日离线 merge，默认处理昨天分区，支持 merge audit 和覆盖前备份。
- `spark_streaming_kafka_to_kudu_loop.py`：循环 Kafka->Kudu micro batch。
- `spark_streaming_kafka_to_kudu_once.py`：执行一次 Kafka->Kudu micro batch。
- `table_ops.sh`：表级运维命令行入口，支持补数、链路检查、MySQL/ODS 行数一致性检查、新表接入后验收，并支持 `--dry-run` 预览命令。SpringBoot 的 Table Ops 页面也调用它。
- `validate_deployment_config.py`：校验部署配置是否缺失关键环境变量。
- `verify_end_to_end.sh`：部署后的一键端到端验收入口。本地模式会验证 MySQL、Maxwell、Kafka、SparkStreaming、HDFS、ODS merge、Hive ODS、ADS 和 SpringBoot API；服务器模式会调用 `deploy/server/control.sh health/smoke`。

## `warehouse/spark_runtime`

PySpark 公共运行时。

- `warehouse/spark_runtime/__init__.py`：包标记文件。
- `warehouse/spark_runtime/maxwell_schema.py`：Maxwell JSON 的 Spark StructType schema，以及元数据转 Spark schema。
- `warehouse/spark_runtime/session.py`：创建 SparkSession，设置时区和 HDFS DataNode hostname 参数。

## `warehouse/storage`

存储适配层，让同一套逻辑可以写本地文件或 HDFS。

- `warehouse/storage/__init__.py`：包标记文件。
- `warehouse/storage/hdfs_web.py`：WebHDFS 客户端，支持读写 JSONL/CSV、mkdir、exists、list_status。
- `warehouse/storage/local_lake.py`：本地文件 lake 适配器，主要给单测和 debug fallback 用。

## `streaming`

流式接入和公共 CDC 处理。

### `streaming/common`

- `binlog_parser.py`：解析 Maxwell 事件，抽取库表、类型、业务日期等。
- `checkpoint.py`：文件 checkpoint，记录 topic offset 或处理进度。
- `logging_config.py`：日志格式配置，支持 text/json。
- `maxwell_event.py`：Maxwell 事件模型。
- `sensitive_masker.py`：根据敏感规则对字段做 md5 或默认值处理。
- `shutdown.py`：优雅退出处理。

### `streaming/offline_sink`

- `pyspark_kafka_to_hdfs.py`：真实 Spark Streaming 作业，从 Kafka 读 Maxwell JSON，按 `db/table/dt` 写 HDFS `ods_binlog`。

### `streaming/realtime_sink`

- `pyspark_kafka_to_kudu.py`：生产实时链路核心。Structured Streaming 直接消费 Kafka，按主键和版本折叠 micro-batch，通过 Impala UPSERT/DELETE Kudu，并由 Spark checkpoint 管理 offset。
- `kafka_to_kudu.py`：一次性 smoke/debug 写入入口。

## `tests`

单测目录。面试时可说：这里覆盖 merge、监控、接入、调度、实时 Kudu/Impala、回放等核心逻辑。

- `tests/test_binlog_merge.py`：验证 binlog merge：update 覆盖旧快照、delete 删除、merge loop。
- `tests/test_ds_client_http.py`：验证 DolphinScheduler API client、血缘、行数/分区/空值监控。
- `tests/test_ingestion_controls.py`：验证延迟门禁、字段 alter SQL、敏感字段脱敏。
- `tests/test_monitors.py`：验证字段监控、敏感文本、特殊值监控。
- `tests/test_mysql_bootstrap.py`：验证 MySQL bootstrap SQL、TSV 类型转换、bootstrap 事件写入。
- `tests/test_realtime_kudu_impala.py`：验证 Kudu/Impala bootstrap、SQL 切分、local/real Kudu 路径。
- `tests/test_scheduler_and_monitor_store.py`：验证 DS audit、监控结果存储、通知器。
- `tests/test_spark_runtime.py`：验证 PySpark 检测函数。
- `tests/test_streaming_and_replay.py`：验证 Maxwell 解析、checkpoint、微批写入、Kudu upsert/delete、回放。
- `tests/test_table_onboarding.py`：验证新表接入产物生成。

## `warehouse`

离线数仓开发目录。

### `warehouse/generator`

- `render_hive_ddl.py`：根据表元数据生成 Hive DDL。
- `render_ods_merge_sql.py`：根据元数据渲染 ODS merge SQL。

### `warehouse/jobs`

- `check_delay_gate.py`：命令行检查延迟门禁。
- `delay_gate.py`：判断某表是否达到 merge 条件，避免数据没到齐就合并。
- `merge_ods_snapshot.py`：本地 fallback merge 实现，保留用于单测和理解 merge 算法。
- `pyspark_ods_merge.py`：真实 PySpark ODS merge，读取 HDFS `ods_binlog` 和旧 ODS，按主键/版本开窗，过滤 delete，覆盖写受影响 ODS 分区；同时写 merge audit JSON，并可把覆盖前旧分区备份到 `ods_backup`。
- `warehouse/metadata_loader.py`：统一表元数据读取和校验入口，避免各任务散落读取 JSON；后续如果改成 MySQL 元数据中心，只需要收敛改这里。

### `warehouse/scheduler/dolphinscheduler`

- `ds_api_client.py`：DolphinScheduler OpenAPI 客户端，支持 audit 文件输出和 live 调用。
- `warehouse_daily_process.json`：每日数仓工作流定义。
- `tasks/merge_ods_basiccomment_avatar_commentbatchsource_dic.json`：评论表 merge 任务定义。
- `tasks/merge_ods_trade_order_info_dic.json`：订单表 merge 任务定义。
- `tasks/merge_ods_user_user_info_dic.json`：用户表 merge 任务定义。

### `warehouse/sql`

Hive 数仓 SQL。结构按层级组织：

- `warehouse/sql/ods_binlog/ddl/*.sql`：ods_binlog 外部表 DDL，承接 Maxwell 原始事件。
- `warehouse/sql/ods/ddl/*.sql`：ODS 快照表 DDL。
- `warehouse/sql/ods/merge/*.sql`：ODS merge SQL，体现 binlog 合并核心逻辑。
- `warehouse/sql/dim/ddl/*.sql`：维度表 DDL。
- `warehouse/sql/dim/jobs/*.sql`：维度层加工 SQL。
- `warehouse/sql/dwd/ddl/*.sql`：明细事实层 DDL。
- `warehouse/sql/dwd/jobs/*.sql`：DWD 清洗、关联、维度退化 SQL。
- `warehouse/sql/dws/ddl/*.sql`：按主题聚合的 DWS 表 DDL。
- `warehouse/sql/dws/jobs/*.sql`：DWS 聚合 SQL。
- `warehouse/sql/dwt/ddl/*.sql`：累计宽表 DDL。
- `warehouse/sql/dwt/jobs/*.sql`：DWT 累计加工 SQL。
- `warehouse/sql/ads/ddl/*.sql`：ADS 指标表 DDL。
- `warehouse/sql/ads/jobs/*.sql`：ADS 指标计算 SQL。

### `warehouse/templates`

- `warehouse/templates/ods_merge_snapshot.sql.j2`：ODS merge SQL 模板。面试重点：不同表只换元数据，merge 模式相同。

## 面试高频问法

### 1. 你这个项目最核心的代码是哪块？

答：核心有三块：

- `streaming/offline_sink/pyspark_kafka_to_hdfs.py`：实时采集 Kafka binlog 写 HDFS。
- `warehouse/jobs/pyspark_ods_merge.py`：离线 ODS merge，保证快照最新。
- `platform/springboot-admin`：管理平台，把元数据、任务、监控、日志、回放和查询做成页面。

### 2. merge 为什么要 union old ODS？

答：binlog 只包含变更数据。如果只处理 T-1 binlog，会丢掉这个分区中没有变化的旧数据。因此要把“新增 binlog”和“受影响 ODS 分区旧快照” union，再按主键选最新版本。

### 3. delete 怎么处理？

答：把事件类型映射成 `binlog_type`，delete 最大。开窗排序 `ver desc, binlog_type desc`，取最新一条后，如果最新是 delete，就过滤掉。

### 4. 为什么需要延迟门禁？

答：merge 是离线快照覆盖写。如果 Kafka/Spark 还没把 T-1 数据写完就 merge，会生成不完整快照。`delay_gate.py` 根据同步进度判断是否允许 merge。

### 4.1 merge 失败怎么排查和恢复？

答：PySpark merge 会写 `data/ops/merge_audit` 审计文件，记录每个分区的 binlog 行数、旧快照行数、输出行数和目标路径。如果配置了 `MERGE_BACKUP_ROOT`，覆盖前旧分区会备份到 `ods_backup`，失败时可以用备份分区恢复。

### 5. 为什么用元数据驱动？

答：每张表主键、版本列、分区列、字段不同，但 DDL 和 merge 模式相同。元数据驱动可以减少手工 SQL，新增表时通过 onboarding 自动生成产物。

### 6. Kudu/Impala 在这里解决什么问题？

答：Hive/HDFS 适合离线批处理，不适合低延迟查询。Kudu 支持 upsert/delete，Impala 提供低延迟 SQL 查询，所以实时链路把 binlog 写 Kudu，再用 Impala 查实时指标。

### 7. 项目哪里是真实部署，哪里是本地 fallback？

答：默认主链路已经走 Docker/生产 HDFS、Hive、Kafka、Spark、Kudu/Impala。`warehouse/storage/local_lake.py` 和 `merge_ods_snapshot.py` 的本地模式主要用于单测和 debug，不是生产主链路。
