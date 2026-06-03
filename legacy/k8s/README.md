# Legacy Kubernetes manifests (NOT the deploy path)

These manifests are **not** used to deploy RAGRO. The canonical deploy is **AWS ECS**
via [`.github/workflows/aws.yml`](../../.github/workflows/aws.yml) (build image → ECR →
ECS task definition with env from GitHub Secrets).

They are kept here for reference only. Notes before any reuse:

- `01-secrets.yaml` ships **placeholder/default** credentials (base64 of `postgres`/`admin`)
  and a `GOOGLE_MAPS_API_KEY` placeholder — never apply as-is. Create real secrets out of band.
- Runtime config (Keycloak/MinIO URLs, `NVIDIA_API_KEY`, CORS, `MEDIA_PUBLIC_URL`, admin creds) must be
  injected the same way the ECS task does it; these manifests predate several of those env vars.

If Kubernetes is permanently dropped, delete this directory.
