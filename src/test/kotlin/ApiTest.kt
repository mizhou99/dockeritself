import org.junit.jupiter.api.Test
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.server.application.Application
import kotlinx.serialization.json.Json
import mi.yxz.mizu.Utils
import mi.yxz.mizu.module
import org.junit.jupiter.api.assertNotNull
import java.io.File
import kotlin.test.assertEquals
class ApiTest {
    @Test
    fun `test-manifest`() = testApplication {
        application {
            module()
        }
        val repo = "test/repo"

        val configContent = """
        {
            "architecture": "amd64",
            "os": "linux",
            "config": {
                "Env": ["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],
                "Cmd": ["/bin/sh"]
            },
            "rootfs": {
                "type": "layers",
                "diff_ids": [
                    "sha256:4f211c63f64c5e2d40ce48366873a5135b09d9a9c12c36153f66b0439fc294f2"
                ]
            },
            "history": [
                {
                    "created": "2024-01-01T00:00:00Z",
                    "created_by": "/bin/sh -c #(nop) ADD file:test in /"
                }
            ]
        }
        """.trimIndent()


        val startResponse = client.post("/v2/$repo/blobs/uploads/")
        println(startResponse)
        assertEquals(HttpStatusCode.Accepted, startResponse.status)
        val location = startResponse.headers["Location"]
        val uuid = startResponse.headers["Docker-Upload-UUID"]
        println(location)
        println(uuid)
        assertNotNull(location)
        assertNotNull(uuid)

        val configDigest = "sha256:" + Utils.calculateBytesSha256(configContent.toByteArray())
        val completeResponse = client.put("/v2/$repo/blobs/uploads/$uuid?digest=$configDigest") {
            setBody(configContent.toByteArray())
        }
        println(completeResponse)
        assertEquals(HttpStatusCode.Created, completeResponse.status)


        val testData = "This is test blob data for Docker Registry"
        val manifestJson = """
            {
                "schemaVersion": 2,
                "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
                "config": {
                    "mediaType": "application/vnd.docker.container.image.v1+json",
                    "size": ${configContent.toByteArray().size},
                    "digest": "$configDigest"
                },
                "layers": [
                    {
                        "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
                        "size": ${testData.toByteArray().size},
                        "digest": "sha256:4f211c63f64c5e2d40ce48366873a5135b09d9a9c12c36153f66b0439fc294f2"
                    }
                ]
            }
        """.trimIndent()

        val tag = "latest"
        // 1. 上传 manifest
        val putResponse = client.put("/v2/$repo/manifests/$tag") {
            header("Content-Type","application/vnd.docker.distribution.manifest.v2+json")
            setBody(manifestJson)
        }

        assertEquals(HttpStatusCode.Created, putResponse.status)
        assertNotNull(putResponse.headers["Docker-Content-Digest"])
        assertNotNull(putResponse.headers["Location"])

        // 2. 获取 manifest
        val getResponse = client.get("/v2/$repo/manifests/$tag")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        println(getResponse)

        // 3. HEAD 请求检查存在性
        val headResponse = client.head("/v2/$repo/manifests/$tag")
        assertEquals(HttpStatusCode.OK, headResponse.status)
    }
    @Test
    fun `test-blob`() = testApplication {
        application {
            module()
        }
        //post
        val repo = "test/repo"
        println("/v2/$repo/blobs/uploads/")
        val startResponse = client.post("/v2/$repo/blobs/uploads/")
        println(startResponse)
        assertEquals(HttpStatusCode.Accepted, startResponse.status)
        val location = startResponse.headers["Location"]
        val uuid = startResponse.headers["Docker-Upload-UUID"]
        println(location)
        println(uuid)
        assertNotNull(location)
        assertNotNull(uuid)
        //get upload status
        val statusResponse = client.get("/v2/$repo/blobs/uploads/$uuid")
        println(statusResponse)
        assertEquals(HttpStatusCode.NoContent, statusResponse.status)

        //put
        val testData = "This is test blob data for Docker Registry"
        val digest = "sha256:"+ Utils.calculateBytesSha256(testData.toByteArray())
        val completeResponse = client.put("/v2/$repo/blobs/uploads/$uuid?digest=$digest") {
            setBody(testData)
        }
        println(completeResponse)
        assertEquals(HttpStatusCode.Created, completeResponse.status)

        //head
        val headResponse = client.head("/v2/$repo/blobs/$digest")
        println(headResponse)
        assertEquals(HttpStatusCode.OK,headResponse.status)

        //get blob
        val blobResponse = client.get("/v2/$repo/blobs/$digest")
        println(blobResponse)
        assertEquals(HttpStatusCode.OK,blobResponse.status)
    }
    @Test
    fun `test-auth-simulataion`() = testApplication {
        application {
            module()
        }
        val res = client.get("/auth")
        println(res)
        assertEquals(HttpStatusCode.OK, res.status)
    }
}