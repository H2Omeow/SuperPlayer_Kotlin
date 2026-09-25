package top.nekoh2o.player.data.net

/** Keep account details and request URLs out of user-visible transport errors. */
fun kugouLoginError(error: Exception): String = when (error) {
    is KugouApiException, is IllegalArgumentException -> error.message ?: "登录失败，请重试"
    is javax.net.ssl.SSLException -> "酷狗安全连接失败，请检查设备日期或更新应用后重试"
    is java.net.UnknownHostException -> "无法解析酷狗服务器地址，请检查网络或 DNS 后重试"
    is java.net.SocketTimeoutException -> "酷狗请求超时，请稍后重试"
    is java.io.IOException -> "无法连接酷狗服务，请检查网络后重试"
    else -> "酷狗响应异常，请稍后重试"
}
