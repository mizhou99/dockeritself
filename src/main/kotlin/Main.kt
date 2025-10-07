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
import io.ktor.server.request.path
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
import io.ktor.server.engine.*
import io.ktor.server.netty.EngineMain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mi.yxz.mizu.auth.Auth
import mi.yxz.mizu.dto.ImageManifestV2
import mi.yxz.mizu.dto.ImageManifestV2List
import mi.yxz.mizu.services.BlobMapService
import mi.yxz.mizu.services.BlobService
import mi.yxz.mizu.services.ManifestService
import java.io.File

fun main(args: Array<String>) {
    //?
    val appArg = args[0]
    EngineMain.main(arrayOf(appArg))
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}
fun Application.module(
    blobService: BlobService = BlobMapService(),
    manifestService: ManifestService = ManifestService(blobService),
    auth: Auth = Auth()
) {
    val env = try {
        environment.config.property("app.env").getString()
    } catch (e: Exception) {
        "linux"
    }
    install(ContentNegotiation) {
        json(
            Json {
                isLenient = true
                prettyPrint = true
            }
        )
    }
    routing {
        route("/v2") {
            blobsRoutes(blobService)
            manifestsRoutes(manifestService)
        }
        route("/admin") {
            get("/gc") {
                call.respond(HttpStatusCode.OK,"Garbage Collection")
            }
        }
        // simulate auth
        route("/auth") {
            get {
                call.respondText { auth.token() }
            }
        }
    }
}
fun Route.blobsRoutes(
    blobService: BlobService
) {
    route("{namespace}/{repository}/blobs") {
        route("/uploads/") {
            post {
                val namespace = call.parameters["namespace"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val repository = call.parameters["repository"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val name = "$namespace/$repository"
                /*TODO ???*/
                val repoNameRegex = Regex("^[a-z0-9]+([._-][a-z0-9]+)*(?:/[a-z0-9]+([._-][a-z0-9]+)*)*\$")
                if (!repoNameRegex.matches(name)) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid repository name")
                }
                val ex = blobService.checkRepository(name)
                println("Repository exists: $ex")
                if (!ex) {
                    /*404*/
                    return@post call.respond(HttpStatusCode.NotFound,"Repository not found")
                }
                blobService.ensureRepositoryDirectoryExist(name)
                val mount = call.queryParameters["mount"]
                val from = call.queryParameters["from"]
                if (mount != null && from != null) {
                    /*201*/
                    call.respond(HttpStatusCode.Created)
                    return@post
                }
                val session = blobService.createUploadSession(name) ?: return@post call.respond(HttpStatusCode.InternalServerError)
                val location = "/v2/$name/blobs/uploads/${session.uuid}"
                call.response.headers.append("Location",location)
                call.response.headers.append("Docker-Upload-UUID",session.uuid)
                call.response.headers.append("Range","0-0")
                /*202*/
                call.respond(HttpStatusCode.Accepted)
            }
            route("/{uuid}") {
                get {
                    val namespace = call.parameters["namespace"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val repository = call.parameters["repository"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val name = "$namespace/$repository"
                    val uuid = call.parameters["uuid"]!!
                    val session = blobService.getUploadSession(uuid)
                    if(session == null) {
                        /*404*/
                        return@get call.respond(HttpStatusCode.NotFound)
                    }
                    val uploadedBytes = session.receivedSize
                    val location = "/v2/$name/blobs/uploads/$uuid"
                    call.response.headers.append("Range","0-${uploadedBytes - 1}")
                    call.response.headers.append("Docker-Upload-UUID",uuid)
                    call.response.headers.append("Location",location)
                    /*204*/
                    call.respond(HttpStatusCode.NoContent)
                }
                patch {
                    val namespace = call.parameters["namespace"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val repository = call.parameters["repository"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val name = "$namespace/$repository"
                    val uuid = call.parameters["uuid"]!!
                    val contentRange = call.request.headers["Content-Range"]
                    if (!blobService.findUploadSession(uuid)) {
                        /*404*/
                        return@patch call.respond(HttpStatusCode.NotFound,"Upload session not found")
                    }
                    if (contentRange != null) {
                        val session = blobService.getUploadSession(uuid) ?: return@patch
                        /*400*/
                        val match = Regex("""bytes\s*(\d+)-(\d+)""").matchEntire(contentRange) ?: return@patch call.respond(HttpStatusCode.BadRequest)
                        val start = match.groupValues[1].toLong()
                        val end = match.groupValues[2].toLong()
                        if (start != session.receivedSize) {
                            /*416*/
                            return@patch call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                        }
                        if (end < start) {
                            /*416*/
                            return@patch call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                        }
                    }
                    val receivedBytesInputStream = call.receiveChannel().toInputStream()
                    val uploadedSize = blobService.appendChunk(uuid, name, receivedBytesInputStream)
                    val location = "/v2/$name/blobs/uploads/$uuid"
                    call.response.headers.append("Location",location)
                    call.response.headers.append("Range","0-${uploadedSize - 1}")
                    call.response.headers.append("Docker-Upload-UUID",uuid)
                    /*202*/
                    call.respond(HttpStatusCode.Accepted,"Chunk accepted and stored")
                }
                put {
                    val namespace = call.parameters["namespace"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val repository = call.parameters["repository"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val name = "$namespace/$repository"
                    val uuid = call.parameters["uuid"]!!
                    //such as sha256:wwwwwwwwwwwww
                    val digest = call.queryParameters["digest"]
                    if (digest == null) {
                        /*400*/
                        return@put call.respond(HttpStatusCode.BadRequest,"Invalid digest or missing parameters")
                    }
                    if(!digest.matches(Regex("^[a-z0-9]+:[a-f0-9]{64}$"))) {
                        /*400*/
                        return@put call.respond(HttpStatusCode.BadRequest,"Invalid digest or missing parameters")
                    }
                    val digestHex = digest.substringAfter(":")
                    var session = blobService.getUploadSession(uuid)
                    if (session == null) {
                        /*404*/
                        return@put call.respond(HttpStatusCode.NotFound, "Upload session not found")
                    }
                    val tmpFile = File(session.uploadTempPath)
                    var finalDigest = "sha256:"
                    if (session.receivedSize == 0L) {
                        /*TODO ???*/
                        val monolithic = call.receive<ByteArray>()
                        tmpFile.writeBytes(monolithic)
                        session.receivedSize = monolithic.size.toLong()

                        val calculatedDigestHex = Utils.calculateBytesSha256(monolithic)
                        if (calculatedDigestHex != digestHex) {
                            tmpFile.delete()
                            /*400*/
                            return@put call.respond(HttpStatusCode.BadRequest,"Invalid digest or missing parameters")
                        }
                        finalDigest += calculatedDigestHex
                    } else {
                        val last = call.receiveChannel().toInputStream()
                        val uploadedSize = blobService.appendChunk(uuid,name,last)
                        val calculatedDigestHex = Utils.calculateFileSha256(tmpFile)
                        if (calculatedDigestHex != digestHex) {
                            tmpFile.delete()
                            /*400*/
                            return@put call.respond(HttpStatusCode.BadRequest,"Invalid digest or missing parameters")
                        }
                        finalDigest += calculatedDigestHex
                    }
                    blobService.saveBlob(digestHex = digestHex, blob = tmpFile)
                    blobService.completeUploadSession(uuid)
                    blobService.clearUploadedTempFiles(uuid,name)
                    call.response.headers.append("Docker-Content-Digest",finalDigest)
                    call.response.headers.append("Location","/v2/$name/blobs/$finalDigest")
                    call.response.headers.append("Content-Length","0")
                    /*201*/
                    call.respond(HttpStatusCode.Created)

                    if (false) {
                        /*TODO 416*/
                        call.respond(HttpStatusCode.RequestedRangeNotSatisfiable,"Requested range not satisfiable")
                    }
                }
                delete {
                    val namespace = call.parameters["namespace"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val repository = call.parameters["repository"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val name = "$namespace/$repository"
                    val uuid = call.parameters["uuid"]!!
                    /*404*/
                    if (!blobService.findUploadSession(uuid)) {
                        call.respond(HttpStatusCode.NotFound,"Upload session not found")
                    }
                    blobService.deleteUploadSession(uuid = uuid)
                    blobService.clearUploadedTempFiles(uuid,name)
                    call.response.headers.append("Content-Length","0")
                    /*204*/
                    call.respond(HttpStatusCode.NoContent,"Upload session cancelled successfully, No body is returned")
                }
            }
        }
        route("/{digest}") {
            get {
                // get 'lib/ubuntu'
                val namespace = call.parameters["namespace"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val repository = call.parameters["repository"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val name = "$namespace/$repository"
//                val name = call.parameters.getAll("name")?.joinToString("/") ?: return@get call.respond(HttpStatusCode.BadRequest)
                val digest = call.parameters["digest"]
                //such as sha256:wwwwwwwwwww
                if (digest == null) {
                    return@get call.respond(HttpStatusCode.BadRequest)
                }
                if(!digest.matches(Regex("^[a-z0-9]+:[a-f0-9]{64}$"))) {
                    return@get call.respond(HttpStatusCode.BadRequest)
                }
                val redirect = blobService.getRedirectUrl(name,digest)
                if (redirect != null) {
                    call.response.headers.append("Location",redirect)
                    return@get call.respond(HttpStatusCode.TemporaryRedirect)
                }
                /*TODO big file?*/
                /*404*/
                val blobFile = blobService.getBlobByDigest(digest) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.response.headers.append("Content-Length", blobFile.length().toString())
                call.response.headers.append("Content-Type","application/octet-stream")
                call.response.headers.append("Docker-Content-Digest", Utils.calculateFileSha256(blobFile))
                /*200*/
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
                val namespace = call.parameters["namespace"] ?: return@head call.respond(HttpStatusCode.BadRequest)
                val repository = call.parameters["repository"] ?: return@head call.respond(HttpStatusCode.BadRequest)
                val name = "$namespace/$repository"
                val digest = call.parameters["digest"]
                if (digest == null) {
                    return@head call.respond(HttpStatusCode.BadRequest)
                }
                val blobFile = blobService.getBlobByDigest(digest)
                if (blobFile == null || !blobFile.exists()) {
                    /*404*/
                    return@head call.respond(HttpStatusCode.NotFound)
                }
                // content-length auto
                call.response.headers.append("Docker-Content-Digest", Utils.calculateFileSha256(blobFile))
                call.response.headers.append("Content-Type","application/octet-stream")
                /*200*/
                call.respond(HttpStatusCode.OK,"Blob exists")
            }
        }
    }
}
fun Route.manifestsRoutes(
    manifestService: ManifestService
) {
    route("/{namespace}/{repository}/manifests/{reference}") {
        get {
            val namespace = call.parameters["namespace"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val repository = call.parameters["repository"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val name = "$namespace/$repository"
            val reference = call.parameters["reference"]!!

            val supportedTypes = listOf(
                "application/vnd.docker.distribution.manifest.v2+json",
                "application/vnd.docker.distribution.manifest.list.v2+json",
                "application/vnd.oci.image.manifest.v1+json",
                "application/vnd.oci.image.index.v1+json"
            )
            /*client support*/
            val accept = call.request.headers["Accept"]

            val manifest = manifestService.getManifest(name,reference) ?: return@get call.respond(HttpStatusCode.NotFound,"Repository or manifest not found.")
            val jsonText = manifest.readText()
            val json = Json.parseToJsonElement(jsonText).jsonObject
            val mediaType = json["mediaType"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing")

            call.response.headers.append("Docker-Content-Digest", Utils.calculateFileSha256(manifest))
            when(mediaType) {
                "application/vnd.docker.distribution.manifest.v2+json",
                "application/vnd.oci.image.manifest.v1+json"
                    -> {
                        val manifestV2 = Json.decodeFromString<ImageManifestV2>(jsonText)
                        call.response.headers.append("Content-Type",manifestV2.mediaType)
                        /*200*/
                        call.respond(HttpStatusCode.OK, manifestV2)
                    }
                "application/vnd.docker.distribution.manifest.list.v2+json",
                "application/vnd.oci.image.index.v1+json"
                    -> {
                        val manifestV2List = Json.decodeFromString<ImageManifestV2List>(jsonText)
                        call.response.headers.append("Content-Type",manifestV2List.mediaType)
                        /*200*/
                        call.respond(HttpStatusCode.OK, manifestV2List)
                    }
                else -> {
                    /*which code?*/
                    return@get call.respond(HttpStatusCode.MethodNotAllowed)
                }
            }
        }
        put {
            val namespace = call.parameters["namespace"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val repository = call.parameters["repository"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val name = "$namespace/$repository"
            val reference = call.parameters["reference"]!!
            val contentType = call.request.headers["Content-Type"]

            //body
            val raw = call.receiveChannel().toByteArray()
            val jsonText = raw.decodeToString()
            val digest = Utils.calculateBytesSha256(raw)
            val jsonObj = Json.parseToJsonElement(jsonText).jsonObject
            val schemaVersion = jsonObj["schemaVersion"]?.jsonPrimitive?.int
            val mediaType = jsonObj["mediaType"]?.jsonPrimitive?.content
            if (schemaVersion != 2) {
                /*400*/
                return@put call.respond(HttpStatusCode.BadRequest)
            }
            if (contentType != mediaType) {
                /*check*/
                /*400*/
                return@put call.respond(HttpStatusCode.BadRequest)
            }
            when(mediaType) {
                "application/vnd.docker.distribution.manifest.v2+json",
                "application/vnd.oci.image.manifest.v1+json"
                    -> {
                    val manifestV2 = Json.decodeFromString<ImageManifestV2>(jsonText)
                    if (!manifestService.validateBlobsExist(manifestV2)) {
                        /*400*/
                        return@put call.respond(HttpStatusCode.BadRequest)
                    }
                    manifestService.putManifest(name,reference,raw)
                }
                "application/vnd.docker.distribution.manifest.list.v2+json",
                "application/vnd.oci.image.index.v1+json"
                    -> {
                    val manifestV2List = Json.decodeFromString<ImageManifestV2List>(jsonText)
                    if (!manifestService.validateManifestsExist(manifestV2List)) {
                        /*400*/
                        return@put call.respond(HttpStatusCode.BadRequest)
                    }
                    manifestService.putManifest(name,reference,raw)
                }
                else -> {
                    /*which code?*/
                    return@put
                }
            }
            call.response.headers.append("Docker-Content-Digest", "sha256:$digest")
            call.response.headers.append("Location","/v2/$name/manifests/$reference")
            /*201*/
            call.respond(HttpStatusCode.Created)
        }
        head {
            val namespace = call.parameters["namespace"] ?: return@head call.respond(HttpStatusCode.BadRequest)
            val repository = call.parameters["repository"] ?: return@head call.respond(HttpStatusCode.BadRequest)
            val name = "$namespace/$repository"
            val reference = call.parameters["reference"]!!
            /*TODO*/
            val accept = call.request.headers["Accept"] ?: "application/vnd.docker.distribution.manifest.v2+json"

            /*404*/
            val manifestInfo = manifestService.getManifestInfo(name,reference) ?: return@head call.respond(HttpStatusCode.NotFound)
            call.response.headers.append("Docker-Content-Digest",manifestInfo.first)
            call.response.headers.append("Content-Type",manifestInfo.second)
            /*200*/
            call.respond(HttpStatusCode.OK)
        }
        /*DONE*/
        delete {
            val namespace = call.parameters["namespace"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val repository = call.parameters["repository"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val name = "$namespace/$repository"
            val reference = call.parameters["reference"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            /*404*/
            val manifest = manifestService.getManifest(name,reference) ?: return@delete call.respond(HttpStatusCode.NotFound)
            if (manifestService.isDigest(reference)) {
                /*405*/
                return@delete call.respond(HttpStatusCode.MethodNotAllowed)
            }
            if (manifestService.deleteManifest(name,reference) == false) {
                /*403*/
                return@delete call.respond(HttpStatusCode.Forbidden)
            }
            /*202*/
            call.respond(HttpStatusCode.Accepted)
        }
        /*unsupported method*/
        method(HttpMethod.Post) {
            handle {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}