# List of task

- [ ] MaximalMatchEnumeration repeated outputs
  - [ ] Prepare some complex examples and try to find a relationship between the repeated elements.
  - [ ] Write an email to Stijn with the examples and ask him if he knows this problem.
  - [ ] The idea would be to fine a mathematical definition of the problem (see set theory). Then, we can apply a theorem to avoid duplication or assume it is not tractable.

- [ ] Prepare benchmarks for UPC cluster
  - [ ] Prepare new script

- [ ] Load balancing: matches vs length (the problem right now is that enumeration does not take length into account)
  - [ ] Add execution time of MaximalMatch + Configuration
  - [ ] Execute a benchmark and store in CSV: #matches, length of matches, execution time
  - [ ] Plot this information to see if load balancing is affected by length of matches.


## Optional

- [ ] I don't really like the implementation based on node lists. 
`dcer.data.Match` could store a `List[List[Event]]` instead.
This would make grouping 0 cost and traversing is actually still linear with a custom iterator.
