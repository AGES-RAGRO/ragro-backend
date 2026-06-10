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
- [ ] `webOrigins`/`redirectUris` vazios (standard flow desabilitado; ROPC não usa redirect).
- [ ] Senha do admin master do Keycloak (`KEYCLOAK_ADMIN_PASSWORD`) via secret, nunca `admin`.
- [ ] Considerar MFA para contas do grupo ADMIN.

## Mudanças de hardening aplicadas (Etapa 1 / E0)

- `sslRequired: none → external` — exige TLS para requests externos; localhost/IPs privados
  (caso do dev local e da rede do compose) continuam isentos, então o dev via HTTP não muda.
- `redirectUris: ["*"] → []` e `webOrigins: ["*"] → []` — o client `ragro-app` só usa ROPC
  (standard/implicit flow desabilitados), então não há redirect; CORS aberto no token endpoint
  era desnecessário (apps nativos não enviam Origin). Se um dia rodar Flutter **web** em dev,
  adicionar a origem explícita (ex.: `http://localhost:5000`) em `webOrigins`.
