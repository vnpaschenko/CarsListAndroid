package com.example.ipaschenko.carslist.data

/**
 * Class that represents components of the car number
 */
data class CarNumber(val prefix: String?, val root: String, val suffix: String?,
                      val isCustom: Boolean) {

    fun matchWithParts(desiredPrefix: String?, desiredSuffix: String?): Int {
        var result = 0
        if (prefix != null && desiredPrefix != null) {
            result += matchParts(desiredPrefix, prefix, true)
        }

        if (suffix != null && desiredSuffix != null) {
            result += matchParts(desiredSuffix, suffix, false)
        }

        return result
    }

    override fun toString(): String {
        val builder = StringBuilder()

        if (prefix != null) {
            builder.append(prefix).append(" ")
        }
        builder.append(root)

        if (suffix != null) {
            builder.append(" ").append(suffix)
        }

        return builder.toString()
    }

    companion object {
        /**
         * Create car number from the specified string. Set translateCyrillic to true if some
         * number symbols can be Cyrillic (text recognition lib deals only with latin)
         */
        fun fromString(data: String, translateCyrillic: Boolean): CarNumber? {
            val components = data.split(" ", "\t", "-")
            val count = components.count()
            var prefix: String? = null
            var root: String?
            var suffix: String? = null
            var custom = false

            when (count) {
                !in 1..4 -> return null // Skip strings with many components for better performance
                1 -> {
                    root = components.first()
                    if (root.length < 3) return null
                    if (!root.containsOnlyNumbers) {
                        // root has letters, the number is custom
                        root = normalize(root, translateCyrillic)
                        custom = true
                    }
                }
                else -> {
                    // Look for the part that contains from 3-4 digits and assume it as root
                    var rootIndex = components.indexOfFirst {
                        it.length in (3..5) && it.indexOfFirst { !it.isDigit() } == -1
                    }

                    if (rootIndex != -1) {
                        // Root are numbers
                        root = components[rootIndex]
                    } else {
                        // Custom number ?
                        rootIndex = components.indexOfMaximum { it.length }
                        root = components[rootIndex]
                        if (root.length < 3) {
                            // Too short word can't be custom number
                            return null
                        }
                        root = normalize(root, translateCyrillic)

                        custom = true
                    }
                    if (rootIndex > 0) {
                        prefix = normalize(components[rootIndex - 1], translateCyrillic)
                    }
                    if (rootIndex < count - 1) {
                        suffix = normalize(components[rootIndex + 1], translateCyrillic)
                    }
                }
            }

            return CarNumber(prefix, root, suffix, custom)
        }

        private const val CYRILLIC_CHARS = "АВЕІКМНОРСТУХ"
        private const val LATIN_CHARS = "ABEIKMHOPCTYX"
        private fun normalize(string: String, translateCyrillic: Boolean): String {
            val builder = StringBuilder(string.length)
            for (chr in string) {
                val char = chr.toUpperCase()
                when (char) {
                    in ('0'..'9') + ('A'..'Z') -> builder.append(char)
                    ' ', '\t', '-' -> builder.append(' ')
                    else -> {
                        val index = if (translateCyrillic) CYRILLIC_CHARS.indexOf(char) else -1
                        builder.append(if (index != -1) LATIN_CHARS[index] else '*')
                    }
                }

            }

            return builder.toString()
        }
    }
}

private fun matchParts(desiredPart: String, obtainedPart: String, reverse: Boolean): Int {
    val desired = if (reverse) desiredPart.reversed() else desiredPart
    val obtained = if (reverse) obtainedPart.reversed() else obtainedPart

    var result = 0
    val len = obtained.length
    for ((index, desiredChar) in desired.withIndex()) {
        if (index >= len) {
            break
        }
        val obtainedChar = obtained[index]
        if (desiredChar == obtainedChar) {
            result += 2
        } else if (desiredChar == '*' || obtainedChar == '*') {
            result += 1
        } else {
            break
        }
    }

    return result
}

private val String.containsOnlyNumbers: Boolean
    get() = this.indexOfFirst { !it.isDigit() } == -1

private inline fun<T> Iterable<T>.indexOfMaximum(matcher: (T) -> Int): Int {
    var max = Int.MIN_VALUE
    var result = -1
    for ((i, element) in this.withIndex()) {
        val newValue = matcher(element)
        if (result == -1 || newValue > max) {
            result = i
            max = newValue
        }
    }
    return result
}
