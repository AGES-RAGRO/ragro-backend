# ECS — cluster + services. As task definitions são gerenciadas pelo CI/CD: o
# workflow aws.yml renderiza uma nova revisão a cada deploy, injetando os
# segredos do GitHub. Os services referenciam a revisão ativa via data source e
# ignoram mudanças em task_definition para evitar conflito com o pipeline.

resource "aws_ecs_cluster" "default" {
  name = "default"

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

# Revisão ativa de cada task definition (gerenciada pelo CI).
data "aws_ecs_task_definition" "backend" {
  task_definition = "ragro-backend"
}

data "aws_ecs_task_definition" "keycloak" {
  task_definition = "ragro-keycloak"
}

resource "aws_ecs_service" "backend" {
  name            = "ragro-backend"
  cluster         = aws_ecs_cluster.default.arn
  task_definition = data.aws_ecs_task_definition.backend.arn
  desired_count   = 1

  launch_type      = "FARGATE"
  platform_version = "1.4.0"

  scheduling_strategy                = "REPLICA"
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100
  enable_ecs_managed_tags            = true
  availability_zone_rebalancing      = "ENABLED"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_controller {
    type = "ECS"
  }

  network_configuration {
    assign_public_ip = true
    security_groups  = [aws_security_group.backend.id]
    subnets          = data.aws_subnets.default.ids
  }

  service_registries {
    registry_arn   = aws_service_discovery_service.backend.arn
    container_name = "Main"
    container_port = 8080
  }

  # task_definition é atualizada pelo CI a cada deploy e desired_count pode mudar
  # por escala manual; ambos são ignorados para o Terraform não revertê-los.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }
}

resource "aws_ecs_service" "keycloak" {
  name            = "ragro-keycloak"
  cluster         = aws_ecs_cluster.default.arn
  task_definition = data.aws_ecs_task_definition.keycloak.arn
  desired_count   = 1

  launch_type      = "FARGATE"
  platform_version = "LATEST"

  scheduling_strategy                = "REPLICA"
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100
  enable_ecs_managed_tags            = true
  availability_zone_rebalancing      = "ENABLED"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_controller {
    type = "ECS"
  }

  network_configuration {
    assign_public_ip = true
    security_groups  = [aws_security_group.backend.id]
    subnets          = data.aws_subnets.default.ids
  }

  service_registries {
    registry_arn   = aws_service_discovery_service.keycloak.arn
    container_name = "Keycloak"
    container_port = 8180
  }

  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }
}
