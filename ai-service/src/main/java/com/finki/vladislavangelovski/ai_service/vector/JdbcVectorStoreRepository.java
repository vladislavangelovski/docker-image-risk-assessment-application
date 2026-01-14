package com.finki.vladislavangelovski.ai_service.vector;

import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class JdbcVectorStoreRepository implements VectorStoreRepository {
    private final JdbcTemplate jdbc;
    private final String table;
    private final int expectedDim;
    private final String embedModel;
    private final String embedVersion;
    
    public JdbcVectorStoreRepository(JdbcTemplate jdbc,
                                     @Value("${vectorstore.table}") String table,
                                     @Value("${embeddings.expected-dim}") int expectedDim,
                                     @Value("${embeddings.model}") String embedModel,
                                     @Value("${embeddings.version:initial}") String embedVersion) {
        this.jdbc = jdbc;
        this.table = table;
        this.expectedDim = expectedDim;
        this.embedModel = embedModel;
        this.embedVersion = embedVersion;
    }
    
    private static String toPgVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Embedding vector must not be null or empty");
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
    
    @Override
    public void upsertAll(List<CveForEmbedding> docs,
                          List<float[]> vectors) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        if (vectors == null || docs.size() != vectors.size()) {
            throw new IllegalArgumentException("docs.size() and vectors.size() must match");
        }
        
        final String sql = """
                INSERT INTO %s (
                    cve_id,
                    chunk_no,
                    title,
                    description,
                    chunk_text,
                    cvss_base,
                    epss,
                    epss_percentile,
                    cwe,
                    published,
                    last_modified,
                    embed_model,
                    embed_version,
                    embedding,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, now())
                ON CONFLICT (cve_id, chunk_no) DO UPDATE SET
                    title           = EXCLUDED.title,
                    description     = EXCLUDED.description,
                    chunk_text      = EXCLUDED.chunk_text,
                    cvss_base       = EXCLUDED.cvss_base,
                    epss            = EXCLUDED.epss,
                    epss_percentile = EXCLUDED.epss_percentile,
                    cwe             = EXCLUDED.cwe,
                    published       = EXCLUDED.published,
                    last_modified   = EXCLUDED.last_modified,
                    embed_model     = EXCLUDED.embed_model,
                    embed_version   = EXCLUDED.embed_version,
                    embedding       = EXCLUDED.embedding,
                    updated_at      = now()
                """.formatted(table);
        
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps,
                                  int i) throws SQLException {
                CveForEmbedding doc = docs.get(i);
                float[] vector = vectors.get(i);
                
                if (doc.cveId() == null || doc.cveId().isBlank()) {
                    throw new IllegalArgumentException("cveId must not be null or blank");
                }
                
                // Title (fallback to CVE ID if missing)
                String title = doc.title();
                if (title == null || title.isBlank()) {
                    title = doc.cveId();
                }
                
                String description = doc.description();
                
                // Canonical text we embed and store in chunk_text
                String chunkText;
                if (description != null && !description.isBlank()) {
                    chunkText = title + "\n\n" + description;
                }
                else {
                    chunkText = title;
                }
                
                // CWE list -> comma-separated string
                String cwe = null;
                if (doc.cwe() != null && !doc.cwe().isEmpty()) {
                    cwe = String.join(",", doc.cwe());
                }
                
                Instant published = doc.published();
                Instant lastModified = doc.lastModified();
                
                // 1) cve_id
                ps.setString(1, doc.cveId());
                // 2) chunk_no (MVP: always 0)
                ps.setInt(2, 0);
                // 3) title (NOT NULL)
                ps.setString(3, title);
                // 4) description (nullable)
                if (description != null) {
                    ps.setString(4, description);
                }
                else {
                    ps.setNull(4, Types.VARCHAR);
                }
                // 5) chunk_text (NOT NULL)
                ps.setString(5, chunkText);
                
                // 6) cvss_base
                if (doc.cvssBase() != null) {
                    ps.setDouble(6, doc.cvssBase());
                }
                else {
                    ps.setNull(6, Types.DOUBLE);
                }
                
                // 7) epss
                if (doc.epss() != null) {
                    ps.setDouble(7, doc.epss());
                }
                else {
                    ps.setNull(7, Types.DOUBLE);
                }
                
                // 8) epss_percentile
                if (doc.epssPercentile() != null) {
                    ps.setDouble(8, doc.epssPercentile());
                }
                else {
                    ps.setNull(8, Types.DOUBLE);
                }
                
                // 9) cwe (comma-separated)
                if (cwe != null && !cwe.isBlank()) {
                    ps.setString(9, cwe);
                }
                else {
                    ps.setNull(9, Types.VARCHAR);
                }
                
                // 10) published (TIMESTAMPTZ)
                if (published != null) {
                    ps.setObject(10, OffsetDateTime.ofInstant(published, ZoneOffset.UTC));
                }
                else {
                    ps.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE);
                }
                
                // 11) last_modified (TIMESTAMPTZ)
                if (lastModified != null) {
                    ps.setObject(11, OffsetDateTime.ofInstant(lastModified, ZoneOffset.UTC));
                }
                else {
                    ps.setNull(11, Types.TIMESTAMP_WITH_TIMEZONE);
                }
                
                // 12) embed_model
                ps.setString(12, embedModel);
                // 13) embed_version
                ps.setString(13, embedVersion);
                // 14) embedding (pgvector text literal, cast to vector in SQL)
                ps.setString(14, toPgVectorLiteral(vector));
            }
            
            @Override
            public int getBatchSize() {
                return docs.size();
            }
        });
    }
    
    @Override
    public List<SearchHit> search(float[] queryVector,
                                  int k) {
        if (expectedDim > 0 && queryVector.length != expectedDim) {
            throw new IllegalStateException("Embedding dim " + queryVector.length + " != expected " + expectedDim);
        }
        
        String sql = """
                    SELECT cve_id, title, epss, cvss_base,
                           1 - (embedding <=> ?::vector) AS similarity
                    FROM %s
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                """.formatted(table);
        
        String literal = toPgVectorLiteral(queryVector);
        return jdbc.query(sql, ps -> {
            ps.setString(1, literal);
            ps.setString(2, literal);
            ps.setInt(3, k);
        }, (rs, rowNum) -> {
            BigDecimal epss = rs.getBigDecimal("epss");
            BigDecimal cvss = rs.getBigDecimal("cvss_base");
            
            return new SearchHit(
                    rs.getString("cve_id"),
                    rs.getDouble("similarity"),
                    rs.getString("title"),
                    epss != null ? epss.doubleValue() : null,
                    cvss != null ? cvss.doubleValue() : null
            );
        });
    }
    
    @Override
    public boolean existsByCveId(String cveId) {
        String sql = "select exists (select 1 from %s where cve_id = ?)".formatted(table);
        Boolean exists = jdbc.queryForObject(sql, Boolean.class, cveId);
        return Boolean.TRUE.equals(exists);
    }
}
