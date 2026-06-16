# Variáveis de entrada. Parâmetros que diferem entre os ambientes (AGES e
# cliente) ficam aqui, permitindo reutilizar o mesmo código em ambas as contas.
variable "aws_region" {
  description = "Região AWS onde os recursos vivem."
  type        = string
  default     = "us-east-2"
}

variable "aws_profile" {
  description = "Profile do AWS CLI a usar. null = credenciais default do ambiente (AGES)."
  type        = string
  default     = null
}

variable "environment" {
  description = "Identificador do ambiente (ages | cliente)."
  type        = string
  default     = "ages"
}

# Senha do usuário master do RDS. O valor não é legível via API (write-only),
# por isso o Terraform não o reconcilia (ver ignore_changes em rds.tf). Deve ser
# definida (terraform.tfvars gitignored ou TF_VAR_db_password) apenas ao
# provisionar o banco do zero na conta do cliente. Na AGES permanece null.
variable "db_password" {
  description = "Senha do usuario master do RDS (so usada ao criar do zero)."
  type        = string
  sensitive   = true
  default     = null
}

variable "kms_key_id" {
  description = "ARN da KMS key usada pelo RDS (storage e Performance Insights)."
  type        = string
}

variable "monitoring_role_arn" {
  description = "ARN da role de Enhanced Monitoring do RDS."
  type        = string
}

variable "db_subnet_group_name" {
  description = "Nome do DB subnet group (default da VPC) usado pelo RDS."
  type        = string
}

# Ajustes de RDS por ambiente. Defaults = AGES (não muda nada lá). No Free Tier
# (cliente) reduzimos/desligamos features pagas via prod.tfvars.
variable "backup_retention_period" {
  description = "Dias de retenção de backup do RDS (0 desativa; Free Tier limita)."
  type        = number
  default     = 7
}

variable "performance_insights_enabled" {
  description = "Habilita Performance Insights (pago; desligar no Free Tier)."
  type        = bool
  default     = true
}

variable "monitoring_interval" {
  description = "Intervalo do Enhanced Monitoring em segundos (0 desativa; pago)."
  type        = number
  default     = 60
}

# gp3 com < 400 GB não permite IOPS/throughput explícitos ao CRIAR. AGES foi
# importado com 3000/125; para criar do zero (cliente) usar null (baseline).
variable "storage_iops" {
  description = "IOPS do RDS gp3 (null = baseline, obrigatório p/ < 400 GB ao criar)."
  type        = number
  default     = 3000
}

variable "storage_throughput" {
  description = "Throughput do RDS gp3 em MBps (null = baseline ao criar)."
  type        = number
  default     = 125
}
