package top.nekoh2o.player.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * QQ 登录辅助类
 * 通过 Intent 拉起手机 QQ 进行授权登录
 */
object QQLoginHelper {

    /**
     * 拉起手机 QQ 登录
     *
     * @param context Context
     * @param appId QQ 互联的 AppID（需要在 QQ 互联平台申请）
     * @return true=成功拉起，false=手机未安装 QQ 或拉起失败
     */
    fun launchQQLogin(context: Context, appId: String = "102058589"): Boolean {
        try {
            // 构造 QQ 授权 URL
            // scope: get_user_info 获取用户信息
            val redirectUri = Uri.encode("nekoplayer://qqauth")
            val qqAuthUrl = "mqqopensdkapi://qzapp?style=1" +
                    "&response_type=token" +
                    "&client_id=$appId" +
                    "&redirect_uri=$redirectUri" +
                    "&scope=get_user_info" +
                    "&display=mobile"

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qqAuthUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 检查是否安装了 QQ 或 TIM
     */
    fun isQQInstalled(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            // 检查 QQ
            try {
                packageManager.getPackageInfo("com.tencent.mobileqq", 0)
                return true
            } catch (e: Exception) {
                // QQ 未安装，检查 TIM
                packageManager.getPackageInfo("com.tencent.tim", 0)
                return true
            }
        } catch (e: Exception) {
            false
        }
    }
}
