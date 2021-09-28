# Actor Hierarchy

## Proposal 1 (Oct, 2021)

This is a `1-n` architecture. 

There is only 1 master, the `Engine` actor, and, n `Worker` actors.

The `Engine` processes the events and sends the maximal matches to the actors using the configured distribution strategy.

Once the `Worker` finishes applying the second-order predicate to the event trends, it sends back to the master the filtered matches.

```
Root - - > EngineManager (1) -- Engine(1)
  |             |
  |             |
   - - - > Worker (1..*)
```
