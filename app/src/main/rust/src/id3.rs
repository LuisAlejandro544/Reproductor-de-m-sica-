//! Módulo de análisis y decodificación de etiquetas ID3 (v1 y v2.2/v2.3/v2.4).

use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use crate::models::RustAudioMetadata;

/// Parser para ID3v2 (v2.2, v2.3, v2.4)
pub fn parse_id3v2(file: &mut File, meta: &mut RustAudioMetadata, artwork: &mut Option<Vec<u8>>) {
    if file.seek(SeekFrom::Start(0)).is_err() {
        return;
    }

    let mut header = [0u8; 10];
    if file.read_exact(&mut header).is_err() || &header[0..3] != b"ID3" {
        return;
    }

    let version_major = header[3]; // 2 -> 2.2, 3 -> 2.3, 4 -> 2.4
    let tag_size = synchsafe_to_u32(&header[6..10]) as usize;

    if tag_size == 0 || tag_size > 30 * 1024 * 1024 {
        return;
    }

    let mut tag_buffer = vec![0u8; tag_size];
    if file.read_exact(&mut tag_buffer).is_err() {
        return;
    }

    let mut pos = 0;
    while pos + 10 < tag_size {
        if version_major == 2 {
            if pos + 6 > tag_size { break; }
            let id = match std::str::from_utf8(&tag_buffer[pos..pos + 3]) {
                Ok(s) => s,
                Err(_) => break,
            };
            if id.as_bytes()[0] == 0 { break; }
            let frame_size = ((tag_buffer[pos + 3] as usize) << 16)
                | ((tag_buffer[pos + 4] as usize) << 8)
                | (tag_buffer[pos + 5] as usize);
            pos += 6;
            if pos + frame_size > tag_size { break; }
            let frame_data = &tag_buffer[pos..pos + frame_size];
            pos += frame_size;

            let text = decode_text_frame(frame_data);
            match id {
                "TT2" => meta.title = Some(text),
                "TP1" => meta.artist = Some(text),
                "TAL" => meta.album = Some(text),
                "TRK" => meta.track_number = text.parse::<u32>().ok(),
                "TYE" => meta.year = text.parse::<u32>().ok(),
                _ => {}
            }
        } else {
            let id = match std::str::from_utf8(&tag_buffer[pos..pos + 4]) {
                Ok(s) => s,
                Err(_) => break,
            };
            if id.as_bytes()[0] == 0 { break; }

            let frame_size = if version_major == 4 {
                synchsafe_to_u32(&tag_buffer[pos + 4..pos + 8]) as usize
            } else {
                ((tag_buffer[pos + 4] as usize) << 24)
                    | ((tag_buffer[pos + 5] as usize) << 16)
                    | ((tag_buffer[pos + 6] as usize) << 8)
                    | (tag_buffer[pos + 7] as usize)
            };

            pos += 10;
            if pos + frame_size > tag_size { break; }
            let frame_data = &tag_buffer[pos..pos + frame_size];
            pos += frame_size;

            match id {
                "TIT2" => meta.title = Some(decode_text_frame(frame_data)),
                "TPE1" => meta.artist = Some(decode_text_frame(frame_data)),
                "TALB" => meta.album = Some(decode_text_frame(frame_data)),
                "TPE2" => meta.album_artist = Some(decode_text_frame(frame_data)),
                "TCON" => meta.genre = Some(decode_text_frame(frame_data)),
                "TRCK" => {
                    let txt = decode_text_frame(frame_data);
                    meta.track_number = txt.split('/').next().and_then(|t| t.trim().parse().ok());
                }
                "TYER" | "TDRC" => {
                    let txt = decode_text_frame(frame_data);
                    meta.year = txt.chars().take(4).collect::<String>().parse().ok();
                }
                "TLEN" => {
                    let txt = decode_text_frame(frame_data);
                    if let Ok(ms) = txt.parse::<u64>() {
                        meta.duration_ms = ms;
                    }
                }
                "APIC" => {
                    if artwork.is_none() && frame_data.len() > 10 {
                        if let Some((mime, data)) = parse_apic_frame(frame_data) {
                            meta.artwork_mime = Some(mime);
                            *artwork = Some(data);
                        }
                    }
                }
                _ => {}
            }
        }
    }
}

