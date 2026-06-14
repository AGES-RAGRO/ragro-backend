# RDS PostgreSQL — bancos `ragro` e `keycloak`.
# Utiliza o DB subnet group e o parameter group padrão da AWS (criados
# automaticamente), referenciados por nome — não gerenciados como recursos.
#
# Os ARNs específicos da conta AGES (KMS, monitoring role) e o nome do subnet
# group estão literais. Parametrizar (kms_key_id, monitoring_role_arn,
# db_subnet_group_name) ao provisionar na conta do cliente.
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
  iops                  = 3000
  storage_throughput    = 125
  storage_encrypted     = true
  kms_key_id            = "arn:aws:kms:us-east-2:610639371759:key/77f17bed-3090-4f8f-948a-ec7a10d14ed5"

  availability_zone   = "us-east-2c"
  multi_az            = false
  publicly_accessible = false
  port                = 5432
  network_type        = "IPV4"

  db_subnet_group_name   = "default-vpc-00ebf3c6f3a41f645"
  parameter_group_name   = "default.postgres16"
  option_group_name      = "default:postgres-16"
  vpc_security_group_ids = [aws_default_security_group.default.id]

  backup_retention_period  = 7
  backup_window            = "09:03-09:33"
  maintenance_window       = "tue:05:14-tue:05:44"
  copy_tags_to_snapshot    = true
  delete_automated_backups = true

  auto_minor_version_upgrade = true
  ca_cert_identifier         = "rds-ca-rsa2048-g1"
  license_model              = "postgresql-license"
  engine_lifecycle_support   = "open-source-rds-extended-support-disabled"

  monitoring_interval = 60
  monitoring_role_arn = "arn:aws:iam::610639371759:role/rds-monitoring-role"

  performance_insights_enabled          = true
  performance_insights_kms_key_id       = "arn:aws:kms:us-east-2:610639371759:key/77f17bed-3090-4f8f-948a-ec7a10d14ed5"
  performance_insights_retention_period = 7

  deletion_protection = false
  skip_final_snapshot = true

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [password]
  }
}
