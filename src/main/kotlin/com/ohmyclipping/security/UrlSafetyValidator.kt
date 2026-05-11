package com.ohmyclipping.security

import org.springframework.stereotype.Component
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException

@Component
class UrlSafetyValidator {

    fun validatePublicHttpUrl(rawUrl: String): URI {
        val uri = try {
            URI(rawUrl.trim())
        } catch (_: URISyntaxException) {
            throw IllegalArgumentException("올바르지 않은 URL 형식입니다.")
        }

        val scheme = uri.scheme?.lowercase()
            ?: throw IllegalArgumentException("http:// 또는 https://로 시작하는 주소를 입력해 주세요.")
        require(scheme == "http" || scheme == "https") { "http:// 또는 https://로 시작하는 주소만 허용됩니다." }

        val host = uri.host ?: throw IllegalArgumentException("URL에 호스트가 필요합니다.")
        val normalizedHost = normalizeHost(host)
        require(normalizedHost != "localhost") { "localhost 주소는 허용되지 않습니다." }

        val addresses = try {
            InetAddress.getAllByName(normalizedHost)
        } catch (_: UnknownHostException) {
            throw IllegalArgumentException("URL 호스트를 확인할 수 없습니다. 주소를 다시 확인해 주세요.")
        }

        require(addresses.isNotEmpty()) { "URL 호스트를 확인할 수 없습니다." }
        if (addresses.any { isBlockedAddress(it) }) {
            throw IllegalArgumentException("내부 네트워크 주소는 허용되지 않습니다.")
        }

        return uri
    }

    internal fun isBlockedAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }

        if (address is Inet6Address) {
            val firstByte = address.address[0].toInt() and 0xFF
            if ((firstByte and 0xFE) == 0xFC) {
                return true // fc00::/7 unique local
            }
        }

        if (address is Inet4Address) {
            val bytes = address.address.map { it.toInt() and 0xFF }
            val b1 = bytes[0]
            val b2 = bytes[1]
            val b3 = bytes[2]

            if (b1 == 0) return true // 0.0.0.0/8 current network
            if (b1 == 100 && b2 in 64..127) return true // 100.64.0.0/10
            if (b1 == 192 && b2 == 0 && b3 == 0) return true // 192.0.0.0/24 IETF protocol assignments
            if (b1 == 192 && b2 == 0 && b3 == 2) return true // 192.0.2.0/24 TEST-NET-1
            if (b1 == 198 && (b2 == 18 || b2 == 19)) return true // 198.18.0.0/15
            if (b1 == 198 && b2 == 51 && b3 == 100) return true // 198.51.100.0/24 TEST-NET-2
            if (b1 == 203 && b2 == 0 && b3 == 113) return true // 203.0.113.0/24 TEST-NET-3
            if (b1 >= 224) return true // multicast/reserved
        }

        return false
    }

    private fun normalizeHost(host: String): String =
        IDN.toASCII(host.trim().trimEnd('.')).lowercase()
}
