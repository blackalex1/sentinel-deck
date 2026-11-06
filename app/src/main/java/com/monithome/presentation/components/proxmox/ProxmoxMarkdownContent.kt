package com.monithome.presentation.components.proxmox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Структурированное представление блоков разметки Proxmox Sentinel.
 */
sealed class ProxmoxBlock {
    data class Header(val level: Int, val text: String) : ProxmoxBlock()
    object Divider : ProxmoxBlock()
    data class Table(val headers: List<String>?, val rows: List<List<String>>) : ProxmoxBlock()
    data class CollapsibleDetails(val summary: String, val codeContent: String) : ProxmoxBlock()
    data class CodeBlock(val language: String?, val code: String) : ProxmoxBlock()
    data class Paragraph(val text: String, val isAside: Boolean = false) : ProxmoxBlock()
}

/**
 * Парсер Markdown/HTML-разметки от Proxmox Sentinel.
 */
object ProxmoxMarkdownParser {

    private val detailsRegex = Regex(
        """<details[^>]*>\s*<summary[^>]*>([\s\S]*?)</summary>\s*(?:<pre>)?(?:<code>)?([\s\S]*?)(?:</code>)?(?:</pre>)?\s*</details>""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String): List<ProxmoxBlock> {
        if (rawText.isBlank()) return emptyList()

        // 1. Очищаем экранирование Telegram MarkdownV2 (например \., \-, \_)
        val cleaned = unescapeMarkdownV2(rawText)

        // 2. Извлекаем блоки <details>...</details>, заменяя их плейсхолдерами
        val detailsList = mutableListOf<ProxmoxBlock.CollapsibleDetails>()
        val textWithoutDetails = detailsRegex.replace(cleaned) { matchResult ->
            val summaryRaw = matchResult.groupValues[1].trim()
            val codeRaw = matchResult.groupValues[2].trim()
            val index = detailsList.size
            detailsList.add(ProxmoxBlock.CollapsibleDetails(summary = summaryRaw, codeContent = codeRaw))
            "\n__PROXMOX_DETAILS_${index}__\n"
        }

        val lines = textWithoutDetails.lines()
        val blocks = mutableListOf<ProxmoxBlock>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Плейсхолдер <details>
            if (trimmed.startsWith("__PROXMOX_DETAILS_") && trimmed.endsWith("__")) {
                val idxStr = trimmed.removePrefix("__PROXMOX_DETAILS_").removeSuffix("__")
                val idx = idxStr.toIntOrNull()
                if (idx != null && idx in detailsList.indices) {
                    blocks.add(detailsList[idx])
                }
                i++
                continue
            }

            // Заголовки Markdown (#, ##, ###)
            if (trimmed.startsWith("### ")) {
                blocks.add(ProxmoxBlock.Header(3, trimmed.removePrefix("### ").trim()))
                i++
                continue
            }
            if (trimmed.startsWith("## ")) {
                blocks.add(ProxmoxBlock.Header(2, trimmed.removePrefix("## ").trim()))
                i++
                continue
            }
            if (trimmed.startsWith("# ")) {
                blocks.add(ProxmoxBlock.Header(1, trimmed.removePrefix("# ").trim()))
                i++
                continue
            }

            // Разделители (---, ***, <hr>, <hr/>)
            if (trimmed == "---" || trimmed == "***" || trimmed == "___" || trimmed.equals("<hr>", ignoreCase = true) || trimmed.equals("<hr/>", ignoreCase = true)) {
                blocks.add(ProxmoxBlock.Divider)
                i++
                continue
            }

            // Блок кода ``` ... ```
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // пропускаем закрывающий ```
                blocks.add(ProxmoxBlock.CodeBlock(lang, codeLines.joinToString("\n")))
                continue
            }

            // Таблицы Markdown (| col1 | col2 |)
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }

                val parsedTable = parseTableLines(tableLines)
                if (parsedTable != null) {
                    blocks.add(parsedTable)
                }
                continue
            }

            // Цитаты или сноски / футер (например, *Sentinel Security Guard...* или > ...)
            val isAside = (trimmed.startsWith("*") && trimmed.endsWith("*") && !trimmed.startsWith("**")) ||
                    trimmed.startsWith("> ") ||
                    trimmed.contains("Sentinel Security Guard", ignoreCase = true)

            blocks.add(ProxmoxBlock.Paragraph(line, isAside = isAside))
            i++
        }

        return blocks
    }

    private fun parseTableLines(lines: List<String>): ProxmoxBlock.Table? {
        if (lines.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        var headers: List<String>? = null

        for (line in lines) {
            val content = line.trim().removePrefix("|").removeSuffix("|")
            val cells = content.split("|").map { it.trim() }

            // Пропускаем строку-разделитель (например :--- | :---)
            val isSeparator = cells.all { cell ->
                cell.all { ch -> ch == '-' || ch == ':' || ch == ' ' } && cell.isNotEmpty()
            }
            if (isSeparator) continue

            if (headers == null && isHeaderRow(cells)) {
                headers = cells
            } else {
                rows.add(cells)
            }
        }

        return if (rows.isNotEmpty() || headers != null) {
            ProxmoxBlock.Table(headers, rows)
        } else {
            null
        }
    }

    private fun isHeaderRow(cells: List<String>): Boolean {
        if (cells.size < 2) return false
        val first = cells[0].lowercase()
        val second = cells[1].lowercase()
        return (first == "параметр" && second == "значение") ||
                (first == "metric" && second == "value") ||
                (first == "key" && second == "value")
    }

    private fun unescapeMarkdownV2(text: String): String {
        val charsToUnescape = listOf('.', '-', '_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '=', '|', '{', '}', '!')
        var res = text
        for (c in charsToUnescape) {
            res = res.replace("\\$c", "$c")
        }
        return res
    }
}

/**
 * Парсер инлайн-форматирования в AnnotatedString:
 * Поддерживает **bold**, *italic*, `code`, <b>bold</b>, <i>italic</i>, <code>code</code>.
 */
fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val len = text.length

        // Регулярное выражение для токенов: **жирный**, *курсив*, `код`, <b>жирный</b>, <i>курсив</i>, <code>код</code>
        val tokenRegex = Regex(
            """(\*\*(.+?)\*\*)|(<b>(.+?)</b>)|(`([^`]+?)`)|(<code>([\s\S]+?)</code>)|(\*([^*]+?)\*)|(<i>(.+?)</i>)""",
            RegexOption.IGNORE_CASE
        )

        val matches = tokenRegex.findAll(text)
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > cursor) {
                append(cleanHtmlTags(text.substring(cursor, start)))
            }

            when {
                // **жирный** или <b>жирный</b>
                match.groupValues[1].isNotEmpty() || match.groupValues[3].isNotEmpty() -> {
                    val content = match.groupValues[2].ifEmpty { match.groupValues[4] }
                    val spanStart = length
                    append(cleanHtmlTags(content))
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, color = Color.White),
                        spanStart,
                        length
                    )
                }
                // `код` или <code>код</code>
                match.groupValues[5].isNotEmpty() || match.groupValues[7].isNotEmpty() -> {
                    val content = match.groupValues[6].ifEmpty { match.groupValues[8] }
                    val spanStart = length
                    append(cleanHtmlTags(content))
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8),
                            background = Color(0x2638BDF8),
                            fontSize = 11.5.sp
                        ),
                        spanStart,
                        length
                    )
                }
                // *курсив* или <i>курсив</i>
                match.groupValues[9].isNotEmpty() || match.groupValues[11].isNotEmpty() -> {
                    val content = match.groupValues[10].ifEmpty { match.groupValues[12] }
                    val spanStart = length
                    append(cleanHtmlTags(content))
                    addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFF94A3B8)),
                        spanStart,
                        length
                    )
                }
            }

            cursor = end
        }

        if (cursor < len) {
            append(cleanHtmlTags(text.substring(cursor, len)))
        }
    }
}

private fun cleanHtmlTags(str: String): String {
    return str
        .replace("<br>", "\n")
        .replace("<br/>", "\n")
        .replace("<br />", "\n")
        .replace(Regex("""</?(?:b|i|u|span|p|code|pre|details|summary)[^>]*>""", RegexOption.IGNORE_CASE), "")
}

/**
 * Основной Composable для отображения отформатированного Proxmox-алерта.
 */
@Composable
fun ProxmoxMarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val blocks = remember(text) { ProxmoxMarkdownParser.parse(text) }
    ProxmoxMarkdownBlocksContent(
        blocks = blocks,
        modifier = modifier,
        isCompact = isCompact
    )
}

/**
 * Отображение предварительно распарсенных блоков разметки.
 */
@Composable
fun ProxmoxMarkdownBlocksContent(
    blocks: List<ProxmoxBlock>,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ProxmoxBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> if (isCompact) 13.sp else 14.5.sp
                        2 -> if (isCompact) 12.5.sp else 13.5.sp
                        else -> if (isCompact) 12.sp else 13.sp
                    }
                    val color = when (block.level) {
                        1 -> Color(0xFFF8FAFC)
                        2 -> Color(0xFFE2E8F0)
                        else -> Color(0xFF38BDF8) // Акцентный голубой для подзаголовков
                    }
                    Text(
                        text = parseInlineMarkdown(block.text),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        lineHeight = (fontSize.value + 4).sp
                    )
                }

                is ProxmoxBlock.Divider -> {
                    HorizontalDivider(
                        color = Color(0xFF334155).copy(alpha = 0.5f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = if (isCompact) 2.dp else 4.dp)
                    )
                }

                is ProxmoxBlock.Table -> {
                    TableBlockView(table = block, isCompact = isCompact)
                }

                is ProxmoxBlock.CollapsibleDetails -> {
                    CollapsibleDetailsView(details = block, isCompact = isCompact)
                }

                is ProxmoxBlock.CodeBlock -> {
                    CodeBlockView(codeBlock = block)
                }

                is ProxmoxBlock.Paragraph -> {
                    val isAside = block.isAside
                    Text(
                        text = parseInlineMarkdown(block.text),
                        fontSize = if (isAside) 10.5.sp else if (isCompact) 12.sp else 12.5.sp,
                        fontStyle = if (isAside) FontStyle.Italic else FontStyle.Normal,
                        color = if (isAside) Color(0xFF64748B) else Color(0xFFCBD5E1),
                        lineHeight = if (isAside) 14.sp else 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TableBlockView(table: ProxmoxBlock.Table, isCompact: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A).copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .padding(horizontal = if (isCompact) 8.dp else 10.dp, vertical = if (isCompact) 4.dp else 6.dp)
    ) {
        table.rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    color = Color(0xFF1E293B).copy(alpha = 0.7f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val key = row.getOrNull(0) ?: ""
                val value = row.getOrNull(1) ?: (if (row.size > 2) row.drop(1).joinToString(" | ") else "")

                // Ключ параметра (слева)
                Text(
                    text = parseInlineMarkdown(key),
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Значение параметра (справа)
                Text(
                    text = parseInlineMarkdown(value),
                    fontSize = if (isCompact) 11.5.sp else 12.sp,
                    color = Color(0xFFF1F5F9),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CollapsibleDetailsView(details: ProxmoxBlock.CollapsibleDetails, isCompact: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0F19), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
    ) {
        // Заголовок спойлера (кликабельный)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Details",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = parseInlineMarkdown(details.summary),
                    fontSize = if (isCompact) 11.sp else 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Toggle",
                tint = Color.LightGray,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotationAngle)
            )
        }

        // Раскрывающийся контент
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF030712))
                    .padding(8.dp)
            ) {
                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = details.codeContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8),
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Proxmox Log", details.codeContent))
                            Toast.makeText(context, "Лог скопирован в буфер", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(codeBlock: ProxmoxBlock.CodeBlock) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090D16), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        if (!codeBlock.language.isNullOrEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = codeBlock.language.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Proxmox Code", codeBlock.code))
                        Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = codeBlock.code,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF38BDF8),
                lineHeight = 15.sp
            )
        }
    }
}
