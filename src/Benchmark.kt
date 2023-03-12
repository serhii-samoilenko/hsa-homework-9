import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.MINUTES
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class Benchmark(private val connectionPool: ConnectionPool) {

    data class Result(
        val count: Long,
        val duration: Duration,
    ) {
        private fun opsPerSecond() = round(1000.0 * count * 1000.0 / duration.inWholeMilliseconds) / 1000.0
        override fun toString() = "$count ops in $duration - ${opsPerSecond()} ops/sec"
    }

    fun benchmark(duration: Duration, concurrency: Int, taskSupplier: () -> Runnable, rps: Double? = null): Result {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + duration.inWholeMilliseconds
        val executor = Executors.newFixedThreadPool(concurrency) as ThreadPoolExecutor
        val rateLimiter = rps?.let { RateLimiter(it) }

        while (System.currentTimeMillis() < endTime) {
            val totalCount = executor.activeCount + executor.queue.size
            val capacity = concurrency - totalCount
            for (i in 0 until capacity) {
                rateLimiter?.limit()
                executor.submit(taskSupplier())
            }
        }
        executor.shutdown()
        executor.awaitTermination(10, MINUTES)
        val actuaTime = System.currentTimeMillis() - startTime
        return Result(executor.completedTaskCount, actuaTime.milliseconds)
    }

    fun benchmarkSelects(duration: Duration, concurrency: Int, querySupplier: () -> String) =
        benchmark(duration, concurrency, { Runnable { query(querySupplier()) } })

    fun benchmarkInserts(duration: Duration, concurrency: Int, rps: Double?, querySupplier: () -> String) =
        benchmark(duration, concurrency, { Runnable { execute(querySupplier()) } }, rps)

    fun execute(sql: String): Duration {
        val start = System.currentTimeMillis()
        connectionPool.connection().use { connection ->
            connection.createStatement().executeUpdate(sql)
        }
        val end = System.currentTimeMillis()
        return (end - start).milliseconds
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

class RateLimiter(private val rps: Double) {
    private var lastCallTime: Long = 0
    fun limit() {
        val currentTime = System.nanoTime()
        val timeSinceLastCall = currentTime - lastCallTime
        val timeToWait = 1 / rps * 1000000000 // convert rps to nanoseconds
        val timeLeftToWait = (timeToWait - timeSinceLastCall).toLong()
        if (timeLeftToWait > 0) {
            val waitMs = timeLeftToWait / 1000000
            val waitNs = (timeLeftToWait % 1000000).toInt()
            Thread.sleep(waitMs, waitNs)
        }
        lastCallTime = System.nanoTime()
    }
}

fun randomName(): String = UUID.randomUUID().toString()

fun randomDate(): LocalDate = LocalDate.now().minusDays(Random.nextLong(365 * 100))
