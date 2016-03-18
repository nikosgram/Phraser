package io.github.nikosgram13.conduit.interfaces

import java.net.URL

interface DownloadHandler {
    fun execute(url: URL): String
}
