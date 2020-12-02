package com.tencent.bk.devops.plugin.docker.pojo.job.request

import com.fasterxml.jackson.annotation.JsonInclude

data class JobParam(
    @JsonInclude(JsonInclude.Include.NON_NULL)
    var env: Map<String, String>? = null,
    val command: List<String>? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    var nfsVolume: List<NfsVolume>? = null,
    var workDir: String? = "/data/landun/workspace"
) {
    data class NfsVolume(
        val server: String? = null,
        val path: String? = null,
        val mountPath: String? = null
    )
}