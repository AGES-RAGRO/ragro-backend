# RDS PostgreSQL — bancos `ragro` e `keycloak`.
# Utiliza o DB subnet group e o parameter group padrão da AWS (criados
# automaticamente), referenciados por nome — não gerenciados como recursos.
#
# Valores específicos de conta (KMS key, monitoring role, subnet group) vêm de
# variáveis definidas por ambiente em envs/*.tfvars.
#
# Já os ajustes de Free Tier (abaixo) são POLÍTICA, não dado de conta: o ambiente
# do cliente ("prod") desliga features pagas do RDS. Por isso são derivados do
# próprio `var.environment` aqui no código — não precisam ser passados de fora.
locals {
  free_tier = var.environment == "prod"

  rds_backup_retention_period      = local.free_tier ? 0 : 7
  rds_performance_insights_enabled = !local.free_tier
  rds_monitoring_interval          = local.free_tier ? 0 : 60
  # gp3 < 400 GB não aceita IOPS/throughput explícitos ao CRIAR (cliente, do zero);
  # a AGES foi importada com 3000/125.
  rds_storage_iops       = local.free_tier ? null : 3000
  rds_storage_throughput = local.free_tier ? null : 125
}

resource "aws_db_instance" "ragro" {
  identifier     = "ragro-backend-db"
  engine         = "postgres"
  engine_version = "16.13"
  instance_class = "db.t3.micro"
  username       = "postgres"
  password       = var.db_password

  allocated_storage     = 20
  max_allocated_storage = 1000
  storage_type          = "gp3"
  iops                  = local.rds_storage_iops
  storage_throughput    = local.rds_storage_throughput
  storage_encrypted     = true
  kms_key_id            = var.kms_key_id

  availability_zone   = "us-east-2c"
  multi_az            = false
  publicly_accessible = false
  port                = 5432
  network_type        = "IPV4"

  db_subnet_group_name   = var.db_subnet_group_name
  parameter_group_name   = "default.postgres16"
  option_group_name      = "default:postgres-16"
  vpc_security_group_ids = [aws_default_security_group.default.id]

  backup_retention_period  = local.rds_backup_retention_period
  backup_window            = "09:03-09:33"
  maintenance_window       = "tue:05:14-tue:05:44"
  copy_tags_to_snapshot    = true
  delete_automated_backups = true

  auto_minor_version_upgrade = true
  ca_cert_identifier         = "rds-ca-rsa2048-g1"
  license_model              = "postgresql-license"
  engine_lifecycle_support   = "open-source-rds-extended-support-disabled"

  monitoring_interval = local.rds_monitoring_interval
  monitoring_role_arn = local.rds_monitoring_interval > 0 ? var.monitoring_role_arn : null

  performance_insights_enabled          = local.rds_performance_insights_enabled
  performance_insights_kms_key_id       = local.rds_performance_insights_enabled ? var.kms_key_id : null
  performance_insights_retention_period = local.rds_performance_insights_enabled ? 7 : null

  deletion_protection = false
  skip_final_snapshot = true

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [password]
  }
}
