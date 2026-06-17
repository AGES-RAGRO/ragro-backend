# S3 — bucket de mídia (ragro-prod-<accountId>). Totalmente privado (acesso
# público bloqueado), criptografia AES256 e ACLs desabilitadas
# (BucketOwnerEnforced). Configuração em recursos separados (padrão do provider
# v5), em vez de blocos inline.
resource "aws_s3_bucket" "ragro" {
  bucket = "ragro-prod-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "ragro" {
  bucket                  = aws_s3_bucket.ragro.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "ragro" {
  bucket = aws_s3_bucket.ragro.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "ragro" {
  bucket = aws_s3_bucket.ragro.id

  rule {
    bucket_key_enabled = false

    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_cors_configuration" "ragro" {
  bucket = aws_s3_bucket.ragro.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "HEAD"]
    allowed_origins = ["*"]
    max_age_seconds = 3600
  }
}
