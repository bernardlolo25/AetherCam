//! AetherCam Binary Streaming Protocol (ABSP v2.1)
//! Ultra-low latency, zero-copy packet parser, serializer, and FEC engine.

use byteorder::{BigEndian, ByteOrder};
use std::fmt;

pub const ABSP_MAGIC: u32 = 0xAE544331; // "AETC1"
pub const ABSP_VERSION: u8 = 0x02;
pub const HEADER_SIZE: usize = 16;
pub const FRAGMENT_HEADER_SIZE: usize = 4;
pub const MAX_PAYLOAD_SIZE: usize = 1400; // Optimal MTU safe chunk

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum PacketType {
    VideoNalu = 0x01,
    AudioFrame = 0x02,
    ControlRpc = 0x03,
    TelemetryPing = 0x04,
    KeyExchange = 0x05,
    FecParity = 0x06,
    Unknown = 0xFF,
}

impl From<u8> for PacketType {
    fn from(val: u8) -> Self {
        match val {
            0x01 => PacketType::VideoNalu,
            0x02 => PacketType::AudioFrame,
            0x03 => PacketType::ControlRpc,
            0x04 => PacketType::TelemetryPing,
            0x05 => PacketType::KeyExchange,
            0x06 => PacketType::FecParity,
            _ => PacketType::Unknown,
        }
    }
}

pub mod flags {
    pub const KEYFRAME: u8 = 0x01;
    pub const FRAGMENTED: u8 = 0x02;
    pub const LAST_FRAGMENT: u8 = 0x04;
    pub const ENCRYPTED: u8 = 0x08;
    pub const FEC_PARITY: u8 = 0x10;
}

#[derive(Debug, Clone)]
pub struct AbspHeader {
    pub magic: u32,
    pub version: u8,
    pub packet_type: PacketType,
    pub flags: u8,
    pub channel: u8,
    pub sequence: u16,
    pub payload_len: u16,
    pub timestamp_us: u32,
    pub fragment_index: Option<u16>,
    pub total_fragments: Option<u16>,
}

#[derive(Clone)]
pub struct AbspPacket {
    pub header: AbspHeader,
    pub payload: Vec<u8>,
    pub auth_tag: Option<[u8; 16]>,
}

impl fmt::Debug for AbspPacket {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("AbspPacket")
            .field("header", &self.header)
            .field("payload_bytes", &self.payload.len())
            .field("has_auth_tag", &self.auth_tag.is_some())
            .finish()
    }
}

impl AbspPacket {
    /// Parse an incoming raw byte slice into an AbspPacket with zero memory allocation where possible.
    pub fn parse(buf: &[u8]) -> Result<Self, &'static str> {
        if buf.len() < HEADER_SIZE {
            return Err("Buffer smaller than ABSP header size");
        }

        let magic = BigEndian::read_u32(&buf[0..4]);
        if magic != ABSP_MAGIC {
            return Err("Invalid ABSP magic identifier");
        }

        let version = buf[4];
        let packet_type = PacketType::from(buf[5]);
        let flags = buf[6];
        let channel = buf[7];
        let sequence = BigEndian::read_u16(&buf[8..10]);
        let payload_len = BigEndian::read_u16(&buf[10..12]) as usize;
        let timestamp_us = BigEndian::read_u32(&buf[12..16]);

        let mut offset = HEADER_SIZE;
        let mut fragment_index = None;
        let mut total_fragments = None;

        if (flags & flags::FRAGMENTED) != 0 {
            if buf.len() < offset + FRAGMENT_HEADER_SIZE {
                return Err("Buffer missing fragment header");
            }
            fragment_index = Some(BigEndian::read_u16(&buf[offset..offset + 2]));
            total_fragments = Some(BigEndian::read_u16(&buf[offset + 2..offset + 4]));
            offset += FRAGMENT_HEADER_SIZE;
        }

        if buf.len() < offset + payload_len {
            return Err("Incomplete payload received");
        }

        let payload = buf[offset..offset + payload_len].to_vec();
        offset += payload_len;

        let mut auth_tag = None;
        if (flags & flags::ENCRYPTED) != 0 {
            if buf.len() >= offset + 16 {
                let mut tag = [0u8; 16];
                tag.copy_from_slice(&buf[offset..offset + 16]);
                auth_tag = Some(tag);
            }
        }

