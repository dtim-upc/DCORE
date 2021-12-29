# List of task

## Project

- [ ] Stijn
    - Define an enumeration algorithm on complex events
    - Define a distributed enumeration algorithm
    - Proof that your enumeration algorithm fulfills the theorems.
    - Example https://upcommons.upc.edu/bitstream/handle/2117/346074/TKDE_CR.pdf

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
