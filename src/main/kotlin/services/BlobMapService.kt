package mi.yxz.mizu.services

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


class BlobMapService: BlobService {
    private val sessions = ConcurrentHashMap<String, Session>()
    private val rootPath = "data"
    private val algo = "sha256"
    val blobPath = "$rootPath/v2/blobs/$algo"
    val manifestPath = "$rootPath/v2/repositories"
    init {
        listOf(blobPath,manifestPath).forEach { path ->
            File(path).mkdirs()
        }
    }
    /**
     * @param name
     * */
    override fun createUploadSession(name: String): Session? {
        val uuid = UUID.randomUUID().toString()
        val uploadDataDir = File("$manifestPath/$name/_uploads/$uuid")
        if (!uploadDataDir.exists()) {
            uploadDataDir.mkdirs()
        }
        val data = File(uploadDataDir,"data")
        if (!uploadDataDir.canWrite() || !data.createNewFile() || !data.exists() || !data.isFile) {
            return null
        }
        var session = Session(
            uuid,
            name,
            "$manifestPath/$name/_uploads/$uuid/data"
        )
        sessions[uuid] = session
        return session
    }
    /**
     * @param uuid
     * */
    override fun findUploadSession(uuid: String): Boolean {
        return sessions.containsKey(uuid)
    }
    /**
     * @param uuid
     * */
    override fun getUploadSession(uuid: String): Session? {
        return sessions[uuid]
    }
    /**
     * @param uuid
     * @param name: like library/ubuntu
     * */
    override fun appendChunk(uuid: String, name: String, inputStream: InputStream): Long {
        val session = sessions[uuid] ?: throw IllegalArgumentException("Upload session not found for UUID: $uuid")
        if (session.name != name) {
            throw IllegalArgumentException("Name mismatch")
        }
        if (session.status != Status.UPLOADING) {
            throw IllegalArgumentException("Upload not in progress")
        }
        val bufferedInputStream = inputStream.buffered()
        val data = File(session.uploadTempPath)
        val bytesWritten = FileOutputStream(data,true).use { output ->
            bufferedInputStream.copyTo(output)
        }
        session.receivedSize += bytesWritten
        return session.receivedSize
    }
    /**
     * @param uuid
     * */
    override fun deleteUploadSession(uuid: String) {
        sessions[uuid]?.status = Status.FAILED
        sessions.remove(uuid)
    }

    override fun completeUploadSession(uuid: String) {
        sessions[uuid]?.status = Status.COMPLETED
        sessions.remove(uuid)
    }

    /**
     * @param uuid
     * @param name: like library/ubuntu
     * */
    override fun clearUploadedTempFiles(uuid: String, name: String) {
        val tmpUploadPath = "$manifestPath/$name/_uploads/$uuid"
        val tmpUpload = File(tmpUploadPath)
        if (tmpUpload.exists()) {
            tmpUpload.deleteRecursively()
        }
    }
    /**
     * @param digest: like sha256:wwwwwwww
     * */
    override fun getBlobByDigest(digest: String): File? {
        val (prefix, hex) = generateBlobChildPathFromDigest(digest)
        val file = File(blobPath,"${prefix}/${hex}/data")
        return if (file.exists() && file.isFile) file else null
    }
    override fun blobExists(digest: String): Boolean {
        val (prefix, hex) = generateBlobChildPathFromDigest(digest)
        val file = File(blobPath,"${prefix}/${hex}/data")
        return (file.exists() && file.isFile)
    }
    override fun deleteBlob(digest: String): Boolean {
        val (prefix, hex) = generateBlobChildPathFromDigest(digest)
        val targetBlob = File(blobPath,"${prefix}/${hex}/data")
        return false
    }
    override fun checkBlobLink(digest: String) {
        TODO("Not yet implemented")
    }
    /**
     * @param digestHex: like wwwwwwww (no prefix 'sha256:')
     * @param blob: temp file
     * */
    override fun saveBlob(digestHex: String, blob: File) {
        val file = File(blobPath,"${digestHex.substringAfter(":").substring(0,2)}/${digestHex}/data")
        file.parentFile.mkdirs()
        blob.copyTo(file, overwrite = true)
    }
    /**
     * @param digest: like sha256:wwwwwwww
     * */
    fun generateBlobChildPathFromDigest(digest: String): Pair<String, String> {
        val hex = digest.substringAfter(":")
        val prefix = hex.substring(0,2)
        return Pair(prefix,hex)
    }
    /**
     * @param uuid
     * */
    @Deprecated("no use")
    override fun getTempFile(uuid: String, name: String): File? {
        val tmpUploadDir = File("$rootPath/v2/repositories/$name/_uploads/$uuid")
        if (!tmpUploadDir.exists()) {
            tmpUploadDir.mkdirs()
        }
        val tmpUploadFile = File(tmpUploadDir, "data")
        return tmpUploadFile
    }

    override fun getRedirectUrl(name: String, digest: String): String? {
        /*TODO*/
        return null
    }
    /**
     * @param repo: such as 'library/ubuntu'
     * */
    override fun checkRepository(repo: String): Boolean {
        val repoDir = File("$manifestPath/$repo")
        val exists = repoDir.exists() && repoDir.isDirectory
        return exists
    }

    override fun ensureRepositoryDirectoryExist(repo: String) {
        val list = listOf(
            "$manifestPath/$repo/_layers/$algo",
            "$manifestPath/$repo/_manifests/revisions/$algo",
            "$manifestPath/$repo/_manifests/tags",
            "$manifestPath/$repo/_uploads"
        )
        list.forEach { path ->
            File(path).mkdirs()
        }
    }
}