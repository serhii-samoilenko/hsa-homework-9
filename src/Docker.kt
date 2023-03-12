@file:DependsOn("com.github.docker-java:docker-java-core:3.3.0")
@file:DependsOn("com.github.docker-java:docker-java-transport-zerodep:3.3.0")

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import java.io.File

class Docker {
    private val config: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost("unix:///var/run/docker.sock")
        .withDockerTlsVerify(false)
        .build()
    private val httpClient = ZerodepDockerHttpClient.Builder().dockerHost(config.dockerHost).build()
    private val dockerClient = DockerClientImpl.getInstance(config, httpClient)

    fun isRunningInDocker() = File("/.dockerenv").exists()

    fun restartContainer(containerName: String) {
        println("Restarting $containerName...")
        dockerClient.restartContainerCmd(containerName).exec()
        print("Waiting for $containerName to become healthy...")
        while (true) {
            if (waitForContainer(containerName)) {
                println()
                return
            } else {
                print('.')
                Thread.sleep(1000)
            }
        }
    }

    private fun waitForContainer(containerName: String): Boolean {
        val containerResponse = dockerClient.inspectContainerCmd(containerName).exec()
        if (containerResponse.state.health != null) {
            containerResponse.state.health?.let { health ->
                if (health.status == "healthy") {
                    return true
                }
            }
        } else {
            containerResponse.state.status?.let { status ->
                if (status == "running") {
                    return true
                }
            }
        }
        return false
    }
}
