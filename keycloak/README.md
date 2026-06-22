# Keycloak — realm de DESENVOLVIMENTO

`ragro-realm.json` é um export **exclusivo para dev local** (importado pelo `docker-compose.yml`
via `start-dev --import-realm`). Ele contém um usuário ADMIN seed (`admin@ragro.com.br` /
`Admin@123`) necessário para logar como admin no app em dev — o fluxo de login é ROPC
(`directAccessGrantsEnabled`), e uma senha `temporary: true` quebraria o ROPC
("Account is not fully set up").

## NUNCA use este export em produção

Checklist do ambiente de produção:

- [ ] **Trocar a senha do admin** (`admin@ragro.com.br`) — se o realm de prod foi provisionado a
      partir deste export, a senha `Admin@123` é pública no repositório. Rotacionar IMEDIATAMENTE.
- [ ] `sslRequired` deve ser `external` (já é o valor deste export) ou `all`.
- [ ] `redirectUris` vazio (standard flow desabilitado; ROPC não usa redirect) e `webOrigins`
      com origens EXPLÍCITAS — nunca `*` em prod (em dev fica `*` de propósito; ver abaixo).
- [ ] Senha do admin master do Keycloak (`KEYCLOAK_ADMIN_PASSWORD`) via secret, nunca `admin`.
- [ ] Considerar MFA para contas do grupo ADMIN.

## Mudanças de hardening aplicadas (Etapa 1 / E0)

- `sslRequired: none → external` — exige TLS para requests externos; localhost/IPs privados
  (caso do dev local e da rede do compose) continuam isentos, então o dev via HTTP não muda.
- `redirectUris: ["*"] → []` — o client `ragro-app` só usa ROPC (standard/implicit flow
  desabilitados), então não há redirect.
- `webOrigins` permanece `["*"]` POR CONVENIÊNCIA DE DEV (decisão explícita): apps nativos não
  enviam Origin (CORS é mecanismo de navegador), mas o Flutter **web** local usa porta aleatória
  e o Keycloak não aceita wildcard parcial (`http://localhost:*`) em webOrigins. Em PROD, trocar
  por origens explícitas (item do checklist acima).
