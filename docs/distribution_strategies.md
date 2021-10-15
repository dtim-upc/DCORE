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