/// Decodifica frames de texto ID3v2 considerando la codificación especificada en el primer byte.
pub fn decode_text_frame(data: &[u8]) -> String {
    if data.is_empty() {
        return String::new();
    }
    let encoding = data[0];
    let payload = &data[1..];

    match encoding {
        0 => {
            // ISO-8859-1 / ASCII
            payload.iter()
                .take_while(|&&b| b != 0)
                .map(|&b| b as char)
                .collect::<String>()
                .trim()
                .to_string()
        }
        1 => decode_utf16(payload),
        2 => decode_utf16_be(payload),
        3 | _ => {
            String::from_utf8_lossy(payload)
                .trim_matches('\0')
                .trim()
                .to_string()
        }
    }
}

pub fn decode_utf16(data: &[u8]) -> String {
    if data.len() < 2 { return String::new(); }
    let (is_le, start) = if data[0] == 0xFF && data[1] == 0xFE {
        (true, 2)
    } else if data[0] == 0xFE && data[1] == 0xFF {
        (false, 2)
    } else {
        (true, 0)
    };

    let u16_chars: Vec<u16> = data[start..]
        .chunks_exact(2)
        .map(|c| if is_le { u16::from_le_bytes([c[0], c[1]]) } else { u16::from_be_bytes([c[0], c[1]]) })
        .take_while(|&u| u != 0)
        .collect();

    String::from_utf16_lossy(&u16_chars).trim().to_string()
}

pub fn decode_utf16_be(data: &[u8]) -> String {
    let u16_chars: Vec<u16> = data
        .chunks_exact(2)
        .map(|c| u16::from_be_bytes([c[0], c[1]]))
        .take_while(|&u| u != 0)
        .collect();
    String::from_utf16_lossy(&u16_chars).trim().to_string()
}

pub fn parse_apic_frame(data: &[u8]) -> Option<(String, Vec<u8>)> {
    if data.len() < 10 { return None; }
    let _encoding = data[0];
    let mut pos = 1;

    let mime_start = pos;
    while pos < data.len() && data[pos] != 0 {
        pos += 1;
    }
    let mime = String::from_utf8_lossy(&data[mime_start..pos]).to_string();
    pos += 1;

    if pos >= data.len() { return None; }
    let _picture_type = data[pos];
    pos += 1;

    while pos < data.len() && data[pos] != 0 {
        pos += 1;
    }
    if pos < data.len() { pos += 1; }

    if pos < data.len() {
        let image_bytes = data[pos..].to_vec();
        Some((if mime.is_empty() { "image/jpeg".to_string() } else { mime }, image_bytes))
    } else {
        None
    }
}

/// Parser para ID3v1 (últimos 128 bytes)
pub fn parse_id3v1(file: &mut File, file_len: u64, meta: &mut RustAudioMetadata) {
    if file.seek(SeekFrom::Start(file_len - 128)).is_err() {
        return;
    }
    let mut buffer = [0u8; 128];
    if file.read_exact(&mut buffer).is_err() || &buffer[0..3] != b"TAG" {
        return;
    }

    let extract_str = |slice: &[u8]| -> String {
        slice.iter()
            .take_while(|&&b| b != 0)
            .map(|&b| b as char)
            .collect::<String>()
            .trim()
            .to_string()
    };

    if meta.title.is_none() {
        let t = extract_str(&buffer[3..33]);
        if !t.is_empty() { meta.title = Some(t); }
    }
    if meta.artist.is_none() {
        let a = extract_str(&buffer[33..63]);
        if !a.is_empty() { meta.artist = Some(a); }
    }
    if meta.album.is_none() {
        let alb = extract_str(&buffer[63..93]);
        if !alb.is_empty() { meta.album = Some(alb); }
    }
    if meta.year.is_none() {
        let y = extract_str(&buffer[93..97]);
        if let Ok(year_num) = y.parse::<u32>() {
            meta.year = Some(year_num);
        }
    }
}

pub fn synchsafe_to_u32(bytes: &[u8]) -> u32 {
    let mut result = 0u32;
    for &b in bytes {
        result = (result << 7) | ((b & 0x7F) as u32);
    }
    result
}

pub fn u32_to_synchsafe(mut val: u32) -> [u8; 4] {
    let mut out = [0u8; 4];
    for i in (0..4).rev() {
        out[i] = (val & 0x7F) as u8;
        val >>= 7;
    }
    out
}
