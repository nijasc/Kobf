package dev.kobf.transforms

class NameGenerator {
    private var counter = 0L

    fun next(): String {
        var n = counter++
        val sb = StringBuilder()
        while (true) {
            sb.append('a' + (n % 26).toInt())
            n /= 26
            if (n == 0L) break
            n -= 1
        }
        return sb.reverse().toString()
    }
}