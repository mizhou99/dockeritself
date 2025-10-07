package mi.yxz.mizu.services

import kotlinx.serialization.builtins.FloatArraySerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mi.yxz.mizu.Utils
import mi.yxz.mizu.dto.ImageManifestV2
import mi.yxz.mizu.dto.ImageManifestV2List
import mi.yxz.mizu.dto.Manifest
import java.io.File
import kotlin.toString


class ManifestService(
    private val blobService: BlobService
) {
    private val rootPath = "data"
    val algo = "sha256"
    val manifestPath = "$rootPath/v2/repositories"
    val blobPath = "$rootPath/v2/blobs/$algo"
    /**
     * @param reference tag or digest
     * */
    fun isDigest(reference: String): Boolean {
//        return reference.startsWith("sha256:") && reference.length == 71
        return reference.matches(Regex("^[a-z0-9]+:[a-f0-9]{64}$"))
    }
    /**
     * @param repo like mylib/arch
     * @param reference tag or digest
     * */
    fun getManifest(repo: String, reference: String): File? {
        return if (isDigest(reference)) {
            /*digest*/
            val referHex = reference.substringAfter(":")
            val revisionLink = File("$manifestPath/$repo/_manifests","revisions/$algo/$referHex/link")
            if (!revisionLink.exists()) return null
            val digest = revisionLink.readText().trim()
            blobService.getBlobByDigest(digest)
        } else {
            /*tags*/
            val currentLink = File("$manifestPath/$repo/_manifests","tags/$reference/current/link")
            if (!currentLink.exists()) return null
//            val revisionHex = currentLink.readText().trim().substringAfter(":")
//            val revisionLink = File("$manifestPath/$repo/_manifests","revisions/$algo/$revisionHex/link")
//            if (!revisionLink.exists()) return null
//            val digest = revisionLink.readText().trim()
            val digest = currentLink.readText().trim()
            // /v2/blobs/sha256/**/******/data
            blobService.getBlobByDigest(digest)
        }
    }
    fun getManifestInfo(repo: String, reference: String): Triple<String, String, Long>? {
        val manifestFile = getManifest(repo,reference) ?: return null
        return try {
            val manifest = manifestFile.readText().trim()
            val json = Json.parseToJsonElement(manifest).jsonObject
            val mediaType = json["mediaType"]?.jsonPrimitive?.content ?: "application/vnd.docker.distribution.manifest.v2+json"
            Triple(
                "sha256:"+ Utils.calculateBytesSha256(manifest.toByteArray()),
                mediaType,
                manifest.length.toLong()
            )
        } catch (e: Exception) {
            null
        }
    }
    fun deleteManifest(repo: String, digest: String): Boolean {
        val parts = digest.split(":", limit = 2)
        if (parts.size != 2 || parts[0] != algo) return false
        val hex = parts[1]
        if (!Regex("^[a-f0-9]{64}$").matches(hex)) return false

        val repoDir = File("$manifestPath/$repo/_manifests")
        val still = File(repoDir,"tags").walkTopDown().any {
            it.isFile && it.name == "link" && it.readText().trim() == digest
        }
        if (still) {
            return false
        }
        val revision = File(repoDir,"revisions/$algo/$hex/link")
        if (revision.exists()) {
            revision.delete()
        }
        return true
    }
    fun putManifest(repo: String, reference: String, byteArray: ByteArray) {

        ensureRepositoryChildExist(repo,reference)

        val digestHex = Utils.calculateBytesSha256(byteArray)
        val revisionLink = "$manifestPath/$repo/_manifests/revisions/$algo/${digestHex}/link"

        val revisionFile = File(revisionLink)
        if (!revisionFile.parentFile.exists()) {
            revisionFile.parentFile.mkdirs()
        }
        revisionFile.writeText("$algo:$digestHex")
        val tmpFile = File.createTempFile("manifest-", ".json")
        try {
            tmpFile.writeBytes(byteArray)
            blobService.saveBlob(digestHex, tmpFile)
        } finally {
            tmpFile.delete()
        }
        if (!isDigest(reference)) {
            val tagDir = File("$manifestPath/$repo/_manifests/tags/$reference")
            if (!tagDir.exists()) {
                tagDir.mkdirs()
            }
            val currentLink = File(tagDir, "current/link")
            if (!currentLink.exists()) {
                currentLink.parentFile.mkdirs()
            }
            currentLink.writeText("$algo:$digestHex")

            val indexLink = File(tagDir, "index/$algo/$digestHex/link")
            if (!indexLink.exists()) {
                indexLink.parentFile.mkdirs()
            }
            indexLink.writeText("$algo:$digestHex")
        }
    }
    fun validateBlobsExist(manifest: ImageManifestV2): Boolean {
        if (!blobService.blobExists(manifest.config.digest)) {
            return false
        }
        return manifest.layers.all { layer ->
            blobService.blobExists(layer.digest)
        }
    }
    fun validateManifestsExist(manifestList: ImageManifestV2List): Boolean {
        return manifestList.manifests.all { manifest ->
            blobService.blobExists(manifest.digest)
        }
    }
    fun validateRepository(repo: String): Boolean {
        val repoDir = File("$manifestPath/$repo")
        return repoDir.exists() && repoDir.isDirectory
    }
    /**
     * @param repo: like library/ubuntu
     * */
    fun ensureRepositoryChildExist(repo: String, reference: String) {
        val list = listOf(
            "$manifestPath/$repo/_manifests/tags/$reference",
            "$manifestPath/$repo/_manifests/tags/$reference/current",
            "$manifestPath/$repo/_manifests/tags/$reference/index/$algo"
        )
        list.forEach { path ->
            File(path).mkdirs()
        }
    }
}