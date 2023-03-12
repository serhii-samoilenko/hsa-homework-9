import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.MINUTES
import kotlin.math.round
import kotlin.random.Random

class Benchmark(private val connectionPool: ConnectionPool) {

    data class Result(
        val count: Long,
        val duration: Long,
    ) {
        private fun opsPerSecond() = round(1000.0 * count * 1000.0 / duration) / 1000.0
        override fun toString() = "$count ops in $duration ms - ${opsPerSecond()} ops/sec)"
    }

    fun benchmark(duration: Duration, concurrency: Int, taskSupplier: () -> Runnable, delay: Int = 0): Result {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + duration.toMillis()
        val executor = Executors.newFixedThreadPool(concurrency) as ThreadPoolExecutor

        while (System.currentTimeMillis() < endTime) {
            for (i in 0 until concurrency - executor.activeCount) {
                if (delay > 0) Thread.sleep(delay.toLong())
                executor.submit(taskSupplier())
            }
        }
        executor.awaitTermination(10, MINUTES)
        val actualEndTime = System.currentTimeMillis() - startTime
        return Result(executor.completedTaskCount, actualEndTime)
    }

    fun benchmarkSelections(duration: Duration, concurrency: Int, querySupplier: () -> String) =
        benchmark(duration, concurrency, { Runnable { query(querySupplier()) } })

    fun execute(sql: String): Long {
        val start = System.currentTimeMillis()
        connectionPool.connection().use { connection ->
            connection.createStatement().executeUpdate(sql)
        }
        val end = System.currentTimeMillis()
        return end - start
    }

    fun tryExecute(sql: String) = try {
        execute(sql)
    } catch (e: Exception) {
        println("Ignoring exception: ${e.message}")
        null
    }

    fun query(sql: String): List<Map<String, Any>> {
        connectionPool.connection().use { connection ->
            connection.createStatement().executeQuery(sql).use { rs ->
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
}

fun randomName(): String = UUID.randomUUID().toString()

fun randomDate(): LocalDate = LocalDate.now().minusDays(Random.nextLong(365 * 100))
