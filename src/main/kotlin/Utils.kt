package mi.yxz.mizu
import kotlinx.io.IOException
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.jvm.Throws

object Utils {
    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun calculateFileSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer,0,bytesRead)
            }
        }
        val bytes = md.digest()
        val stringBuilder = StringBuilder()
        for (b in bytes) {
            stringBuilder.append(String.format("%02x",b))
        }
        return stringBuilder.toString()
    }
    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun calculateStringSha256(str: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(str.toByteArray())
        val hex = bytes.joinToString("") { it -> "%02x".format(it) }
        return hex
    }
    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun calculateBytesSha256(byteArray: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(byteArray)
        val hex = bytes.joinToString("") { it -> "%02x".format(it) }
        return hex
    }
}