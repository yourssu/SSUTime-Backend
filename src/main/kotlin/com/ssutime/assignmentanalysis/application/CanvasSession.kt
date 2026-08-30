package com.ssutime.assignmentanalysis.application

import java.net.http.HttpClient

data class CanvasSession(
    internal val httpClient: HttpClient,
)
