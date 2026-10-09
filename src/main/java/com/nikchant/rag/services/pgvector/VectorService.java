package com.nikchant.rag.services.pgvector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class VectorService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public VectorService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(900)
                .build();
    }

    /**
     * Splits the documents into chunks, embeds them and stores them in qdrant.
     * The source is kept in the metadata so we know where each chunk came from.
     */
    public void addToStore(List<Document> documents, String source, long userId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", source);
        metadata.put("userId", userId);

        List<Document> withSource = documents.stream()
                .map(doc -> doc.mutate().metadata(metadata).build())
                .toList();

        List<Document> chunks = splitter.split(withSource);
        vectorStore.add(chunks);
    }

    /**
     * Returns the chunks that are most similar to the given query.
     */
    public List<Document> search(String query, int topK, long userId) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("userId  == " + userId)
                .similarityThreshold(0.5)
                .build();
        return vectorStore.similaritySearch(request);
    }

}
