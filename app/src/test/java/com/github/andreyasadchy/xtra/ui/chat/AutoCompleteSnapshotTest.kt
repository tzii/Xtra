package com.github.andreyasadchy.xtra.ui.chat

import android.app.Application
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.ui.view.AutoCompleteAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class AutoCompleteSnapshotTest {
    /** Every read must hold the same lock used by ViewModel writers. */
    private class GuardedSuggestions(initial: List<Any?>) : AbstractMutableList<Any?>() {
        private val values = initial.toMutableList()
        private fun checkLock() = check(Thread.holdsLock(this)) { "shared suggestions read outside source lock" }
        override val size: Int get() { checkLock(); return values.size }
        override fun get(index: Int): Any? { checkLock(); return values[index] }
        override fun set(index: Int, element: Any?): Any? { checkLock(); return values.set(index, element) }
        override fun add(index: Int, element: Any?) { checkLock(); values.add(index, element) }
        override fun removeAt(index: Int): Any? { checkLock(); return values.removeAt(index) }
    }

    private fun adapter(source: MutableList<Any?>) = AutoCompleteAdapter(
        RuntimeEnvironment.getApplication(), R.layout.auto_complete_emotes_list_item, R.id.name, source,
    )

    private fun filter(adapter: AutoCompleteAdapter<Any>, query: String): List<*> {
        // Exercise the real filter synchronously; no HandlerThread scheduling assumptions.
        val filter = adapter.filter
        val method = filter.javaClass.getDeclaredMethod("performFiltering", CharSequence::class.java)
            .apply { isAccessible = true }
        val result = method.invoke(filter, query)!!
        return result.javaClass.getField("values").get(result) as List<*>
    }

    @Test fun `filter copies shared suggestions under the producer lock`() {
        val source = GuardedSuggestions(listOf(Emote("Kappa"), Emote("Pog")))
        assertEquals(listOf("Kappa"), filter(adapter(source), ":ka").filterIsInstance<Emote>().map { it.name })
    }

    @Test fun `filter still sees replacements after its original source was captured`() {
        val source = GuardedSuggestions(listOf(Emote("OldSub"), Emote("Keep")))
        val adapter = adapter(source)
        assertEquals(listOf("OldSub"), filter(adapter, ":old").filterIsInstance<Emote>().map { it.name })
        replaceTwitchSuggestions(source, listOf(Emote("NewSub"), Emote("Keep")))
        assertEquals(listOf("NewSub"), filter(adapter, ":new").filterIsInstance<Emote>().map { it.name })
        assertEquals(emptyList<Any>(), filter(adapter, ":old"))
    }
}
