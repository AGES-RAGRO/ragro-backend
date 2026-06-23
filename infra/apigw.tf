# API Gateway (HTTP API) — duas APIs públicas que acessam a VPC via VPC Link e
# roteiam (HTTP_PROXY, rota $default) para os services do Cloud Map.

resource "aws_apigatewayv2_vpc_link" "ragro" {
  name               = "ragro-vpclink"
  security_group_ids = [aws_security_group.vpclink.id]
  subnet_ids         = data.aws_subnets.default.ids
}

# ---------- Backend (ragro-api) ----------
resource "aws_apigatewayv2_api" "backend" {
  name          = "ragro-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "backend" {
  api_id                 = aws_apigatewayv2_api.backend.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = aws_service_discovery_service.backend.arn
  connection_type        = "VPC_LINK"
  connection_id          = aws_apigatewayv2_vpc_link.ragro.id
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "backend" {
  api_id    = aws_apigatewayv2_api.backend.id
  route_key = "$default"
  target    = "integrations/${aws_apigatewayv2_integration.backend.id}"
}

resource "aws_apigatewayv2_stage" "backend" {
  api_id      = aws_apigatewayv2_api.backend.id
  name        = "$default"
  auto_deploy = true
}

# ---------- Keycloak (ragro-keycloak-api) ----------
resource "aws_apigatewayv2_api" "keycloak" {
  name          = "ragro-keycloak-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "keycloak" {
  api_id                 = aws_apigatewayv2_api.keycloak.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = aws_service_discovery_service.keycloak.arn
  connection_type        = "VPC_LINK"
  connection_id          = aws_apigatewayv2_vpc_link.ragro.id
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "keycloak" {
  api_id    = aws_apigatewayv2_api.keycloak.id
  route_key = "$default"
  target    = "integrations/${aws_apigatewayv2_integration.keycloak.id}"
}

resource "aws_apigatewayv2_stage" "keycloak" {
  api_id      = aws_apigatewayv2_api.keycloak.id
  name        = "$default"
  auto_deploy = true
}
