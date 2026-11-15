//! Automatic USB ADB Reverse Port Forwarding & Device Discovery Service

use std::process::Command;
use log::{info, warn};

pub struct UsbAdbBridge;

impl UsbAdbBridge {
    /// Detects connected Android devices over USB ADB.
    pub fn list_adb_devices() -> Vec<String> {
        let output = match Command::new("adb").arg("devices").output() {
            Ok(out) => out,
            Err(_) => return Vec::new(),
        };

        let stdout = String::from_utf8_lossy(&output.stdout);
        let mut devices = Vec::new();

        for line in stdout.lines().skip(1) {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 2 && parts[1] == "device" {
                devices.push(parts[0].to_string());
            }
        }
        devices
    }

    /// Sets up reverse port forwarding so the Android phone connects to localhost:48200/48201 directly over USB.
    pub fn setup_reverse_ports(udp_port: u16, tcp_port: u16) -> Result<(), String> {
        let devices = Self::list_adb_devices();
        if devices.is_empty() {
            return Err("No USB ADB devices detected".into());
        }

        info!("Configuring ADB reverse port forwarding on {} devices...", devices.len());

        for dev in &devices {
            // Forward UDP stream port
            let _ = Command::new("adb")
                .args(["-s", dev, "reverse", &format!("tcp:{}", udp_port), &format!("tcp:{}", udp_port)])
                .output();

            // Forward TCP control port
            let _ = Command::new("adb")
                .args(["-s", dev, "reverse", &format!("tcp:{}", tcp_port), &format!("tcp:{}", tcp_port)])
                .output();
            
            info!("Configured reverse port forwarding for USB device: {}", dev);
        }

        Ok(())
    }

    /// Clean up ADB reverse rules when terminating
    pub fn remove_reverse_ports() {
        let _ = Command::new("adb").args(["reverse", "--remove-all"]).output();
    }
}
