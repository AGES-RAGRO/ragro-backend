# Security Groups do ragro. O ingress do ragro-backend-sg é restrito ao SG do
# VPC Link: somente o API Gateway alcança as portas das aplicações (8080
# backend, 8180 keycloak).

# SG das tasks ECS (backend + keycloak).
resource "aws_security_group" "backend" {
  name        = "ragro-backend-sg"
  description = "ragro backend ECS"
  vpc_id      = data.aws_vpc.default.id

  # Apenas o VPC Link (API Gateway) tem acesso às portas das aplicações.
  # 8080 = backend, 8180 = keycloak. O backend acessa o keycloak pela URL
  # pública do API Gateway, logo não há tráfego SG-a-SG interno a liberar.
  ingress = [{
    cidr_blocks      = []
    description      = "API Gateway (VPC Link) to backend"
    from_port        = 8080
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = [aws_security_group.vpclink.id]
    self             = false
    to_port          = 8080
    }, {
    cidr_blocks      = []
    description      = "API Gateway (VPC Link) to keycloak"
    from_port        = 8180
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = [aws_security_group.vpclink.id]
    self             = false
    to_port          = 8180
  }]

  egress = [{
    cidr_blocks      = ["0.0.0.0/0"]
    description      = ""
    from_port        = 0
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "-1"
    security_groups  = []
    self             = false
    to_port          = 0
  }]
}

# SG do VPC Link (API Gateway HTTP API -> VPC).
resource "aws_security_group" "vpclink" {
  name        = "ragro-vpclink-sg"
  description = "VPC Link for API Gateway HTTP API"
  vpc_id      = data.aws_vpc.default.id

  ingress = []

  egress = [{
    cidr_blocks      = ["0.0.0.0/0"]
    description      = ""
    from_port        = 0
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "-1"
    security_groups  = []
    self             = false
    to_port          = 0
  }]
}

# SG default da VPC — usado pelo RDS. Aceita 5432 apenas do SG das tasks.
resource "aws_default_security_group" "default" {
  vpc_id = data.aws_vpc.default.id

  ingress = [{
    cidr_blocks      = []
    description      = ""
    from_port        = 0
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "-1"
    security_groups  = []
    self             = true
    to_port          = 0
    }, {
    cidr_blocks      = []
    description      = ""
    from_port        = 5432
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = [aws_security_group.backend.id]
    self             = false
    to_port          = 5432
  }]

  egress = [{
    cidr_blocks      = ["0.0.0.0/0"]
    description      = ""
    from_port        = 0
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "-1"
    security_groups  = []
    self             = false
    to_port          = 0
  }]
}
