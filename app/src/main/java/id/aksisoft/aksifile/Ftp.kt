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
import android.os.Environment
import android.provider.OpenableColumns
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun FtpScreen() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(Net.servers()) }
    var add by remember { mutableStateOf<String?>(null) }
    var verifyTarget by remember { mutableStateOf<Srv?>(null) }
    var proposal by remember { mutableStateOf<SftpTrustProposal?>(null) }
    var trustBusy by remember { mutableStateOf(false) }
    var trustMessage by remember { mutableStateOf<String?>(null) }
    var forgetTarget by remember { mutableStateOf<Srv?>(null) }

    fun verify(s: Srv) {
        verifyTarget = s; proposal = null; trustMessage = null; trustBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Net.probeSftpHost(s) } }
            trustBusy = false
            result.onSuccess { proposal = it }.onFailure { trustMessage = it.message ?: "Could not read host key" }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("FTP / SFTP", { Nav.back() })
        listOf(Triple("📡", "FTP Server", "ftp.example.com") to "ftp", Triple("🔐", "SFTP Server", "sftp.example.com") to "sftp").forEach { (tr, type) ->
            Row(Modifier.fillMaxWidth().padding(12.dp, 4.dp).clip(RoundedCornerShape(18.dp)).background(K.card)
                .clickable { add = type }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr.first, fontSize = 22.sp)
                Column(Modifier.padding(start = 12.dp)) { Text(tr.second); Text(tr.third, fontSize = 12.sp, color = K.sub) }
            }
        }
        Button({ add = "ftp" }, Modifier.fillMaxWidth().padding(12.dp, 4.dp)) { Text("＋  " + t("Add Server")) }
        Sec("Server List")
        list.forEach { server ->
            Row(Modifier.fillMaxWidth().padding(12.dp, 4.dp).clip(RoundedCornerShape(18.dp)).background(K.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (server.type == "sftp") "🔐" else "📡", fontSize = 22.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp).clickable { Nav.go("remote", server) }) {
                    Text(server.name, maxLines = 1)
                    Text("${server.type.uppercase()}  ${server.host}:${server.port}", fontSize = 12.sp, color = K.sub, maxLines = 1)
                    if (server.type == "sftp") Text("Host identity checked before connecting", fontSize = 10.sp, color = K.sub)
                }
                if (server.type == "sftp") {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton({ verify(server) }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Verify key", fontSize = 11.sp) }
                        TextButton({ forgetTarget = server }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Forget key", fontSize = 10.sp) }
                    }
                }
                Text("🗑", Modifier.clickable { list = list - server; Net.saveServers(list) }.padding(8.dp))
            }
        }
    }
    add?.let { type0 ->
        var type by remember { mutableStateOf(type0) }
        var name by remember { mutableStateOf("") }; var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf(if (type0 == "sftp") "22" else "21") }
        var user by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }
        AlertDialog({ add = null },
            confirmButton = { TextButton({
                val parsedPort = port.toIntOrNull()
                if (host.isBlank() || user.isBlank() || parsedPort == null || parsedPort !in 1..65535) {
                    Toast.makeText(c, "Enter a valid host, user, and port", Toast.LENGTH_LONG).show()
                } else {
                    val srv = Srv(name.trim().ifBlank { host.trim() }, type, host.trim(), parsedPort, user.trim(), pass)
                    list = list.filterNot { it.host.equals(srv.host, true) && it.port == srv.port && it.type == srv.type } + srv
                    Net.saveServers(list); add = null
                }
            }) { Text(t("Save")) } },
            dismissButton = { TextButton({ add = null }) { Text(t("Cancel")) } },
            title = { Text(t("Add Server")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == "ftp", { type = "ftp"; port = "21" }, { Text("FTP") })
                    FilterChip(type == "ftps", { type = "ftps"; port = "21" }, { Text("FTPS") })
                    FilterChip(type == "sftp", { type = "sftp"; port = "22" }, { Text("SFTP") })
                }
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") })
                OutlinedTextField(host, { host = it }, singleLine = true, label = { Text("Host") })
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, singleLine = true, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(user, { user = it }, singleLine = true, label = { Text("User") })
                OutlinedTextField(pass, { pass = it }, singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
                Text("Server passwords are encrypted with Android Keystore.", fontSize = 11.sp, color = K.sub)
            } })
    }

    forgetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { forgetTarget = null },
            title = { Text("Forget SFTP host key?") },
            text = { Text("This removes the saved key for ${target.host}:${target.port}. Future SFTP connections will be blocked until you verify the new fingerprint and trust it again. Do this only when you intend to rotate or reset the server key.") },
            confirmButton = { TextButton({
                val result = runCatching { Net.forgetSftpHost(target) }
                Toast.makeText(c, if (result.isSuccess) "Saved host key forgotten" else (result.exceptionOrNull()?.message ?: "Could not remove host key"), Toast.LENGTH_LONG).show()
                forgetTarget = null
            }) { Text("Forget key") } },
            dismissButton = { TextButton({ forgetTarget = null }) { Text(t("Cancel")) } }
        )
    }

    if (verifyTarget != null) {
        val target = verifyTarget!!
        AlertDialog(
            onDismissRequest = { verifyTarget = null; proposal = null; trustMessage = null; trustBusy = false },
            title = { Text(if (proposal != null) "Verify SFTP host key" else "SFTP host verification") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${target.host}:${target.port}", fontWeight = FontWeight.SemiBold)
                    when {
                        trustBusy -> { CircularProgressIndicator(); Text("Reading the host key without sending the saved password…") }
                        proposal != null -> {
                            Text("Key type: ${proposal!!.keyType}")
                            Text("Fingerprint", fontSize = 12.sp, color = K.sub)
                            Text(proposal!!.fingerprint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Only trust this key after comparing its fingerprint with the server administrator or another trusted channel. A first-time trust decision cannot by itself rule out a man-in-the-middle attack.", fontSize = 12.sp)
                        }
                        trustMessage != null -> Text(trustMessage!!, color = MaterialTheme.colorScheme.error)
                        else -> Text("No host key is available.")
                    }
                }
            },
            confirmButton = {
                if (proposal != null && !trustBusy) TextButton({
                    val chosen = proposal!!
                    proposal = null; trustBusy = true; trustMessage = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { Net.trustSftpHost(chosen) } }
                        trustBusy = false
                        result.onSuccess {
                            Toast.makeText(c, "Trusted ${chosen.hostSpec} (${chosen.fingerprint})", Toast.LENGTH_LONG).show()
                            verifyTarget = null
                        }.onFailure { trustMessage = it.message ?: "Could not save host key" }
                    }
                }) { Text("Trust key") }
                else TextButton({ verifyTarget = null; proposal = null; trustMessage = null }) { Text(if (trustMessage != null) "Close" else "Cancel") }
            },
            dismissButton = {
                if (proposal != null && !trustBusy) TextButton({ verifyTarget = null; proposal = null; trustMessage = null }) { Text("Cancel") }
            }
        )
    }
}

