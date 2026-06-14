# Ambientes Terraform

O código `.tf` é o mesmo para todos os ambientes (AGES, cliente). Só mudam as
variáveis específicas de conta e o backend do state.

## Arquivos

- `*.tfvars` e `*.backend.hcl` — valores **reais** (contêm o account ID).
  São **gitignored** (repo público). Crie os seus copiando os `.example`.
- `*.example` — templates versionados, só com placeholders.

## Rodar localmente

```bash
cp envs/ages.tfvars.example      envs/ages.tfvars
cp envs/ages.backend.hcl.example envs/ages.backend.hcl
# preencha os valores reais nos dois arquivos, e então:
terraform init -backend-config=envs/ages.backend.hcl
terraform plan -var-file=envs/ages.tfvars
```

## No CI (GitHub Actions)

Os valores **não** ficam no repo — vêm de **GitHub Variables**
(Settings → Secrets and variables → Actions → aba **Variables**). São Variables
e não Secrets porque não são segredos, apenas identificadores:

| Variable                  | Exemplo                                         |
| ------------------------- | ----------------------------------------------- |
| `TFSTATE_BUCKET`          | `ragro-tfstate-<accountId>`                     |
| `RDS_KMS_KEY_ARN`         | `arn:aws:kms:us-east-2:<acct>:key/<id>`         |
| `RDS_MONITORING_ROLE_ARN` | `arn:aws:iam::<acct>:role/rds-monitoring-role`  |
| `RDS_DB_SUBNET_GROUP`     | `default-vpc-<vpcId>`                           |

A senha do RDS (quando criar o banco do zero) é a única coisa que vai em
**Secret**, injetada como `TF_VAR_db_password`.
