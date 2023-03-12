#!/usr/bin/env kscript
@file:Import("connection.kt")

println("Starting MySQL Flush demo...")

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

val insertsCount = 10_000
val chunkSize = 1000

println("Counting existing records...")




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

val queryCount = 20
println("\nRunning $queryCount queries per test")

println("\n 1. Without index")
tryExecute("DROP INDEX persons_birthdate ON persons")
benchmarkSelections(2) // Warmup
benchmarkSelections(queryCount).also { time -> println("Benchmark time: $time ms") }

println("\n 2. With BTREE index")
execute("CREATE INDEX persons_birthdate ON persons (birthdate) USING BTREE").also { time -> println("Index created in: $time ms") }
benchmarkSelections(20) // Warmup
benchmarkSelections(queryCount).also { time -> println("Benchmark time: $time ms") }

println("\n 3. With HASH index")
execute("DROP INDEX persons_birthdate ON persons")
execute("CREATE INDEX persons_birthdate ON persons (birthdate) USING HASH").also { time -> println("Index created in: $time ms") }
benchmarkSelections(20) // Warmup
benchmarkSelections(queryCount).also { time -> println("Benchmark time: $time ms") }

println("Done!")

// Close the connection
connection.close()
println("Connection closed")
