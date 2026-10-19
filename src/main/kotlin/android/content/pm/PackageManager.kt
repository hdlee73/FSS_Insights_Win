package android.content.pm

/** 안드로이드 PackageManager의 데스크톱 대역. */
class PackageManager {
    /** 데스크톱에서는 출처를 알 수 없는 앱 설치 제한이 없다. */
    fun canRequestPackageInstalls(): Boolean = true

    fun getLaunchIntentForPackage(packageName: String): android.content.Intent? = null

    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
    }
}
