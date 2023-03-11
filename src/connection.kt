@file:DependsOn("mysql:mysql-connector-java:8.0.32")

import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

object Database {
    init {
        DriverManager.setLoginTimeout(60)
        println("Opening connection to MySQL...")
    }
}

val connection: Connection = DriverManager.getConnection("jdbc:mysql://mysql:3306/test", "root", "root")

fun benchmarkSelections(count: Int): Long {
    val start = System.currentTimeMillis()
    (1..count).forEach { _ ->
        query("SELECT * FROM persons WHERE birthdate = '${randomDate()}' LIMIT 1000")
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

fun tryExecute(sql: String) = try {
    execute(sql)
} catch (e: Exception) {
    println("Ignoring exception: ${e.message}")
    null
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
