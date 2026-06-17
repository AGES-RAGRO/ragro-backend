# Cloud Map (service discovery) — namespace privado ragro.local + services.
# As tasks ECS se registram nestes services; as integrações do API Gateway
# (via VPC Link) roteiam para eles pelo ARN do service (ver apigw.tf).
resource "aws_service_discovery_private_dns_namespace" "ragro" {
  name = "ragro.local"
  vpc  = data.aws_vpc.default.id
}

resource "aws_service_discovery_service" "backend" {
  name = "backend"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.ragro.id
    routing_policy = "MULTIVALUE"

    dns_records {
      type = "A"
      ttl  = 60
    }
    dns_records {
      type = "SRV"
      ttl  = 60
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_service_discovery_service" "keycloak" {
  name = "keycloak"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.ragro.id
    routing_policy = "MULTIVALUE"

    dns_records {
      type = "A"
      ttl  = 60
    }
    dns_records {
      type = "SRV"
      ttl  = 60
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}
