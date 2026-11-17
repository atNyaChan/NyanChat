package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_32_33"

/**
 * 上游 RikkaHub 32 -> 33：`message_node` 的索引从 (conversation_id) 换成
 * (conversation_id, node_index)。
 *
 * 加载会话时按 node_index 排序分页读取，只有 conversation_id 索引时每一页都要把整个会话的行取出来排序，
 * 耗时随消息数平方增长，几千条消息的会话要好几秒才能打开。
 * 复合索引以 conversation_id 开头，外键级联删除和按会话的查询照样能用，旧索引不再需要。
 * 只建索引、删索引，不改动表里的数据。
 *
 * 本迁移只用于恢复上游 32 的存档，随后由 `AppDatabaseFactory` 继续执行后续上游迁移链；
 * 本 fork 自己的库从 102 -> 103 走 [Migration_102_103]。
 */
val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 32 to 33 (message_node index)")
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_message_node_conversation_id_node_index` " +
                    "ON `message_node` (`conversation_id`, `node_index`)"
            )
            db.execSQL("DROP INDEX IF EXISTS `index_message_node_conversation_id`")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 32 to 33 success")
        } finally {
            db.endTransaction()
        }
    }
}
