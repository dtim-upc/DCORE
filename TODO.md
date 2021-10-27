# TODO

- [ ] Strategy: Maximal Matches Strategy
  - [x] Distribute maximal matches with configuration.
  - [x] Worker: given a maximal match and a configuration compute all matches.
  - [x] Check when binomial overflows
  - [x] BlueprintSpec (more comments there)
  - [x] Replace 'SELECT *' by 'SELECT MAX *' in the query.
  - [x] Worker MaximalMatch fails because Stop happens afterwards before processing the matches.
    - [x] Refactor EngineManager
    - [x] Refactor Worker: Stop means stop
  - [x] Run DistributionSpec and fix any error
  - [ ] Add test case to DistributionSpec that generates multiple maximal matches
  - [ ] Test benchmark 
- [ ] Statistics: coefficient of variation
- [ ] Benchmark everything


## Good to have

- [ ] The load in MaximalMatchEnumeration is by number of matches in the maximal match and blueprint. We can do a better balance if we count the sum of sizes of each match in the maximal match.
- [ ] I don't really like the implementation based on node lists. 
`dcer.data.Match` could store a `List[List[Event]]` instead.
This would make grouping 0 cost and traversing is actually still linear with a custom iterator.