# Distribution Strategies

## General Distribution Strategies

### Power of Two Choices (PoTC)

Select two bins u.a.r and later assign the message to the least loaded.
The load is solely based on the number of messages.
Each key might be assigned to any of the workers.

> This algorithm is not suitable for stateful operations
> Use PKG instead

### [Partial Key Grouping (PKG)](../papers/PartialKeyGrouping.pdf)

PoTC adapted to distributed stream processing with **stateful operations** with keys.

- **Key splitting**. Choice based on load for each message.
- **Local load estimation**. There are _n_ sources but we only need to keep local load information of each source (not global). Proof on paper.

```
P_t(k) = {arg\ max}_i \{ L_i(t) : \mathcal{H}_1(k) = i \lor \mathcal{H}_2(k) = i \}
```

### [Improvement on PKG](../papers/WhenTwoChoicesAreNotEnoughBalancingAtScaleInDistributedStreamProcessing.pdf)

### [Consistent Grouping](../papers/LoadBalancingForSkewedStreamsOnHeterogeneousClusters.pdf)

## CER-specific Distribution Strategies

### Maximal Matches

In order to implement this strategy, you must signal CORE to compute only the maximal matches.
This can be achieved by replacing `SELECT *` by `SELECT MAX *`

#### Enumeration on Engine

> Do not implement.
> CORE does this by default in a more efficient way.

```
Query: AB+C+D
Stream: A1 B1 B2 C1 C2 C3 D1
Maximal Match: A1 B1 B2 C1 C2 C3 D1

1. Generate the power sets of each kleene star (remove empty set)
Powerset of A: {{A1}}
Powerset of B: {{B1}, {B2}, {B1, B2}}
Powerset of C: {{C1}, {C2}, {C3}, {C1,C2}, {C1, C3}, {C1, C2, C3}}
Powerset of D: {{D1}}

2. Use guava's cartesian product https://guava.dev/releases/snapshot/api/docs/com/google/common/collect/Sets.html#cartesianProduct-java.util.List
    to generate all possible combinations of the different kleene powersets.
[A1, B1, C1, D1]
[A1, B1, C2, D1]
[A1, B1, C3, D1]
[A1, B1, C1, C2, D1]
[A1, B1, C1, C3, D1]
[A1, B1, C1, C2, C3, D1]
[A1, B2, C1, D1]
...

3. For each one, create a Match.
4. Distribute the matches taking into account the load.
```

#### Enumeration on workers

The key idea here is that enumerating is expensive, so we want to delegate it to the workers.

```
Query: AB+C+D
Stream: A1 B1 B2 C1 C2 C3 D1
Maximal Match: A1 B1 B2 C1 C2 C3 D1

For all maximal matches:
    1. Group by event type: {{A1}, {B1 B2}, {C1 C2 C3}, {D1}}
    2. Subsets sizes: {{1}, {1,2}, {1,2,3}, {1}}
    3. Cartesian product of subsets sizes to generate all configurations:
    {A=1,B=1,C=1,D=1} <-> maximal match
    {A=1,B=1,C=2,D=1} <-> maximal match
    ...
Distribute all configurations using a load-balancing algorithm.
```

Configurations and load-balancing must be computed efficiently. Otherwise, the cost of these centralized tasks would overweight the distribution gains.