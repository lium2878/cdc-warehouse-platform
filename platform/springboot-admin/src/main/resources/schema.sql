create database if not exists cdc_warehouse_admin default character set utf8mb4 collate utf8mb4_unicode_ci;

use cdc_warehouse_admin;

create table if not exists table_metadata (
  id bigint primary key auto_increment,
  source_database varchar(128) not null,
  source_table varchar(128) not null,
  ods_binlog_table varchar(256) not null,
  ods_table varchar(256) not null,
  primary_keys varchar(512) not null,
  version_column varchar(128) not null,
  partition_column varchar(128) not null,
  columns_json text not null,
  enabled tinyint not null default 1,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_source_table (source_database, source_table)
);

create table if not exists sync_task (
  id bigint primary key auto_increment,
  task_name varchar(256) not null,
  task_type varchar(64) not null,
  command text not null,
  schedule_expr varchar(128) not null,
  enabled tinyint not null default 1,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_task_name (task_name)
);

create table if not exists task_execution (
  id bigint primary key auto_increment,
  task_name varchar(256) not null,
  task_type varchar(64) not null,
  command text not null,
  status varchar(32) not null,
  exit_code int,
  output_excerpt text,
  duration_ms bigint,
  parameters_json text,
  log_path varchar(1024),
  process_id bigint,
  timeout_seconds int,
  triggered_by varchar(128),
  started_at timestamp null,
  finished_at timestamp null,
  heartbeat_at timestamp null,
  parent_execution_id bigint,
  error_message text,
  created_at timestamp not null default current_timestamp,
  key idx_task_execution_created_at (created_at),
  key idx_task_execution_name (task_name)
);

create table if not exists task_execution_lock (
  lock_key varchar(512) primary key,
  execution_id bigint not null,
  created_at timestamp not null default current_timestamp,
  key idx_task_execution_lock_execution (execution_id)
);

create table if not exists merge_task_status (
  id bigint primary key auto_increment,
  source_database varchar(128) not null,
  source_table varchar(128) not null,
  process_dt varchar(32) not null,
  run_id varchar(64) not null,
  status varchar(32) not null,
  binlog_rows int default 0,
  old_rows int default 0,
  output_rows int default 0,
  target_partitions text,
  audit_path varchar(1024),
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_merge_run (source_database, source_table, process_dt, run_id),
  key idx_merge_updated_at (updated_at),
  key idx_merge_table_dt (source_database, source_table, process_dt)
);

create table if not exists replay_record (
  id bigint primary key auto_increment,
  source_database varchar(128) not null,
  source_table varchar(128) not null,
  start_time varchar(32) not null,
  end_time varchar(32) not null,
  command text not null,
  status varchar(32) not null default 'CREATED',
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table if not exists monitor_result (
  id bigint primary key auto_increment,
  monitor_type varchar(64) not null,
  source_database varchar(128) not null,
  source_table varchar(128) not null,
  status varchar(32) not null,
  message text,
  metric_value varchar(128),
  created_at timestamp not null default current_timestamp
);

create table if not exists alert_record (
  id bigint primary key auto_increment,
  alert_type varchar(64) not null,
  target varchar(256) not null,
  title varchar(256) not null,
  body text,
  status varchar(32) not null default 'CREATED',
  created_at timestamp not null default current_timestamp
);

create table if not exists sensitive_rule (
  id bigint primary key auto_increment,
  column_pattern varchar(128) not null,
  action varchar(32) not null,
  default_value varchar(256),
  enabled tinyint not null default 1,
  created_at timestamp not null default current_timestamp,
  unique key uk_sensitive_column_pattern (column_pattern)
);

create table if not exists special_value_rule (
  id bigint primary key auto_increment,
  source_database varchar(128) not null,
  source_table varchar(128) not null,
  column_name varchar(128) not null,
  rule_type varchar(32) not null,
  rule_value varchar(512),
  enabled tinyint not null default 1,
  created_at timestamp not null default current_timestamp
);

create table if not exists schema_snapshot (
  id bigint primary key auto_increment,
  source_type varchar(32) not null,
  source_database varchar(128) not null,
  source_table varchar(128) not null,
  columns_json text not null,
  created_at timestamp not null default current_timestamp,
  key idx_schema_snapshot_table (source_type, source_database, source_table)
);

create table if not exists action_audit (
  id bigint primary key auto_increment,
  action_name varchar(128) not null,
  operator varchar(128) not null,
  client_ip varchar(64),
  request_json text,
  exit_code int,
  output_excerpt text,
  duration_ms bigint,
  created_at timestamp not null default current_timestamp,
  key idx_action_created_at (created_at),
  key idx_action_name (action_name)
);
