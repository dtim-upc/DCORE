# List of task

## Project

- getNumberOfMatches doesn't return the size

- [ ] Distributed second order predicates in CORE2
  - [ ] Implementation
  - [ ] Benchmark
    - [ ] Local
    - [ ] Cluster

- [ ] Is there a difference between enumerate(0,1M) and enumerate(1M, 2M) ?
- On multiple queries since may affect the cost.
- A;B+;C (or A+;B+;C) vs A;B;C (this is bounded and should be better)   Benchmark: AAAA..BBBB..C  = n^2 number of CE
- This A;B;C will affect if the second point makes sense since we want to enumerate and evaluate second-order filter on small workloads.
- [ ] A;B;C FILTER A.x = B.y and B.z = C.u 
- Validate this binary predicate distributed.
- There is polynomial number of complex events.

- [ ] Prepare benchmarks for UPC cluster
  - [ ] Prepare new script

### Optional

This may not be needed, just consider if it is worth doing.

- [ ] Load balancing: matches vs length (the problem right now is that enumeration does not take length into account)
  - [ ] Add execution time of MaximalMatch + Configuration
  - [ ] Execute a benchmark and store in CSV: #matches, length of matches, execution time
  - [ ] Plot this information to see if load balancing is affected by length of matches.

- [ ] I don't really like the implementation based on node lists. `dcer.core.data.Match` could store a `List[List[Event]]` instead. This would make grouping 0 cost and traversing is actually still linear with a custom iterator.

## Document

- [ ] Set the latex template https://github.com/latextemplates/LNCS

### Chapter 1. Introduction

### Chapter 2. Preliminaries

- [ ] Restate everything explained in CORE paper.
- [ ] Formalize the definition of match and maximal match using complex event + S[i] + t_i.

### Chapter 3. Related Work

### Chapter 4. ??? (Theory)

- [ ] Write pseudo-code for Maximal Match Enumeration
- [ ] Proof soundness and completeness of Maximal Match Enumeration

- [ ] Remove output-linear delay
- [ ] Explain Algorithm DTE.

### Chapter 5. Implementation

### Chapter 6. Experiments

### Chapter 7. Conclusions & Future work

## Readings & Related Work

- [ ] Read "A Formal Framework for Complex Event Recognition"

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
