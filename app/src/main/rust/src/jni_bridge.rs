//! Puente JNI y C-ABI para comunicación entre Rust, C++ y Kotlin.

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jbyteArray, jint, jstring};
use std::os::raw::c_char;
use crate::parse_audio_file;
use crate::writer::update_audio_metadata;

// ==========================================
// C-ABI EXPORTS (Para C++ / CMake / native_bridge.cpp)
// ==========================================

#[no_mangle]
pub extern "C" fn ritmo_rust_ping() -> i32 {
    777 // Código de enlace confirmado con el núcleo Rust
}

#[no_mangle]
pub extern "C" fn ritmo_rust_core_version() -> *const c_char {
    static VERSION: &[u8] = b"Ritmo Rust Audiophile Tag Engine v0.2.0-modular\0";
    VERSION.as_ptr() as *const c_char
}

// ==========================================
// JNI EXPORTS (Para RustAudioEngine.kt)
// ==========================================

#[no_mangle]
pub extern "system" fn Java_com_example_util_RustAudioEngine_nativePing(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    777
}

#[no_mangle]
pub extern "system" fn Java_com_example_util_RustAudioEngine_nativeGetVersion<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let msg = "Rust Audiophile Tag Engine v0.2.0 (Dual Native C++/Rust)";
    match env.new_string(msg) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_util_RustAudioEngine_nativeExtractMetadata<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    j_path: JString<'local>,
) -> jstring {
    let path_str: String = match env.get_string(&j_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let (mut meta, _) = parse_audio_file(&path_str, false);
    meta.engine_badge = "Rust Native Core v0.2.0".to_string();

    let json_str = match serde_json::to_string(&meta) {
        Ok(j) => j,
        Err(e) => format!("{{\"error\":\"Error serializando JSON de metadatos: {}\"}}", e),
    };

    match env.new_string(json_str) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_util_RustAudioEngine_nativeExtractArtwork<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    j_path: JString<'local>,
) -> jbyteArray {
    let path_str: String = match env.get_string(&j_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let (_, artwork) = parse_audio_file(&path_str, true);

    if let Some(bytes) = artwork {
        match env.byte_array_from_slice(&bytes) {
            Ok(arr) => arr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    } else {
        std::ptr::null_mut()
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_util_RustAudioEngine_nativeUpdateMetadata<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    j_path: JString<'local>,
    j_title: JString<'local>,
    j_artist: JString<'local>,
    j_album: JString<'local>,
    j_genre: JString<'local>,
    j_year: JString<'local>,
) -> jint {
    let path_str: String = match env.get_string(&j_path) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };
    let title_str: String = match env.get_string(&j_title) {
        Ok(s) => s.into(),
        Err(_) => return -2,
    };
    let artist_str: String = match env.get_string(&j_artist) {
        Ok(s) => s.into(),
        Err(_) => return -3,
    };
    let album_str: String = match env.get_string(&j_album) {
        Ok(s) => s.into(),
        Err(_) => return -4,
    };
    let genre_str: String = match env.get_string(&j_genre) {
        Ok(s) => s.into(),
        Err(_) => return -5,
    };
    let year_str: String = match env.get_string(&j_year) {
        Ok(s) => s.into(),
        Err(_) => return -6,
    };

    match update_audio_metadata(&path_str, &title_str, &artist_str, &album_str, &genre_str, &year_str) {
        Ok(_) => 0,
        Err(_) => -10,
    }
}
