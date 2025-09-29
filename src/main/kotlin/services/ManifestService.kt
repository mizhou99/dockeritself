package mi.yxz.mizu.services

import kotlinx.serialization.builtins.FloatArraySerializer
import mi.yxz.mizu.Utils
import mi.yxz.mizu.dto.ImageManifestV2
import mi.yxz.mizu.dto.Manifest
import java.io.File



class ManifestService(
    private val blobService: BlobService
) {
    private val rootPath = ""
    private val baseDir = ""
//    private val manifestsDir = File(baseDir, "manifests").apply { mkdirs() }
    val algo = "sha256"
    val manifestPath = "$rootPath/v2/repositories"
    val blobPath = "$rootPath/v2/blobs/$algo"
    fun isDigest(reference: String): Boolean {
        return reference.startsWith("sha256:") && reference.length == 71
    }
    fun findManifest(repo: String, reference: String) {

    }
    fun getManifest(repo: String, reference: String): File? {
        return if (isDigest(reference)) {
            val hex = reference.substringAfter(":")
            val revisionLink = File("$manifestPath/$repo/_manifests","revisions/$algo/$hex/link")
            if (!revisionLink.exists()) {
                return null
            }
            val digest = revisionLink.readText().trim().substringAfter(":")
            // /v2/blobs/sha256/******/data
            File("$blobPath/$digest/data")
        } else {

            File("")
        }

    }
    fun deleteManifest(repo: String, reference: String) {

    }
    fun putManifest(repo: String, reference: String, manifest: ImageManifestV2) {
        val digest = Utils.calculateStringSha256(manifest.toString())
        if (!isDigest(reference)) {
            val tagLink = "$baseDir/$repo/_manifests/tags/$reference/current/link"
            val tagFile = File(tagLink)
            tagFile.parentFile.mkdirs()
            tagFile.writeText(digest)
        }
        val revisionLink = "$baseDir/$repo/_manifests/revisions/$algo/${digest.substringAfter(":")}/link"
        val revisionFile = File(revisionLink)
        revisionFile.parentFile.mkdirs()
        revisionFile.writeText(digest)
        /*TODO update*/
        val tmpFile = File.createTempFile("manifest-",".json")
        tmpFile.writeText(manifest.toString())
        blobService.saveBlob(digest, File(""))
        tmpFile.delete()
    }
    fun resolveReference(repo: String,reference: String): String? {
        return null
    }

}