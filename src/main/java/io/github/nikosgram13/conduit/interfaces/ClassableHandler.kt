package io.github.nikosgram13.conduit.interfaces

interface ClassableHandler<T> {
    fun execute(response: String): T
}
