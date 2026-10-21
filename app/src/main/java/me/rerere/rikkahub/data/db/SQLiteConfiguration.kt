package me.rerere.rikkahub.data.db

import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

/**
 * 应用数据库与备份校验共用的 SQLite 配置。
 *
 * 这里不注册任何扩展：旧版用 `simple` 分词器建的空 `message_fts` 兼容表已随 `libsimple`
 * 扩展一起移除，并在打开数据库时删除（见 `data/db/fts/LegacyMessageFts.kt`）。保留 requery
 * 的 SQLite 是因为 minSdk 28 的系统 SQLite 还没有备份快照用的 `VACUUM INTO`。
 */
internal object SQLiteConfiguration {
    const val DATABASE_NAME = "rikka_hub"

    fun openHelperFactory() = RequerySQLiteOpenHelperFactory()
}
