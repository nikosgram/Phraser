package io.github.nikosgram13.phraser

import com.yahoo.platform.yui.compressor.CssCompressor
import com.yahoo.platform.yui.compressor.JavaScriptCompressor
import io.github.nikosgram13.conduit.ConduitUtils
import io.github.nikosgram13.conduit.interfaces.ClassableHandler
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag
import org.jsoup.select.Elements
import org.mozilla.javascript.ErrorReporter
import org.mozilla.javascript.EvaluatorException
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern

class Phraser(private val original: Path, private val formatted: Path?) {

    private val stringVariables = HashMap<String, String>()
    private val booleanVariables = HashMap<String, Boolean>()
    private val integerVariables = HashMap<String, Int>()

    private var document: Document? = null

    init {
        ConduitUtils.appendHandler(JSONObject::class.java, JSONExecutor())
    }

    constructor(original: String, formatted: String?) : this(Paths.get(original), if (formatted != null) Paths.get(formatted) else null) {
    }

    @Throws(IOException::class)
    fun simplify(): Boolean {
        val delay: Long = System.currentTimeMillis()
        log("The simplifying started.")
        if (!Files.exists(original)) {
            log("The requested file was not found '" + original.toString() + "'!", true)
            return false
        }
        log("Reading the html file...")

        val data: String
        if (isLink(original.toString())) {
            data = ConduitUtils.query(original.toString(), HashMap<String, String>())
        } else {
            data = toString(Files.readAllLines(original))
        }

        document = Jsoup.parse(data, "UTF-8")

        checkIncludes()

        document = Jsoup.parse(document!!.html(), "UTF-8")

        run {
            val del: Long = System.currentTimeMillis()
            log("Script simplifying started.")

            for (script: Element in document!!.select("script")) {
                if (script.hasAttr("src")) {
                    if (script.hasAttr("static")) {
                        script.removeAttr("static")
                    } else {
                        simplifyScript(script)
                    }
                }
            }

            log("Script simplifying takes " + (System.currentTimeMillis() - del) + "ms to completed.", true)
        }

        run {
            val del: Long = System.currentTimeMillis()
            log("Stylesheet simplifying started.")

            for (stylesheet: Element in document!!.select("link")) {
                if (stylesheet.hasAttr("href") && stylesheet.hasAttr("rel") &&
                        stylesheet.attr("rel").equals("stylesheet", ignoreCase = true)) {
                    if (stylesheet.hasAttr("static")) {
                        stylesheet.removeAttr("static")
                    } else {
                        simplifyStylesheet(stylesheet)
                    }
                }
            }

            log("Stylesheet simplifying takes " + (System.currentTimeMillis() - del) + "ms to completed.", true)
        }

        val del: Long = System.currentTimeMillis()
        run {
            log("Variables simplifying started.")

            for (variable: Element in document!!.select("meta")) {
                if (variable.hasAttr("variable") && variable.hasAttr("name") &&
                        variable.hasAttr("content")) {
                    if (!variable.hasAttr("datatype")) {
                        stringVariables.put(variable.attr("name"), variable.attr("content"))
                    } else {
                        when (variable.attr("datatype").toLowerCase()) {
                            "integer", "int" -> try {
                                integerVariables.put(variable.attr("name"), Integer.valueOf(variable.attr("content")))
                            } catch (ignored: NumberFormatException) {
                            }
                            "boolean", "bool" -> booleanVariables.put(variable.attr("name"), java.lang.Boolean.valueOf(variable.attr("content")))
                            "str", "string" -> stringVariables.put(variable.attr("name"), variable.attr("content"))
                            else -> stringVariables.put(variable.attr("name"), variable.attr("content"))
                        }
                    }
                    variable.remove()
                }
            }
        }

        var response: String = document!!.html()

        for (variable in stringVariables.entries) {
            var key: String = variable.key;
            response = response.replace("{{$$key}}", variable.value)
        }

        for (variable in integerVariables.entries) {
            var key: String = variable.key;
            response = response.replace("{{$$key}}", variable.value.toString())
        }

        for (variable in booleanVariables.entries) {
            var key: String = variable.key;
            response = response.replace("{{$$key}}", variable.value.toString())
        }

        log("Variables simplifying takes " + (System.currentTimeMillis() - del) + "ms to completed.", true)

        if (formatted != null) {
            Files.deleteIfExists(formatted)
            Files.createFile(formatted)
            Files.write(formatted, Arrays.asList<String>(*response.split(System.getProperty("line.separator").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()), Charset.forName("UTF-8"))
        } else {
            println(response)
        }

        log("Simplifying takes " + (System.currentTimeMillis() - delay) + "ms to completed.", true)

        return true
    }

    private fun checkIncludes() {
        log("Checking for the including files...")

        for (include: Element in includes) {
            val href: String = include.attr("href")

            if (isLink(include.attr("href"))) {
                try {
                    include.replaceWith(DataNode(ConduitUtils.query(URL(href)), include.baseUri()))
                } catch (e: MalformedURLException) {
                    e.printStackTrace()
                }

            } else {
                val path: Path = Paths.get(href)
                if (Files.exists(path)) {
                    try {
                        include.replaceWith(DataNode(toString(Files.readAllLines(path)), include.baseUri()))
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                } else {
                    log("The requested file was not found '$href'!", true)
                }
            }
        }

        if (includes.size > 0) {
            checkIncludes()
        }
    }

    private val includes: Elements get() {
        val returned: ArrayList<Element> = ArrayList()

        for (include: Element in document!!.select("link")) {
            if (include.hasAttr("property") && include.attr("property").equals("include", ignoreCase = true) &&
                    include.hasAttr("href")) {

                returned.add(include)
            }
        }

        return Elements(returned)
    }

    private fun simplifyStylesheet(stylesheet: Element) {
        val href: String = stylesheet.attr("href")
        if (isLink(href)) {
            try {
                simplifyScript(stylesheet, ConduitUtils.query(URL(href)))
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }

        } else {
            val path: Path = Paths.get(href)
            if (Files.exists(path)) {
                try {
                    simplifyStylesheet(stylesheet, toString(Files.readAllLines(path, Charset.forName("UTF-8"))))
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            } else {
                log("The requested file was not found '$href'!", true)
            }
        }
    }

    private fun simplifyStylesheet(stylesheet: Element, data: String) {
        var result: String = compileStylesheet(stylesheet, data)
        val style: Element = Element(Tag.valueOf("style"), stylesheet.baseUri())
        style.appendChild(DataNode(result, style.baseUri()))
        style.attributes().addAll(stylesheet.attributes())
        stylesheet.replaceWith(style)
    }

    private fun compileStylesheet(stylesheet: Element, data: String): String {
        stylesheet.removeAttr("href")
        stylesheet.removeAttr("rel")
        if (stylesheet.hasAttr("compile")) {
            stylesheet.removeAttr("compile")
            try {
                val writer: Writer = StringWriter()

                val compressor: CssCompressor = CssCompressor(StringReader(data))

                compressor.compress(writer, -1)

                return writer.toString()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        return data
    }

    private fun simplifyScript(script: Element) {
        val src: String = script.attr("src")
        if (isLink(src)) {
            try {
                simplifyScript(script, ConduitUtils.query(URL(src)))
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }

        } else {
            val path: Path = Paths.get(src)
            if (Files.exists(path)) {
                try {
                    simplifyScript(script, toString(Files.readAllLines(path, Charset.forName("UTF-8"))))
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }
    }

    private fun simplifyScript(script: Element, data: String) {
        script.empty()
        script.appendChild(DataNode(compileScript(script, data), script.baseUri()))
    }

    private fun compileScript(script: Element, data: String): String {
        val localFilename: String = script.attr("src")
        if (script.hasAttr("compile") || script.hasAttr("compress")) {
            try {
                val writer: Writer = StringWriter()

                val compressor: JavaScriptCompressor = JavaScriptCompressor(StringReader(data), object : ErrorReporter {
                    override fun warning(message: String, sourceName: String,
                                         line: Int, lineSource: String, lineOffset: Int) {
                        log("\n[WARNING] in " + localFilename, true)
                        if (line < 0) {
                            log("  $message", true)
                        } else {
                            log("  $line:$lineOffset:$message", true)
                        }
                    }

                    override fun error(message: String, sourceName: String,
                                       line: Int, lineSource: String, lineOffset: Int) {
                        log("\n[WARNING] in " + localFilename, true)
                        if (line < 0) {
                            log("  $message", true)
                        } else {
                            log("  $line:$lineOffset:$message", true)
                        }
                    }

                    override fun runtimeError(message: String, sourceName: String,
                                              line: Int, lineSource: String, lineOffset: Int): EvaluatorException {
                        error(message, sourceName, line, lineSource, lineOffset)
                        return EvaluatorException(message)
                    }
                })

                compressor.compress(
                        writer,
                        if (script.hasAttr("compress")) 8000 else -1,
                        script.hasAttr("compress"),
                        false,
                        false,
                        false)

                script.removeAttr("src")
                script.removeAttr("compile")
                script.removeAttr("compress")
                return writer.toString()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        script.removeAttr("src")

        return data
    }

    private fun toString(lines: List<String>): String {
        val builder: StringBuilder = StringBuilder()

        for (line: String in lines) {
            builder.append(line).append(System.getProperty("line.separator"))
        }

        return builder.toString()
    }

    private fun isLink(string: String): Boolean {
        return REGEX.matcher(string).find()
    }

    private fun log(message: String, force: Boolean = false) {
        if (formatted != null) {
            println(message)
        } else if (force) {
            println("<!-- $message -->")
        }
    }

    companion object {
        private val REGEX: Pattern = Pattern.compile("(?i)\\b(?:(?:https?|ftp)://)(?:\\S+(?::\\S*)?@)?(?:(?!(?:10|127)(?:\\.\\d{1,3}){3})(?!(?:169\\.254|192\\.168)(?:\\.\\d{1,3}){2})(?!172\\.(?:1[6-9]|2\\d|3[0-1])(?:\\.\\d{1,3}){2})(?:[1-9]\\d?|1\\d\\d|2[01]\\d|22[0-3])(?:\\.(?:1?\\d{1,2}|2[0-4]\\d|25[0-5])){2}(?:\\.(?:[1-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))|(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff]{2,}))\\.?)(?::\\d{2,5})?(?:[/?#]\\S*)?\\b")
    }

    @Suppress("UNREACHABLE_CODE")
    class JSONExecutor : ClassableHandler<JSONObject> {
        override fun execute(response: String): JSONObject {
            try {
                return JSONObject(response)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null!!;
        }
    }
}
