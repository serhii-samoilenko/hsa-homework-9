@file:DependsOn("com.google.guava:guava:31.1-jre")

import com.google.common.util.concurrent.RateLimiter
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.MINUTES
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Suppress("UnstableApiUsage")
class Benchmark(
    private val connectionPool: ConnectionPool,
    val repeats: Int = 1,
    val cooldown: Duration = Duration.ZERO,
) {

    data class Result(
        val count: Long,
        val duration: Duration,
    ) {
        private fun opsPerSecond() = round(1000.0 * count * 1000.0 / duration.inWholeMilliseconds) / 1000.0
        override fun toString() = "$count ops in $duration - ${opsPerSecond()} ops/sec"
    }

    private fun benchmark(
        duration: Duration,
        concurrency: Int,
        taskSupplier: () -> Runnable,
        rps: Double? = null,
    ): Result {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + duration.inWholeMilliseconds
        val executor = Executors.newFixedThreadPool(concurrency) as ThreadPoolExecutor
        val rateLimiter = rps?.let { RateLimiter.create(it) }

        while (System.currentTimeMillis() < endTime) {
            val totalCount = executor.activeCount + executor.queue.size
            val capacity = concurrency - totalCount - 1
            for (i in 0 until capacity) {
                rateLimiter?.acquire()
                executor.submit(taskSupplier())
            }
        }
        executor.shutdown()
        executor.awaitTermination(10, MINUTES)
        val actuaTime = System.currentTimeMillis() - startTime
        return Result(executor.completedTaskCount, actuaTime.milliseconds)
    }

    fun benchmarkSelects(duration: Duration, concurrency: Int, querySupplier: () -> String): List<Result> =
        (1..repeats).map {
            val result = benchmark(duration, concurrency, { Runnable { query(querySupplier()) } })
            Thread.sleep(cooldown.inWholeMilliseconds)
            result
        }

    fun benchmarkInserts(
        duration: Duration,
        concurrency: Int,
        rps: Double?,
        querySupplier: () -> String,
    ): List<Result> =
        (1..repeats).map {
            val result = benchmark(duration, concurrency, { Runnable { execute(querySupplier()) } }, rps)
            Thread.sleep(cooldown.inWholeMilliseconds)
            result
        }

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

    fun query(sql: String) {
        connectionPool.connection().use { connection ->
            connection.createStatement(
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY,
            ).apply { fetchSize = 2000 }
                .executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        // do nothing
                    }
                    rs.close()
                }
        }
    }

    fun queryData(sql: String): List<Map<String, Any>> {
        connectionPool.connection().use { connection ->
            connection.createStatement().executeQuery(sql).use { rs ->
                val columns = (1..rs.metaData.columnCount).map { rs.metaData.getColumnName(it) }
                val rows = mutableListOf<Map<String, Any>>()
                while (rs.next()) {
                    rows.add(columns.associateWith { rs.getObject(it) })
                }
                rs.close()
                return rows
            }
        }
    }

    fun querySingleValue(sql: String): Any = queryData(sql).first().values.first()
}

fun randomName(): String = UUID.randomUUID().toString()

fun randomDate(): LocalDate = LocalDate.now().minusDays(Random.nextLong(365 * 100))