// Remote browser: browse, upload (multi-file, threaded), download (files + folders, threaded), mkdir, rename, delete
@Composable
fun RemoteScreen(s: Srv) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("/") }
    var rows by remember { mutableStateOf<List<RItem>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var msg by remember { mutableStateOf("") }
    var dlg by remember { mutableStateOf("") }
    var inp by remember { mutableStateOf("") }
    fun full(n: String) = path.trimEnd('/') + "/" + n
    fun load() {
        scope.launch {
            rows = null; err = null
            withContext(Dispatchers.IO) { runCatching { val r = Net.open(s); try { r.ls(path) } finally { r.close() } } }
                .onSuccess { l -> rows = l.sortedWith(compareBy<RItem>({ !it.dir }, { it.name.lowercase() })) }
                .onFailure { e -> err = e.message ?: "Error" }
        }
    }
    fun up() { path = path.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" } }
    fun prog(reload: Boolean): (Int, Int, Int) -> Unit = { ok, tot, bad ->
        msg = "$ok/$tot" + (if (bad > 0) "  ✗$bad" else "")
        if (reload && ok + bad == tot) scope.launch { load() }
    }
    fun op(block: (Remote) -> Unit) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { val rm = Net.open(s); try { block(rm) } finally { rm.close() } } }
            msg = if (r.isSuccess) "✓" else (r.exceptionOrNull()?.message ?: "Error"); sel = setOf(); load()
        }
    }
    LaunchedEffect(path) { sel = setOf(); load() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { us ->
        if (us.isNotEmpty()) scope.launch {
            msg = "⬆ …"
            val files = withContext(Dispatchers.IO) {
                us.mapIndexedNotNull { i, u ->
                    runCatching {
                        val n = c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { q -> if (q.moveToFirst()) q.getString(0) else null } ?: "file$i"
                        val tmp = File(c.cacheDir, n)
                        c.contentResolver.openInputStream(u)!!.use { src -> tmp.outputStream().use { src.copyTo(it) } }; tmp
                    }.getOrNull()
                }
            }
            Net.upload(s, files, path, prog(true))
        }
    }
    BackHandler(path != "/") { up() }
    Column(Modifier.fillMaxSize()) {
        TopBar(s.name + "  " + path, { if (path != "/") up() else Nav.back() }) {
            TextButton({ inp = ""; dlg = "mkdir" }) { Text("📁＋") }
            TextButton({ pick.launch("*/*") }) { Text("⬆") }
        }
        val l = rows
        when {
            err != null -> Text(err!!, Modifier.padding(20.dp), color = Color(0xFFEF4444))
            l == null -> CircularProgressIndicator(Modifier.padding(32.dp))
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                items(l) { e ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (e.name in sel) BLUE.copy(alpha = .2f) else Color.Transparent)
                        .clickable { if (e.dir) path = full(e.name) else sel = if (e.name in sel) sel - e.name else sel + e.name }.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(e.name in sel, { sel = if (it) sel + e.name else sel - e.name })
                        Text(if (e.dir) "📁" else "📄", fontSize = 20.sp)
                        Text(e.name, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1)
                        if (!e.dir) Text(FS.fmt(e.size), fontSize = 12.sp, color = K.sub)
                    }
                }
            }
        }
        if (sel.isNotEmpty() || msg.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(msg, Modifier.weight(1f), fontSize = 12.sp)
            if (sel.isNotEmpty()) {
                Button({
                    val names = sel.toList(); val dirs = rows?.filter { it.dir }?.map { it.name }?.toSet() ?: emptySet(); sel = setOf(); msg = "⬇ …"
                    scope.launch {
                        val jobs = withContext(Dispatchers.IO) { runCatching { Net.expand(s, names.map { full(it) to (it in dirs) }) } }
                        jobs.onSuccess { Net.download(s, it, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Aksi"), prog(false)) }
                            .onFailure { msg = it.message ?: "Error" }
                    }
                }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("⬇ ${sel.size}") }
                if (sel.size == 1) OutlinedButton({ inp = sel.first(); dlg = "rename" }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("✎") }
                OutlinedButton({ dlg = "delete" }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("🗑") }
            }
        }
    }
    when (dlg) {
        "mkdir", "rename" -> AlertDialog({ dlg = "" },
            confirmButton = { TextButton({
                val d = dlg; val n = inp.trim(); val old = sel.firstOrNull(); dlg = ""
                if (n.isNotEmpty()) { if (d == "mkdir") op { it.mkdir(full(n)) } else if (old != null) op { it.mv(full(old), full(n)) } }
            }) { Text("OK") } },
            dismissButton = { TextButton({ dlg = "" }) { Text(t("Cancel")) } },
            title = { Text(t(if (dlg == "mkdir") "New folder" else "Rename")) }, text = { OutlinedTextField(inp, { inp = it }, singleLine = true) })
        "delete" -> AlertDialog({ dlg = "" },
            confirmButton = { TextButton({
                val names = sel.toList(); val dirs = rows?.filter { it.dir }?.map { it.name }?.toSet() ?: emptySet(); dlg = ""
                op { r -> names.forEach { r.rmTree(full(it), it in dirs) } }
            }) { Text(t("Delete")) } },
            dismissButton = { TextButton({ dlg = "" }) { Text(t("Cancel")) } }, text = { Text(t("Delete selected?") + " (${sel.size})") })
    }
}
