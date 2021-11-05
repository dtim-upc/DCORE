# Repeated Matches on Maximal Matches Enumeration

The problem with respect to duplicates is inherent to the approach based on maximal matches, I believe. Let me clarify:

- Let Q be a CER query and let S be a stream. At some position j in the stream we want to enumerate Q(S,j), the set of all complex events found at instant j.  Because this set can be large, we wish  to distribute the enumeration.  
- The approach that you and Sergi propose essentially wishes to partition Q(S,j) in a number of partitions, lets call these P1, ..., Pk. The idea is to represent these partitions *symbolically* in some manner, and ship the symbolic representation to the workers so that they can then take care of the enumeration (in parallel). Importantly, we want to create the partitions without first enumerating Q(S,j)
- The proposed approach is to take the maximal matches (identified in the datastructure), and use the maximal matches together with their configurations to determine P1, ..., Pk. The problem is that, as your example shows, maximal matches + configurations are *not* disjoint. So, they *do* not provide the partition that we look for.
- One simple way of solving the issue is to expand the notion of a configuration so that under this expansion, P1, ..., Pk *do become* disjoint.
- One possible way of doing this is to consider the signature of the maximal match, .i.e., 
{{A1}, {B1 B2}, {C1 C2 C3}, {D1}} 
in your example, and identify the *last* "group" that corresponds to a Kleene-closure that has more than 1 element. In your example, this would be {C1 C2 C3}. Now define a configuration to consist of a subset size for each group *plus* a concrete subset of the desired size for the identified (last) group. E.g.,

{#A=1,#B=1,#C=1,#D=1} and C = C1
{#A=1,#B=1,#C=1,#D=1} and C = C2
{#A=1,#B=1,#C=1,#D=1} and C = C3
...
{#A=1,#B=1,#C=2,#D=1} and C = C1, C2
{#A=1,#B=1,#C=2,#D=1} and C = C1, C3
{#A=1,#B=1,#C=2,#D=1} and C = C2, C3
...

The workers are now asked to enumerate the matches compatible with their given configuration. In the example above, the workers assigned {#A=1,#B=1,#C=2,#D=1} and C = C1, C2 hence needs to enumerate matches with 1 A  event, 1 B event, 2 C events which must be {C1,C2}, and 1 D event.

This makes the matches produced by each worker disjoint.

Note that, instead of fixing C above we could equally well have fixed B, e.g.,
{#A=1,#B=1,#C=1,#D=1} and B = B1
{#A=1,#B=1,#C=1,#D=1} and B = B2
{#A=1,#B=2,#C=1,#D=1} and B = B1. B2
...

This also makes the enumerated sets disjoint. In general, fixing a group corresponding to a Kleene closure that has more than 1 element, but for which the number of elements is the smallest among groups would be a good heuristic to keep the number of configurations under control.
