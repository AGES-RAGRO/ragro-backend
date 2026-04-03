# Gitflow Basico

Este projeto adota um fluxo simples com quatro tipos principais de branch:

- `main`
- `develop`
- `feat/*`
- `fix/*`

O objetivo e manter a linha principal estavel, organizar o desenvolvimento do time e facilitar correcoes rapidas.

## Visao Geral

- `main`: contem apenas codigo pronto para producao.
- `develop`: branch principal de desenvolvimento e integracao.
- `feat/*`: usada para novas funcionalidades.
- `fix/*`: usada para correcao de bugs.

Fluxo esperado:

```text
main
  └── develop
       ├── feat/autenticacao-keycloak
       ├── feat/cadastro-consumidor
       └── fix/correcao-validacao-usuario
```

## Regras por Branch

### `main`

- Deve refletir a versao mais estavel do sistema.
- Nao deve receber commits diretos.
- Recebe merge apenas de `develop` quando houver um conjunto validado de entregas.
- Toda alteracao em `main` deve passar por revisao.

### `develop`

- E a branch base do desenvolvimento diario.
- Todas as novas features e fixes devem sair dela, salvo combinacao diferente para hotfix urgente.
- Recebe merge de `feat/*` e `fix/*` apos revisao e validacao.
- Deve permanecer funcional o suficiente para integracao do time.

### `feat/*`

Use para desenvolver novas funcionalidades.

Padrao de nome:

```bash
feat/nome-curto-da-feature
```

Exemplos:

```bash
feat/autenticacao-keycloak
feat/cadastro-produtor
feat/listagem-admin-usuarios
```

Regras:

- Sempre criar a partir de `develop`.
- Deve conter apenas o escopo da feature.
- Ao finalizar, abrir PR para `develop`.

### `fix/*`

Use para corrigir bugs identificados durante o desenvolvimento.

Padrao de nome:

```bash
fix/nome-curto-do-bug
```

Exemplos:

```bash
fix/validacao-email-usuario
fix/erro-role-keycloak
fix/endpoint-me
```

Regras:

- Em geral, criar a partir de `develop`.
- Deve conter apenas a correcao do problema.
- Ao finalizar, abrir PR para `develop`.

## Fluxo do Dia a Dia

### 1. Atualizar a branch local

Antes de iniciar qualquer tarefa:

```bash
git checkout develop
git pull origin develop
```

### 2. Criar uma branch de trabalho

Para feature:

```bash
git checkout -b feat/minha-feature
```

Para correcao:

```bash
git checkout -b fix/meu-ajuste
```

### 3. Desenvolver e realizar commits pequenos

Exemplo:

```bash
git add .
git commit -m "feat: adiciona integracao inicial com keycloak"
```

Sugestao de prefixos de commit:

- `feat:`
- `fix:`
- `refactor:`
- `test:`
- `docs:`
- `chore:`

### 4. Atualizar sua branch com `develop`

Se `develop` avancar durante a tarefa:

```bash
git checkout develop
git pull origin develop
git checkout feat/minha-feature
git merge develop
```

Se houver conflitos, resolva antes de seguir.

### 5. Enviar branch para o remoto

```bash
git push -u origin feat/minha-feature
```

ou

```bash
git push -u origin fix/meu-ajuste
```

### 6. Abrir Pull Request para `develop`

Antes de abrir o PR:

- revisar o proprio codigo
- garantir que a branch sobe sem erros
- validar o escopo da tarefa
- atualizar com `develop`, se necessario

### 7. Merge em `develop`

Depois da aprovacao:

- fazer merge da branch em `develop`
- apagar a branch remota, se o time adotar essa pratica

## Promocao para `main`

Quando `develop` estiver validada e pronta para entrega:

```bash
git checkout main
git pull origin main
git merge develop
git push origin main
```

Recomendacoes:

- subir para `main` apenas entregas validadas
- evitar misturar codigo incompleto
- registrar bem o que entrou no merge

## Boas Praticas

- Nao commitar direto em `main`.
- Nao commitar direto em `develop` sem alinhamento do time.
- Criar uma branch por tarefa.
- Manter PRs pequenos e objetivos.
- Evitar branches com multiplos assuntos.
- Escrever nomes de branch claros.
- Escrever commits descritivos.
- Sempre revisar antes de mergear.

## Exemplo Completo

```bash
git checkout develop
git pull origin develop
git checkout -b feat/autenticacao-keycloak

# faz alteracoes no codigo

git add .
git commit -m "feat: adiciona validacao de grupos do keycloak"
git push -u origin feat/autenticacao-keycloak
```

Depois:

- abrir PR de `feat/autenticacao-keycloak` para `develop`
- revisar
- aprovar
- mergear em `develop`

## Resumo Rapido

- `main`: producao
- `develop`: integracao do time
- `feat/*`: novas funcionalidades
- `fix/*`: correcoes

Regra principal:

```text
Sempre criar feat/* e fix/* a partir de develop
Sempre abrir PR de feat/* e fix/* para develop
Promover develop para main apenas quando estiver estavel
```
