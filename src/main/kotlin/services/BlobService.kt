package mi.yxz.mizu.services

import io.ktor.util.Digest
import kotlinx.serialization.Serializable

import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * @param uuid
 * @param name
 * @param receivedSize
 * @param receivedChunks
 * @param status
 * */
@Serializable
data class Session(
    val uuid: String,
    val name: String,
    val path: String,
    var receivedSize: Long = 0,
    var receivedChunks: MutableList<ByteArray> = mutableListOf(),
    var status: Status = Status.UPLOADING
)
enum class Status {
    UPLOADING, COMPLETED, FAILED
}

interface BlobService{
    fun createUploadSession(name: String): Session
    fun findUploadSession(uuid: String): Boolean
    fun getUploadSession(uuid: String): Session?
    fun appendChunk(uuid: String, name: String,inputStream: InputStream)
    fun deleteUploadSession(uuid: String)
    fun findBlobByDigest(digest: String): Boolean
    fun getBlobByDigest(digest: String): File?
    fun deleteBlob(digest: String)
    fun checkBlobLink(digest: String)
    fun saveBlob(digest: String, blob: File)
    fun clearUploadedTempFiles(uuid: String, name: String)
    fun getTempFile(uuid: String, name: String): File?
    fun getRedirectUrl(name: String, digest: String): String?
}