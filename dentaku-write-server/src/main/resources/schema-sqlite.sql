-- Pekko Persistence JDBC 用の SQLite スキーマ
-- (pekko-persistence-jdbc は SQLite 向け公式 SQL を同梱していないため、
--  H2 用スキーマを SQLite 互換に書き直したもの)

CREATE TABLE IF NOT EXISTS "event_journal" (
    "ordering" INTEGER PRIMARY KEY AUTOINCREMENT,
    "deleted" INTEGER DEFAULT 0 NOT NULL,
    "persistence_id" TEXT NOT NULL,
    "sequence_number" INTEGER NOT NULL,
    "writer" TEXT NOT NULL,
    "write_timestamp" INTEGER NOT NULL,
    "adapter_manifest" TEXT NOT NULL,
    "event_payload" BLOB NOT NULL,
    "event_ser_id" INTEGER NOT NULL,
    "event_ser_manifest" TEXT NOT NULL,
    "meta_payload" BLOB,
    "meta_ser_id" INTEGER,
    "meta_ser_manifest" TEXT,
    UNIQUE("persistence_id", "sequence_number")
);

CREATE TABLE IF NOT EXISTS "event_tag" (
    "event_id" INTEGER NOT NULL,
    "tag" TEXT NOT NULL,
    PRIMARY KEY("event_id", "tag"),
    FOREIGN KEY("event_id") REFERENCES "event_journal"("ordering") ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS "snapshot" (
    "persistence_id" TEXT NOT NULL,
    "sequence_number" INTEGER NOT NULL,
    "created" INTEGER NOT NULL,
    "snapshot_ser_id" INTEGER NOT NULL,
    "snapshot_ser_manifest" TEXT NOT NULL,
    "snapshot_payload" BLOB NOT NULL,
    "meta_ser_id" INTEGER,
    "meta_ser_manifest" TEXT,
    "meta_payload" BLOB,
    PRIMARY KEY("persistence_id", "sequence_number")
);
