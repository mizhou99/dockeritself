package mi.yxz.mizu.services

import io.ktor.util.Digest
import kotlinx.serialization.Serializable

import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * @param uuid
 * @param name
 * @param uploadTempPath
 * @param receivedSize
 * @param status
 * */
@Serializable
data class Session(
    val uuid: String,
    val name: String,
    val uploadTempPath: String,
    var receivedSize: Long = 0,
    var status: Status = Status.UPLOADING
)
enum class Status {
    UPLOADING, COMPLETED, FAILED
}

interface BlobService{
    fun createUploadSession(name: String): Session?
    fun findUploadSession(uuid: String): Boolean
    fun getUploadSession(uuid: String): Session?
    fun appendChunk(uuid: String, name: String, inputStream: InputStream): Long
    fun deleteUploadSession(uuid: String)
    fun completeUploadSession(uuid: String)
    fun getBlobByDigest(digest: String): File?
    fun blobExists(digest: String): Boolean
    fun deleteBlob(digest: String): Boolean
    fun checkBlobLink(digest: String)
    fun saveBlob(digestHex: String, blob: File)
    fun clearUploadedTempFiles(uuid: String, name: String)
    fun getTempFile(uuid: String, name: String): File?
    fun getRedirectUrl(name: String, digest: String): String?
    fun checkRepository(repo: String): Boolean
    fun ensureRepositoryDirectoryExist(repo: String)
}