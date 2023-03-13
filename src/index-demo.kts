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
val concurrency = 10
val connectionPool = ConnectionPool("jdbc:mysql://$dbHost:3306/test", "root", "root", concurrency)
val benchmark = Benchmark(
    connectionPool = connectionPool,
    repeats = 1,
    cooldown = 5.seconds,
)

with(benchmark) {

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

    fun exactMatchSql() = "SELECT * FROM persons WHERE birthdate = '${randomDate()}' LIMIT 100000"

    fun greaterThanSql() = with(randomDate()) {
        "SELECT * FROM persons WHERE birthdate > '$this' LIMIT 100000"
    }

    fun smallRangeSql() = with(randomDate()) {
        "SELECT * FROM persons WHERE birthdate BETWEEN '$this' AND '${plusDays(10)}' LIMIT 100000"
    }

    fun largeRangeSql() = with(randomDate()) {
        "SELECT * FROM persons WHERE birthdate BETWEEN '$this' AND '${plusDays(1000)}' LIMIT 100000"
    }

    fun benchmarkSequence(duration: Duration, warmupDuration: Duration, concurrency: Int) {
        benchmarkSelects(warmupDuration, 1) { exactMatchSql() }
        benchmarkSelects(duration, concurrency) { exactMatchSql() }.also { println("Exact match: $it") }
        benchmarkSelects(duration, concurrency) { greaterThanSql() }.also { println("Greater then: $it") }
        benchmarkSelects(duration, concurrency) { smallRangeSql() }.also { println("Small range: $it") }
        benchmarkSelects(duration, concurrency) { largeRangeSql() }.also { println("Large range: $it") }
    }

    val duration = 1.minutes
    val warmupDuration = 10.seconds

    println("\nRunning benchmark: ${benchmark.repeats} repeats, ${benchmark.cooldown} cooldown between repeats")
    println("Each run: $duration with $concurrency threads and $warmupDuration warmup")

    println("\n 1. Without index")
    tryExecute("DROP INDEX persons_birthdate ON persons").also { time -> println("Index dropped in: $time") }
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("\n 2. With BTREE index")
    execute("CREATE INDEX persons_birthdate ON persons (birthdate) USING BTREE").also { time -> println("Index created in: $time") }
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("\n 3. With HASH index and no adaptive hash index")
    execute("DROP INDEX persons_birthdate ON persons")
    execute("SET GLOBAL innodb_adaptive_hash_index = OFF").also { time -> println("Option set in: $time") }
    execute("CREATE INDEX persons_birthdate ON persons (birthdate) USING HASH").also { time -> println("Index created in: $time") }
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("\n 4. With HASH index and with adaptive hash index")
    execute("SET GLOBAL innodb_adaptive_hash_index = ON").also { time -> println("Option set in: $time") }
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("Done!")
}
