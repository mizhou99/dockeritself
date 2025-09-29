package mi.yxz.mizu.services

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


class BlobMapService: BlobService {
    private val sessions = ConcurrentHashMap<String, Session>()
    private val rootPath = ""
    private val algo = "sha256"
    val blobPath = "$rootPath/v2/blobs/$algo"
    /**
     * @param name
     * */
    override fun createUploadSession(name: String): Session {
        val uuid = UUID.randomUUID().toString()
        var session = Session(
            uuid,
            name,
            "$blobPath/upload/$uuid"
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
        return sessions[uuid] ?: throw IllegalArgumentException("Session Not Found")
    }
    /**
     * @param uuid
     * @param name
     * */
    override fun appendChunk(uuid: String, name: String, inputStream: InputStream) {
        val session = sessions[uuid] ?: throw IllegalArgumentException("Upload session not found for UUID: $uuid")
        var tmp = getTempFile(uuid,name)

    }
    /**
     * @param uuid
     * */
    override fun deleteUploadSession(uuid: String) {
        sessions.remove(uuid)
    }
    /**
     * @param uuid
     * @param name
     * */
    override fun clearUploadedTempFiles(uuid: String, name: String) {
        val tmpUploadPath = "$rootPath/v2/repositories/$name/_uploads/$uuid"
        val tmpUpload = File(tmpUploadPath)
        if (tmpUpload.exists()) {
            tmpUpload.deleteRecursively()
        }
    }
    /**
     * @param digest
     * @return blob
     * */
    override fun findBlobByDigest(digest: String): Boolean {
        val (prefix, hex) = generateBlobChildPathFromDigest(digest)
        return File(blobPath,"${prefix}/${hex}").exists()
    }
    /**
     * @param digest
     * */
    override fun getBlobByDigest(digest: String): File? {
        val (prefix, hex) = generateBlobChildPathFromDigest(digest)
        return File(blobPath,"${prefix}/${hex}")
    }

    override fun deleteBlob(digest: String) {
        TODO("Not yet implemented")
        val (prefix, hex) = generateBlobChildPathFromDigest(digest)
        val targetBlob = File(blobPath,"${prefix}/${hex}")
        

    }
    override fun checkBlobLink(digest: String) {
        TODO("Not yet implemented")

    }
    override fun saveBlob(digest: String, blob: File) {
        val (prefix, hex) = generateBlobChildPathFromDigest(digest)
        val file = File(blobPath,"${prefix}/${hex}/data")
        file.parentFile.mkdirs()
        blob.copyTo(file, overwrite = true)
    }
    /**
     * @param digest
     * */
    fun generateBlobChildPathFromDigest(digest: String): Pair<String, String> {
        val hex = digest.substringAfter(":")
        val prefix = hex.substring(0,2)
        return Pair(prefix,hex)
    }
    override fun getTempFile(uuid: String, name: String): File? {
        val tmpUploadDir = File("$rootPath/v2/repositories/$name/_uploads/$uuid")
        if (!tmpUploadDir.exists()) {
            tmpUploadDir.mkdirs()
        }
        val tmpUploadFile = File(tmpUploadDir, "data")
        return tmpUploadFile
    }

    override fun getRedirectUrl(name: String, digest: String): String? {
        return null
    }

}