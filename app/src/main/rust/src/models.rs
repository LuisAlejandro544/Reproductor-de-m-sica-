//! Modelos de datos para metadatos de audio audiófilo en Rust.

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct RustAudioMetadata {
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub album_artist: Option<String>,
    pub genre: Option<String>,
    pub track_number: Option<u32>,
    pub year: Option<u32>,
    pub duration_ms: u64,
    pub sample_rate: Option<u32>,
    pub bit_depth: Option<u32>,
    pub channels: Option<u32>,
    pub format_name: String,
    pub has_artwork: bool,
    pub artwork_mime: Option<String>,
    pub engine_badge: String,
}

impl Default for RustAudioMetadata {
    fn default() -> Self {
        RustAudioMetadata {
            title: None,
            artist: None,
            album: None,
            album_artist: None,
            genre: None,
            track_number: None,
            year: None,
            duration_ms: 0,
            sample_rate: None,
            bit_depth: None,
            channels: None,
            format_name: "Desconocido".to_string(),
            has_artwork: false,
            artwork_mime: None,
            engine_badge: "Rust Audiophile Core 2.0".to_string(),
        }
    }
}
