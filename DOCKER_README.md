# Ragro Backend - Docker Setup

## Requisitos

- Docker 20.10+
- Docker Compose 2.0+

## Execucao Rapida

Para subir a aplicacao com um unico comando:

```bash
docker compose up --build
```

Isso ira:
1. **Construir** a imagem Docker do backend
2. **Iniciar** o PostgreSQL com o schema executado automaticamente
3. **Criar** o banco `keycloak` dentro do PostgreSQL (via `data/00-create-keycloak-db.sh`)
4. **Iniciar** o Keycloak 26 com o realm `ragro` pre-configurado
5. **Aguardar** o banco estar pronto
6. **Subir** o backend conectado ao banco e ao Keycloak

## Acesso a Aplicacao

- **Backend**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health
- **Keycloak Admin Console**: http://localhost:8180
  - Usuario: `admin`
  - Senha: `admin`
- **PostgreSQL**: localhost:5432
  - Usuario: `postgres`
  - Senha: `postgres`
  - Bancos: `gearheads` (aplicacao), `keycloak` (Keycloak)

## Estrutura Docker

### Dockerfile

- **Image Base**: `eclipse-temurin:21-jre-alpine` (Java 21 optimizado)
- **Build**: Multi-stage com Maven 3.9 para reduzir tamanho final
- **Health Check**: Validacao automatica a cada 30s via `/actuator/health`

### docker-compose.yml

#### Servico: `postgres`
- Imagem: `postgres:16-alpine`
- Volumes:
  - Dados persistidos em `postgres_data:/var/lib/postgresql/data`
  - Init script: `data/00-create-keycloak-db.sh` (cria banco `keycloak`)
- Health Check: Valida conexao com o banco

#### Servico: `keycloak`
- Imagem: `quay.io/keycloak/keycloak:26.0`
- Modo: `start-dev` com `--import-realm`
- Banco: PostgreSQL compartilhado (database `keycloak`)
- Porta: `8180`
- Realm: Importado de `keycloak/ragro-realm.json` contendo:
  - Client `ragro-app` (publico, Direct Access Grants habilitado)
  - Grupos: `ADMIN`, `CUSTOMER`, `FARMER`
  - Mapper: `groups` claim no JWT
  - Usuarios de teste pre-configurados
- Depends On: PostgreSQL (aguarda health check)

#### Servico: `backend`
- Build a partir do `Dockerfile`
- Migrações executadas automaticamente via Flyway (`src/main/resources/db/migration`)
- Variaveis de ambiente:
  - `SPRING_DATASOURCE_URL`: jdbc:postgresql://postgres:5432/gearheads
  - `KEYCLOAK_SERVER_URL`: http://keycloak:8180 (comunicacao interna)
  - `KEYCLOAK_PUBLIC_URL`: http://localhost:8180 (Swagger UI / browser)
  - `KEYCLOAK_ISSUER_URI`: http://keycloak:8180/realms/ragro
  - `KEYCLOAK_JWK_SET_URI`: http://keycloak:8180/realms/ragro/protocol/openid-connect/certs
- Depends On: PostgreSQL (aguarda health check)
- Redes: Isolado em `ragro-network`

## Arquivos Docker

```
/Dockerfile                       - Imagem Docker do backend (multi-stage)
/docker-compose.yml               - Orquestracao: postgres + keycloak + backend
/docker-compose.test.yml          - Orquestracao para testes
/.dockerignore                    - Arquivos ignorados no build
/src/main/resources/db/migration/ - Migrations Flyway (schema e seed)
/data/00-create-keycloak-db.sh    - Init script: cria banco keycloak no postgres
/keycloak/ragro-realm.json        - Realm Keycloak pre-configurado
```

## Configuracao no application.yml

As variaveis de ambiente sao definidas em `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/gearheads}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/ragro}
          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:http://localhost:8180/realms/ragro/protocol/openid-connect/certs}

keycloak:
  server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8180}
  public-url: ${KEYCLOAK_PUBLIC_URL:http://localhost:8180}
  realm: ragro
  admin:
    username: ${KEYCLOAK_ADMIN:admin}
    password: ${KEYCLOAK_ADMIN_PASSWORD:admin}
```

- **Em Docker**: Usa valores do `docker-compose.yml`
- **Local**: Usa defaults (localhost)

## Comandos Uteis

### Subir tudo
```bash
docker compose up --build
```

### Subir em background
```bash
docker compose up --build -d
```

### Ver logs
```bash
docker compose logs -f backend
docker compose logs -f keycloak
docker compose logs -f postgres
```

### Parar tudo
```bash
docker compose down
```

### Remover volumes (limpar dados e recriar realm)
```bash
docker compose down -v
```

### Reconstruir imagens
```bash
docker compose build --no-cache
```

### Subir apenas infraestrutura (para dev local)
```bash
docker compose up postgres keycloak -d
```

## Troubleshooting

### Porta 5432, 8080 ou 8180 ja em uso

Edite `docker-compose.yml` para usar portas diferentes:

```yaml
ports:
  - "5433:5432"  # PostgreSQL
  - "8081:8080"  # Backend
  - "8181:8180"  # Keycloak
```

### Backend nao conecta ao PostgreSQL

Verifique se o servico postgres esta healthy:

```bash
docker compose ps
```

O backend aguarda o health check do postgres (ate 5 tentativas de 10s cada = ~50s).

### Keycloak nao importa o realm

Se o realm nao aparece no Keycloak admin console:

1. Verifique se `keycloak/ragro-realm.json` existe
2. Recrie os volumes: `docker compose down -v && docker compose up --build`
3. O realm so e importado na primeira inicializacao

### Swagger UI retorna "Failed to fetch" ao autenticar

Verifique se a variavel `KEYCLOAK_PUBLIC_URL` esta como `http://localhost:8180` (nao o hostname interno `keycloak`).

### Reimicializar com novo schema

```bash
docker compose down -v
docker compose up --build
```

Isso remove todos os volumes, forca reconstrucao, o schema e reinserido e o realm e reimportado.

## Performance

- **Multi-stage build**: Reduz tamanho final da imagem (~200MB vs ~500MB)
- **Alpine Linux**: Imagem minima do Java 21
- **PostgreSQL compartilhado**: Keycloak e a aplicacao usam o mesmo container PostgreSQL com bancos separados
- **Network isolation**: Servicos isolados em rede customizada
- **Health checks**: Garante readiness antes de iniciar dependencias

## Seguranca em Producao

Para producao, considere:

```yaml
# docker-compose.yml
environment:
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}
  KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
```

Use um arquivo `.env` (nao versionado):

```
POSTGRES_PASSWORD=senha_segura
SPRING_DATASOURCE_PASSWORD=senha_segura
KEYCLOAK_ADMIN_PASSWORD=senha_segura
```

Alem disso:
- Altere `sslRequired` para `external` no realm Keycloak
- Restrinja `webOrigins` no client `ragro-app` para dominios especificos
- Desabilite o Swagger UI em producao
- Use Docker Secrets ou um gerenciador de secrets externo
