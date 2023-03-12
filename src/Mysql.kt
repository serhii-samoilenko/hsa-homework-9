@file:DependsOn("mysql:mysql-connector-java:8.0.32")
@file:DependsOn("com.zaxxer:HikariCP:5.0.1")

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.DriverManager

class Mysql(host: String) {
    init {
        DriverManager.setLoginTimeout(60)
//        println("Opening connection to MySQL...")
    }

    val dataSource = DataSource("jdbc:mysql://$host:3306/test", "root", "root")

    fun connection() = dataSource.connection()

//    val connection: Connection = DriverManager.getConnection("jdbc:mysql://$host:3306/test", "root", "root")
}

class DataSource(
    jdbcUrl: String,
    username: String,
    password: String,
) {
    private val config = HikariConfig()
    private lateinit var ds: HikariDataSource

    init {
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        config.maximumPoolSize = 10
        ds = HikariDataSource(config)
    }

    fun connection() = ds.getConnection()
}
