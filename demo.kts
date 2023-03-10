#!/usr/bin/env kscript
@file:DependsOn("mysql:mysql-connector-java:8.0.32")

import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

println("Starting MySQL demo...")

DriverManager.setLoginTimeout(60)
println("Opening connection to MySQL...")
val connection: Connection = DriverManager.getConnection("jdbc:mysql://mysql:3306/test", "root", "root")
// connection.autoCommit = false

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

val queryCount = 12
val concurrency = 4
println("\nRunning queries with parameters: queryCount=$queryCount, concurrency=$concurrency")

println("\n 1. Without index")
try {
    execute("DROP INDEX persons_birthdate ON persons")
} catch (_: Exception) {
}
benchmarkSelections(queryCount, concurrency).also { time -> println("Benchmark time: $time ms") }

println("\n 2. With BTREE index")
execute("CREATE INDEX persons_birthdate ON persons (birthdate) USING BTREE").also { time -> println("Index created in: $time ms") }
benchmarkSelections(queryCount, concurrency).also { time -> println("Benchmark time: $time ms") }

println("\n 3. With HASH index")
execute("DROP INDEX persons_birthdate ON persons")
execute("CREATE INDEX persons_birthdate ON persons (birthdate) USING HASH").also { time -> println("Index created in: $time ms") }
benchmarkSelections(queryCount, concurrency).also { time -> println("Benchmark time: $time ms") }

println("Done!")

// Close the connection
connection.close()
println("Connection closed")

/*
 * Helper functions
 */

fun benchmarkSelections(count: Int, concurrency: Int): Long {
    val start = System.currentTimeMillis()
    (1..count).chunked(concurrency).forEach { chunk: List<Int> ->
        chunk.parallelStream().forEach {
            query("SELECT * FROM persons WHERE birthdate = '${randomDate()}' LIMIT 1000")
        }
    }
    val end = System.currentTimeMillis()
    return end - start
}

fun execute(sql: String): Long {
    val start = System.currentTimeMillis()
    connection.createStatement().use { statement ->
        statement.executeUpdate(sql)
    }
    val end = System.currentTimeMillis()
    return end - start
}

fun query(sql: String): List<Map<String, Any>> {
    connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rs ->
            val columns = (1..rs.metaData.columnCount).map { rs.metaData.getColumnName(it) }
            val rows = mutableListOf<Map<String, Any>>()
            while (rs.next()) {
                rows.add(columns.associateWith { rs.getObject(it) })
            }
            return rows
        }
    }
}

fun querySingle(sql: String): Map<String, Any> = query(sql).first()

fun querySingleValue(sql: String): Any = querySingle(sql).values.first()

fun randomName(): String = UUID.randomUUID().toString()

fun randomDate(): String = LocalDate.now().minusDays(Random.nextLong(365 * 100)).toString()
