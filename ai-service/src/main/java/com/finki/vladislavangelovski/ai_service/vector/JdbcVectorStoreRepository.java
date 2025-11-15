package com.finki.vladislavangelovski.ai_service.vector;

import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class JdbcVectorStoreRepository implements VectorStoreRepository {
    private final JdbcTemplate jdbc;
    private final String table;
    private final int expectedDim;
    
    public JdbcVectorStoreRepository(JdbcTemplate jdbc,
                                     @Value("${vectorstore.table}") String table,
                                     @Value("${embeddings.expected-dim}") int expectedDim) {
        this.jdbc = jdbc;
        this.table = table;
        this.expectedDim = expectedDim;
    }
    
    @Override
    public void upsertAll(List<CveForEmbedding> docs,
                          List<float[]> vectors) {
        if (docs == null || vectors == null || docs.size() != vectors.size()) {
            throw new IllegalArgumentException("docs and vectors must be same size");
        }
        
        String sql = """
                    INSERT INTO %s (cve_id, title, description, cvss_base, epss, embedding)
                    VALUES (?, ?, ?, ?, ?, ?::vector)
                    ON CONFLICT (cve_id) DO UPDATE
                    SET title = EXCLUDED.title,
                        description = EXCLUDED.description,
                        cvss_base = EXCLUDED.cvss_base,
                        epss = EXCLUDED.epss,
                        embedding = EXCLUDED.embedding
                """.formatted(table);
        
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps,
                                  int i) throws SQLException {
                CveForEmbedding d = docs.get(i);
                float[] vec = vectors.get(i);
                if (expectedDim > 0 && vec.length != expectedDim) {
                    throw new IllegalStateException("Embedding dim " + vec.length + " != expected " + expectedDim);
                }
                
                ps.setString(1, d.cveId());
                ps.setString(2, d.title());
                ps.setString(3, d.description());
                if (d.cvssBase() == null) {
                    ps.setObject(4, null);
                }
                else {
                    ps.setDouble(4, d.cvssBase());
                }
                if (d.epss() == null) {
                    ps.setObject(5, null);
                }
                else {
                    ps.setDouble(5, d.epss());
                }
                
                ps.setString(6, toVectorLiteral(vec)); // casted with ::vector in SQL
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
                           (embedding <-> ?::vector) AS distance
                    FROM %s
                    ORDER BY embedding <-> ?::vector
                    LIMIT ?
                """.formatted(table);
        
        String literal = toVectorLiteral(queryVector);
        return jdbc.query(sql, ps -> {
            ps.setString(1, literal);
            ps.setString(2, literal);
            ps.setInt(3, k);
        }, (rs, rowNum) -> new SearchHit(rs.getString("cve_id"), rs.getDouble("distance"), rs.getString("title"),
                                         (Double) rs.getObject("epss"), (Double) rs.getObject("cvss_base")));
    }
    
    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            // keep reasonable precision
            sb.append(Float.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
