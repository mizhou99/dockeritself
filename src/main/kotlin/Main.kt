package mi.yxz.mizu


import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.util.url
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.toByteArray

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mi.yxz.mizu.dto.ImageManifestV2
import mi.yxz.mizu.services.BlobMapService
import mi.yxz.mizu.services.BlobService
import mi.yxz.mizu.services.ManifestService
import java.io.File

fun main() {
    val blobService = BlobMapService()
    val manifestService = ManifestService(blobService)

    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0"
    ) {
        install(ContentNegotiation) {
            json(
                Json {
                    isLenient = true
                }
            )
        }

        routing {
            route("/v2") {
                blobsRoutes(blobService)
                manifestsRoutes(manifestService)
            }
        }
    }
}
fun Route.blobsRoutes(
    blobService: BlobService
) {
    route("/{name...}") {
        route("/blobs/{digest}") {
            get {
                val name = call.parameters.getAll("name")?.joinToString("/") ?: return@get call.respond(HttpStatusCode.BadRequest)
                val digest = call.parameters["digest"]!!
                val blobFile = blobService.getBlobByDigest(digest)
                if (blobFile == null || !blobFile.exists()) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }
                val redirect = blobService.getRedirectUrl(name,digest)
                if (redirect != null) {
                    call.response.headers.append("Location",redirect)
                    return@get call.respond(HttpStatusCode.TemporaryRedirect)
                }
                call.response.headers.append("Content-Length", blobFile.length().toString())
                call.response.headers.append("Content-Type","application/octet-stream")
                call.response.headers.append("Docker-Content-Digest", Utils.calculateFileSha256(blobFile))
                call.respondOutputStream(
                    status = HttpStatusCode.OK,
                    contentType = ContentType.Application.OctetStream
                ) {
                    blobFile.inputStream().use { input ->
                        input.copyTo(this)
                    }
                }
            }
            head {
                val name = call.parameters.getAll("name")?.joinToString("/") ?: return@head call.respond(HttpStatusCode.BadRequest)
                val digest = call.parameters["digest"]!!
                val blobFile = blobService.getBlobByDigest(digest)
                if (blobFile == null || !blobFile.exists()) {
                    return@head call.respond(HttpStatusCode.NotFound)
                }
                call.response.headers.append("Content-Length", blobFile.length().toString())
                call.response.headers.append("Docker-Content-Digest", Utils.calculateFileSha256(blobFile))
                call.response.headers.append("Content-Type","application/octet-stream")
                call.respond(HttpStatusCode.OK)
            }
        }
        route("/blobs/uploads") {
            post {
                val name = call.parameters.getAll("name")?.joinToString("/") ?: return@post call.respond(HttpStatusCode.BadRequest)
                val mount = call.queryParameters["mount"]
                val from = call.queryParameters["from"]

                val session = blobService.createUploadSession(name)
                call.response.headers.append("Location",session.path)
                call.response.headers.append("Docker-Upload-UUID",session.uuid)
                call.response.headers.append("Range","0-0")
                call.response.headers.append("Content-Length","0")
                //return 202
                call.respond(HttpStatusCode.Accepted)
            }
            route("/{uuid}") {
                get {
                    val name = call.parameters["name"]!!
                    val uuid = call.parameters["uuid"]!!
                    val session = blobService.getUploadSession(uuid)

                    val uploadedBytes = 0
                    if(false) {
                        return@get call.respond(HttpStatusCode.NotFound)
                    }

                    call.response.headers.append("Range",uploadedBytes.toString())
                    call.response.headers.append("Docker-Upload-UUID","")
                    call.response.headers.append("Location","")
                    call.respond(HttpStatusCode.NoContent)
                }
                patch {
                    val name = call.parameters.getAll("name")?.joinToString("/") ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val uuid = call.parameters["uuid"]!!
                    if (!blobService.findUploadSession(uuid)) {
                        return@patch call.respond(HttpStatusCode.NotFound)
                    }
                    val receivedBytesInputStream = call.receiveChannel().toInputStream()
                    blobService.appendChunk(
                        name,
                        uuid,
                        receivedBytesInputStream
                    )
                    call.response.headers.append("Location","")
                    call.response.headers.append("Range","")
                    call.response.headers.append("Docker-Upload-UUID",uuid)
                    call.respond(HttpStatusCode.Accepted)

                }
                put {
                    val name = call.parameters.getAll("name")?.joinToString("/") ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val uuid = call.parameters["uuid"]!!
                    val digest = call.queryParameters["digest"]
                    if (digest == null) {
                        return@put call.respond(HttpStatusCode.BadRequest)
                    }
                    val tmpFile = blobService.getTempFile(uuid,name)
                    if (tmpFile == null) {
                        return@put
                    }
                    var session = blobService.getUploadSession(uuid)
                    if (session == null) {
                        return@put call.respond(HttpStatusCode.NotFound)
                    }
                    if (session.receivedSize == 0L) {
                        val monolithic = call.receive<ByteArray>()
                        tmpFile.writeBytes(monolithic)
                        session.receivedSize = monolithic.size.toLong()
                        val calculatedDigest = Utils.calculateFileSha256(tmpFile)
                        if (calculatedDigest != digest) {
                            return@put call.respond(HttpStatusCode.BadRequest, "Digest mismatch")
                        }
                        blobService.saveBlob(calculatedDigest,tmpFile)
                    } else {
                        val last = call.receiveChannel().toInputStream()
                        blobService.appendChunk(uuid,name,last)
                        val calculatedDigest = Utils.calculateFileSha256(tmpFile)
                        if (calculatedDigest != digest) {
                            return@put call.respond(HttpStatusCode.BadRequest, "Digest mismatch")
                        }
                        blobService.saveBlob(calculatedDigest,tmpFile)
                    }
                    blobService.deleteUploadSession(uuid)

                    call.response.headers.append("Docker-Content-Digest","")
                    call.response.headers.append("Location","")
                    call.response.headers.append("Content-Length","0")
                    call.respond(HttpStatusCode.Created)

                    if (false) {
                        call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                    }
                }
                delete {
                    val name = call.parameters["name"]!!
                    val uuid = call.parameters["uuid"]!!
                    if (!blobService.findUploadSession(uuid)) {
                        call.respond(HttpStatusCode.NotFound)
                    }
                    blobService.deleteUploadSession(uuid = uuid)
                    blobService.clearUploadedTempFiles(uuid,name)
                    call.response.headers.append("Content-Length","0")
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

    }
}
fun Route.manifestsRoutes(
    manifestService: ManifestService
) {
    route("/{name...}/manifests/{reference}") {
        get {
            val name = call.parameters.getAll("name")?.joinToString("/") ?: return@get call.respond(HttpStatusCode.BadRequest)

            val reference = call.parameters["reference"]!!



            val supportedTypes = listOf(
                "application/vnd.docker.distribution.manifest.v2+json",
                "application/vnd.docker.distribution.manifest.list.v2+json",
                "application/vnd.oci.image.manifest.v1+json",
                "application/vnd.oci.image.index.v1+json"
            )

            val type = call.request.headers["Accept"]
            if (type.toString() == "application/vnd.docker.distribution.manifest.list.v2+json") {

            }
            if (type.toString() == "application/vnd.docker.distribution.manifest.v2+json") {

            }


            /*TODO*/
            val manifestFile = manifestService.getManifest(name,reference) ?: return@get call.respond(HttpStatusCode.NotFound)
            val manifestV2 = Json.decodeFromString<ImageManifestV2>(manifestFile.readText())

            call.response.headers.append("Docker-Content-Digest", Utils.calculateFileSha256(manifestFile))
            call.response.headers.append("Content-Type",manifestV2.mediaType)
            call.respond(
                status = HttpStatusCode.OK,
                manifestV2
            )
        }
        put {
            val name = call.parameters["name"]!!
            val reference = call.parameters["reference"]!!

            val contentType = call.request.headers["Content-Type"]

            //body
            val manifest = call.receive<ImageManifestV2>()

            if (manifest.schemaVersion != 2) {
                return@put call.respond(HttpStatusCode.BadRequest)
            }
            
            manifestService.putManifest(name,reference,manifest)
            val digest = Utils.calculateStringSha256(manifest.toString())
            
            call.response.headers.append("Docker-Content-Digest",digest)
            call.response.headers.append("Location","")
            call.response.headers.append("Content-Length","0")

            call.respond(HttpStatusCode.Created)
        }
        head {
            val name = call.parameters["name"]!!
            val reference = call.parameters["reference"]!!

            call.response.headers.append("Content-Length","...length...")
            call.response.headers.append("Docker-Content-Digest","...sha256...")
            call.response.headers.append("Content-Type","...type...")
            call.respond(HttpStatusCode.OK)
        }
        delete {
            val name = call.parameters["name"]!!
            val reference = call.parameters["reference"]!!
            if (!reference.startsWith("sha256")) {
                return@delete call.respond(HttpStatusCode.MethodNotAllowed)
            }
            if (manifestService.findByDigest(reference) == null) {
                return@delete call.respond(HttpStatusCode.NotFound)
            }

            if (false) {
                return@delete call.respond(HttpStatusCode.Forbidden)
            }
            call.respond(HttpStatusCode.Accepted)
        }
    }
}