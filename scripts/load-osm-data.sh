#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-gearheads}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-postgres}"

OSM_FILE="sul-latest.osm.pbf"
OSM_URL="https://download.geofabrik.de/south-america/brazil/sul-latest.osm.pbf"

if ! command -v osm2pgrouting &>/dev/null; then
  echo "ERROR: osm2pgrouting not found. Install it with: sudo apt-get install osm2pgrouting"
  exit 1
fi

if [ ! -f "$OSM_FILE" ]; then
  echo "Downloading $OSM_FILE (~150MB)..."
  wget -O "$OSM_FILE" "$OSM_URL"
else
  echo "Found existing $OSM_FILE, skipping download."
fi

echo "Importing road network into PostgreSQL (this may take a few minutes)..."
PGPASSWORD="$DB_PASS" osm2pgrouting \
  --file "$OSM_FILE" \
  --host "$DB_HOST" \
  --port "$DB_PORT" \
  --dbname "$DB_NAME" \
  --user "$DB_USER" \
  --password "$DB_PASS" \
  --clean

echo "Done. Road network imported into tables: ways, ways_vertices_pgr"
