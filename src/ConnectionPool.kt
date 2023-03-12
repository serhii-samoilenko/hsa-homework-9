@file:DependsOn("mysql:mysql-connector-java:8.0.32")
@file:DependsOn("com.zaxxer:HikariCP:5.0.1")

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

class ConnectionPool(
    jdbcUrl: String,
    username: String,
    password: String,
    poolSize: Int
) {
    private val ds: HikariDataSource

    init {
        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        config.maximumPoolSize = poolSize
        ds = HikariDataSource(config)
    }

    fun connection() = ds.getConnection()
}