        Ok(AbspPacket {
            header: AbspHeader {
                magic,
                version,
                packet_type,
                flags,
                channel,
                sequence,
                payload_len: payload_len as u16,
                timestamp_us,
                fragment_index,
                total_fragments,
            },
            payload,
            auth_tag,
        })
    }

    /// Serialize the packet into network byte buffer for wire transmission.
    pub fn serialize(&self) -> Vec<u8> {
        let mut extra_len = 0;
        if self.header.fragment_index.is_some() {
            extra_len += FRAGMENT_HEADER_SIZE;
        }
        if self.auth_tag.is_some() {
            extra_len += 16;
        }

        let mut out = Vec::with_capacity(HEADER_SIZE + extra_len + self.payload.len());
        let mut header_bytes = [0u8; HEADER_SIZE];
        BigEndian::write_u32(&mut header_bytes[0..4], self.header.magic);
        header_bytes[4] = self.header.version;
        header_bytes[5] = self.header.packet_type as u8;
        header_bytes[6] = self.header.flags;
        header_bytes[7] = self.header.channel;
        BigEndian::write_u16(&mut header_bytes[8..10], self.header.sequence);
        BigEndian::write_u16(&mut header_bytes[10..12], self.payload.len() as u16);
        BigEndian::write_u32(&mut header_bytes[12..16], self.header.timestamp_us);

        out.extend_from_slice(&header_bytes);

        if let (Some(idx), Some(total)) = (self.header.fragment_index, self.header.total_fragments) {
            let mut frag_bytes = [0u8; 4];
            BigEndian::write_u16(&mut frag_bytes[0..2], idx);
            BigEndian::write_u16(&mut frag_bytes[2..4], total);
            out.extend_from_slice(&frag_bytes);
        }

        out.extend_from_slice(&self.payload);

        if let Some(tag) = self.auth_tag {
            out.extend_from_slice(&tag);
        }

        out
    }

    pub fn is_keyframe(&self) -> bool {
        (self.header.flags & flags::KEYFRAME) != 0
    }
}

/// Real-time XOR Forward Error Correction (FEC) block helper.
pub struct FecEngine;

impl FecEngine {
    /// Compute XOR parity vector across an array of equal or variable-length byte buffers.
    pub fn generate_parity(packets: &[&[u8]]) -> Vec<u8> {
        if packets.is_empty() {
            return Vec::new();
        }

        let max_len = packets.iter().map(|p| p.len()).max().unwrap_or(0);
        let mut parity = vec![0u8; max_len];

        for pkt in packets {
            for (i, b) in pkt.iter().enumerate() {
                parity[i] ^= *b;
            }
        }
        parity
    }

    /// Reconstruct one lost packet given parity and all other received packets.
    pub fn recover_packet(parity: &[u8], received_packets: &[&[u8]]) -> Vec<u8> {
        let mut recovered = parity.to_vec();
        for pkt in received_packets {
            for (i, b) in pkt.iter().enumerate() {
                if i < recovered.len() {
                    recovered[i] ^= *b;
                }
            }
        }
        recovered
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_absp_roundtrip_serialization() {
        let original = AbspPacket {
            header: AbspHeader {
                magic: ABSP_MAGIC,
                version: ABSP_VERSION,
                packet_type: PacketType::VideoNalu,
                flags: flags::KEYFRAME | flags::FRAGMENTED,
                channel: 0,
                sequence: 1042,
                payload_len: 5,
                timestamp_us: 500000,
                fragment_index: Some(0),
                total_fragments: Some(3),
            },
            payload: vec![1, 2, 3, 4, 5],
            auth_tag: None,
        };

        let bytes = original.serialize();
        let parsed = AbspPacket::parse(&bytes).expect("Failed to parse valid packet");

        assert_eq!(parsed.header.magic, ABSP_MAGIC);
        assert_eq!(parsed.header.sequence, 1042);
        assert_eq!(parsed.header.fragment_index, Some(0));
        assert_eq!(parsed.header.total_fragments, Some(3));
        assert!(parsed.is_keyframe());
        assert_eq!(parsed.payload, vec![1, 2, 3, 4, 5]);
    }

    #[test]
    fn test_fec_parity_and_recovery() {
        let p1 = vec![0x10, 0x20, 0x30];
        let p2 = vec![0x05, 0x05, 0x05];
        let p3 = vec![0xAA, 0xBB, 0xCC];

        let packets: Vec<&[u8]> = vec![&p1, &p2, &p3];
        let parity = FecEngine::generate_parity(&packets);

        // Assume p2 is lost. Recover p2 using parity and {p1, p3}
        let remaining: Vec<&[u8]> = vec![&p1, &p3];
        let recovered_p2 = FecEngine::recover_packet(&parity, &remaining);

        assert_eq!(recovered_p2, p2);
    }
}
