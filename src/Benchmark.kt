@file:DependsOn("mysql:mysql-connector-java:8.0.32")
@file:DependsOn("com.zaxxer:HikariCP:5.0.1")

import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

class Benchmark(private val mysql: Mysql) {

    fun benchmarkSelections(count: Int, querySupplier: () -> String): Long {
        val start = System.currentTimeMillis()
        (1..count).forEach { _ -> query(querySupplier()) }
        val end = System.currentTimeMillis()
        return end - start
    }

    fun execute(sql: String): Long {
        val start = System.currentTimeMillis()
        mysql.connection().use { connection ->
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
        mysql.connection().use { connection ->
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
