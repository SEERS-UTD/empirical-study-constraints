package edu.utdallas.seers.lasso.detector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class GraphBuildingTest {
    private final Logger logger = LoggerFactory.getLogger(GraphBuildingTest.class);

    public static void main(String[] args) {
        String systemName = args[0];

        new GraphBuildingTest().test(systemName);
    }

    private void test(String systemName) {
        logger.info("Starting building for system: {}", systemName);
        long startTime = System.currentTimeMillis();

        new PatternDetector(
                systemName,
                SystemInfo.buildInfo().get(systemName),
                false,
                Paths.get("/tmp/graph-building-test")
        );

        long endTime = System.currentTimeMillis();
        logger.info("Building graphs for {} took {} minutes",
                systemName, (endTime - startTime) / 60000);
    }
}