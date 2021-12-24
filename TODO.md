# List of task


## Project

- [ ] Benchmark local
  - Output to a single file?

- [ ] Prepare benchmarks for UPC cluster
  - [ ] Prepare new script

- [ ] Stijn
    - Define an enumeration algorithm on complex events
    - Define a distributed enumeration algorithm
    - Proof that your enumeration algorithm fulfills the theorems.
    - Example https://upcommons.upc.edu/bitstream/handle/2117/346074/TKDE_CR.pdf

### Experiment 1: On the evaluation of non-unary predicates

- Different queries: A;B;C    A;B+;C    A+;B+;C
- Different predicates: linear, quadratic
- Different stream sizes
- Fix number of workers

### Experiment 2: On the constant-delay enumeration

- Different queries: A;B;C    A;B+;C    A+;B+;C
- Fix stream
- Different workers: 1,2,3,4,5,6,7,8
- Enumeration time for each process.

We are expecting to see a U shape. Since the time will decrease the more workers you put but 
there is a theoretical limit where the cost of getting into the starting position of the enumeration for just a few complex events will not compensate.

A use could be evaluating queries like (A;B;C) FILTER A.x = B.y and B.z = C.u
These queries may generate a polynomial number of complex events and evaluating the non-unary predicate would be costly in a sequential way.

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

- Introduccio al problema estil CORE.pdf
- Overview de l'area: explicar el sistemes de CEP: automata based, tree based, logic based
- Limitacions del related work: lent d'enumerar i problemes dels predicats
- Contributions (see CORE.pdf): conjunt d'estrategies per a distribuir l'enumeracio i predicats complexos.
- Outline of the work.

### Chapter 2. Related Work

### Chapter 3. Preliminaries

- Explain same concepts as CORE
- Explain notion of completeness and soundness per als algorismes d'enumeracio: example https://upcommons.upc.edu/bitstream/handle/2117/346074/TKDE_CR.pdf


### Chapter 4. ???

- Remove output-linear delay
- Explain Algorithm DTE.

### Chapter 5. ???

### Chapter 6. Experiments

### Chapter 7. Conclusions & Future work

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
