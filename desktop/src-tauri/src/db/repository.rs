use rusqlite::{params, Connection, Result};
use serde::{Deserialize, Serialize};
use std::sync::Mutex;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairedDevice {
    pub device_id: String,
    pub device_name: String,
    pub client_public_key: String,
    pub paired_at: String,
    pub auto_connect: bool,
    pub preferred_transport: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AiPreset {
    pub id: String,
    pub name: String,
    pub background_mode: String,
    pub blur_intensity: f64,
    pub virtual_background_path: Option<String>,
    pub face_tracking: bool,
    pub eye_contact: bool,
    pub beautification: f64,
    pub low_light_boost: bool,
    pub noise_suppression: bool,
}

pub struct Database {
    conn: Mutex<Connection>,
}

impl Database {
    pub fn new(db_path: &str) -> Result<Self> {
        let conn = Connection::open(db_path)?;
        let schema = include_str!("schema.sql");
        conn.execute_batch(schema)?;
        Ok(Self {
            conn: Mutex::new(conn),
        })
    }

    pub fn upsert_paired_device(&self, device: &PairedDevice) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO paired_devices (device_id, device_name, client_public_key, auto_connect, preferred_transport, last_seen_at)
             VALUES (?1, ?2, ?3, ?4, ?5, CURRENT_TIMESTAMP)
             ON CONFLICT(device_id) DO UPDATE SET
                device_name=excluded.device_name,
                client_public_key=excluded.client_public_key,
                last_seen_at=CURRENT_TIMESTAMP",
            params![
                device.device_id,
                device.device_name,
                device.client_public_key,
                device.auto_connect as i32,
                device.preferred_transport
            ],
        )?;
        Ok(())
    }

    pub fn get_paired_devices(&self) -> Result<Vec<PairedDevice>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT device_id, device_name, client_public_key, paired_at, auto_connect, preferred_transport FROM paired_devices")?;
        let rows = stmt.query_map([], |row| {
            Ok(PairedDevice {
                device_id: row.get(0)?,
                device_name: row.get(1)?,
                client_public_key: row.get(2)?,
                paired_at: row.get(3)?,
                auto_connect: row.get::<_, i32>(4)? != 0,
                preferred_transport: row.get(5)?,
            })
        })?;

        let mut devices = Vec::new();
        for r in rows {
            devices.push(r?);
        }
        Ok(devices)
    }

    pub fn delete_paired_device(&self, device_id: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute("DELETE FROM paired_devices WHERE device_id = ?1", params![device_id])?;
        Ok(())
    }

    pub fn get_ai_presets(&self) -> Result<Vec<AiPreset>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT id, name, background_mode, blur_intensity, virtual_background_path, face_tracking, eye_contact, beautification, low_light_boost, noise_suppression FROM ai_presets")?;
        let rows = stmt.query_map([], |row| {
            Ok(AiPreset {
                id: row.get(0)?,
                name: row.get(1)?,
                background_mode: row.get(2)?,
                blur_intensity: row.get(3)?,
                virtual_background_path: row.get(4)?,
                face_tracking: row.get::<_, i32>(5)? != 0,
                eye_contact: row.get::<_, i32>(6)? != 0,
                beautification: row.get(7)?,
                low_light_boost: row.get::<_, i32>(8)? != 0,
                noise_suppression: row.get::<_, i32>(9)? != 0,
            })
        })?;

        let mut presets = Vec::new();
        for r in rows {
            presets.push(r?);
        }
        Ok(presets)
    }
}
