//! TCP Control Plane Listener (Port 48201)

use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::broadcast;
use log::{error, info};
use serde_json::json;

use crate::state::AppState;

pub struct TcpControlServer {
    app_state: AppState,
    command_tx: broadcast::Sender<String>,
}

impl TcpControlServer {
    pub fn new(app_state: AppState) -> (Self, broadcast::Receiver<String>) {
        let (tx, rx) = broadcast::channel(128);
        (Self { app_state, command_tx: tx }, rx)
    }

    pub fn get_sender(&self) -> broadcast::Sender<String> {
        self.command_tx.clone()
    }

    pub async fn run(&self, port: u16) -> Result<(), Box<dyn std::error::Error>> {
        let listener = TcpListener::bind(format!("0.0.0.0:{}", port)).await?;
        info!("AetherCam TCP Control Server listening on port {}", port);

        loop {
            match listener.accept().await {
                Ok((stream, peer_addr)) => {
                    info!("Incoming control connection from: {}", peer_addr);
                    let app_state = self.app_state.clone();
                    let mut cmd_rx = self.command_tx.subscribe();

                    tokio::spawn(async move {
                        if let Err(e) = Self::handle_client(stream, app_state, &mut cmd_rx).await {
                            error!("Control client error: {:?}", e);
                        }
                    });
                }
                Err(e) => {
                    error!("TCP accept failure: {:?}", e);
                }
            }
        }
    }

    async fn handle_client(
        mut stream: TcpStream,
        app_state: AppState,
        cmd_rx: &mut broadcast::Receiver<String>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let mut len_buf = [0u8; 4];

        loop {
            tokio::select! {
                // Read commands / messages from mobile client
                read_res = stream.read_exact(&mut len_buf) => {
                    if read_res.is_err() {
                        break;
                    }
                    let msg_len = u32::from_be_bytes(len_buf) as usize;
                    if msg_len > 65536 {
                        break; // Malformed payload guard
                    }

                    let mut body_buf = vec![0u8; msg_len];
                    stream.read_exact(&mut body_buf).await?;

                    if let Ok(json_str) = String::from_utf8(body_buf) {
                        Self::process_client_rpc(&json_str, &mut stream, &app_state).await?;
                    }
                }

                // Push pending desktop commands to mobile client
                Ok(cmd_str) = cmd_rx.recv() => {
                    let bytes = cmd_str.as_bytes();
                    let len_prefix = (bytes.len() as u32).to_be_bytes();
                    stream.write_all(&len_prefix).await?;
                    stream.write_all(bytes).await?;
                    stream.flush().await?;
                }
            }
        }

        info!("Control client disconnected");
        Ok(())
    }

    async fn process_client_rpc(
        req_str: &str,
        stream: &mut TcpStream,
        app_state: &AppState,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let req: serde_json::Value = serde_json::from_str(req_str)?;

        if let Some(method) = req.get("method").and_then(|m| m.as_str()) {
            match method {
                "handshake" => {
                    info!("Received client handshake: {:?}", req["params"]);
                    let resp = json!({
                        "jsonrpc": "2.0",
                        "result": {
                            "status": "ACCEPTED",
                            "protocolVersion": "2.1",
                            "sessionToken": "aether_sess_9021a8f",
                            "negotiatedWidth": 1920,
                            "negotiatedHeight": 1080,
                            "negotiatedFps": 60,
                            "targetBitrateBps": 12000000
                        },
                        "id": req.get("id").unwrap_or(&json!(1))
                    });

                    let resp_bytes = resp.to_string().into_bytes();
                    let len_prefix = (resp_bytes.len() as u32).to_be_bytes();
                    stream.write_all(&len_prefix).await?;
                    stream.write_all(&resp_bytes).await?;
                    stream.flush().await?;

                    // Update state to Connected
                    let mut telem = app_state.telemetry.write();
                    telem.state = crate::state::ConnectionState::Connected;
                }
                "telemetry" => {
                    if let Some(params) = req.get("params") {
                        let mut telem = app_state.telemetry.write();
                        if let Some(battery) = params.get("batteryLevel").and_then(|b| b.as_f64()) {
                            telem.battery_level = battery as f32;
                        }
                        if let Some(temp) = params.get("batteryTemp").and_then(|t| t.as_f64()) {
                            telem.battery_temp_c = temp as f32;
                        }
                    }
                }
                _ => {}
            }
        }

        Ok(())
    }
}
