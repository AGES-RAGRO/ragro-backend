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
