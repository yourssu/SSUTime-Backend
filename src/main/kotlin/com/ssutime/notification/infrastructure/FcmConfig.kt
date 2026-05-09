package com.ssutime.notification.infrastructure

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.io.FileInputStream

@Configuration
class FcmConfig {
    @Bean
    fun firebaseApp(
        @Value("\${fcm.credentials-path:}") credentialsPath: String,
        @Value("\${fcm.credentials-json:}") credentialsJson: String,
    ): FirebaseApp? {
        if (credentialsPath.isBlank() && credentialsJson.isBlank()) return null
        if (FirebaseApp.getApps().isNotEmpty()) return FirebaseApp.getInstance()
        val credentials = if (credentialsJson.isNotBlank()) {
            GoogleCredentials.fromStream(ByteArrayInputStream(credentialsJson.toByteArray()))
        } else {
            GoogleCredentials.fromStream(FileInputStream(credentialsPath))
        }
        val options = FirebaseOptions.builder().setCredentials(credentials).build()
        return FirebaseApp.initializeApp(options)
    }
}
