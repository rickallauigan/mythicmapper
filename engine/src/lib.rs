// SPDX-License-Identifier: GPL-3.0-or-later
//! Pure reference math. Android's existing Java runtime remains authoritative.

/// Normalize an evdev axis about its observed midpoint, using asymmetric extents.
pub fn normalize(value: i32, min: i32, max: i32, center: i32) -> f32 {
    if min >= max || center <= min || center >= max {
        return 0.0;
    }
    let extent = if value < center {
        center as f64 - min as f64
    } else {
        max as f64 - center as f64
    };
    ((value as f64 - center as f64) / extent).clamp(-1.0, 1.0) as f32
}

/// Radial dead zone, sensitivity, inversion, then unit-circle clamp.
/// Mirrors ControllerAimHandler.vector for valid configurations. Invalid FFI inputs
/// fail closed to neutral. f64 intermediates preserve the original Java calculation.
pub fn vector(x: f32, y: f32, dead_zone: f32, sx: f32, sy: f32, invert_y: bool) -> [f32; 2] {
    if ![x, y, dead_zone, sx, sy].iter().all(|v| v.is_finite())
        || !(0.0..1.0).contains(&dead_zone)
        || sx <= 0.0
        || sy <= 0.0
    {
        return [0.0; 2];
    }
    let length = (x as f64).hypot(y as f64);
    if length <= dead_zone as f64 || length == 0.0 {
        return [0.0; 2];
    }
    // Java computes 1 - deadZone as float before promotion to double.
    let magnitude = (length.min(1.0) - dead_zone as f64) / (1.0_f32 - dead_zone) as f64;
    let mut vx = (x as f64 / length * magnitude * sx as f64) as f32;
    let mut vy =
        (y as f64 / length * magnitude * sy as f64 * if invert_y { -1.0 } else { 1.0 }) as f32;
    let scaled = (vx as f64).hypot(vy as f64);
    if scaled > 1.0 {
        vx = (vx as f64 / scaled) as f32;
        vy = (vy as f64 / scaled) as f32;
    }
    [vx, vy]
}

/// Stateless C ABI, no pointers, allocation, callbacks, or unwind across JNI.
/// Upper 32 bits are x's IEEE bits; lower 32 bits are y's IEEE bits. ABI version 1.
#[unsafe(no_mangle)]
pub extern "C" fn mythicmapper_vector_v1(
    x: f32,
    y: f32,
    dz: f32,
    sx: f32,
    sy: f32,
    invert: i32,
) -> u64 {
    let [vx, vy] = vector(x, y, dz, sx, sy, invert != 0);
    ((vx.to_bits() as u64) << 32) | vy.to_bits() as u64
}

#[unsafe(no_mangle)]
pub extern "C" fn mythicmapper_abi_version() -> i32 {
    1
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn asymmetric_extents_and_extreme_integer_ranges() {
        assert_eq!(normalize(-32768, -32768, 32767, 0), -1.0);
        assert_eq!(normalize(32767, -32768, 32767, 0), 1.0);
        assert_eq!(normalize(128, 0, 255, 128), 0.0);
        assert_eq!(normalize(i32::MAX, i32::MIN, i32::MAX, 0), 1.0);
        assert_eq!(normalize(1, 1, 1, 1), 0.0);
    }
    #[test]
    fn dead_zone_and_inversion() {
        assert_eq!(vector(0.1, 0.0, 0.15, 1.0, 1.0, false), [0.0; 2]);
        assert_eq!(vector(0.15, 0.0, 0.15, 1.0, 1.0, false), [0.0; 2]);
        assert!((vector(0.0, 1.0, 0.15, 1.0, 1.0, true)[1] + 1.0).abs() < 1e-6);
    }
    #[test]
    fn invalid_ffi_values_are_neutral() {
        for bad in [f32::NAN, f32::INFINITY, f32::NEG_INFINITY] {
            assert_eq!(vector(bad, 1.0, 0.15, 1.0, 1.0, false), [0.0; 2]);
            assert_eq!(vector(1.0, 1.0, bad, 1.0, 1.0, false), [0.0; 2]);
        }
        for dz in [-1.0, 1.0, 2.0] {
            assert_eq!(vector(1.0, 1.0, dz, 1.0, 1.0, false), [0.0; 2]);
        }
    }
    #[test]
    fn exhaustive_grid_properties() {
        // Deterministic property coverage; no external testing crate required.
        for xi in -100..=100 {
            for yi in -100..=100 {
                for dz in [0.0, 0.15, 0.9] {
                    let x = xi as f32 / 100.0;
                    let y = yi as f32 / 100.0;
                    let a = vector(x, y, dz, 1.0, 1.0, false);
                    let b = vector(-x, -y, dz, 1.0, 1.0, false);
                    assert!(a.iter().all(|v| v.is_finite()));
                    assert!((a[0] as f64).hypot(a[1] as f64) <= 1.0000001);
                    assert!((a[0] + b[0]).abs() < 1e-6 && (a[1] + b[1]).abs() < 1e-6);
                }
            }
        }
    }
    #[test]
    fn replay_is_deterministic_and_packing_roundtrips() {
        let trace = [(0.0, 0.0), (0.5, -0.3), (1.0, 1.0), (0.0, 0.0)];
        let replay = || trace.map(|(x, y)| mythicmapper_vector_v1(x, y, 0.15, 1.0, 1.0, 0));
        assert_eq!(replay(), replay());
        for ((x, y), packed) in trace.into_iter().zip(replay()) {
            assert_eq!(
                [
                    f32::from_bits((packed >> 32) as u32),
                    f32::from_bits(packed as u32)
                ],
                vector(x, y, 0.15, 1.0, 1.0, false)
            );
        }
    }
}
