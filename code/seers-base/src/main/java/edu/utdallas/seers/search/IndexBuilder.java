package edu.utdallas.seers.search;

import edu.utdallas.seers.files.Files;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class IndexBuilder<T> {
    final Logger logger = LoggerFactory.getLogger(IndexBuilder.class);

    public Index<T> buildIndex(String indexName, Stream<? extends T> items) throws IOException {
        IndexWriterConfig writerConfig = new IndexWriterConfig()
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        getSimilarity().ifPresent(writerConfig::setSimilarity);

        Path indexPath = resolveIndexPathForName(indexName);

        try (IndexWriter indexWriter = new IndexWriter(FSDirectory.open(indexPath), writerConfig)) {
            items.forEach(t -> {
                Optional<Iterable<IndexableField>> fields = generateFields(t, indexName);

                fields.ifPresent(f -> {
                    try {
                        indexWriter.addDocument(f);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

                if (fields.isEmpty()) {
                    logger.warn("Item not indexed: " + t);
                }
            });
        }

        return createIndex(indexPath);
    }

    protected Path resolveIndexPathForName(String indexName) {
        return Files.getTempFilePath("lucene-indexes", indexName);
    }

    protected abstract Index<T> createIndex(Path indexPath) throws IOException;

    /**
     * Generate the fields for indexing an entity, or an empty optional if the entity should not be
     * indexed.
     *
     * @param item      The entity.
     * @param indexName Name of the index.
     * @return Optional of fields or empty.
     */
    protected abstract Optional<Iterable<IndexableField>> generateFields(T item, String indexName);

    /**
     * Similarity to use for Lucene's IndexBuilder. If an empty optional is returned, the default
     * is used.
     *
     * @return Similarity.
     */
    protected Optional<Similarity> getSimilarity() {
        return Optional.empty();
    }
}
