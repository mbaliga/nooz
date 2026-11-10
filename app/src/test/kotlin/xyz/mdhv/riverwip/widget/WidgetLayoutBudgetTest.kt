package xyz.mdhv.riverwip.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import xyz.mdhv.riverwip.model.ListStyle
import xyz.mdhv.riverwip.model.WidgetSize
import xyz.mdhv.riverwip.model.WidgetSizing
import xyz.mdhv.riverwip.model.WidgetTemplate
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The launcher picks a widget's layout by size, from breakpoints that
 * WidgetSizing (in `:core:model`) adds up from each layout's parts. This pins
 * the layout XML to those parts, so a margin, a text size or a line count
 * cannot change in one place and not the other, which would let the launcher
 * pick a layout that is clipped at the bottom (where the end-of-list mark and
 * the "as of" time are).
 */
class WidgetLayoutBudgetTest {

    private val res: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private val dimens: Map<String, String> by lazy {
        val doc = parse(File(res, "values/widget_dimens.xml"))
        val out = HashMap<String, String>()
        val nodes = doc.getElementsByTagName("dimen")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            out[el.getAttribute("name")] = el.textContent.trim()
        }
        out
    }

    private fun parse(file: File): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)

    private fun layout(name: String): Document = parse(File(res, "layout/$name.xml"))

    private fun Element.a(name: String): String? = getAttributeNS(ANDROID, name).ifEmpty { null }

    private fun Document.view(id: String): Element? {
        val all = getElementsByTagName("*")
        for (i in 0 until all.length) {
            val el = all.item(i) as Element
            val value = el.a("id") ?: continue
            if (value == "@+id/$id" || value == "@id/$id" || value == "@android:id/$id") return el
        }
        return null
    }

    private fun Document.required(id: String): Element = view(id) ?: throw AssertionError("missing $id")

    private fun Document.count(prefix: Regex): Int {
        val all = getElementsByTagName("*")
        var n = 0
        for (i in 0 until all.length) {
            val id = (all.item(i) as Element).a("id") ?: continue
            if (prefix.matches(id.substringAfter('/'))) n++
        }
        return n
    }

    /** A dimension in dp, through @dimen references; absent is 0. */
    private fun dp(value: String?): Float {
        if (value == null) return 0f
        if (value.startsWith("@dimen/")) return dp(dimens[value.removePrefix("@dimen/")] ?: error("no dimen $value"))
        return value.removeSuffix("dp").toFloat()
    }

    private fun sp(value: String?): Float = value!!.removeSuffix("sp").toFloat()

    private fun Element.lines(): Int = a("maxLines")!!.toInt()

    /** The model's line heights hold the XML's text sizes, font padding included. */
    private fun assertLine(el: Element, lineDp: Float, what: String) {
        val serif = el.a("fontFamily") == "serif"
        // Roboto's line is 1.33 of its size with font padding; the serif face is taller.
        val factor = if (serif) 1.4f else 1.33f
        assertTrue("$what: ${el.a("textSize")} needs ${sp(el.a("textSize")) * factor}dp, budget is $lineDp", sp(el.a("textSize")) * factor <= lineDp)
    }

    private fun Document.rootPadding(): Float {
        val root = documentElement
        return root.a("padding")?.let { 2 * dp(it) } ?: (dp(root.a("paddingTop")) + dp(root.a("paddingBottom")))
    }

    /** The last element child of the root: the one clipped first if a layout ever overflows. */
    private fun Document.lastChildId(): String? {
        var node = documentElement.lastChild
        while (node != null && node !is Element) node = node.previousSibling
        return (node as Element?)?.a("id")?.substringAfter('/')
    }

    private val sized = listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE)

    private fun name(template: WidgetTemplate, size: WidgetSize) = "widget_${template.name.lowercase()}_${size.name.lowercase()}"

    @Test fun everyLayoutHasThePaddingAndTitleTheBudgetCounts() {
        for (template in WidgetTemplate.entries) {
            for (size in sized) {
                val doc = layout(name(template, size))
                assertEquals(name(template, size), WidgetSizing.PADDING, doc.rootPadding(), 0.01f)
                val title = doc.required("widget_title")
                assertEquals(1, title.lines())
                assertLine(title, WidgetSizing.LINE_SMALL, "title")
                // The footer is the last child, below the part that takes the spare height.
                assertEquals(name(template, size), "widget_footer", doc.lastChildId())
                val footer = doc.required("widget_footer")
                assertEquals(WidgetSizing.footerLines(template), footer.lines())
                assertLine(footer, WidgetSizing.LINE_SMALL, "footer")
            }
        }
        val tiny = layout("widget_tiny")
        assertEquals(WidgetSizing.TINY_PADDING, tiny.rootPadding(), 0.01f)
        assertLine(tiny.required("widget_title"), WidgetSizing.LINE_SMALL, "tiny title")
        assertLine(tiny.required("widget_value"), WidgetSizing.LINE_BODY, "tiny value")
        assertEquals(1, tiny.required("widget_value").lines())
    }

    @Test fun listLayoutsMatchTheirRowBudget() {
        for (size in sized) {
            val doc = layout(name(WidgetTemplate.LIST, size))
            val slots = doc.count(Regex("row[0-9]+"))
            assertEquals("row slots at $size", ListStyle.entries.maxOf { WidgetSizing.listRows(it, size) }, slots)
            val rows = doc.required("widget_rows")
            assertEquals(0f, dp(rows.a("layout_marginTop")), 0.01f)
            assertEquals("rows take the spare height", "1", rows.a("layout_weight"))
            for (n in 1..slots) {
                val row = doc.required("row$n")
                assertEquals(WidgetSizing.ROW_PADDING, dp(row.a("paddingTop")) + dp(row.a("paddingBottom")), 0.01f)
                assertEquals(WidgetSizing.ROW_MIN, dp(row.a("minHeight")), 0.01f)
                assertLine(doc.required("row${n}_title"), WidgetSizing.LINE_BODY, "row title")
                assertLine(doc.required("row${n}_meta"), WidgetSizing.LINE_SMALL, "row meta")
            }
            assertNull("lists have no caption", doc.view("widget_caption"))
            assertEquals(WidgetSizing.LIST_FOOTER_GAP, dp(doc.required("widget_footer").a("layout_marginTop")), 0.01f)
        }
    }

    @Test fun statLayoutsMatchTheirBudget() {
        for ((size, columns) in sized.zip(listOf(1, 2, 3))) {
            val doc = layout(name(WidgetTemplate.STAT, size))
            assertEquals(columns, doc.count(Regex("stat[0-9]+")))
            assertEquals(WidgetSizing.GAP, dp(doc.required("widget_stats").a("layout_marginTop")), 0.01f)
            for (n in 1..columns) {
                assertLine(doc.required("stat${n}_value"), if (size == WidgetSize.SMALL) WidgetSizing.LINE_VALUE_SMALL else WidgetSizing.LINE_VALUE, "value")
                assertEquals(2, doc.required("stat${n}_label").lines())
                assertLine(doc.required("stat${n}_label"), WidgetSizing.LINE_SMALL, "label")
            }
            val caption = doc.required("widget_caption")
            assertEquals(WidgetSizing.captionLines(WidgetTemplate.STAT, size), caption.lines())
            assertEquals(WidgetSizing.GAP, dp(caption.a("layout_marginTop")), 0.01f)
            assertEquals("the caption takes the spare height", "1", caption.a("layout_weight"))
            assertEquals(WidgetSizing.GAP, dp(doc.required("widget_footer").a("layout_marginTop")), 0.01f)
            assertNull("no widest-gap line", doc.view("widget_line"))
        }
    }

    @Test fun imageLayoutsMatchTheirBudget() {
        for (size in sized) {
            val doc = layout(name(WidgetTemplate.IMAGE, size))
            val picture = doc.required("widget_picture")
            assertEquals(WidgetSizing.PICTURE_GAP, dp(picture.a("layout_marginTop")), 0.01f)
            assertEquals("the picture takes the spare height", "1", picture.a("layout_weight"))
            val caption = doc.required("widget_caption")
            assertEquals(WidgetSizing.captionLines(WidgetTemplate.IMAGE, size), caption.lines())
            assertEquals(WidgetSizing.GAP, dp(caption.a("layout_marginTop")), 0.01f)
            assertEquals(WidgetSizing.GAP, dp(doc.required("widget_footer").a("layout_marginTop")), 0.01f)
            assertEquals(size != WidgetSize.SMALL, doc.view("widget_label_top") != null)
            if (size == WidgetSize.LARGE) {
                val head = doc.required("widget_legend_head")
                assertEquals(WidgetSizing.PICTURE_GAP, dp(head.a("layout_marginTop")), 0.01f)
                assertEquals(WidgetSizing.LEGEND_HEAD_LINES, head.lines())
                assertEquals(WidgetSizing.LEGEND_GAP, dp(doc.required("widget_legend").a("layout_marginTop")), 0.01f)
                assertEquals(WidgetSizing.LEGEND_SLOTS, doc.count(Regex("legend[0-9]+")))
                for (n in 1..WidgetSizing.LEGEND_SLOTS) {
                    val entry = doc.required("legend$n")
                    assertEquals(1, entry.lines())
                    assertLine(entry, WidgetSizing.LINE_SMALL, "legend entry")
                }
                // Two entries a line: ten topics take the five lines the budget counts.
                assertEquals(WidgetSizing.LEGEND_LINES, WidgetSizing.LEGEND_SLOTS / 2)
            } else {
                assertNull(doc.view("widget_legend"))
            }
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
