//! Módulo de análisis para contenedores MP4 / M4A / AAC (átomos moov/udta/meta/ilst).

use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use crate::models::RustAudioMetadata;

/// Parser para MP4/M4A átomos (moov/udta/meta/ilst)
pub fn parse_mp4_atoms(file: &mut File, file_len: u64, meta: &mut RustAudioMetadata, artwork: &mut Option<Vec<u8>>) {
    if file.seek(SeekFrom::Start(0)).is_err() { return; }
    let max_read = std::cmp::min(file_len, 2 * 1024 * 1024) as usize;
    let mut buf = vec![0u8; max_read];
    if file.read_exact(&mut buf).is_err() { return; }

    // Buscar "ilst"
    if let Some(ilst_pos) = buf.windows(4).position(|w| w == b"ilst") {
        let mut p = ilst_pos + 4;
        while p + 8 < buf.len() {
            let atom_size = u32::from_be_bytes([buf[p], buf[p + 1], buf[p + 2], buf[p + 3]]) as usize;
            if atom_size < 8 || p + atom_size > buf.len() { break; }
            let atom_name = &buf[p + 4..p + 8];

            // Buscar sub-átomo 'data'
            let sub = &buf[p + 8..p + atom_size];
            if let Some(data_pos) = sub.windows(4).position(|w| w == b"data") {
                let val_start = data_pos + 12; // 4 id + 4 type/flags + 4 locale
                if val_start < sub.len() {
                    let val_bytes = &sub[val_start..];
                    if atom_name == b"\xa9nam" {
                        meta.title = Some(String::from_utf8_lossy(val_bytes).to_string());
                    } else if atom_name == b"\xa9ART" {
                        meta.artist = Some(String::from_utf8_lossy(val_bytes).to_string());
                    } else if atom_name == b"\xa9alb" {
                        meta.album = Some(String::from_utf8_lossy(val_bytes).to_string());
                    } else if atom_name == b"\xa9gen" {
                        meta.genre = Some(String::from_utf8_lossy(val_bytes).to_string());
                    } else if atom_name == b"\xa9day" {
                        let txt = String::from_utf8_lossy(val_bytes);
                        meta.year = txt.chars().take(4).collect::<String>().parse().ok();
                    } else if atom_name == b"covr" && artwork.is_none() {
                        meta.artwork_mime = Some("image/jpeg".to_string());
                        *artwork = Some(val_bytes.to_vec());
                    }
                }
            }

            p += atom_size;
        }
    }
}
