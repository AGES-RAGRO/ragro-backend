# Restrições de versão do Terraform e dos providers. O provider AWS é mantido na
# linha 5.x (~> 5.0) para evitar saltos de major version; a versão exata fica
# registrada no .terraform.lock.hcl, garantindo builds reproduzíveis.
terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # State remoto no S3 (bucket dedicado, versionado e criptografado).
  # Lock nativo do S3 (use_lockfile) — não precisa de DynamoDB.
  # A chave separa ambientes: ages/ aqui, cliente/ na fase 2.
  backend "s3" {
    bucket       = "ragro-tfstate-610639371759"
    key          = "ages/terraform.tfstate"
    region       = "us-east-2"
    encrypt      = true
    use_lockfile = true
  }
}
