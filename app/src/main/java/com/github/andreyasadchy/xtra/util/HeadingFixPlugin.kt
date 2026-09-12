package com.github.andreyasadchy.xtra.util

import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.CorePlugin
import org.commonmark.internal.HeadingParser
import org.commonmark.node.Heading
import org.commonmark.parser.Parser
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

/** Twitch panels accept ATX headings without the CommonMark space after '#'. */
class HeadingFixPlugin : AbstractMarkwonPlugin() {
    override fun configureParser(builder: Parser.Builder) {
        builder.enabledBlockTypes(CorePlugin.enabledBlockTypes().filter { it != Heading::class.java }.toSet())
        builder.customBlockParserFactory(object : HeadingParser.Factory() {
            override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
                val line = state.line
                var index = state.nextNonSpaceIndex
                while (line.getOrNull(index) == '#') index++
                val count = index - state.nextNonSpaceIndex
                val next = line.getOrNull(index)
                val headingState = if (count in 1..6 && next != null && next != ' ' && next != '\t') {
                    // CharSequence's end is exclusive; lastIndex would lose the final character.
                    val normalizedLine = line.subSequence(0, index).toString() + " " + line.subSequence(index, line.length)
                    object : ParserState by state {
                        override fun getLine(): CharSequence = normalizedLine
                    }
                } else state
                return super.tryStart(headingState, matchedBlockParser)
            }
        })
    }
}
