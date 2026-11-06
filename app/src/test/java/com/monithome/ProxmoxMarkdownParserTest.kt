package com.monithome

import com.monithome.presentation.components.proxmox.ProxmoxBlock
import com.monithome.presentation.components.proxmox.ProxmoxMarkdownParser
import com.monithome.presentation.components.proxmox.parseInlineMarkdown
import org.junit.Assert.*
import org.junit.Test

class ProxmoxMarkdownParserTest {

    @Test
    fun testParseSshLoginAlert() {
        val rawAlert = """
            # 🖥️ VPS SSH Security: 192.168.1.97
            ---

            ### 🟢 Успешный вход по SSH!

            | Параметр | Значение |
            | :--- | :--- |
            | **👤 Пользователь** | `root` |
            | **🌐 IP-адрес** | `192.168.1.50` |
            | **🔑 Метод** | `publickey` |

            <details>
              <summary>🔍 <b>Показать лог входа</b></summary>
              <pre><code>Accepted publickey for root from 192.168.1.50 port 54321 ssh2</code></pre>
            </details>

            *Sentinel Security Guard • Время: 21:05:00*
        """.trimIndent()

        val blocks = ProxmoxMarkdownParser.parse(rawAlert)
        assertTrue(blocks.isNotEmpty())

        // Проверяем наличие заголовка
        val header1 = blocks.filterIsInstance<ProxmoxBlock.Header>().firstOrNull { it.level == 1 }
        assertNotNull(header1)
        assertEquals("🖥️ VPS SSH Security: 192.168.1.97", header1?.text)

        // Проверяем разделитель
        assertTrue(blocks.any { it is ProxmoxBlock.Divider })

        // Проверяем подзаголовок
        val header3 = blocks.filterIsInstance<ProxmoxBlock.Header>().firstOrNull { it.level == 3 }
        assertNotNull(header3)
        assertEquals("🟢 Успешный вход по SSH!", header3?.text)

        // Проверяем таблицу
        val table = blocks.filterIsInstance<ProxmoxBlock.Table>().firstOrNull()
        assertNotNull(table)
        assertEquals(3, table?.rows?.size)
        assertEquals("**👤 Пользователь**", table?.rows?.get(0)?.get(0))
        assertEquals("`root`", table?.rows?.get(0)?.get(1))

        // Проверяем спойлер лога
        val details = blocks.filterIsInstance<ProxmoxBlock.CollapsibleDetails>().firstOrNull()
        assertNotNull(details)
        assertTrue(details?.summary?.contains("Показать лог входа") == true)
        assertTrue(details?.codeContent?.contains("Accepted publickey") == true)

        // Проверяем сноску/футер
        val aside = blocks.filterIsInstance<ProxmoxBlock.Paragraph>().firstOrNull { it.isAside }
        assertNotNull(aside)
        assertTrue(aside?.text?.contains("Sentinel Security Guard") == true)
    }

    @Test
    fun testParseMultipleDetailsAlert() {
        val alert = """
            # ✅ IPS Investigation Done
            ---

            ### ✅ [IPS: Расследование завершено] Нарушитель найден!

            | Параметр | Значение |
            | :--- | :--- |
            | **👤 Нарушитель** | `user1` (Заблокирован) |
            | **🔓 Транзитный туннель** | `tunnel1@test.com` (Активен) |

            ✨ *Все остальные пользователи туннеля снова в сети!*

            <details>
              <summary>📋 <b>Показать детали глобального бана</b></summary>
              <pre><code>Block details info</code></pre>
            </details>

            <details>
              <summary>📋 <b>Показать статус транзитного туннеля</b></summary>
              <pre><code>Tunnel status info</code></pre>
            </details>

            *Sentinel Security Guard • Время: 18:00:00*
        """.trimIndent()

        val blocks = ProxmoxMarkdownParser.parse(alert)
        val details = blocks.filterIsInstance<ProxmoxBlock.CollapsibleDetails>()
        assertEquals(2, details.size)
        assertTrue(details[0].summary.contains("детали глобального бана"))
        assertEquals("Block details info", details[0].codeContent)
        assertTrue(details[1].summary.contains("статус транзитного туннеля"))
        assertEquals("Tunnel status info", details[1].codeContent)
    }

    @Test
    fun testParseInlineMarkdown() {
        val annotated = parseInlineMarkdown("**Пользователь** вошел через `publickey` *успешно*")
        assertEquals("Пользователь вошел через publickey успешно", annotated.text.trim())
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
