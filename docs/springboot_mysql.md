# SpringBoot MySQL Platform

The management platform uses SpringBoot + Freemarker + MySQL.

## Tables

SQL files:

```text
platform/springboot-admin/src/main/resources/schema.sql
platform/springboot-admin/src/main/resources/data.sql
```

Core tables:

```text
table_metadata   MySQL table onboarding metadata
sync_task        SparkStreaming/SparkSQL task config
replay_record    Maxwell bootstrap/replay records
monitor_result   monitor result records
alert_record     alert records
```

## MySQL Init

```bash
mysql -uroot -proot < platform/springboot-admin/src/main/resources/schema.sql
mysql -uroot -proot < platform/springboot-admin/src/main/resources/data.sql
```

Or:

```bash
cd platform/springboot-admin
./init_mysql.sh
```

Run app:

```bash
cd platform/springboot-admin
mvn spring-boot:run
```

## Config

```text
platform/springboot-admin/src/main/resources/application.yml
```

Default:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/cdc_warehouse_admin
    username: root
    password: root
```

## Pages

```text
/             table metadata from MySQL
/tasks        sync_task from MySQL, supports task save form
/onboarding   MySQL to Hive onboarding form, executes scripts/onboard_table.py
/replay       replay form, creates replay command and writes replay_record
/monitors     monitor_result from MySQL
/rules        sensitive_rule and special_value_rule from MySQL
```

If MySQL is unavailable, service layer returns demo data for metadata/tasks and
empty monitor rows, so pages still render.

## Onboarding Execution

`POST /onboarding` does:

```text
read form input
  -> run python3 scripts/onboard_table.py
  -> generate metadata JSON
  -> generate ods_binlog DDL
  -> generate ods DDL
  -> generate ODS merge SQL
  -> generate DolphinScheduler task JSON
  -> upsert table_metadata
  -> upsert sync_task
```

Long-running configured tasks are submitted to:

```text
TaskExecutionService
  -> task_execution PENDING record + task_execution_lock
  -> warehouseTaskExecutor
  -> CommandExecutorService
  -> data/task-executions/<date>/<execution-id>.log
```

The Tasks page refreshes status every five seconds. It supports live logs,
cancel, timeout, duplicate-run protection, and rerun. Short dashboard probes
still call `CommandExecutorService` synchronously.

Default working directory:

```yaml
warehouse:
  project-root: ../..
```
