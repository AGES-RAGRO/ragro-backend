# Repositório ECR da imagem do backend.
# image_tag_mutability=MUTABLE e scan-on-push desabilitado refletem o estado
# atual. Recomenda-se habilitar scan_on_push na conta do cliente (sem custo).
resource "aws_ecr_repository" "ragro" {
  name                 = "namespace/ragro-repo"
  image_tag_mutability = "MUTABLE"

  encryption_configuration {
    encryption_type = "AES256"
  }

  image_scanning_configuration {
    scan_on_push = false
  }
}

# Execution role das tasks ECS (pull da imagem no ECR e envio de logs ao
# CloudWatch). É a role padrão criada pelo console do ECS (path /service-role/).
# As task definitions referenciam esta role; não há task role separada.
resource "aws_iam_role" "ecs_execution" {
  name        = "ecsTaskExecutionRole"
  path        = "/service-role/"
  description = "Allows access to other AWS service resources that are required to run Amazon ECS tasks."

  max_session_duration  = 3600
  force_detach_policies = false

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Condition = {
        ArnEquals = {
          "aws:SourceArn" = "arn:aws:ecs:*:${data.aws_caller_identity.current.account_id}:*"
        }
        StringEquals = {
          "aws:SourceAccount" = data.aws_caller_identity.current.account_id
        }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}
