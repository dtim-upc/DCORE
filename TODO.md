# List of task

- [ ] Benchmarks
  - [x] Add new benchmark for maximal matches
  - [ ] Fix the following bug `sbt "benchmark/multi-jvm:run benchmark1.query1.workers12.linear.MaximalMatchesEnumeration"`

```
[ERROR] [akka://ClusterSystem@127.0.0.1:25251] - null
akka.actor.ActorInitializationException: akka://ClusterSystem/user/EngineManager-127.0.0.1:25251/Engine: exception during creation
	at akka.actor.ActorInitializationException$.apply(Actor.scala:196)
	at akka.actor.ActorCell.create(ActorCell.scala:664)
	at akka.actor.ActorCell.invokeAll$1(ActorCell.scala:514)
	at akka.actor.ActorCell.systemInvoke(ActorCell.scala:536)
	at akka.dispatch.Mailbox.processAllSystemMessages(Mailbox.scala:295)
	at akka.dispatch.Mailbox.run(Mailbox.scala:230)
	at akka.dispatch.Mailbox.exec(Mailbox.scala:243)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:290)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1020)
	at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1656)
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1594)
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:183)
Caused by: java.lang.NullPointerException: null
	at edu.puc.core.engine.streams.StreamManager.add(StreamManager.java:60)
	at edu.puc.core.engine.streams.StreamManager.fromCOREFile(StreamManager.java:54)
	at dcer.actors.Engine$.$anonfun$buildEngine$6(Engine.scala:94)
	at scala.util.Try$.apply(Try.scala:213)
	at dcer.actors.Engine$.$anonfun$buildEngine$5(Engine.scala:94)
	at scala.util.Either.flatMap(Either.scala:341)
	at dcer.actors.Engine$.$anonfun$buildEngine$3(Engine.scala:93)
	at scala.util.Either.flatMap(Either.scala:341)
	at dcer.actors.Engine$.$anonfun$buildEngine$1(Engine.scala:90)
	at scala.util.Either.flatMap(Either.scala:341)
	at dcer.actors.Engine$.buildEngine(Engine.scala:86)
	at dcer.actors.Engine$.$anonfun$apply$1(Engine.scala:30)
	at akka.actor.typed.internal.BehaviorImpl$DeferredBehavior$$anon$1.apply(BehaviorImpl.scala:120)
	at akka.actor.typed.Behavior$.start(Behavior.scala:168)
	at akka.actor.typed.internal.adapter.ActorAdapter.preStart(ActorAdapter.scala:290)
	at akka.actor.Actor.aroundPreStart(Actor.scala:548)
	at akka.actor.Actor.aroundPreStart$(Actor.scala:548)
	at akka.actor.typed.internal.adapter.ActorAdapter.aroundPreStart(ActorAdapter.scala:265)
	at akka.actor.ActorCell.create(ActorCell.scala:644)
	... 10 common frames omitted
```


  - [ ] Change `run-benchmarks.hs` to move outputs (matches, time and stats) to a folder named by the test.
  - [ ] Benchmark each strategy and nMaxMatches and store the output in a CSV.

- [ ] MaximalMatchEnumeration repeated outputs
  - [ ] Prepare some complex examples and try to find a relationship between the repeated elements.
  - [ ] Write an email to Stijn with the examples and ask him if he knows this problem.
  - [ ] The idea would be to fine a mathematical definition of the problem (see set theory). Then, we can apply a theorem to avoid duplication or assume it is not tractable.

- [ ] Load balancing: matches vs length (the problem right now is that enumeration does not take length into account)
  - [ ] Add execution time of MaximalMatch + Configuration
  - [ ] Execute a benchmark and store in CSV: #matches, length of matches, execution time
  - [ ] Plot this information to see if load balancing is affected by length of matches.

## Optional

- [ ] I don't really like the implementation based on node lists. 
`dcer.data.Match` could store a `List[List[Event]]` instead.
This would make grouping 0 cost and traversing is actually still linear with a custom iterator.