//! ritmo_rust — Núcleo nativo en Rust para Ritmo Music Player
//! Estructurado para procesamiento seguro de metadatos, buffers y algoritmos DSP.

/// Retorna la versión del núcleo de Rust para verificar enlace con JNI/C++.
#[no_mangle]
pub extern "C" fn ritmo_rust_core_version() -> i32 {
    1
}

/// Función de verificación de salud del módulo Rust.
#[no_mangle]
pub extern "C" fn ritmo_rust_ping() -> i32 {
    42
}
