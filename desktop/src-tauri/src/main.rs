#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

mod ai;
mod db;
mod decoder;
mod ipc;
mod protocol;
mod server;
mod state;

use std::sync::Arc;
use serde_json::json;
use tauri::{CustomMenuItem, Manager, SystemTray, SystemTrayEvent, SystemTrayMenu};
use tokio::sync::broadcast;

use crate::db::{Database, PairedDevice};
use crate::ipc::VideoSharedMemory;
use crate::server::{TcpControlServer, TransportManager, UdpMediaListener};
use crate::state::{AiStudioState, AppState, CameraControls, StreamTelemetry};

struct ServerChannels {
    control_tx: broadcast::Sender<String>,
}

#[tauri::command]
fn get_stream_telemetry(state: tauri::State<AppState>) -> StreamTelemetry {
    state.telemetry.read().clone()
}

#[tauri::command]
fn get_camera_controls(state: tauri::State<AppState>) -> CameraControls {
    state.camera_controls.read().clone()
}

#[tauri::command]
async fn set_camera_controls(
    controls: CameraControls,
    state: tauri::State<'_, AppState>,
    channels: tauri::State<'_, ServerChannels>,
) -> Result<bool, String> {
    {
        let mut current = state.camera_controls.write();
        *current = controls.clone();
    }

    // Broadcast update to connected Android phone
    let cmd = json!({
        "jsonrpc": "2.0",
        "method": "updateCameraParams",
        "params": {
            "lensId": controls.lens,
            "zoomRatio": controls.zoom_ratio,
            "torchMode": controls.torch_on,
            "iso": controls.iso,
            "exposureTimeNs": controls.exposure_time_ns,
            "whiteBalanceKelvin": controls.white_balance_k,
            "manualFocusDistance": controls.focus_distance,
            "stabilization": controls.stabilization,
            "hdr": controls.hdr
        },
        "id": 101
    });

    let _ = channels.control_tx.send(cmd.to_string());
    Ok(true)
}

#[tauri::command]
fn get_ai_state(state: tauri::State<AppState>) -> AiStudioState {
    state.ai_state.read().clone()
}

#[tauri::command]
fn set_ai_state(new_state: AiStudioState, state: tauri::State<AppState>) -> Result<bool, String> {
    let mut current = state.ai_state.write();
    *current = new_state;
    Ok(true)
}

#[tauri::command]
fn get_paired_devices(db: tauri::State<Arc<Database>>) -> Result<Vec<PairedDevice>, String> {
    db.get_paired_devices().map_err(|e| e.to_string())
}

#[tauri::command]
fn delete_paired_device(device_id: String, db: tauri::State<Arc<Database>>) -> Result<bool, String> {
    db.delete_paired_device(&device_id)
        .map(|_| true)
        .map_err(|e| e.to_string())
}

#[tauri::command]
fn check_driver_status() -> Result<serde_json::Value, String> {
    #[cfg(windows)]
    {
        use std::process::Command;
        // Check Windows Registry query for CLSID_AetherCamVirtualCamera
        let output = Command::new("reg")
            .args(["query", "HKCR\\CLSID\\{A84CE391-7681-4321-B089-8FA3E5114D20}"])
            .output();

        let installed = match output {
            Ok(o) => o.status.success(),
            Err(_) => false,
        };

        Ok(json!({
            "virtualCameraInstalled": installed,
            "virtualMicInstalled": true,
            "driverName": "AetherCam Virtual Camera"
        }))
    }
    #[cfg(not(windows))]
    {
        Ok(json!({
            "virtualCameraInstalled": true,
            "virtualMicInstalled": true,
            "driverName": "AetherCam Virtual Camera (Emulated)"
        }))
    }
}

