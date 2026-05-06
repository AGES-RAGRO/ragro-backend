UPDATE "addresses" SET "neighborhood" = '' WHERE "neighborhood" IS NULL;
ALTER TABLE "addresses" ALTER COLUMN "neighborhood" SET NOT NULL;
