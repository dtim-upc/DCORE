# List of task

## Project

- [ ] MaximalMatchEnumeration repeated outputs
- [ ] Prepare benchmarks for UPC cluster
  - [ ] Prepare new script
- [ ] Load balancing: matches vs length (the problem right now is that enumeration does not take length into account)
  - [ ] Add execution time of MaximalMatch + Configuration
  - [ ] Execute a benchmark and store in CSV: #matches, length of matches, execution time
  - [ ] Plot this information to see if load balancing is affected by length of matches.

- [ ] (Optional) I don't really like the implementation based on node lists. `dcer.data.Match` could store a `List[List[Event]]` instead. This would make grouping 0 cost and traversing is actually still linear with a custom iterator.

## Document

- [ ] Write pseudo-code for Maximal Match Enumeration
- [ ] Proof soundness and completeness of Maximal Match Enumeration

## Readings & Related Work

- [ ] Read "COmplex event Recognition Engine"
- [ ] Read "A Formal Framework for Complex Event Processing"

## Future work

- [ ] Implement SOL queries.

**Proposal 1:**

```
1. Parsing SOL queries and splitting them in (a) and (b)
   - (a) output should be processable by CORE i.e. first-order queries.
   - (b) output should encode the information needed to process first-order queries' output with second-order predicates.
2. (a) should be given to CORE and CORE should output the corresponding first-order matches (if any).
3. Given the first-order matches and (b) we should be able to run second-order predicates on top of those matches.
```