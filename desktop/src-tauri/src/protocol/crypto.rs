//! End-to-End Cryptography Engine (X25519 & AES-256-GCM)

use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Key, Nonce};
use rand::rngs::OsRng;
use ring::digest::{Context, SHA256};
use x25519_dalek::{EphemeralSecret, PublicKey};

pub struct KeyPair {
    pub secret: EphemeralSecret,
    pub public: PublicKey,
}

impl KeyPair {
    pub fn generate() -> Self {
        let secret = EphemeralSecret::random_from_rng(OsRng);
        let public = PublicKey::from(&secret);
        Self { secret, public }
    }

    pub fn public_key_base64(&self) -> String {
        use std::io::Write;
        let mut buf = [0u8; 32];
        buf.copy_from_slice(self.public.as_bytes());
        // Simple base64 format
        base64_encode(&buf)
    }
}

pub fn derive_shared_secret(secret: EphemeralSecret, peer_public_bytes: &[u8; 32]) -> [u8; 32] {
    let peer_public = PublicKey::from(*peer_public_bytes);
    let shared = secret.diffie_hellman(&peer_public);
    let mut key = [0u8; 32];
    key.copy_from_slice(shared.as_bytes());
    key
}

pub fn encrypt_payload(
    key_bytes: &[u8; 32],
    sequence_num: u16,
    plaintext: &[u8],
) -> Result<(Vec<u8>, [u8; 16]), String> {
    let key = Key::<Aes256Gcm>::from_slice(key_bytes);
    let cipher = Aes256Gcm::new(key);

    let mut nonce_bytes = [0u8; 12];
    nonce_bytes[10] = (sequence_num >> 8) as u8;
    nonce_bytes[11] = sequence_num as u8;
    let nonce = Nonce::from_slice(&nonce_bytes);

    let ciphertext_with_tag = cipher
        .encrypt(nonce, plaintext)
        .map_err(|e| format!("Encryption error: {:?}", e))?;

    let tag_offset = ciphertext_with_tag.len() - 16;
    let mut tag = [0u8; 16];
    tag.copy_from_slice(&ciphertext_with_tag[tag_offset..]);

    let ciphertext = ciphertext_with_tag[..tag_offset].to_vec();
    Ok((ciphertext, tag))
}

pub fn decrypt_payload(
    key_bytes: &[u8; 32],
    sequence_num: u16,
    ciphertext: &[u8],
    tag: &[u8; 16],
) -> Result<Vec<u8>, String> {
    let key = Key::<Aes256Gcm>::from_slice(key_bytes);
    let cipher = Aes256Gcm::new(key);

    let mut nonce_bytes = [0u8; 12];
    nonce_bytes[10] = (sequence_num >> 8) as u8;
    nonce_bytes[11] = sequence_num as u8;
    let nonce = Nonce::from_slice(&nonce_bytes);

    let mut combined = Vec::with_capacity(ciphertext.len() + 16);
    combined.extend_from_slice(ciphertext);
    combined.extend_from_slice(tag);

    cipher
        .decrypt(nonce, combined.as_ref())
        .map_err(|e| format!("Decryption authentication failed: {:?}", e))
}

pub fn hash_pin_code(pin: &str) -> String {
    let mut context = Context::new(&SHA256);
    context.update(pin.as_bytes());
    let digest = context.finish();
    base64_encode(digest.as_ref())
}

fn base64_encode(data: &[u8]) -> String {
    const CHARSET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::new();
    let mut i = 0;
    while i < data.len() {
        let b0 = data[i] as usize;
        let b1 = if i + 1 < data.len() { data[i + 1] as usize } else { 0 };
        let b2 = if i + 2 < data.len() { data[i + 2] as usize } else { 0 };

        let triple = (b0 << 16) | (b1 << 8) | b2;
        out.push(CHARSET[(triple >> 18) & 0x3F] as char);
        out.push(CHARSET[(triple >> 12) & 0x3F] as char);
        if i + 1 < data.len() {
            out.push(CHARSET[(triple >> 6) & 0x3F] as char);
        } else {
            out.push('=');
        }
        if i + 2 < data.len() {
            out.push(CHARSET[triple & 0x3F] as char);
        } else {
            out.push('=');
        }
        i += 3;
    }
    out
}
