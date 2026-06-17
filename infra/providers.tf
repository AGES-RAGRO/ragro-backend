# Provider AWS. Sem `profile` definido, utiliza a cadeia de credenciais padrão
# do AWS CLI/ambiente (atualmente a conta da AGES).
#
# default_tags é intencionalmente omitido: os recursos existentes não possuem
# tags padronizadas, portanto aplicá-las geraria diferenças no plan. A infra foi
# importada para refletir o estado real ("no changes").
provider "aws" {
  region  = var.aws_region
  profile = var.aws_profile
}
