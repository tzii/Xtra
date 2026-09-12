package com.github.andreyasadchy.xtra.util.update

fun main() {
    var failures = 0
    for (case in UpdatePolicyCases.all) {
        try { case.verify(); println("PASS ${case.name}") }
        catch (error: Throwable) { failures++; System.err.println("FAIL ${case.name}"); error.printStackTrace() }
    }
    println("${UpdatePolicyCases.all.size - failures}/${UpdatePolicyCases.all.size} production-core cases passed")
    check(failures == 0) { "$failures failures" }
}
