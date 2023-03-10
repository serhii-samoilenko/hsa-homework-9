#!/usr/bin/env kscript
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3")
@file:DependsOn("mysql:mysql-connector-java:8.0.32")

import java.sql.Connection
import java.sql.DriverManager

println("Starting MySQL demo...")
val connection: Connection = DriverManager.getConnection("jdbc:mysql://mysql:3306/test", "root", "root")



// Create a Persons table
val statement = connection.createStatement()
val sql = """
            CREATE TABLE IF NOT EXISTS persons (
                id INT PRIMARY KEY,
                name VARCHAR(255),
                birthdate DATE
            )
        """
statement.executeUpdate(sql)

// Insert a record into the Persons table
val insertSql = "INSERT INTO persons (id, name, birthdate) VALUES (1, 'John Doe', '2021-01-01')"
statement.executeUpdate(insertSql)

// Close the connection
connection.close()


