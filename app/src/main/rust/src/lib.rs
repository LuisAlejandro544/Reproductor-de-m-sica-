//! # Ritmo Rust Audiophile Engine
//!
//! Núcleo nativo audiófilo modular en Rust para extracción ultra-rápida,
//! indexación sin pérdida y edición directa de metadatos de audio en smartphone.
//!
//! Arquitectura modular:
//! - `models`: Estructuras de datos serializables.
//! - `id3`: Decodificación y análisis de ID3v1 e ID3v2 (v2.2, v2.3, v2.4).
//! - `flac`: Decodificación de bloques STREAMINFO, Vorbis Comment y PICTURE en FLAC.
//! - `vorbis`: Parseo de comentarios Vorbis y contenedor OGG Vorbis / Opus.
//! - `ape`: Parseo de APE Tag v1 y v2.
//! - `mp4`: Parseo de átomos MP4/M4A/AAC ilst.
//! - `writer`: Reescritura in-place de títulos y artistas en tags.
//! - `jni_bridge`: Exportación C-ABI y JNI para C++ y Kotlin.

pub mod models;
pub mod id3;
pub mod flac;
pub mod vorbis;
pub mod ape;
pub mod mp4;
pub mod writer;
pub mod jni_bridge;

use std::fs::File;
use std::path::Path;
use models::RustAudioMetadata;
pub use writer::update_audio_metadata;

/// Función principal de extracción de metadatos en Rust.
/// Analiza el archivo por su firma de bytes sin depender de códecs lentos de Java/Kotlin.
pub fn parse_audio_file(path: &str, extract_artwork: bool) -> (RustAudioMetadata, Option<Vec<u8>>) {
    let mut meta = RustAudioMetadata::default();
    let mut artwork: Option<Vec<u8>> = None;

    let path_obj = Path::new(path);
    let mut file = match File::open(path_obj) {
        Ok(f) => f,
        Err(_) => return (meta, None),
    };

    let file_len = match file.metadata() {
        Ok(m) => m.len(),
        Err(_) => 0,
    };

    let ext = path_obj
        .extension()
        .and_then(|e| e.to_str())
        .map(|s| s.to_lowercase())
        .unwrap_or_default();

    let mut unused_art: Option<Vec<u8>> = None;
    let art_ref = if extract_artwork {
        &mut artwork
    } else {
        &mut unused_art
    };

    // Detección por extensión y números mágicos
    match ext.as_str() {
        "flac" => {
            meta.format_name = "FLAC (Lossless)".to_string();
            flac::parse_flac(&mut file, &mut meta, art_ref);
        }
        "ogg" | "opus" => {
            meta.format_name = if ext == "opus" { "Opus (Ogg)".to_string() } else { "Vorbis (Ogg)".to_string() };
            vorbis::parse_ogg(&mut file, &mut meta);
        }
        "m4a" | "aac" | "mp4" => {
            meta.format_name = "M4A / AAC (MPEG-4)".to_string();
            mp4::parse_mp4_atoms(&mut file, file_len, &mut meta, art_ref);
        }
        "ape" => {
            meta.format_name = "Monkey's Audio (APE)".to_string();
            ape::parse_ape_tags(&mut file, file_len, &mut meta);
        }
        "wav" => {
            meta.format_name = "WAV (PCM)".to_string();
            id3::parse_id3v2(&mut file, &mut meta, art_ref);
        }
        "mp3" | _ => {
            meta.format_name = "MPEG Audio (MP3)".to_string();
            id3::parse_id3v2(&mut file, &mut meta, art_ref);
            if file_len > 128 {
                id3::parse_id3v1(&mut file, file_len, &mut meta);
            }
            if file_len > 32 {
                ape::parse_ape_tags(&mut file, file_len, &mut meta);
            }
        }
    }

    meta.has_artwork = artwork.is_some();
    (meta, artwork)
}
