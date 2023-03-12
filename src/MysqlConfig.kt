import java.io.File

class MysqlConfig(location: String) {
    private val file = File(location)
    private val properties = file.readLines()
        .filter { it.contains('=') }
        .map { it.split("=") }
        .associate { it[0].trim() to it[1].trim() }
        .toMutableMap()

    fun get(key: String) = properties[key]

    fun set(key: String, value: String): MysqlConfig {
        properties[key] = value
        return this
    }

    fun save() {
        file.writeText(
            "[mysqld]\n" + properties.map { "${it.key} = ${it.value}" }.joinToString("\n"),
        )
    }
}
