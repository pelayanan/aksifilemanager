package id.aksisoft.aksifile

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import android.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class Srv(val name: String, val type: String, val host: String, val port: Int, val user: String, val pass: String)
data class RItem(val name: String, val dir: Boolean, val size: Long)
data class SftpTrustProposal(
    val hostSpec: String,
    val host: String,
    val port: Int,
    val keyType: String,
    val keyBase64: String,
    val fingerprint: String
)

interface Remote {
    fun ls(p: String): List<RItem>
    fun get(p: String, out: File)
    fun put(f: File, p: String)
    fun mkdir(p: String)
    fun rm(p: String, dir: Boolean)
    fun mv(a: String, b: String)
    fun close()
}

fun Remote.rmTree(p: String, dir: Boolean) {
    if (dir) ls(p).forEach { rmTree(p.trimEnd('/') + "/" + it.name, it.dir) }
    rm(p, dir)
}

// FTP and FTPS (explicit TLS)
class FtpR(s: Srv) : Remote {
    private val c: FTPClient = (if (s.type == "ftps") FTPSClient() else FTPClient()).apply {
        connectTimeout = 10000; connect(s.host, s.port)
        if (!login(s.user, s.pass)) throw IOException("Login failed")
        if (this is FTPSClient) { execPBSZ(0); execPROT("P") }
        enterLocalPassiveMode(); setFileType(FTP.BINARY_FILE_TYPE)
    }
    override fun ls(p: String) = c.listFiles(p).map { RItem(it.name, it.isDirectory, it.size) }
    override fun get(p: String, out: File) { out.outputStream().use { if (!c.retrieveFile(p, it)) throw IOException("Download failed: $p") } }
    override fun put(f: File, p: String) { f.inputStream().use { if (!c.storeFile(p, it)) throw IOException("Upload failed: ${f.name}") } }
    override fun mkdir(p: String) { if (!c.makeDirectory(p)) throw IOException("mkdir failed") }
    override fun rm(p: String, dir: Boolean) { if (!(if (dir) c.removeDirectory(p) else c.deleteFile(p))) throw IOException("Delete failed: $p") }
    override fun mv(a: String, b: String) { if (!c.rename(a, b)) throw IOException("Rename failed") }
    override fun close() { runCatching { c.logout(); c.disconnect() } }
}

class SftpR(s: Srv) : Remote {
    private val session = JSch().apply { setKnownHosts(Net.knownHostsFile().path) }
        .getSession(s.user, s.host, s.port).apply {
            setPassword(s.pass)
            setConfig("StrictHostKeyChecking", "yes")
            connect(10000)
        }
    private val channel = (session.openChannel("sftp") as ChannelSftp).apply { connect(10000) }
    override fun ls(p: String) = channel.ls(p).filterIsInstance<ChannelSftp.LsEntry>()
        .filter { it.filename != "." && it.filename != ".." }.map { RItem(it.filename, it.attrs.isDir, it.attrs.size) }
    override fun get(p: String, out: File) { channel.get(p, out.path) }
    override fun put(f: File, p: String) { channel.put(f.path, p) }
    override fun mkdir(p: String) { channel.mkdir(p) }
    override fun rm(p: String, dir: Boolean) { if (dir) channel.rmdir(p) else channel.rm(p) }
    override fun mv(a: String, b: String) { channel.rename(a, b) }
    override fun close() { runCatching { channel.disconnect(); session.disconnect() } }
}

object Net {
    val pool: ExecutorService = Executors.newFixedThreadPool(3)
    fun open(s: Srv): Remote = if (s.type == "sftp") SftpR(s) else FtpR(s)

    fun knownHostsFile(): File = File(Prefs.filesDir, "known_hosts").apply {
        if (!exists()) createNewFile()
    }

    private fun hostSpec(host: String, port: Int) = if (port == 22) host else "[$host]:$port"

    /**
     * Read an SFTP host key without sending the saved password. The probe only permits SSH's
     * "none" authentication method. The UI shows the fingerprint and requires explicit approval.
     */
    fun probeSftpHost(s: Srv): SftpTrustProposal {
        require(s.type == "sftp") { "Only SFTP servers have SSH host keys" }
        val session = JSch().getSession(s.user.ifBlank { "aksi-host-key-check" }, s.host, s.port).apply {
            setConfig("StrictHostKeyChecking", "no")
            setConfig("PreferredAuthentications", "none")
        }
        try {
            // Servers may reject "none" authentication after host-key negotiation; that is fine.
            runCatching { session.connect(8000) }
            val key = session.hostKey ?: throw IOException("Could not read the SFTP host key. Check the host and port.")
            val rawKey = Base64.decode(key.key, Base64.DEFAULT)
            val digest = MessageDigest.getInstance("SHA-256").digest(rawKey)
            val fingerprint = "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP).trimEnd('=')
            return SftpTrustProposal(hostSpec(s.host, s.port), s.host, s.port, key.type, key.key, fingerprint)
        } finally {
            runCatching { session.disconnect() }
        }
    }

