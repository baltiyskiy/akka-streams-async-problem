package com.github.baltiyskiy;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.japi.function.Function;
import akka.stream.*;
import akka.stream.javadsl.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;


public class AsyncProblemTest {
  @Test
  public void test() throws ExecutionException, InterruptedException {
    final Exception failure = new RuntimeException("boom");

    ActorSystem system = ActorSystem.create();
    Materializer mat = ActorMaterializer.create(system);

    Flow<Integer, Integer, NotUsed> flow = Flow.of(Integer.class).map((Function<Integer, Integer>) param -> {
      throw failure;
    }).async();
    Pair<SourceQueueWithComplete<Integer>, CompletionStage<Done>> result =
      (Pair<SourceQueueWithComplete<Integer>, CompletionStage<Done>>)
        Source.fromGraph(
          GraphDSL.create(Source.<Integer>queue(1, OverflowStrategy.fail()),
            (builder, s) -> {
              UniformFanOutShape<Integer, Integer> p = builder.add(Partition.<Integer>create(2, (x) -> 0, true));
              UniformFanInShape<Integer, Integer> m = builder.add(Merge.<Integer>create(2, true));
              builder.from(s).toFanOut(p);
              builder.from(p).via(builder.add(flow)).toFanIn(m);
              builder.from(p).via(builder.add(Flow.of(Integer.class).map((x) -> 2))).toFanIn(m);
              return new SourceShape(m.out());
            }))
          .toMat(Sink.ignore(), Keep.both())
          .run(mat);

    SourceQueueWithComplete<Integer> sourceQueue = result.first();
    CompletionStage<Done> future = result.second();
    sourceQueue.offer(1).toCompletableFuture().get();
    try {
      Done done = future.toCompletableFuture().get();
      Assert.fail("must have failed, but got " + done);
    } catch (Exception ignored) {
    }
  }
}
