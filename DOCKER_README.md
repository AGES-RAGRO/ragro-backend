# Ragro Backend - Docker Setup

## Requisitos

- Docker 20.10+
- Docker Compose 2.0+

## Execução Rápida

Para subir a aplicação com um único comando:

```bash
docker-compose up --build
```

Isso irá:
1. **Construir** a imagem Docker do backend
2. **Iniciar** o PostgreSQL com o schema executado automaticamente
3. **Aguardar** o banco estar pronto
4. **Subir** o backend conectado ao banco

## Acesso à Aplicação

- **Backend**: http://localhost:8080
- **Health Check**: http://localhost:8080/actuator/health
- **PostgreSQL**: localhost:5432
  - Usuário: `postgres`
  - Senha: `postgres`
  - Banco: `gearheads`

## Estrutura Docker

### Dockerfile

- **Image Base**: `eclipse-temurin:21-jre-alpine` (Java 21 optimizado)
- **Build**: Multi-stage com Maven 3.9 para reduzir tamanho final
- **Health Check**: Validação automática a cada 30s via `/actuator/health`

### docker-compose.yml

#### Serviço: `postgres`
- Imagem: `postgres:16-alpine`
- Volumes:
  - Dados persistidos em `postgres_data:/var/lib/postgresql/data`
  - Schema SQL montado em `/docker-entrypoint-initdb.d/schema.sql` (executa automaticamente)
- Health Check: Valida conexão com o banco

#### Serviço: `backend`
- Build a partir do `Dockerfile`
- Variáveis de ambiente:
  - `SPRING_DATASOURCE_URL`: jdbc:postgresql://postgres:5432/gearheads
  - `SPRING_DATASOURCE_USERNAME`: postgres
  - `SPRING_DATASOURCE_PASSWORD`: postgres
  - `SPRING_JPA_HIBERNATE_DDL_AUTO`: validate
- Depends On: PostgreSQL (aguarda health check)
- Redes: Isolado em `ragro-network`

## Arquivos Criados

```
/Dockerfile              - Imagem Docker do backend (multi-stage)
/docker-compose.yml     - Orquestração de serviços (postgres + backend)
/.dockerignore          - Arquivos ignorados no build
/data/schema.sql        - Script SQL (montado automaticamente no postgres)
```

## Configuração no application.yml

As variáveis de ambiente são definidas em `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/gearheads}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
```

- **Em Docker**: Usa valores do `docker-compose.yml`
- **Local**: Usa defaults (localhost:5432)

## Comandos Úteis

### Subir tudo
```bash
docker-compose up --build
```

### Subir em background
```bash
docker-compose up --build -d
```

### Ver logs
```bash
docker-compose logs -f backend
docker-compose logs -f postgres
```

### Parar tudo
```bash
docker-compose down
```

### Remover volumes (limpar dados)
```bash
docker-compose down -v
```

### Reconstruir imagens
```bash
docker-compose build --no-cache
```

## Troubleshooting

### Porta 5432 ou 8080 já em uso

Edite `docker-compose.yml` para usar portas diferentes:

```yaml
ports:
  - "5433:5432"  # PostgreSQL
  - "8081:8080"  # Backend
```

### Backend não conecta ao PostgreSQL

Verifique se o serviço postgres está healthy:

```bash
docker-compose ps
```

O backend aguarda o health check do postgres (até 5 tentativas de 10s cada = ~50s).

### Reimicializar com novo schema

```bash
docker-compose down -v
docker-compose up --build
```

Isso remove todos os volumes, força reconstrução e o schema é reinserido.

## Performance

- **Multi-stage build**: Reduz tamanho final da imagem (~200MB vs ~500MB)
- **Alpine Linux**: Imagem mínima do Java 21
- **Network isolation**: Serviços isolados em rede customizada
- **Health checks**: Garante readiness antes de iniciar dependências

## Segurança em Produção

Para produção, considere:

```yaml
# docker-compose.yml
environment:
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}  # Via .env
  SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}
```

Use um arquivo `.env` (não versionado):

```
POSTGRES_PASSWORD=seu_senha_segura
SPRING_DATASOURCE_PASSWORD=seu_senha_segura
```

Ou use Docker Secrets/AWS Secrets Manager em produção.
