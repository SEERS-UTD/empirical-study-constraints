package edu.utdallas.seers.lasso.detector;

import java.util.concurrent.*;

public class TestBuilder {

    private static void testAllBuilders(int n) throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> future = executor.submit(new Task(n));
        try {
            future.get(30, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            future.cancel(true);
            System.out.println("terminated by timeout");
        }
        executor.shutdownNow();
    }


    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 8; i++) {
            testAllBuilders(i);
        }
    }
}

