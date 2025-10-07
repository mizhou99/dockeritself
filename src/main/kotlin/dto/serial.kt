package mi.yxz.mizu.dto

import kotlinx.serialization.Serializable

@Serializable
data class ImageManifestV2(
    val schemaVersion: Int,
    val mediaType: String,
    val config: ManifestConfig,
    val layers: List<Layer>
)

@Serializable
data class ManifestConfig(
    val mediaType: String,
    val digest: String,
    val size: Int,
    val data: String? = null
)

@Serializable
data class Layer(
    val mediaType: String,
    val digest: String,
    val size: Int
)

@Serializable
data class ImageManifestV2List(
    val manifests: List<Manifest>,
    val mediaType: String,
    val schemaVersion: Int
)
@Serializable
data class Manifest(
    val annotation: Map<String, String>,
    val digest: String,
    val mediaType: String,
    val platform: Platform,
    val size: Int
)
@Serializable
data class Platform(
    val architecture: String,
    val os: String
)



