# Execution Time

## 1st October 2021

Query:

```
SELECT *
FROM S
WHERE (T as t1 ; H + as hs ; H as h1)
FILTER
    (t1[temp < 0] AND
     hs[hum < 60] AND
     h1[hum > 60])
```

Stream:

```
T;2021-09-27 0:0:0;-2;barcelona
H;2021-09-27 0:0:1;30;barcelona
H;2021-09-27 0:0:2;20;barcelona
H;2021-09-27 0:0:3;10;barcelona
H;2021-09-27 0:0:4;65;barcelona
T;2021-09-27 0:0:5;-5;barcelona
H;2021-09-27 0:0:6;10;barcelona
H;2021-09-27 0:0:7;70;barcelona
```

eventProcessingDuration: 10.millis

**Event results:**

```
Match (#events=3, complexity=Linear()) processed in 52 milliseconds
Match (#events=3, complexity=Quadratic()) processed in 238 milliseconds
Match (#events=3, complexity=Cubic()) processed in 786 milliseconds
```

A careful reader may appreciate that there is an **overhead** on 
processing time in the simulated delay. 
For example, 3 _linear_ events should take 3*10 milliseconds but they took 50 milliseconds
which is caused by the overhead of message-passing style of akka.

**Results:**

- Linear: 11711 milliseconds
- Quadratic: 12683 milliseconds
- Cubic: 14947 milliseconds

The results are alike because the data stream was very small
and the overhead of the distribution is shadowing the computational time costs and gains.