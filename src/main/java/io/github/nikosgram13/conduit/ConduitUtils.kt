package io.github.nikosgram13.conduit

import io.github.nikosgram13.conduit.interfaces.ClassableHandler
import io.github.nikosgram13.conduit.interfaces.DownloadHandler
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.Charset
import java.util.*

object ConduitUtils {
    private val map = HashMap<Class<*>, ClassableHandler<*>>()
    private var handler: DownloadHandler = UrlDownloadHandler();

    fun appendHandler(tClass: Class<*>, handler: ClassableHandler<*>) {
        map.put(tClass, handler)
    }

    fun changeHandler(handler: DownloadHandler) {
        ConduitUtils.handler = handler
    }

    fun <T> query(path: String, query: Map<String, String>, tClass: Class<T>): T {
        if (map.containsKey(tClass)) {
            return (map[tClass] as ClassableHandler<T>).execute(query(path, query))
        }
        throw RuntimeException("ClassableHandler not founded!")
    }

    fun query(path: String, query: Map<String, String>): String {
        try {
            return query(URL(path + '?' + urlQuery(query)))
        } catch (e: MalformedURLException) {
            throw RuntimeException(e)
        }

    }

    fun query(url: URL): String {
        val response = handler.execute(url)

        if (response != null) {
            return response
        }

        throw RuntimeException("Response is null!")
    }

    private fun urlQuery(query: Map<String, String>): String {
        val builder = StringBuilder()
        for (entry in query.entries) {
            if (builder.length > 0) {
                builder.append('&')
            }
            builder.append(entry.key).append('=').append(entry.value)
        }
        return builder.toString()
    }

    @Suppress("UNREACHABLE_CODE")
    class UrlDownloadHandler : DownloadHandler {
        override fun execute(url: URL): String {

            val connection: HttpURLConnection
            var reader: BufferedReader? = null
            try {
                connection = url.openConnection() as HttpURLConnection

                if (!connection.doInput) {
                    return null!!;
                }

                reader = BufferedReader(InputStreamReader(
                        connection.inputStream,
                        Charset.forName("UTF-8")))

                val builder = StringBuilder()

                var line: String? = reader.readLine();
                while (line != null) {
                    builder.append(line)
                }

                return builder.toString()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                if (reader != null) {
                    try {
                        reader.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            }

            return null!!;
        }
    }
}
