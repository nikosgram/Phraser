package io.github.nikosgram13.phraser

import java.io.IOException

object Bootstrap {
    @JvmStatic fun main(args: Array<String>) {
        if (java.lang.Float.parseFloat(System.getProperty("java.class.version")) < 52.0) {
            System.err.println("*** ERROR *** Phraser requires Java 8 or above to function! Please download and install it!")
            println("You can check your Java version with the command: java -version")
            System.exit(1)
            return
        }

        if (args.size < 1 || args.size > 2) {
            println("Arguments is wrong, please read the documents about Phraser.")
            System.exit(1)
            return
        }

        val phraser: Phraser = Phraser(args[0], if (args.size == 2) args[1] else null)

        try {
            System.exit(if (phraser.simplify()) 0 else 1)
            return
        } catch (e: IOException) {
            e.printStackTrace()
        }

        System.exit(1)
    }
}