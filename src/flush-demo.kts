#!/usr/bin/env kscript
@file:Import("ConnectionPool.kt")
@file:Import("Docker.kt")
@file:Import("Benchmark.kt")
@file:Import("MysqlConfig.kt")

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

println("Starting MySQL Flush demo...")
val docker = Docker()
val dbHost = if (docker.isRunningInDocker()) "mysql" else "localhost"
val connectionPool = ConnectionPool("jdbc:mysql://$dbHost:3306/test", "root", "root", 20)
val benchmark = Benchmark(connectionPool)
val mysqlConfig = MysqlConfig("./config/mysql.cnf")

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

    println("Counting existing records...")
    val count = (querySingleValue("SELECT COUNT(*) FROM persons") as Long).toInt()
    println("Persons count is $count")

    /*
     * Running queries
     */

    fun insertSql() = "INSERT INTO persons (name, birthdate) VALUES " +
        (1..10).joinToString(", ") { "('${randomName()}', '${randomDate()}')" }

    fun benchmarkSequence(duration: Duration, warmupDuration: Duration, concurrency: Int) {
        benchmarkInserts(warmupDuration, 1, 10.0) { insertSql() }
        benchmarkInserts(duration, concurrency, 1000.0) { insertSql() }.also { result -> println("1000 rps: $result") }
        benchmarkInserts(duration, concurrency, 10000.0) { insertSql() }.also { result -> println("10 000 rps: $result") }
        benchmarkInserts(duration, concurrency, 100000.0) { insertSql() }.also { result -> println("100 000 rps: $result") }
        benchmarkInserts(duration, concurrency, 1000000.0) { insertSql() }.also { result -> println("1 000 000 rps: $result") }
        benchmarkInserts(duration, concurrency, null) { insertSql() }.also { result -> println("Unlimited rps: $result") }
    }

    val duration = 20.seconds // 1.minutes
    val warmupDuration = 10.seconds
    val concurrency = 20

    println("\nRunning benchmark for $duration with $concurrency threads and $warmupDuration warmup")
    tryExecute("DROP INDEX persons_birthdate ON persons").also { time -> println("Index dropped in: $time") }

    /*
     *
     */

    println("\n 1. With Option 0 - Once per second")
    mysqlConfig.set("innodb_flush_log_at_trx_commit", "0").save()
    docker.restartContainer("mysql")
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("\n 2. With Option 1 - At each transaction commit")
    mysqlConfig.set("innodb_flush_log_at_trx_commit", "1").save()
    docker.restartContainer("mysql")
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("\n 3. With Option 2 - Once per second, but logs at each transaction commit")
    mysqlConfig.set("innodb_flush_log_at_trx_commit", "2").save()
    docker.restartContainer("mysql")
    benchmarkSequence(duration, warmupDuration, concurrency)

    println("Done!")
}
