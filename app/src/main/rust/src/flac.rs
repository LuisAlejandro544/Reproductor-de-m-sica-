//! Módulo de análisis para formato FLAC (Free Lossless Audio Codec).

use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use crate::models::RustAudioMetadata;
use crate::vorbis::parse_vorbis_comment;

/// Parser nativo para FLAC
pub fn parse_flac(file: &mut File, meta: &mut RustAudioMetadata, artwork: &mut Option<Vec<u8>>) {
    if file.seek(SeekFrom::Start(4)).is_err() {
        return;
    }

    let mut is_last = false;
    while !is_last {
        let mut header = [0u8; 4];
        if file.read_exact(&mut header).is_err() {
            break;
        }

        is_last = (header[0] & 0x80) != 0;
        let block_type = header[0] & 0x7F;
        let length = ((header[1] as usize) << 16)
            | ((header[2] as usize) << 8)
            | (header[3] as usize);

        if length > 20 * 1024 * 1024 { break; }

        let mut block_data = vec![0u8; length];
        if file.read_exact(&mut block_data).is_err() {
            break;
        }

        match block_type {
            0 => {
                // STREAMINFO: 34 bytes
                if block_data.len() >= 18 {
                    let sr = ((block_data[10] as u32) << 12)
                        | ((block_data[11] as u32) << 4)
                        | ((block_data[12] as u32) >> 4);
                    let ch = (((block_data[12] >> 1) & 0x07) + 1) as u32;
                    let bps = ((((block_data[12] & 0x01) << 4) | (block_data[13] >> 4)) + 1) as u32;

                    let total_samples = (((block_data[13] & 0x0F) as u64) << 32)
                        | ((block_data[14] as u64) << 24)
                        | ((block_data[15] as u64) << 16)
                        | ((block_data[16] as u64) << 8)
                        | (block_data[17] as u64);

                    meta.sample_rate = Some(sr);
                    meta.channels = Some(ch);
                    meta.bit_depth = Some(bps);

                    if sr > 0 && total_samples > 0 {
                        meta.duration_ms = (total_samples * 1000) / (sr as u64);
                    }
                }
            }
            4 => {
                // VORBIS_COMMENT
                parse_vorbis_comment(&block_data, meta);
            }
            6 => {
                // PICTURE
                if artwork.is_none() && block_data.len() > 32 {
                    if let Some((mime, data)) = parse_flac_picture(&block_data) {
                        meta.artwork_mime = Some(mime);
                        *artwork = Some(data);
                    }
                }
            }
            _ => {}
        }
    }
}

pub fn parse_flac_picture(data: &[u8]) -> Option<(String, Vec<u8>)> {
    let mut pos = 4; // saltar tipo de imagen
    if pos + 4 > data.len() { return None; }
    let mime_len = u32::from_be_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]) as usize;
    pos += 4;

    if pos + mime_len > data.len() { return None; }
    let mime = String::from_utf8_lossy(&data[pos..pos + mime_len]).to_string();
    pos += mime_len;

    // Saltar descripción
    if pos + 4 > data.len() { return None; }
    let desc_len = u32::from_be_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]) as usize;
    pos += 4 + desc_len;

    // Saltar width, height, color_depth, colors_used (16 bytes)
    pos += 16;

    if pos + 4 > data.len() { return None; }
    let data_len = u32::from_be_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]) as usize;
    pos += 4;

    if pos + data_len <= data.len() {
        Some((mime, data[pos..pos + data_len].to_vec()))
    } else {
        None
    }
}
