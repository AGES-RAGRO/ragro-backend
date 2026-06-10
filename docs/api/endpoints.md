# RAGRO API — Endpoints

A fonte de verdade do contrato da API é o **OpenAPI gerado do código**, sempre atualizado:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Spec JSON: `http://localhost:8080/v3/api-docs`

> A versão anterior deste arquivo mantinha uma cópia manual do catálogo de endpoints que
> divergiu do código (rotas com paths errados e endpoints listados como implementados que não
> existiam — auditoria Fase 0, 2026-06). Documentação manual de contrato duplica o que as
> annotations `@Operation`/`@Tag` já geram; consulte o Swagger.

**Terminologia**: o backlog usa "customer"/"producer" (termos de produto); banco e código usam
"customer"/"farmer". Ver [conventions.md](../conventions.md#1-terminology-glossary).

**Autenticação**: endpoints autenticados exigem `Authorization: Bearer <token>` (JWT do
Keycloak, realm `ragro`). As regras de autorização por rota estão em
`src/main/java/br/com/ragro/config/SecurityConfig.java` e nos `@PreAuthorize` dos controllers.
