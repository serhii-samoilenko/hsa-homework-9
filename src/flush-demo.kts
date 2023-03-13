#!/usr/bin/env kscript
@file:Import("ConnectionPool.kt")
@file:Import("Docker.kt")
@file:Import("Benchmark.kt")

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

println("Starting MySQL Flush demo...")
val docker = Docker()
val dbHost = if (docker.isRunningInDocker()) "mysql" else "localhost"
val connectionPool = ConnectionPool("jdbc:mysql://$dbHost:3306/test", "root", "root", 20)
val benchmark = Benchmark(
    connectionPool = connectionPool,
    repeats = 3,
    cooldown = 20.seconds,
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

    println("Counting existing records...")
    val count = (querySingleValue("SELECT COUNT(*) FROM persons") as Long).toInt()
    println("Persons count is $count")

    /*
     * Running queries
     */

    fun insertSql() = "INSERT INTO persons (name, birthdate) VALUES " +
        (1..10).joinToString(", ") { "('${randomName()}', '${randomDate()}')" }

    fun benchmarkSequence(duration: Duration, concurrency: Int) {
        benchmarkInserts(duration, concurrency, 1200.0) { insertSql() }.also { println("1200 rps: $it") }
        benchmarkInserts(duration, concurrency, 1500.0) { insertSql() }.also { println("1500 rps: $it") }
        benchmarkInserts(duration, concurrency, 2000.0) { insertSql() }.also { println("2000 rps: $it") }
        benchmarkInserts(duration, concurrency, null) { insertSql() }.also { println("Unlimited rps: $it") }
    }

    val duration = 20.seconds
    val concurrency = 20

    println("\nRunning benchmark: ${benchmark.repeats} repeats, ${benchmark.cooldown} cooldown between repeats")
    println("Each run: $duration with $concurrency threads")
    tryExecute("DROP INDEX persons_birthdate ON persons").also { time -> println("Index dropped in: $time") }

    println("\n 1. With Option 0 - Once per second")
    execute("SET GLOBAL innodb_flush_log_at_trx_commit = 0").also { time -> println("Option set in: $time") }
    benchmarkSequence(duration, concurrency)

    println("\n 2. With Option 1 - At each transaction commit")
    execute("SET GLOBAL innodb_flush_log_at_trx_commit = 1").also { time -> println("Option set in: $time") }
    benchmarkSequence(duration, concurrency)

    println("\n 3. With Option 2 - Once per second, but logs at each transaction commit")
    execute("SET GLOBAL innodb_flush_log_at_trx_commit = 2").also { time -> println("Option set in: $time") }
    benchmarkSequence(duration, concurrency)

    println("Done!")
}
