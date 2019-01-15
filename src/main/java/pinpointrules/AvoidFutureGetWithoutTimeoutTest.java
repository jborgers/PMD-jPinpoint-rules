package pinpointrules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class AvoidFutureGetWithoutTimeoutTest {
    /**
     * This method is used to wrap the reply of each thread into one as response
     * content.
     *
     * @param responseCollector
     *            the response content collector
     * @param listOfFutures
     *            list of all thread's Future returns
     */
    public static <T> void collectAllCollectionReplyFromThreads(CompletableFuture<List<T>> complFuture, List<T> responseCollector,
                                                                List<Future<List<T>>> listOfFutures, CompletionService<List<T>> completionService) {
        for (int index = 0; index < listOfFutures.size(); index++) {
            try {
                Future<List<T>> futureLocal = completionService.take();
                RunnableFuture<List<T>> runFutLocal = new FutureTask<List<T>>(()-> Collections.emptyList());
                long timeout = 10;
                responseCollector.addAll(complFuture.get()); // violation
                responseCollector.addAll(complFuture.get(10, TimeUnit.SECONDS)); // OK
                responseCollector.addAll(futureLocal.get()); // violation
                responseCollector.addAll(futureLocal.get(timeout, TimeUnit.SECONDS)); // OK
                responseCollector.addAll(runFutLocal.get()); // violation TODO: multiple arguments in concat
                responseCollector.addAll(runFutLocal.get(timeout, TimeUnit.SECONDS)); // OK
            } catch (InterruptedException | ExecutionException e) {
                //LOGGER.error("Error in Thread : {}", e);
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }

    }

    public static <T> void collectAllCollectionReplyFromThreads(List<T> responseCollector,
                                                                List<Future<List<T>>> listOfFutures, CompletionService<List<T>> completionService) {
        for (int index = 0; index < listOfFutures.size(); index++) {
            try {
                Future<List<T>> future = completionService.take();
                responseCollector.addAll(future.get()); // should violate
            } catch (InterruptedException | ExecutionException e) {
                //LOGGER.error("Error in Thread : {}", e);
            }
        }
    }

    private <Input, Output> Map<Input, Output> getResults(final Map<Input, Future<Output>> futures) throws ExecutionException, InterruptedException {
        Map<Input, Output> outputs = new LinkedHashMap<>();
        for (Map.Entry<Input, Future<Output>> entry : futures.entrySet()) {
            Input input = entry.getKey();
            Future<Output> future = entry.getValue();
            outputs.put(input, future.get()); // should violate
        }

        return outputs;
    }

    private void logExecutionDetails(final List<Future<String>> futures) throws InterruptedException, ExecutionException {
        int totalWorkflows = 0;
        int succeededWorkflows = 0;
        int failedWorkflows = 0;

        for (final Future<String> future : futures) {
            String workflowDetails = future.get(); // should violate
            //log.info("Workflow " + workflowDetails.getWorkflowName() + " completed with status : " + workflowDetails);
            totalWorkflows++;
            if ("success".equals(future.get())) { // should violate
                succeededWorkflows++;
            } else {
                failedWorkflows++;
            }
        }

    }
}
