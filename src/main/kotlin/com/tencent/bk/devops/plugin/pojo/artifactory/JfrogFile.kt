package com.tencent.bk.devops.plugin.pojo.artifactory

import com.fasterxml.jackson.annotation.JsonProperty
import lombok.Data

@Data
data class JfrogFile(
    val uri: String = "",
    val size: Long = 0,
    val lastModified: String = "",
    val folder: Boolean = false,
    @JsonProperty(required = false)
    val sha1: String = ""
)