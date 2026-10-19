package android.content

import java.io.File

/**
 * 안드로이드 Context의 데스크톱 대역. 앱 데이터 폴더와 환경설정 접근만 제공한다.
 * (안드로이드 앱과 같은 소스 코드를 거의 그대로 쓸 수 있게 해 주는 최소 구현)
 */
open class Context(val baseDir: File) {

    /** 데스크톱에는 앱/액티비티 구분이 없으므로 자기 자신을 돌려준다. */
    val applicationContext: Context get() = this

    val cacheDir: File get() = File(baseDir, "cache").apply { mkdirs() }
    val filesDir: File get() = File(baseDir, "files").apply { mkdirs() }

    val packageName: String get() = "io.github.hdlee73.financenewsradar"

    val packageManager: android.content.pm.PackageManager get() = android.content.pm.PackageManager()

    val contentResolver: ContentResolver get() = ContentResolver()

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        SharedPreferences(File(baseDir, "shared_prefs"), name)

    @Suppress("UNCHECKED_CAST")
    fun <T> getSystemService(serviceClass: Class<T>): T? = null

    /** 데스크톱에서는 알림 권한을 따로 받지 않는다. */
    fun checkSelfPermission(permission: String): Int = android.content.pm.PackageManager.PERMISSION_GRANTED

    fun getString(resId: Int): String = ""

    /** 인텐트를 처리한다. 웹 주소·메일 주소·파일 열기만 실제 동작으로 연결한다. */
    fun startActivity(intent: Intent) {
        intent.dispatch()
    }

    companion object {
        const val MODE_PRIVATE = 0

        @Volatile
        private var instance: Context? = null

        /** 앱 전체에서 쓰는 하나의 컨텍스트. */
        val app: Context
            get() = instance ?: synchronized(this) {
                instance ?: Context(defaultBaseDir()).also { instance = it }
            }

        fun install(context: Context) {
            instance = context
        }

        private fun defaultBaseDir(): File {
            val roaming = System.getenv("APPDATA")
            val home = System.getProperty("user.home") ?: "."
            val root = if (!roaming.isNullOrBlank()) File(roaming) else File(home)
            return File(root, "FSS_Insights")
        }
    }
}
