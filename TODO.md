# List of task

## Project

- [ ] Prepare benchmarks for UPC cluster
  - [ ] Prepare new script
  
- [ ] dCORE
    - [ ] Create a new branch of CORE.
    - [ ] Add `paths` to CORE.
    - [ ] Take `paths` into account while enumerating.
    - [ ] Prepare new project `dCORE`

- [ ] (optional) Load balancing: matches vs length (the problem right now is that enumeration does not take length into account)
  - [ ] Add execution time of MaximalMatch + Configuration
  - [ ] Execute a benchmark and store in CSV: #matches, length of matches, execution time
  - [ ] Plot this information to see if load balancing is affected by length of matches.

- [ ] (Optional) I don't really like the implementation based on node lists. `dcer.data.Match` could store a `List[List[Event]]` instead. This would make grouping 0 cost and traversing is actually still linear with a custom iterator.

## Document

- [ ] Set the latex template https://github.com/latextemplates/LNCS

### Chapter 1. Introduction

### Chapter 2. Preliminaries

- [ ] Reestate everything explained in CORE paper.
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
