//! Sqlite behind one mutex. `files_dir` comes from Kotlin.

use std::path::Path;
use std::sync::Mutex;

use rusqlite::Connection;

use crate::error::ApiError;
use crate::parse::{Status, last_paint_chrome};

const LAST_PAINT: &str = "last_paint";
const LAST_PAINT_CHROME: &str = "last_paint_chrome";

/// True when the payload actually changed.
pub fn payload_changed(old: &str, new: &str) -> bool {
    old != new
}

/// Domain disk. One connection, one mutex.
pub struct Store {
    db: Mutex<Connection>,
}

impl Store {
    /// Open `{files_dir}/geiravor.sqlite`. Creates the directory if needed.
    pub fn open(files_dir: &str) -> Result<Self, ApiError> {
        let dir = Path::new(files_dir);
        std::fs::create_dir_all(dir).map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        let path = dir.join("geiravor.sqlite");
        let db = Connection::open(path).map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        db.execute_batch(
            "CREATE TABLE IF NOT EXISTS kv (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            );",
        )
        .map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        Ok(Self { db: Mutex::new(db) })
    }

    pub fn get(&self, key: &str) -> Result<Option<String>, ApiError> {
        let db = self.db.lock().expect("store");
        let mut stmt = db
            .prepare("SELECT value FROM kv WHERE key = ?1")
            .map_err(|e| ApiError::Network {
                detail: e.to_string(),
            })?;
        let mut rows = stmt.query([key]).map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        match rows.next().map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })? {
            Some(row) => Ok(Some(row.get(0).map_err(|e| ApiError::Network {
                detail: e.to_string(),
            })?)),
            None => Ok(None),
        }
    }

    /// Write `key` only if `value` differs from what is stored.
    pub fn put_if_changed(&self, key: &str, value: &str) -> Result<bool, ApiError> {
        let old = self.get(key)?;
        if old.as_deref() == Some(value) {
            return Ok(false);
        }
        let db = self.db.lock().expect("store");
        db.execute(
            "INSERT INTO kv(key, value) VALUES(?1, ?2)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            rusqlite::params![key, value],
        )
        .map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        Ok(true)
    }

    /// Persist last-paint JSON unless chrome (minus current/listeners) is unchanged.
    pub fn put_last_paint(&self, json: &str, status: &Status) -> Result<bool, ApiError> {
        let chrome = last_paint_chrome(status);
        let old = self.get(LAST_PAINT_CHROME)?;
        if old.as_deref() == Some(chrome.as_str()) {
            return Ok(false);
        }
        self.put_if_changed(LAST_PAINT, json)?;
        self.put_if_changed(LAST_PAINT_CHROME, &chrome)?;
        Ok(true)
    }

    pub fn last_paint(&self) -> Result<Option<String>, ApiError> {
        self.get(LAST_PAINT)
    }

    /// Delete keys matching a SQL `LIKE` pattern.
    pub fn delete_like(&self, pattern: &str) -> Result<(), ApiError> {
        let db = self.db.lock().expect("store");
        db.execute("DELETE FROM kv WHERE key LIKE ?1", [pattern])
            .map_err(|e| ApiError::Network {
                detail: e.to_string(),
            })?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parse::parse_status_str;

    #[test]
    fn unchanged_payload_is_not_written() {
        assert!(!payload_changed("a", "a"));
        assert!(payload_changed("a", "b"));
    }

    #[test]
    fn delete_like_drops_matching_keys() {
        let dir = std::env::temp_dir().join(format!("geiravor-del-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let store = Store::open(dir.to_str().unwrap()).unwrap();
        store.put_if_changed("news:list:1", "a").unwrap();
        store.put_if_changed("news:list:2", "b").unwrap();
        store.put_if_changed("news:article:9", "c").unwrap();
        store.delete_like("news:list:%").unwrap();
        assert!(store.get("news:list:1").unwrap().is_none());
        assert!(store.get("news:list:2").unwrap().is_none());
        assert_eq!(store.get("news:article:9").unwrap().as_deref(), Some("c"));
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn last_paint_skips_listener_tick() {
        let dir = std::env::temp_dir().join(format!("geiravor-store-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let store = Store::open(dir.to_str().unwrap()).unwrap();
        let json = include_str!("../tests/fixtures/api_snapshot.json");
        let mut status = parse_status_str(json).unwrap();
        assert!(store.put_last_paint(json, &status).unwrap());
        status.listeners = 999;
        status.current += 2;
        let again = serde_json::to_string(&serde_json::json!({"tick": true})).unwrap();
        assert!(!store.put_last_paint(&again, &status).unwrap());
        let _ = std::fs::remove_dir_all(&dir);
    }
}
