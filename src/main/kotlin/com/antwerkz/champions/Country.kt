package com.antwerkz.champions

class Country {
    var nomination: String? = null
    var residence: String? = null
    var birth: String? = null
    var citizenship: String? = null
    override fun toString(): String {
        return "Country(nomination=$nomination, residence=$residence, birth=$birth, citizenship=$citizenship)"
    }

    fun location(): String {
        return residence ?: nomination ?: birth ?: citizenship ?: ""
    }
}
