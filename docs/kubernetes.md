# Kubernetes - RAGro Backend

## Architecture

The cluster has 3 services:
- **Backend (Spring Boot)** - API exposed via LoadBalancer on port 8080
- **Keycloak** - Authentication server exposed via LoadBalancer on port 8180
- **PostgreSQL** - Database, internal-only (ClusterIP) on port 5432, with a 1Gi persistent disk

Backend and Keycloak depend on PostgreSQL. An initContainer waits for Postgres to be available before starting each one.

## Files

| File | Resource | Description |
|---|---|---|
| `00-namespace.yaml` | Namespace | Isolates all resources in the `ragro` namespace |
| `01-secrets.yaml` | Secret | Postgres and Keycloak credentials (base64) |
| `02-configmap.yaml` | ConfigMap | Connection URLs and Spring/Keycloak configuration |
| `03-pvc.yaml` | PersistentVolumeClaim | 1Gi disk for Postgres data |
| `04-postgres-initdb-configmap.yaml` | ConfigMap | Shell script to create the Keycloak database |
| `04b-postgres-initdb-sql.yaml` | ConfigMap | SQL schema and user seed |
| `05-postgres-deployment.yaml` | Deployment + Service | Database (internal access) |
| `06-keycloak-deployment.yaml` | Deployment + Service | Authentication server (external access) |
| `07-backend-deployment.yaml` | Deployment + Service | Spring Boot API (external access) |

## Services

| Service | Type | Port | Access |
|---|---|---|---|
| `backend-service` | LoadBalancer | 8080 | Public |
| `keycloak-service` | LoadBalancer | 8180 | Public |
| `postgres-service` | ClusterIP | 5432 | Internal only |

## Running locally (Minikube)

### Prerequisites

- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- Docker

### Steps

```bash
# 1. Start Minikube
minikube start

# 2. Build the backend image inside Minikube
eval $(minikube docker-env)
docker build -t ragro-backend:latest .

# 3. Apply all manifests
kubectl apply -f k8s/

# 4. Check that the pods are running
kubectl get pods -n ragro

# 5. Expose the services via port-forward
kubectl port-forward svc/backend-service 8080:8080 -n ragro &
kubectl port-forward svc/keycloak-service 8180:8180 -n ragro &
```

Access:
- Backend / Swagger: `http://localhost:8080/swagger-ui/index.html`
- Keycloak: `http://localhost:8180`

## Deploy on AWS (EKS)

### Prerequisites

- Configured EKS cluster
- `kubectl` pointing to the cluster
- Backend image in a registry (ECR)

### Steps

```bash
# 1. Update the image in 07-backend-deployment.yaml to the ECR one
#    image: <account-id>.dkr.ecr.<region>.amazonaws.com/ragro-backend:latest

# 2. Remove imagePullPolicy: Never (or change it to Always)

# 3. Apply
kubectl apply -f k8s/

# 4. Get the public endpoints (ELB)
kubectl get svc -n ragro
```

The `EXTERNAL-IP` column shows the Load Balancer endpoints.

## Useful commands

```bash
# List all resources in the namespace
kubectl get all -n ragro

# View a pod's logs
kubectl logs -f <pod-name> -n ragro

# View pod details (useful for debugging)
kubectl describe pod <pod-name> -n ragro

# Enter the container (like docker exec)
kubectl exec -it <pod-name> -n ragro -- /bin/sh

# Restart a deployment
kubectl rollout restart deployment/<name> -n ragro

# Delete everything
kubectl delete -f k8s/
```
