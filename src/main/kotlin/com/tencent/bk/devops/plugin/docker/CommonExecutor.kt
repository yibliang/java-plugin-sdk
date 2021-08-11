package com.tencent.bk.devops.plugin.docker

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.api.SdkEnv
import com.tencent.bk.devops.atom.pojo.Result
import com.tencent.bk.devops.plugin.utils.OkhttpUtils
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunLogRequest
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunLogResponse
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunRequest
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunResponse
import com.tencent.bk.devops.plugin.docker.pojo.common.DockerStatus
import com.tencent.bk.devops.plugin.utils.JsonUtil
import org.apache.tools.ant.types.Commandline
import org.slf4j.LoggerFactory

object CommonExecutor {

    private val logger = LoggerFactory.getLogger(CommonExecutor::class.java)

    fun execute(
        projectId: String,
        pipelineId: String,
        buildId: String,
        request: DockerRunRequest
    ): DockerRunResponse {
        // start to run
        val runParam = getRunParamJson(request)
        val dockerHostIP = System.getenv("docker_host_ip")
        val dockerHostPort = System.getenv("docker_host_port") ?: "80"
        val vmSeqId = SdkEnv.getVmSeqId()
        val dockerRunUrl = "http://$dockerHostIP:$dockerHostPort/api/docker/run/$projectId/$pipelineId/$vmSeqId/$buildId"
        logger.info("execute docker run url: $dockerRunUrl")
        val responseContent = OkhttpUtils.doPost(dockerRunUrl, runParam).use { it.body()!!.string() }
        logger.info("execute docker run response: $responseContent")

        val extraOptions = JsonUtil.to(responseContent, object : TypeReference<Result<Map<String, Any>>>() {}).data

        val extraOptionsResult = mutableMapOf<String, Any>(
            "containerId" to extraOptions["containerId"].toString(),
            "startTimeStamp" to extraOptions["startTimeStamp"].toString()
        )
        if (extraOptions["dockerRunPortBindings"] != null) {
            extraOptionsResult["dockerRunPortBindings"] = extraOptions["dockerRunPortBindings"]!!
        }

        return DockerRunResponse(extraOptions = extraOptionsResult)
    }

    fun getLogs(
        projectId: String,
        pipelineId: String,
        buildId: String,
        request: DockerRunLogRequest
    ): DockerRunLogResponse {
        val containerId = request.extraOptions.getValue("containerId")
        val startTimeStamp = request.extraOptions.getValue("startTimeStamp")
        val dockerHostIP = System.getenv("docker_host_ip")
        val dockerHostPort = System.getenv("docker_host_port") ?: "80"
        val vmSeqId = SdkEnv.getVmSeqId()
        val dockerGetLogUrl =
            "http://$dockerHostIP:$dockerHostPort/api/docker/runlog/$projectId/$pipelineId/$vmSeqId/$buildId/$containerId/$startTimeStamp"
        val logResponse = OkhttpUtils.doGet(dockerGetLogUrl).use { it.body()!!.string() }
        val logResult = JsonUtil.to(logResponse, object : TypeReference<Result<LogParam?>>() {}).data
            ?: return DockerRunLogResponse(
                status = DockerStatus.failure,
                message = "the log data is null with get http: $dockerGetLogUrl",
                extraOptions = request.extraOptions
            )

        return if (logResult.running != true) {
            if (logResult.exitCode == 0) {
                DockerRunLogResponse(
                    log = trimLogs(logResult.logs),
                    status = DockerStatus.success,
                    message = "the Docker Run Log is listed as follows:",
                    extraOptions = request.extraOptions
                )
            } else {
                DockerRunLogResponse(
                    log = trimLogs(logResult.logs),
                    status = DockerStatus.failure,
                    message = "the Docker Run Log is listed as follows:",
                    extraOptions = request.extraOptions
                )
            }
        } else {
            DockerRunLogResponse(
                log = logResult.logs,
                status = DockerStatus.running,
                message = "get log...",
                extraOptions = request.extraOptions.plus(mapOf(
                    "startTimeStamp" to (startTimeStamp.toLong() + request.timeGap / 1000).toString()
                ))
            )
        }

    }

    private fun getRunParamJson(param: DockerRunRequest): String {
        val runParam = with(param) {
            val cmdTmp = mutableListOf<String>()
            command.forEach {
                cmdTmp.add(it.removePrefix("\"").removeSuffix("\"").removePrefix("\'").removeSuffix("\'"))
            }
            val cmd = if (cmdTmp.size == 1) {
                Commandline.translateCommandline(cmdTmp.first()).toList()
            } else {
                cmdTmp
            }
            // get user pass param
            DockerRunParam(
                imageName = imageName,
                registryUser = param.dockerLoginUsername,
                registryPwd = param.dockerLoginPassword,
                command = cmd,
                env = envMap ?: mapOf(),
                poolNo = System.getenv("pool_no"),
                portList = portList
            )
        }

        logger.info("execute docker run image: $runParam")

        return JsonUtil.toJson(runParam)
    }

    private fun trimLogs(list: List<String>?): List<String>? {
        return list?.map {
            val split = it.split("\\s+".toRegex()).toTypedArray()
            val log = if (split.size >= 3) {
                it.substring(it.indexOf(split[2]))
            } else {
                it
            }
            log
        }
    }

    private data class DockerRunParam(
        val imageName: String?,
        val registryUser: String?,
        val registryPwd: String?,
        val command: List<String>,
        val env: Map<String, String>,
        val poolNo: String?,
        val portList: List<Int>?
    ) {
        override fun toString(): String {
            return "image name: $imageName, registry user: $registryUser, command: $command, env: $env, pool no: $poolNo, portList: $portList"
        }
    }

    data class LogParam(
        val exitCode: Int? = null,
        val logs: List<String>? = null,
        val running: Boolean? = null
    )
}