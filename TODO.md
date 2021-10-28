# TODO
- [ ] Statistics: coefficient of variation
- [ ] Benchmark and store outputs


## Optional

- [ ] MaximalMatchEnumeration repeats outputs (see DistributionSpec.Query2)
- [ ] The load in MaximalMatchEnumeration is by number of matches in the maximal match and blueprint. We can do a better balance if we count the sum of sizes of each match in the maximal match.
- [ ] I don't really like the implementation based on node lists. 
`dcer.data.Match` could store a `List[List[Event]]` instead.
This would make grouping 0 cost and traversing is actually still linear with a custom iterator.