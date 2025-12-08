-- V5__enrich_cve_embeddings.sql
-- Enrich existing cve_embeddings table with chunk_no, id PK, and metadata

-----------------------------------------------------------------------
-- 0) Ensure pgvector extension exists (usually already there)
-----------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS vector;

-----------------------------------------------------------------------
-- 1) Add missing columns (nullable for now)
-----------------------------------------------------------------------
ALTER TABLE cve_embeddings
    ADD COLUMN id BIGSERIAL,
    ADD COLUMN chunk_no INT,
    ADD COLUMN cwe TEXT,
    ADD COLUMN published TIMESTAMPTZ,
    ADD COLUMN last_modified TIMESTAMPTZ;

-----------------------------------------------------------------------
-- 2) Rename existing generic columns to semantic names
--    text          -> chunk_text
--    percentile    -> epss_percentile
--    source        -> refs_json
-----------------------------------------------------------------------
ALTER TABLE cve_embeddings
    RENAME COLUMN text TO chunk_text;

ALTER TABLE cve_embeddings
    RENAME COLUMN percentile TO epss_percentile;

ALTER TABLE cve_embeddings
    RENAME COLUMN source TO refs_json;

-----------------------------------------------------------------------
-- 3) Backfill new columns for existing rows
--    - chunk_no = 0 for all existing rows
--    - ensure embed_model / embed_version / updated_at are non-null
-----------------------------------------------------------------------
UPDATE cve_embeddings
SET chunk_no = 0
WHERE chunk_no IS NULL;

-- Adjust the model/version strings if you want different labels.
-- 'nomic-embed-text' matches your current embeddings.model property.
UPDATE cve_embeddings
SET embed_model   = COALESCE(embed_model, 'nomic-embed-text'),
    embed_version = COALESCE(embed_version, 'initial'),
    updated_at    = COALESCE(updated_at, now());

-----------------------------------------------------------------------
-- 4) Ensure id is populated for existing rows
--    BIGSERIAL created a sequence (cve_embeddings_id_seq) and a default
--    for new rows, but existing rows still have id = NULL.
-----------------------------------------------------------------------
UPDATE cve_embeddings
SET id = nextval('cve_embeddings_id_seq')
WHERE id IS NULL;

-----------------------------------------------------------------------
-- 5) Tighten nullability constraints
--    These columns must always be present for our design.
-----------------------------------------------------------------------
ALTER TABLE cve_embeddings
    ALTER COLUMN cve_id       SET NOT NULL,
ALTER COLUMN chunk_no     SET NOT NULL,
    ALTER COLUMN title        SET NOT NULL,
    ALTER COLUMN chunk_text   SET NOT NULL,
    ALTER COLUMN embed_model  SET NOT NULL,
    ALTER COLUMN embed_version SET NOT NULL,
    ALTER COLUMN embedding    SET NOT NULL,
    ALTER COLUMN updated_at   SET NOT NULL;

-----------------------------------------------------------------------
-- 6) Primary key and logical uniqueness
--    Assumes there is currently NO primary key on the table.
--    If you already have one, drop it manually or adjust this section.
-----------------------------------------------------------------------
ALTER TABLE cve_embeddings
    ADD CONSTRAINT cve_embeddings_pkey PRIMARY KEY (id);

ALTER TABLE cve_embeddings
    ADD CONSTRAINT cve_embeddings_cve_id_chunk_no_key
        UNIQUE (cve_id, chunk_no);

-----------------------------------------------------------------------
-- 7) Indexes for lookup and ANN search
-----------------------------------------------------------------------
-- Fast lookup by CVE ID (for existsByCveId / upserts)
CREATE INDEX IF NOT EXISTS idx_cve_embeddings_cve_id
    ON cve_embeddings (cve_id);

-- HNSW index for approximate nearest neighbour search (cosine)
-- This assumes pgvector >= 0.5.0.
CREATE INDEX IF NOT EXISTS idx_cve_embeddings_embedding_hnsw
    ON cve_embeddings
    USING hnsw (embedding vector_cosine_ops);
