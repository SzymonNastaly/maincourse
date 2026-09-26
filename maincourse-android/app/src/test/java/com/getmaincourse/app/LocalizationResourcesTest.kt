package com.getmaincourse.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Guard the shipping languages against new English-only strings, plurals and broken arguments. */
class LocalizationResourcesTest {
    @Test
    fun everyAdvertisedLanguageHasCompleteTranslationsAndCompatiblePlaceholders() {
        val root = resourceRoot()
        val source = entries(File(root, "values"))
        val config = parse(File(root, "xml/locales_config.xml")).getElementsByTagName("locale")
        val languages = (0 until config.length).map { (config.item(it) as Element).getAttribute("android:name") }
        assertEquals(setOf("en", "pl", "de"), languages.toSet())
        val required = source.filterValues { it.getAttribute("translatable") != "false" }
        for (language in languages.filter { it != "en" }) {
            val translations = entries(File(root, "values-$language"))
            assertEquals("$language resource keys", required.keys, translations.keys)
            for ((key, original) in required) {
                val translated = translations.getValue(key)
                assertEquals("$language/$key type", original.tagName, translated.tagName)
                val originalText = if (original.tagName == "plurals") {
                    (original.getElementsByTagName("item").item(0) as Element).textContent
                } else original.textContent
                val items = if (translated.tagName == "plurals") {
                    val nodes = translated.getElementsByTagName("item")
                    (0 until nodes.length).map { nodes.item(it) as Element }.also { forms ->
                        val expected = if (language == "pl") setOf("one", "few", "many", "other") else setOf("one", "other")
                        assertEquals("$language/$key plural forms", expected, forms.map { it.getAttribute("quantity") }.toSet())
                    }
                } else listOf(translated)
                for (item in items) {
                    assertTrue("$language/$key empty", item.textContent.isNotBlank())
                    assertEquals("$language/$key arguments", placeholders(originalText), placeholders(item.textContent))
                }
            }
        }
    }

    private fun placeholders(text: String) = Regex("%\\d+\\$[ds]").findAll(text).map { it.value }.sorted().toList()

    private fun entries(directory: File): Map<String, Element> = buildMap {
        directory.listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
            val children = parse(file).documentElement.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index) as? Element ?: continue
                if (element.tagName !in setOf("string", "plurals")) continue
                val key = element.getAttribute("name")
                check(put(key, element) == null) { "Duplicate resource $key in $directory" }
            }
        }
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun resourceRoot(): File = generateSequence(File(checkNotNull(System.getProperty("user.dir"))).canonicalFile, File::getParentFile)
        .flatMap { sequenceOf(File(it, "app/src/main/res"), File(it, "maincourse-android/app/src/main/res")) }
        .first { File(it, "values/strings.xml").isFile }
}
