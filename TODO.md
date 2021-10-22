# TODO

- [ ] Strategy: Maximal Matches Strategy
  - [x] Distribute maximal matches with configuration.
  - [x] Worker: given a maximal match and a configuration compute all matches.
  - [x] Check when binomial overflows
  - [ ] BlueprintSpec (more comments there)
  - [ ] Replace 'SELECT *' by 'SELECT MAX *' in the query.
  - [ ] Run DistributionSpec and fix any error
  - [ ] Test benchmark 
- [ ] Statistics: coefficient of variation
- [ ] Benchmark everything

## Good to have

- [ ] I don't really like the implementation based on node lists. 
`dcer.data.Match` could store a `List[List[Event]]` instead.
This would make grouping 0 cost and traversing is actually still linear with a custom iterator.