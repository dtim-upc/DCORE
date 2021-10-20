# TODO

- [ ] Strategy: Maximal Matches Strategy
  - [ ] Replace 'SELECT *' by 'SELECT MAX *' in the query.
  - [x] Distribute maximal matches with configuration.
  - [x] Worker: given a maximal match and a configuration compute all matches.
  - [x] Check when binomial overflows
  - [ ] Test Blueprint.fromMaximalMatch

## Good to have

- [ ] I don't really like the implementation based on node lists. 
`dcer.data.Match` could store a `List[List[Event]]` instead.
This would make grouping 0 cost and traversing is actually still linear with a custom iterator.