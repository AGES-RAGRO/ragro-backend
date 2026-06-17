# A VPC default e suas subnets não são gerenciadas pelo Terraform (a AWS as cria
# automaticamente em cada conta/região). São apenas referenciadas via data
# source, de modo que o mesmo código resolva a VPC default da conta em que for
# aplicado.
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# Account ID e região atuais — usados para montar ARNs portáveis (resolvem
# para a conta/região em que o Terraform está rodando).
data "aws_caller_identity" "current" {}

data "aws_region" "current" {}
