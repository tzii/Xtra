package com.github.andreyasadchy.xtra.util.update

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class UpdatePolicyTest(private val case: UpdatePolicyCase) {
    @Test fun verify() = case.verify()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = UpdatePolicyCases.all.map { arrayOf<Any>(it) }
    }
}
