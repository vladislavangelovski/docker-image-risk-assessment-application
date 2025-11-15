package com.finki.vladislavangelovski.ai_service.search;

import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EmbeddingSearchRepositoryPg implements EmbeddingSearchRepository {
    private final JdbcTemplate jdbc;
    
    public EmbeddingSearchRepositoryPg(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    
    private static String toPgVectorLiteral(double[] v) {
        StringBuilder sb = new StringBuilder(2 + v.length * 8);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }
    
    private static final RowMapper<SearchHit> MAPPER = (rs, i) -> new SearchHit(rs.getString("cve_id"),
                                                                                rs.getString("title"),
                                                                                rs.getString("description"),
                                                                                (Double) rs.getObject("epss"),
                                                                                (Double) rs.getObject("cvss_base"),
                                                                                rs.getDouble("sim"));
    
    @Override
    public List<SearchHit> search(double[] queryEmbedding,
                                  int k) {
        // If your index is vector_cosine_ops, <=> is cosine distance.
        // Similarity = 1 - distance (range ~0..1)
        final String vec = toPgVectorLiteral(queryEmbedding);
        final String sql = "WITH q AS (SELECT ?::vector AS v) " + "SELECT cve_id, title, description, epss, " +
                "cvss_base, " + "       1 - (embedding <=> q.v) AS sim " + "FROM cve_embeddings, q " + "ORDER BY " +
                "embedding <=> q.v " + "LIMIT ?";
        return jdbc.query(sql, ps -> {
            ps.setString(1, vec);
            ps.setInt(2, k);
        }, MAPPER);
    }
}
