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

  # State remoto no S3 com configuração PARCIAL: os valores (bucket, key, region)
  # vêm de um arquivo por ambiente, passado no init:
  #   terraform init -backend-config=envs/ages.backend.hcl
  # Lock nativo do S3 (use_lockfile) — não precisa de DynamoDB.
  backend "s3" {}
}
