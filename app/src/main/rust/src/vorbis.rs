//! Módulo de análisis para comentarios Vorbis y contenedor OGG Vorbis / Opus.

use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use crate::models::RustAudioMetadata;

/// Extrae comentarios Vorbis (usado en FLAC, OGG Vorbis y Opus).
pub fn parse_vorbis_comment(data: &[u8], meta: &mut RustAudioMetadata) {
    if data.len() < 8 { return; }
    let vendor_len = u32::from_le_bytes([data[0], data[1], data[2], data[3]]) as usize;
    let mut pos = 4 + vendor_len;

    if pos + 4 > data.len() { return; }
    let count = u32::from_le_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]) as usize;
    pos += 4;

    for _ in 0..count {
        if pos + 4 > data.len() { break; }
        let len = u32::from_le_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]) as usize;
        pos += 4;
        if pos + len > data.len() { break; }

        let comment = String::from_utf8_lossy(&data[pos..pos + len]);
        pos += len;

        if let Some((k, v)) = comment.split_once('=') {
            let key = k.trim().to_uppercase();
            let val = v.trim().to_string();

            match key.as_str() {
                "TITLE" => meta.title = Some(val),
                "ARTIST" => meta.artist = Some(val),
                "ALBUM" => meta.album = Some(val),
                "ALBUMARTIST" | "ALBUM ARTIST" => meta.album_artist = Some(val),
                "GENRE" => meta.genre = Some(val),
                "TRACKNUMBER" | "TRACK" => {
                    meta.track_number = val.split('/').next().and_then(|t| t.trim().parse().ok());
                }
                "DATE" | "YEAR" => {
                    meta.year = val.chars().take(4).collect::<String>().parse().ok();
                }
                _ => {}
            }
        }
    }
}

/// Parser básico para Ogg Vorbis / Opus
pub fn parse_ogg(file: &mut File, meta: &mut RustAudioMetadata) {
    if file.seek(SeekFrom::Start(0)).is_err() { return; }
    let mut buffer = vec![0u8; 8192];
    if file.read(&mut buffer).is_err() { return; }

    // Buscar "OpusTags" o "vorbis"
    if let Some(pos) = buffer.windows(8).position(|w| w == b"OpusTags") {
        parse_vorbis_comment(&buffer[pos + 8..], meta);
    } else if let Some(pos) = buffer.windows(6).position(|w| w == b"vorbis") {
        parse_vorbis_comment(&buffer[pos + 6..], meta);
    }
}
