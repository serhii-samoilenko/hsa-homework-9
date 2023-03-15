# Highload Software Architecture 8 Lesson 9 Homework

InnoDB Indexes

## Project structure

The project is based on Kotlin scripting. There are two scripts in the [src](src) folder:

* [index-demo.kts](src/index-demo.kts) - runs the benchmark of the `SELECT` command with and without indexes
* [flush-demo.kts](src/flush-demo.kts) - runs the benchmark of the `INSERT` operations with
  different `innodb_flush_log_at_trx_commit` settings

## How to run

### Using [docker-compose](docker-compose.yaml)

1. Run `docker-compose up mysql -d` to start MySQL server in the background
2. Run `docker-compose up index-demo` to run the first benchmark
3. Run `docker-compose up flush-demo` to run the second benchmark

### Using [kscript](https://github.com/kscripting/kscript) locally

1. Install `ksript` locally:
    ```bash
    curl -s "https://get.sdkman.io" | bash && \
    source "$HOME/.sdkman/bin/sdkman-init.sh" && \
    sdk install kotlin && \
    sdk install kscript
    ```
2. Run `kscript src/index-demo.kts` to run the first benchmark
3. Run `kscript src/flush-demo.kts` to run the second benchmark

## Results

### Indexes benchmark

Examples of queries used in the benchmarks:

1. Exact match: `SELECT * FROM persons WHERE birthdate = '2000-06-06' LIMIT 100000`
2. Greater then: `SELECT * FROM persons WHERE birthdate > '2000-06-06' LIMIT 100000`
3. Small range: `SELECT * FROM persons WHERE birthdate BETWEEN '2000-06-06' AND '2000-06-10' LIMIT 100000`
4. Large range: `SELECT * FROM persons WHERE birthdate BETWEEN '2000-06-06' AND '2005-06-06' LIMIT 100000`

Results for 10 concurrent connections, ~1 minute of benchmarking

| Select kind / ops/sec | **No index** | **BTREE index** | **HASH index (non-adaptive)** | **HASH index (adaptive)** |
|:----------------------|:-------------|:----------------|:------------------------------|:--------------------------|
| Exact match           | 0.150        | 89.79           | 100.821                       | **109.932**               |
| Greater then          | **3.110**    | 1.122           | 0.31                          | 1.131                     |
| Small range           | 0.080        | 7.871           | 7.796                         | **8.837**                 |
| Large range           | 0.472        | 1.233           | 1.187                         | 1.106                     |

#### Conclusion

In general case, index should improve the performance of all kinds of queries, and BTREE index should work better in range queries.

But in my particular setup, the HASH index with `innodb_adaptive_hash_index` option enabled outperformed the BTREE index for exact match queries and small range queries, and was equal to the BTREE index for GREATER THEN queries.

Most interesting is that the GREATER THEN query worked best with no index at all.

### InnoDB flush types benchmark

#### First run

| Option value / ops/sec             | **100 RPS** | **1K RPS** | **10K RPS** | **Unlimited** |
|:-----------------------------------|:------------|:-----------|:------------|:--------------|
| 0 - Once per second                | 100.03      | 999.7      | 1617.465    | 1616.184      |
| 1 - Each transaction commit        | 100.025     | 999.65     | 1355.561    | 1242.877      |
| 2 - Once per second, log on commit | 100.025     | 999.7      | 1574.956    | 1716.278      |

#### Second run

| Option value / ops/sec             | **1.2K RPS** | **1.5K RPS** | **2K RPS** | **Unlimited** |
|:-----------------------------------|:-------------|:-------------|:-----------|:--------------|
| 0 - Once per second                | 1200.13      | 1500.6       | 1999.4     | 2032.38       |
| 1 - Each transaction commit        | 1200.0       | 1224.65      | 1438.2     | 1515.24       |
| 2 - Once per second, log on commit | 1200.15      | 1500.67      | 1880.19    | 1893.28       |

#### Conclusion

As expected, the flush on each transaction commit showed the lowest performance. Other two options showed almost the same performance in my setup, but the second one (flush once per second, but log on commit) showed better performance in the second run.
