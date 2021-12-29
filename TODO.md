# List of task

## Project

- [ ] Stijn
    - Define an enumeration algorithm on complex events
    - Define a distributed enumeration algorithm
    - Proof that your enumeration algorithm fulfills the theorems.
    - Example https://upcommons.upc.edu/bitstream/handle/2117/346074/TKDE_CR.pdf

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