    /** Save a host key only after explicit user confirmation; never replace a different trusted key. */
    fun trustSftpHost(proposal: SftpTrustProposal) {
        val file = knownHostsFile()
        val lines = if (file.length() > 0) file.readLines() else emptyList()
        val matching = lines.filter { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.split(Regex("\\s+")).firstOrNull() == proposal.hostSpec
        }
        if (matching.isNotEmpty()) {
            val identical = matching.any { line ->
                val columns = line.trim().split(Regex("\\s+"))
                columns.size >= 3 && columns[1] == proposal.keyType && columns[2] == proposal.keyBase64
            }
            if (identical) return
            throw IOException("The SFTP host key changed. Verify the new fingerprint with the server administrator before trusting it. Use the Forget key action only after confirming the rotation with the server administrator, then verify and trust the new fingerprint.")
        }
        file.appendText("${proposal.hostSpec} ${proposal.keyType} ${proposal.keyBase64} # trusted by Aksi File Manager\n")
    }

    /** Remove one saved trust record; the next connection must verify and trust the key again. */
    fun forgetSftpHost(s: Srv) {
        val file = knownHostsFile()
        val spec = hostSpec(s.host, s.port)
        val remaining = file.readLines().filter { line ->
            val trimmed = line.trim()
            trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.split(Regex("\\s+")).firstOrNull() != spec
        }
        file.writeText(if (remaining.isEmpty()) "" else remaining.joinToString("\n", postfix = "\n"))
    }

    fun servers(): List<Srv> = runCatching {
        JSONArray(Prefs.secureString("srv", "[]")).let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Srv(o.getString("n"), o.getString("t"), o.getString("h"), o.getInt("p"), o.getString("u"), o.getString("w"))
            }
        }
    }.getOrDefault(emptyList())

    fun saveServers(l: List<Srv>) {
        val array = JSONArray(l.map { JSONObject().put("n", it.name).put("t", it.type).put("h", it.host).put("p", it.port).put("u", it.user).put("w", it.pass) })
        Prefs.putSecureString("srv", array.toString())
    }


    // (remote path, is directory) -> flat list of (remote file, relative local path); folders are walked recursively
    fun expand(s: Srv, items: List<Pair<String, Boolean>>): List<Pair<String, String>> {
        val r = open(s); val out = mutableListOf<Pair<String, String>>()
        fun walk(p: String, rel: String) { r.ls(p).forEach { e -> val np = p.trimEnd('/') + "/" + e.name; val nr = rel + "/" + e.name; if (e.dir) walk(np, nr) else out.add(np to nr) } }
        try { items.forEach { (p, dir) -> val name = p.substringAfterLast('/'); if (dir) walk(p, name) else out.add(p to name) } } finally { r.close() }
        return out
    }

    // up to 3 worker threads, each with its own connection and a slice of the jobs. cb(done, total, failed)
    private fun <T> par(s: Srv, jobs: List<T>, cb: (Int, Int, Int) -> Unit, work: (Remote, T) -> Unit) {
        if (jobs.isEmpty()) return
        var ok = 0; var bad = 0; val lock = Any()
        jobs.chunked((jobs.size + 2) / 3).forEach { chunk ->
            pool.execute {
                val r = runCatching { open(s) }.getOrNull()
                chunk.forEach { j ->
                    val good = r != null && runCatching { work(r, j) }.isSuccess
                    synchronized(lock) { if (good) ok++ else bad++; cb(ok, jobs.size, bad) }
                }
                r?.close()
            }
        }
    }

    fun download(s: Srv, jobs: List<Pair<String, String>>, dir: File, cb: (Int, Int, Int) -> Unit) =
        par(s, jobs, cb) { r, (p, rel) -> val dst = FS.uniq(File(dir, rel)); dst.parentFile?.mkdirs(); r.get(p, dst) }

    fun upload(s: Srv, files: List<File>, remoteDir: String, cb: (Int, Int, Int) -> Unit) =
        par(s, files, cb) { r, f -> r.put(f, remoteDir.trimEnd('/') + "/" + f.name) }
}
