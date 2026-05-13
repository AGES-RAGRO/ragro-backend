CREATE TABLE "notifications" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "user_id" uuid NOT NULL,
  "title" varchar(120) NOT NULL,
  "message" text NOT NULL,
  "type" varchar(40) NOT NULL,
  "reference_type" varchar(40),
  "reference_id" uuid,
  "is_read" boolean NOT NULL DEFAULT false,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "read_at" timestamptz
);

ALTER TABLE "notifications"
  ADD CONSTRAINT "notifications_user_id_fkey"
  FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

CREATE INDEX "idx_notifications_user_created_at"
  ON "notifications" ("user_id", "created_at" DESC);

CREATE INDEX "idx_notifications_user_is_read"
  ON "notifications" ("user_id", "is_read");
