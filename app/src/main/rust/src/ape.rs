//! Módulo de análisis para etiquetas APE (APE Tag v1 y v2).

use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use crate::models::RustAudioMetadata;

/// Parser para etiquetas APE (APE Tag v1 y v2)
pub fn parse_ape_tags(file: &mut File, file_len: u64, meta: &mut RustAudioMetadata) {
    if file_len < 32 { return; }
    let offset = if file_len > 160 { file_len - 160 } else { file_len - 32 };
    if file.seek(SeekFrom::Start(offset)).is_err() { return; }

    let mut buf = vec![0u8; 160];
    if file.read(&mut buf).is_err() { return; }

    if let Some(pos) = buf.windows(8).position(|w| w == b"APETAGEX") {
        let footer = &buf[pos..];
        if footer.len() >= 32 {
            let tag_size = u32::from_le_bytes([footer[12], footer[13], footer[14], footer[15]]) as u64;
            let item_count = u32::from_le_bytes([footer[16], footer[17], footer[18], footer[19]]) as usize;

            if tag_size > 0 && tag_size < 5 * 1024 * 1024 && file_len >= tag_size {
                let tag_start = file_len - tag_size;
                if file.seek(SeekFrom::Start(tag_start)).is_ok() {
                    let mut tag_data = vec![0u8; tag_size as usize];
                    if file.read_exact(&mut tag_data).is_ok() {
                        let mut p = 0;
                        for _ in 0..item_count {
                            if p + 8 > tag_data.len() { break; }
                            let val_len = u32::from_le_bytes([tag_data[p], tag_data[p + 1], tag_data[p + 2], tag_data[p + 3]]) as usize;
                            let _flags = u32::from_le_bytes([tag_data[p + 4], tag_data[p + 5], tag_data[p + 6], tag_data[p + 7]]);
                            p += 8;

                            let key_start = p;
                            while p < tag_data.len() && tag_data[p] != 0 { p += 1; }
                            if p >= tag_data.len() { break; }
                            let key = String::from_utf8_lossy(&tag_data[key_start..p]).to_uppercase();
                            p += 1; // saltar null

                            if p + val_len > tag_data.len() { break; }
                            let val = String::from_utf8_lossy(&tag_data[p..p + val_len]).to_string();
                            p += val_len;

                            match key.as_str() {
                                "TITLE" => if meta.title.is_none() { meta.title = Some(val); },
                                "ARTIST" => if meta.artist.is_none() { meta.artist = Some(val); },
                                "ALBUM" => if meta.album.is_none() { meta.album = Some(val); },
                                "GENRE" => if meta.genre.is_none() { meta.genre = Some(val); },
                                "YEAR" => if meta.year.is_none() { meta.year = val.parse().ok(); },
                                _ => {}
                            }
                        }
                    }
                }
            }
        }
    }
}
