# Kubernetes - RAGro Backend

## Arquitetura

O cluster possui 3 servicos:
- **Backend (Spring Boot)** - API exposta via LoadBalancer na porta 8080
- **Keycloak** - Servidor de autenticacao exposto via LoadBalancer na porta 8180
- **PostgreSQL** - Banco de dados acessivel apenas internamente (ClusterIP) na porta 5432, com disco persistente de 1Gi

O Backend e o Keycloak dependem do PostgreSQL. Um initContainer aguarda o Postgres estar disponivel antes de iniciar cada um.

## Arquivos

| Arquivo | Recurso | Descricao |
|---|---|---|
| `00-namespace.yaml` | Namespace | Isola todos os recursos no namespace `ragro` |
| `01-secrets.yaml` | Secret | Credenciais do Postgres e Keycloak (base64) |
| `02-configmap.yaml` | ConfigMap | URLs de conexao e configuracoes do Spring/Keycloak |
| `03-pvc.yaml` | PersistentVolumeClaim | 1Gi de disco para dados do Postgres |
| `04-postgres-initdb-configmap.yaml` | ConfigMap | Script shell para criar o banco do Keycloak |
| `04b-postgres-initdb-sql.yaml` | ConfigMap | Schema SQL e seed de usuarios |
| `05-postgres-deployment.yaml` | Deployment + Service | Banco de dados (acesso interno) |
| `06-keycloak-deployment.yaml` | Deployment + Service | Servidor de autenticacao (acesso externo) |
| `07-backend-deployment.yaml` | Deployment + Service | API Spring Boot (acesso externo) |

## Services

| Service | Tipo | Porta | Acesso |
|---|---|---|---|
| `backend-service` | LoadBalancer | 8080 | Publico |
| `keycloak-service` | LoadBalancer | 8180 | Publico |
| `postgres-service` | ClusterIP | 5432 | Apenas interno |

## Rodando localmente (Minikube)

### Pre-requisitos

- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- Docker

### Passos

```bash
# 1. Iniciar o Minikube
minikube start

# 2. Buildar a imagem do backend dentro do Minikube
eval $(minikube docker-env)
docker build -t ragro-backend:latest .

# 3. Aplicar todos os manifests
kubectl apply -f k8s/

# 4. Verificar se os pods estao rodando
kubectl get pods -n ragro

# 5. Expor os services via port-forward
kubectl port-forward svc/backend-service 8080:8080 -n ragro &
kubectl port-forward svc/keycloak-service 8180:8180 -n ragro &
```

Acesse:
- Backend / Swagger: `http://localhost:8080/swagger-ui/index.html`
- Keycloak: `http://localhost:8180`

## Deploy na AWS (EKS)

### Pre-requisitos

- Cluster EKS configurado
- `kubectl` apontando para o cluster
- Imagem do backend em um registry (ECR)

### Passos

```bash
# 1. Atualizar a imagem no 07-backend-deployment.yaml para o ECR
#    image: <account-id>.dkr.ecr.<region>.amazonaws.com/ragro-backend:latest

# 2. Remover imagePullPolicy: Never (ou trocar para Always)

# 3. Aplicar
kubectl apply -f k8s/

# 4. Obter os endpoints publicos (ELB)
kubectl get svc -n ragro
```

A coluna `EXTERNAL-IP` mostrara os endpoints dos Load Balancers.

## Comandos uteis

```bash
# Ver todos os recursos do namespace
kubectl get all -n ragro

# Ver logs de um pod
kubectl logs -f <nome-do-pod> -n ragro

# Ver detalhes de um pod (util para debug)
kubectl describe pod <nome-do-pod> -n ragro

# Entrar no container (equivalente a docker exec)
kubectl exec -it <nome-do-pod> -n ragro -- /bin/sh

# Reiniciar um deployment
kubectl rollout restart deployment/<nome> -n ragro

# Deletar tudo
kubectl delete -f k8s/
```
