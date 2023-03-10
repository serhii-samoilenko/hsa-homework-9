
## Results:

```
docker-compose up demo
[+] Running 2/0
 ⠿ Container mysql  Running                                                                                                                                                                    0.0s
 ⠿ Container demo   Created                                                                                                                                                                    0.0s
Attaching to demo
demo  | [kscript] Resolving mysql:mysql-connector-java:8.0.32...
demo  | Starting MySQL demo...
demo  | Opening connection to MySQL...
demo  | Creating persons table if not exists...
demo  | Counting existing records...
demo  | Persons count is already 40000000
demo  | 
demo  | Running queries with parameters: queryCount=12, concurrency=4
demo  | 
demo  |  1. Without index
demo  | Benchmark time: 235523 ms
demo  | 
demo  |  2. With BTREE index
demo  | Index created in: 131505 ms
demo  | Benchmark time: 293 ms
demo  | 
demo  |  3. With HASH index
demo  | Index created in: 184619 ms
demo  | Benchmark time: 1036 ms
demo  | Done!
demo  | Connection closed
demo exited with code 0
```
