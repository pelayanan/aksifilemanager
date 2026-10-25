package id.aksisoft.aksifile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import java.io.File
import android.webkit.WebView
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class Hl(private val ext: String, private val find: String = "") : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val b = AnnotatedString.Builder(s)
        fun p(r: Regex, c: Color) = r.findAll(s).forEach { b.addStyle(SpanStyle(color = c), it.range.first, it.range.last + 1) }
        p(Regex("\\b(fun|val|var|class|function|const|let|return|if|else|for|while|import|package|def|true|false|null)\\b"), Color(0xFFC792EA))
        p(Regex("\\b\\d+\\b"), Color(0xFFF78C6C))
        if (ext in setOf("html", "htm", "xml")) p(Regex("</?[a-zA-Z][^>]*>"), Color(0xFF82AAFF))
        if (ext == "css") p(Regex("[.#]?[a-zA-Z][\\w-]*(?=\\s*\\{)"), Color(0xFF82AAFF))
        if (ext == "md") p(Regex("(?m)^#+ .*$"), Color(0xFF22D3EE))
        p(Regex("\"[^\"\\n]*\"|'[^'\\n]*'"), Color(0xFFC3E88D))
        p(Regex("//.*|/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->"), Color(0xFF64748B))
        if (find.isNotEmpty()) Regex(Regex.escape(find), RegexOption.IGNORE_CASE).findAll(s).forEach { b.addStyle(SpanStyle(background = Color(0x66FBBF24)), it.range.first, it.range.last + 1) }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

fun md2html(src: String): String {
    val out = StringBuilder("<meta name=viewport content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif;padding:16px;line-height:1.5;color-scheme:light dark}" +
        "pre{background:#8882;padding:10px;border-radius:8px;overflow:auto}code{background:#8882;padding:2px 4px;border-radius:4px}blockquote{border-left:4px solid #8886;margin:0;padding-left:12px}</style>")
    var inCode = false; var inList = false
    fun inl(s: String) = Office.esc(s).replace(Regex("`([^`]+)`"), "<code>$1</code>").replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        .replace(Regex("\\*(.+?)\\*"), "<i>$1</i>").replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
    src.lines().forEach { l ->
        when {
            l.trimStart().startsWith("```") -> { out.append(if (inCode) "</pre>" else "<pre>"); inCode = !inCode }
            inCode -> out.append(Office.esc(l)).append('\n')
            else -> {
                val li = Regex("^\\s*([-*]|\\d+\\.)\\s+(.*)").find(l)
                if (li != null && !inList) { out.append("<ul>"); inList = true } else if (li == null && inList) { out.append("</ul>"); inList = false }
                out.append(when {
                    li != null -> "<li>${inl(li.groupValues[2])}</li>"
                    l.startsWith("#") -> { val n = l.takeWhile { it == '#' }.length.coerceAtMost(6); "<h$n>${inl(l.drop(n).trim())}</h$n>" }
                    l.startsWith("> ") -> "<blockquote>${inl(l.drop(2))}</blockquote>"
                    l.trim() == "---" -> "<hr>"
                    l.isBlank() -> ""
                    else -> "<p>${inl(l)}</p>"
                })
            }
        }
    }
    if (inList) out.append("</ul>")
    if (inCode) out.append("</pre>")
    return out.toString()
}

@Composable
fun Editor(f: File) {
    val c = LocalContext.current
    var s by remember { mutableStateOf(runCatching { f.readText() }.getOrDefault("")) }
    var prev by remember { mutableStateOf(false) }
    var findOn by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var rep by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var saveAs by remember { mutableStateOf(false) }
    val ext = f.extension.lowercase()
    val st = TextStyle(color = K.tx, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
    fun save() { f.writeText(s); Nav.tick++; Toast.makeText(c, "✓", Toast.LENGTH_SHORT).show() }
    Column(Modifier.fillMaxSize()) {
        TopBar(f.name, { Nav.back() }) {
            TextButton({ findOn = !findOn }) { Text("🔎") }
            if (ext in setOf("html", "htm", "md")) TextButton({ prev = !prev }) { Text(t("Preview")) }
            Button({ save() }) { Text(t("Save")) }
            Box {
                TextButton({ menu = true }) { Text("⋮", color = K.tx) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(t("Save As")) }, { menu = false; saveAs = true })
                    DropdownMenuItem({ Text(t("Open in browser")) }, { menu = false; save(); FS.open(c, f, "text/html") })
                    DropdownMenuItem({ Text(t("Share")) }, { menu = false; save(); FS.share(c, f) })
                }
            }
        }
        if (findOn) Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(find, { find = it }, Modifier.weight(1f), singleLine = true, label = { Text(t("Find")) })
            OutlinedTextField(rep, { rep = it }, Modifier.weight(1f), singleLine = true, label = { Text(t("Replace")) })
            Button({ if (find.isNotEmpty()) s = s.replace(find, rep, ignoreCase = true) }) { Text("✓") }
        }
        if (prev) AndroidView({ WebView(it).apply { settings.javaScriptEnabled = true; settings.allowFileAccess = true } }, Modifier.weight(1f).fillMaxWidth(),
            update = { it.loadDataWithBaseURL("file://" + f.parent + "/", if (ext == "md") md2html(s) else s, "text/html", "utf-8", null) })
        else Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text((1..(s.count { it == '\n' } + 1)).joinToString("\n"), Modifier.padding(start = 8.dp, end = 4.dp, top = 12.dp), style = st.copy(color = K.sub), textAlign = TextAlign.End)
            BasicTextField(s, { s = it }, Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(12.dp), textStyle = st,
                cursorBrush = SolidColor(BLUE), visualTransformation = Hl(ext, find))
        }
        Row(Modifier.fillMaxWidth().background(K.card).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(ext.uppercase().ifEmpty { "TXT" }, fontSize = 11.sp, color = K.sub, modifier = Modifier.weight(1f))
            Text("UTF-8  •  ${s.count { it == '\n' } + 1} lines", fontSize = 11.sp, color = K.sub)
        }
    }
    if (saveAs) {
        var name by remember { mutableStateOf(f.nameWithoutExtension + "_copy" + (if (f.extension.isEmpty()) "" else "." + f.extension)) }
        AlertDialog({ saveAs = false },
            confirmButton = { TextButton({
                val nf = FS.uniq(File(f.parentFile, name)); nf.writeText(s); saveAs = false; Nav.tick++; Nav.back(); Nav.go("editor", nf)
            }) { Text(t("Save")) } },
            dismissButton = { TextButton({ saveAs = false }) { Text(t("Cancel")) } },
            title = { Text(t("Save As")) }, text = { OutlinedTextField(name, { name = it }, singleLine = true) })
    }
}
