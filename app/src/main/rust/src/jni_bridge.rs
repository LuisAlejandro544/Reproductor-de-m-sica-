//! Puente JNI y C-ABI para comunicación entre Rust, C++ y Kotlin.

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jbyteArray, jstring};
use std::ffi::CStr;
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
    static VERSION: &[u8] = b"Ritmo Rust Audiophile Tag Engine v2.0-modular\0";
    VERSION.as_ptr() as *const c_char
}

// ==========================================
// JNI EXPORTS (Para RustAudioEngine.kt)
// ==========================================

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

    let (meta, _) = parse_audio_file(&path_str, false);

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
) -> jstring {
    let path_str: String = match env.get_string(&j_path) {
        Ok(s) => s.into(),
        Err(e) => return env.new_string(format!("Error JNI path: {}", e)).unwrap().into_raw(),
    };
    let title_str: String = match env.get_string(&j_title) {
        Ok(s) => s.into(),
        Err(e) => return env.new_string(format!("Error JNI title: {}", e)).unwrap().into_raw(),
    };
    let artist_str: String = match env.get_string(&j_artist) {
        Ok(s) => s.into(),
        Err(e) => return env.new_string(format!("Error JNI artist: {}", e)).unwrap().into_raw(),
    };

    let result = update_audio_metadata(&path_str, &title_str, &artist_str);
    let output_msg = match result {
        Ok(msg) => msg,
        Err(err) => format!("ERROR_RUST_UPDATE: {}", err),
    };

    match env.new_string(output_msg) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_util_RustAudioEngine_nativePing<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let msg = "RUST_AUDIO_ENGINE_OK (v2.0 Modular Audiophile Core)";
    match env.new_string(msg) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
