# RDS PostgreSQL — bancos `ragro` e `keycloak`.
# Utiliza o DB subnet group e o parameter group padrão da AWS (criados
# automaticamente), referenciados por nome — não gerenciados como recursos.
#
# Valores específicos de conta (KMS key, monitoring role, subnet group) vêm de
# variáveis definidas por ambiente em envs/*.tfvars.
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
  iops                  = var.storage_iops
  storage_throughput    = var.storage_throughput
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

  backup_retention_period  = var.backup_retention_period
  backup_window            = "09:03-09:33"
  maintenance_window       = "tue:05:14-tue:05:44"
  copy_tags_to_snapshot    = true
  delete_automated_backups = true

  auto_minor_version_upgrade = true
  ca_cert_identifier         = "rds-ca-rsa2048-g1"
  license_model              = "postgresql-license"
  engine_lifecycle_support   = "open-source-rds-extended-support-disabled"

  monitoring_interval = var.monitoring_interval
  monitoring_role_arn = var.monitoring_interval > 0 ? var.monitoring_role_arn : null

  performance_insights_enabled          = var.performance_insights_enabled
  performance_insights_kms_key_id       = var.performance_insights_enabled ? var.kms_key_id : null
  performance_insights_retention_period = var.performance_insights_enabled ? 7 : null

  deletion_protection = false
  skip_final_snapshot = true

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [password]
  }
}
