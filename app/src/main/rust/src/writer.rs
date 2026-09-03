//! Módulo de edición y reescritura de metadatos de audio en Rust.

use std::fs::OpenOptions;
use std::io::{Read, Seek, SeekFrom, Write};
use crate::id3::{synchsafe_to_u32, u32_to_synchsafe};

/// Actualiza el título y el artista en un archivo de audio directamente en Rust.
/// Modifica in-place los frames TIT2 y TPE1 en ID3v2, así como los campos en ID3v1 si existen.
pub fn update_audio_metadata(path: &str, new_title: &str, new_artist: &str) -> Result<String, String> {
    let mut file = OpenOptions::new()
        .read(true)
        .write(true)
        .open(path)
        .map_err(|e| format!("Error abriendo archivo para escritura: {}", e))?;

    let file_len = file.metadata()
        .map_err(|e| format!("Error leyendo metadatos del archivo: {}", e))?
        .len();

    let mut updated_any = false;

    // 1. Intentar actualizar cabecera ID3v2
    let mut id3_header = [0u8; 10];
    if file.seek(SeekFrom::Start(0)).is_ok() && file.read_exact(&mut id3_header).is_ok() {
        if &id3_header[0..3] == b"ID3" {
            let version_major = id3_header[3];
            if update_id3v2_tags(&mut file, version_major, new_title, new_artist).is_ok() {
                updated_any = true;
            }
        }
    }

    // 2. Intentar actualizar ID3v1 si existe
    if file_len >= 128 {
        if update_id3v1_tags(&mut file, file_len, new_title, new_artist).is_ok() {
            updated_any = true;
        }
    }

    if updated_any {
        Ok(format!("Metadatos actualizados en Rust exitosamente para: {}", path))
    } else {
        Ok(format!("Archivo procesado por Rust (sin etiquetas ID3 reconocidas para reescritura directa): {}", path))
    }
}

pub fn update_id3v2_tags(file: &mut std::fs::File, version_major: u8, title: &str, artist: &str) -> Result<(), String> {
    file.seek(SeekFrom::Start(0)).map_err(|e| e.to_string())?;
    let mut header = [0u8; 10];
    file.read_exact(&mut header).map_err(|e| e.to_string())?;

    let tag_size = synchsafe_to_u32(&header[6..10]) as usize;
    if tag_size == 0 || tag_size > 20 * 1024 * 1024 {
        return Err("Tamaño de tag ID3v2 inválido".to_string());
    }

    let mut tag_buffer = vec![0u8; tag_size];
    file.read_exact(&mut tag_buffer).map_err(|e| e.to_string())?;

    let mut modified = false;
    let mut pos = 0;

    while pos + 10 < tag_size {
        let frame_id = match std::str::from_utf8(&tag_buffer[pos..pos + 4]) {
            Ok(s) => s,
            Err(_) => break,
        };
        if frame_id.as_bytes()[0] == 0 { break; }

        let frame_size = if version_major == 4 {
            synchsafe_to_u32(&tag_buffer[pos + 4..pos + 8]) as usize
        } else {
            ((tag_buffer[pos + 4] as usize) << 24)
                | ((tag_buffer[pos + 5] as usize) << 16)
                | ((tag_buffer[pos + 6] as usize) << 8)
                | (tag_buffer[pos + 7] as usize)
        };

        if pos + 10 + frame_size > tag_size { break; }

        if frame_id == "TIT2" || frame_id == "TPE1" {
            let target_text = if frame_id == "TIT2" { title } else { artist };
            let text_bytes = target_text.as_bytes();
            let needed_payload_len = 1 + text_bytes.len(); // 1 byte encoding (0 = ISO/ASCII o 3 = UTF-8)

            if needed_payload_len <= frame_size {
                // Reescribir in-place con encoding UTF-8 (3)
                let payload_start = pos + 10;
                tag_buffer[payload_start] = 3; // UTF-8
                tag_buffer[payload_start + 1..payload_start + 1 + text_bytes.len()].copy_from_slice(text_bytes);

                // Rellenar resto del frame con ceros para no corromper la alineación
                for b in &mut tag_buffer[payload_start + 1 + text_bytes.len()..payload_start + frame_size] {
                    *b = 0;
                }
                modified = true;
            }
        }

        pos += 10 + frame_size;
    }

    if modified {
        file.seek(SeekFrom::Start(10)).map_err(|e| e.to_string())?;
        file.write_all(&tag_buffer).map_err(|e| e.to_string())?;
    }

    Ok(())
}

pub fn update_id3v1_tags(file: &mut std::fs::File, file_len: u64, title: &str, artist: &str) -> Result<(), String> {
    file.seek(SeekFrom::Start(file_len - 128)).map_err(|e| e.to_string())?;
    let mut buffer = [0u8; 128];
    file.read_exact(&mut buffer).map_err(|e| e.to_string())?;

    if &buffer[0..3] != b"TAG" {
        return Ok(()); // No tiene ID3v1
    }

    // Título: 30 bytes (pos 3 a 33)
    let title_bytes = title.as_bytes();
    for i in 0..30 {
        buffer[3 + i] = if i < title_bytes.len() { title_bytes[i] } else { 0 };
    }

    // Artista: 30 bytes (pos 33 a 63)
    let artist_bytes = artist.as_bytes();
    for i in 0..30 {
        buffer[33 + i] = if i < artist_bytes.len() { artist_bytes[i] } else { 0 };
    }

    file.seek(SeekFrom::Start(file_len - 128)).map_err(|e| e.to_string())?;
    file.write_all(&buffer).map_err(|e| e.to_string())?;

    Ok(())
}
