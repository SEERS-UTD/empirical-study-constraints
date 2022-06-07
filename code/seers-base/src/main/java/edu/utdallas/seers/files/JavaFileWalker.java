package edu.utdallas.seers.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Using walker because {@link java.nio.file.Files#walk(Path, FileVisitOption...)} doesn't allow skipping
 * subtrees.
 */
public class JavaFileWalker<T> extends SimpleFileVisitor<Path> {

    final Logger logger = LoggerFactory.getLogger(JavaFileWalker.class);

    private final Set<Path> excludedPaths;
    private final Path projectPath;
    private final JavaFileVisitor<T> visitor;

    public JavaFileWalker(Path projectPath, Set<Path> excludedPaths, JavaFileVisitor<T> visitor) {
        this.projectPath = projectPath;
        this.excludedPaths = excludedPaths;
        this.visitor = visitor;
    }

    public Collection<? extends T> walk() {
        try {
            Files.walkFileTree(
                    projectPath,
                    // Follow links
                    EnumSet.allOf(FileVisitOption.class),
                    Integer.MAX_VALUE,
                    this
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return visitor.getCollection();
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!file.toString().endsWith(".java")) {
            return FileVisitResult.CONTINUE;
        }

        return visitor.visit(file);
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        // Files.walk does not allow skipping subtrees
        if (excludedPaths.contains(projectPath.relativize(dir))) {
            logger.info("Skipping directory: " + dir);
            return FileVisitResult.SKIP_SUBTREE;
        }

        return FileVisitResult.CONTINUE;
    }

    public interface JavaFileVisitor<T> {
        FileVisitResult visit(Path file) throws IOException;

        Collection<? extends T> getCollection();
    }
}
