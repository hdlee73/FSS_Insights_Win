package android.content

import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * 안드로이드 SharedPreferences의 데스크톱 대역. 이름별로 속성 파일 하나를 쓴다.
 */
class SharedPreferences(directory: File, private val name: String) {

    private val file = File(directory, "$name.xml.properties").also { it.parentFile?.mkdirs() }
    private val values = ConcurrentHashMap<String, Any?>()

    init {
        load()
    }

    @Synchronized
    private fun load() {
        if (!file.exists()) return
        runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            props.forEach { (k, v) ->
                val key = k as String
                val raw = v as String
                when {
                    raw.startsWith(SET_PREFIX) -> values[key] = raw.removePrefix(SET_PREFIX).split(SET_SEPARATOR).filter { it.isNotEmpty() }.toMutableSet()
                    raw.startsWith(INT_PREFIX) -> values[key] = raw.removePrefix(INT_PREFIX).toIntOrNull() ?: 0
                    raw.startsWith(LONG_PREFIX) -> values[key] = raw.removePrefix(LONG_PREFIX).toLongOrNull() ?: 0L
                    raw.startsWith(BOOL_PREFIX) -> values[key] = raw.removePrefix(BOOL_PREFIX).toBoolean()
                    else -> values[key] = raw
                }
            }
        }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties()
            values.forEach { (k, v) ->
                val encoded = when (v) {
                    null -> ""
                    is Set<*> -> SET_PREFIX + v.joinToString(SET_SEPARATOR.toString())
                    is Int -> INT_PREFIX + v
                    is Long -> LONG_PREFIX + v
                    is Boolean -> BOOL_PREFIX + v
                    else -> v.toString()
                }
                props.setProperty(k, encoded)
            }
            file.outputStream().use { props.store(it, null) }
        }
    }

    fun getString(key: String, defValue: String?): String? {
        val v = values[key] ?: return defValue
        return v as? String ?: v.toString()
    }

    fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    @Suppress("UNCHECKED_CAST")
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val v = values[key] ?: return defValues
        return (v as? Set<String>)?.toSet() ?: defValues
    }

    fun contains(key: String): Boolean = values.containsKey(key)

    fun getAll(): Map<String, Any?> = values.toMap()

    fun edit(): Editor = Editor()

    inner class Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private var removeAll = false

        fun putString(key: String, value: String?): Editor { pending[key] = value; return this }
        fun putInt(key: String, value: Int): Editor { pending[key] = value; return this }
        fun putLong(key: String, value: Long): Editor { pending[key] = value; return this }
        fun putBoolean(key: String, value: Boolean): Editor { pending[key] = value; return this }
        fun putStringSet(key: String, value: Set<String>?): Editor { pending[key] = value?.toSet(); return this }
        fun remove(key: String): Editor { pending[key] = REMOVE; return this }
        fun clear(): Editor { removeAll = true; return this }

        fun commit(): Boolean { apply(); return true }

        fun apply() {
            if (removeAll) values.clear()
            pending.forEach { (k, v) -> if (v === REMOVE) values.remove(k) else values[k] = v }
            persist()
        }
    }

    private companion object {
        const val SET_SEPARATOR = '\u001F'
        const val SET_PREFIX = "\u0001set:"
        const val INT_PREFIX = "\u0001int:"
        const val LONG_PREFIX = "\u0001long:"
        const val BOOL_PREFIX = "\u0001bool:"
        val REMOVE = Any()
    }
}