#[tauri::command]
fn install_driver() -> Result<String, String> {
    #[cfg(windows)]
    {
        use std::process::Command;
        let dll_path = "AetherCamVirtualCam.dll";
        let output = Command::new("regsvr32.exe")
            .args(["/s", dll_path])
            .output()
            .map_err(|e| format!("Failed to invoke regsvr32: {:?}", e))?;

        if output.status.success() {
            Ok("Virtual Camera driver registered successfully!".into())
        } else {
            Err("Failed to register driver. Administrative privileges required.".into())
        }
    }
    #[cfg(not(windows))]
    {
        Ok("Virtual Camera driver registered (Simulated for platform)".into())
    }
}

#[tauri::command]
fn get_pairing_qr_data() -> serde_json::Value {
    let local_ip = local_ip_address::local_ip().map(|ip| ip.to_string()).unwrap_or_else(|_| "127.0.0.1".into());
    let pin = "849201";

    json!({
        "v": 2,
        "ip": local_ip,
        "udpPort": 48200,
        "tcpPort": 48201,
        "pin": pin,
        "serverPubKey": "BM7v9LqP3rM1z0NvQ8sYt4XuKgH2jR5wFpE6aN8=",
        "hostname": "AetherCam-Studio-PC"
    })
}

// Fallback IP resolver if crate not present
mod local_ip_address {
    pub fn local_ip() -> Result<std::net::IpAddr, ()> {
        let socket = std::net::UdpSocket::bind("0.0.0.0:0").map_err(|_| ())?;
        socket.connect("8.8.8.8:80").map_err(|_| ())?;
        socket.local_addr().map(|addr| addr.ip()).map_err(|_| ())
    }
}

fn main() {
    env_logger::init();

    let app_state = AppState::new();
    let db = Arc::new(Database::new("aethercam.db").expect("Failed to initialize SQLite database"));

    // Initialize Shared Memory Producer for DirectShow Virtual Camera
    let shared_mem = Arc::new(
        VideoSharedMemory::new(
            "Global\\AetherCam_Video_SharedMem_v1",
            "Global\\AetherCam_FrameReady_Event",
            "Global\\AetherCam_SharedMem_Mutex",
        ).expect("Failed to initialize Shared Memory Video Bridge")
    );

    // Initialize Servers
    let (tcp_server, _cmd_rx) = TcpControlServer::new(app_state.clone());
    let control_tx = tcp_server.get_sender();
    let channels = ServerChannels { control_tx: control_tx.clone() };

    let udp_listener = UdpMediaListener::new(app_state.clone(), shared_mem.clone());
    let transport_mgr = TransportManager::new(app_state.clone());

    // Spawn Background Network Runtimes
    tokio::spawn(async move {
        let _ = tcp_server.run(48201).await;
    });

    tokio::spawn(async move {
        let _ = udp_listener.run(48200).await;
    });

    tokio::spawn(async move {
        transport_mgr.run_monitor().await;
    });

    // System Tray Menu
    let tray_menu = SystemTrayMenu::new()
        .add_item(CustomMenuItem::new("open".to_string(), "Open AetherCam Studio"))
        .add_item(CustomMenuItem::new("mute".to_string(), "Mute Microphone"))
        .add_item(CustomMenuItem::new("quit".to_string(), "Quit AetherCam"));

    let system_tray = SystemTray::new().with_menu(tray_menu);

    tauri::Builder::default()
        .manage(app_state)
        .manage(db)
        .manage(channels)
        .system_tray(system_tray)
        .on_system_tray_event(|app, event| match event {
            SystemTrayEvent::MenuItemClick { id, .. } => match id.as_str() {
                "open" => {
                    let window = app.get_window("main").unwrap();
                    window.show().unwrap();
                    window.set_focus().unwrap();
                }
                "quit" => {
                    std::process::exit(0);
                }
                _ => {}
            },
            _ => {}
        })
        .invoke_handler(tauri::generate_handler![
            get_stream_telemetry,
            get_camera_controls,
            set_camera_controls,
            get_ai_state,
            set_ai_state,
            get_paired_devices,
            delete_paired_device,
            check_driver_status,
            install_driver,
            get_pairing_qr_data
        ])
        .run(tauri::generate_context!())
        .expect("Error while running AetherCam Tauri application");
}
