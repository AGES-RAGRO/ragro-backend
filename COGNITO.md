# ragro-backend

## Autenticação com AWS Cognito

O backend está configurado com **OAuth2 Login** (via Cognito no navegador) e **OAuth2 Resource Server** (JWT para APIs).

---

## Setup Cognito (Pré-requisitos)

### 1. Criar User Pool no AWS Cognito

1. Console AWS → Cognito → Create User Pool
2. Escolha um **pool name**, ex: `ragro-pool`
3. Clique em **Create**

### 2. Criar App Client

1. Na página do User Pool → **App integration** → **App clients and analytics**
2. Click **Create app client**
3. Nome: `ragro-app` (ex)
4. Em **Authentication flows**:
   - ✓ ALLOW_ADMIN_USER_PASSWORD_AUTH
   - ✓ ALLOW_CUSTOM_AUTH
   - ✓ ALLOW_USER_PASSWORD_AUTH
   - ✓ ALLOW_USER_SRP_AUTH
5. Em **Allowed redirect URIs** (IMPORTANTE):
   ```
   http://localhost:8080/login/oauth2/code/cognito
   ```
6. Em **Allowed sign-out URLs**:
   ```
   http://localhost:8080/
   ```
7. Salve e copie:
   - **Client ID** → `AWS_COGNITO_CLIENT_ID`
   - **Client Secret** → `AWS_COGNITO_CLIENT_SECRET`

### 3. Configurar Domain (Hosted UI)

1. **App integration** → **Domain name**
2. Escolha um domínio único, ex: `ragro-app`
3. Copie a URL gerada como **AWS_COGNITO_DOMAIN**
   ```
   https://ragro-app.auth.us-east-1.amazoncognito.com
   ```

### 4. Obter Issuer URI

1. **User pool overview**
2. Copie o **User pool ID**, ex: `us-east-1_abc123xyz`
3. Crie o issuer:
   ```
   AWS_COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abc123xyz
   ```

### 5. Criar Grupos (Opcional, mas recomendado)

1. **User and groups** → **Groups**
2. Crie 3 grupos:
   - `ADMIN`
   - `FARMER`
   - `CUSTOMER`
3. Crie um usuário de teste (ex: `testadmin@example.com`)
4. Adicione o usuário ao grupo `ADMIN`

---

## Variáveis de Ambiente (.env ou .env.local)

Copie do `.env.example` e preencha com seus valores:

```bash
AWS_COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abc123xyz
AWS_COGNITO_CLIENT_ID=your-client-id
AWS_COGNITO_CLIENT_SECRET=your-client-secret
AWS_COGNITO_REDIRECT_URI=http://localhost:8080/login/oauth2/code/cognito
AWS_COGNITO_DOMAIN=https://ragro-app.auth.us-east-1.amazoncognito.com
AWS_COGNITO_LOGOUT_URI=http://localhost:8080/
```

---

## Testando a Aplicação

### Teste 1: Login via Navegador (OAuth2 Login)

1. **Inicie a app:**
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Abra no navegador:**
   ```
   http://localhost:8080/
   ```

3. **Clique em "Login with Cognito"**
   - Será redirecionado para o Cognito
   - Faça login com seu usuário
   - Será redirecionado de volta para home.html

4. **Veja os dados autenticados** na página

5. **Clique em "Logout"** para voltar anônimo

---

### Teste 2: Criar Usuário (POST /admin/users)

**Requisito:** O usuário logado precisa estar no grupo `ADMIN` no Cognito.

#### Passo 1: Obter o Token JWT

Via **Cognito Hosted UI**, acesse:
```
https://ragro-app.auth.us-east-1.amazoncognito.com/login?
  client_id=YOUR_CLIENT_ID
  &response_type=code
  &scope=openid+email+phone
  &redirect_uri=http://localhost:8080/login/oauth2/code/cognito
```

Login e volte à app. O token está no header `Authorization: Bearer <token>`.

**Alternativa: Usar Cognito CLI ou AWS SDK** para obter token programaticamente.

#### Passo 2: Chamar a API com curl

Após fazer login na app, tome nota do JWT ou, se tiver acesso, use AWS CLI:

```bash
aws cognito-idp admin-initiate-auth \
  --user-pool-id us-east-1_abc123xyz \
  --client-id YOUR_CLIENT_ID \
  --auth-flow ADMIN_NO_SRP_AUTH \
  --auth-parameters USERNAME=testadmin@example.com,PASSWORD=YourPassword \
  --region us-east-1
```

Copie o `IdToken` da resposta.

#### Passo 3: Criar Usuário

```bash
curl -X POST http://localhost:8080/admin/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ID_TOKEN>" \
  -d '{
    "name": "João Silva",
    "email": "joao@example.com",
    "typeUser": "FARMER"
  }'
```

**Resposta esperada (201 Created):**
```json
{
  "id": 1,
  "name": "João Silva",
  "email": "joao@example.com",
  "typeUser": "FARMER",
  "active": true,
  "cognitoSub": "sub-value-from-token"
}
```

---

### Teste 3: Obter Dados do Usuário Autenticado (GET /users/me)

```bash
curl -X GET http://localhost:8080/users/me \
  -H "Authorization: Bearer <ID_TOKEN>"
```

**Resposta esperada (200 OK):**
```json
{
  "id": 1,
  "name": "João Silva",
  "email": "joao@example.com",
  "typeUser": "FARMER",
  "active": true,
  "cognitoSub": "us-east-1:12345678-1234-1234-1234-123456789012"
}
```

---

## Troubleshooting

### ❌ "Invalid client_id or client_secret"
- Verifique `AWS_COGNITO_CLIENT_ID` e `AWS_COGNITO_CLIENT_SECRET` no `.env`

### ❌ "Redirect URI mismatch"
- Certifique-se que em **Cognito App Client → Callback URLs** está:
  ```
  http://localhost:8080/login/oauth2/code/cognito
  ```

### ❌ "issuer_uri does not match"
- Valide `AWS_COGNITO_ISSUER_URI` está **sem** `/.well-known/jwks.json` no final
- Deve ser: `https://cognito-idp.REGION.amazonaws.com/POOL_ID`

### ❌ "Unauthorized" ao chamar /admin/users
- O usuário logado não pertence ao grupo `ADMIN`
- Adicione-o ao grupo no Cognito e faça login novamente

### ❌ Erro 403 ao fazer logout
- Valide que em **Cognito App Client → Allowed sign-out URLs** está:
  ```
  http://localhost:8080/
  ```

---

## Resumo dos Endpoints

| Método | Rota | Autenticação | Descrição |
|--------|------|--------------|-----------|
| GET | `/` | Anônimo | Home com login/logout |
| POST | `/admin/users` | JWT + ADMIN | Criar usuário |
| GET | `/users/me` | JWT | Dados do usuário autenticado |

---

## Referências Externas

- [AWS Cognito User Pools](https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-lambda-hosted-login.html)
- [Spring Security OAuth2 Login](https://spring.io/projects/spring-security)
- [Spring Security with Cognito](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)

