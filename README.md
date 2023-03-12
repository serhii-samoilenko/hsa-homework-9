
## Results:

```
demo  | [kscript] Resolving mysql:mysql-connector-java:8.0.32...
demo  | Starting MySQL demo...
demo  | Creating persons table if not exists...
demo  | Counting existing records...
demo  | Persons count is already 40000000
demo  | 
demo  | Running 20 queries per test
demo  | 
demo  |  1. Without index
demo  | Benchmark time: 436443 ms
demo  | 
demo  |  2. With BTREE index
demo  | Index created in: 135365 ms
demo  | Benchmark time: 558 ms
demo  | 
demo  |  3. With HASH index
demo  | Index created in: 157744 ms
demo  | Benchmark time: 698 ms
demo  | Done!
demo  | Connection closed
```
