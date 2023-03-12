#!/usr/bin/env kscript
@file:Import("ConnectionPool.kt")
@file:Import("Docker.kt")
@file:Import("Benchmark.kt")

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

println("Starting MySQL Indexing demo...")
val docker = Docker()
val dbHost = if (docker.isRunningInDocker()) "mysql" else "localhost"
val connectionPool = ConnectionPool("jdbc:mysql://$dbHost:3306/test", "root", "root", 10)
val benchmark = Benchmark(connectionPool)

with(benchmark) {

    docker.waitForContainer("mysql", 60)

    /*
     * Preparing the database
     */

    println("Creating persons table if not exists...")
    execute(
        """
    CREATE TABLE IF NOT EXISTS persons (
        id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        name VARCHAR(255),
        birthdate DATE
    )
        """.trimIndent(),
    )

    val maxCount = 40_000_000
    val chunkSize = 1000

    println("Counting existing records...")
    val count = (querySingleValue("SELECT COUNT(*) FROM persons") as Long).toInt()

    when {
        count > maxCount -> {
            println("Deleting excessive ${count - maxCount} records...")
            execute("DELETE FROM persons LIMIT ${count - maxCount}")
        }

        count < maxCount -> {
            println("Inserting ${maxCount - count} records...")
            sequence { var i = count; while (i < maxCount) yield(i++) }
                .chunked(chunkSize)
                .forEach { chunk: List<Int> ->
                    execute("INSERT INTO persons (name, birthdate) VALUES " + chunk.joinToString(", ") { "('${randomName()}', '${randomDate()}')" })
                    if ((chunk.last() / chunkSize) % 100 == 0) println("Inserted ${chunk.last()} records")
                }
        }

        else -> println("Persons count is already $maxCount")
    }

    /*
     * Running queries
     */

    fun exactMatchSql() = "SELECT * FROM persons WHERE birthdate = '${randomDate()}' LIMIT 1000"

    fun smallRangeSql() = with(randomDate()) {
        "SELECT * FROM persons WHERE birthdate BETWEEN '$this' AND '${plusDays(10)}' LIMIT 1000"
    }

    fun largeRangeSql() = with(randomDate()) {
        "SELECT * FROM persons WHERE birthdate BETWEEN '$this' AND '${plusDays(1000)}' LIMIT 1000"
    }

    fun benchmarkSequence(duration: Duration, warmupDuration: Duration, concurrency: Int) {
        benchmarkSelects(warmupDuration, 1) { exactMatchSql() }
        benchmarkSelects(duration, concurrency) { exactMatchSql() }.also { result -> println("Exact match: $result") }
        benchmarkSelects(duration, concurrency) { smallRangeSql() }.also { result -> println("Small range: $result") }
        benchmarkSelects(duration, concurrency) { largeRangeSql() }.also { result -> println("Large range: $result") }
    }

    val duration = 1.minutes
    val warmupDuration = 10.seconds
    val concurrency = 10

    println("\nRunning benchmark for $duration with $concurrency threads and $warmupDuration warmup")

    println("\n 1. Without index")
    tryExecute("DROP INDEX persons_birthdate ON persons").also { time -> println("Index dropped in: $time") }
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("\n 2. With BTREE index")
    execute("CREATE INDEX persons_birthdate ON persons (birthdate) USING BTREE").also { time -> println("Index created in: $time") }
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("\n 3. With HASH index")
    execute("DROP INDEX persons_birthdate ON persons")
    execute("CREATE INDEX persons_birthdate ON persons (birthdate) USING HASH").also { time -> println("Index created in: $time") }
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("Done!")
}
