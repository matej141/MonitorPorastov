package com.skeagis.monitorporastov.connection

import java.net.InetAddress

object InternetAvailability {
    private const val DEFAULT_TEST_URL = "google.com"

    fun check(): Boolean {
        return try {
            val ipAddress: InetAddress =
                InetAddress.getByName(DEFAULT_TEST_URL)
            !ipAddress.equals("")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}