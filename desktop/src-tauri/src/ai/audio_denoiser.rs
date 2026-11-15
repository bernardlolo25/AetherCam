//! Real-Time Neural Audio Denoiser & Noise Gate Filter

pub struct AudioDenoiser {
    noise_floor_estimate: f32,
    gate_threshold: f32,
    alpha_attack: f32,
    alpha_release: f32,
    envelope: f32,
}

impl AudioDenoiser {
    pub fn new() -> Self {
        Self {
            noise_floor_estimate: 0.005,
            gate_threshold: 0.012, // -38 dB threshold
            alpha_attack: 0.8,
            alpha_release: 0.05,
            envelope: 0.0,
        }
    }

    /// Process 16-bit interleaved PCM samples in-place.
    pub fn process_pcm16(&mut self, samples: &mut [i16]) {
        for sample in samples.iter_mut() {
            // Convert to normalized float [-1.0, 1.0]
            let x = (*sample as f32) / 32768.0;
            let abs_x = x.abs();

            // Track signal envelope
            if abs_x > self.envelope {
                self.envelope = self.alpha_attack * self.envelope + (1.0 - self.alpha_attack) * abs_x;
            } else {
                self.envelope = self.alpha_release * self.envelope + (1.0 - self.alpha_release) * abs_x;
            }

            // Spectral gate attenuation
            let gain = if self.envelope > self.gate_threshold {
                1.0
            } else if self.envelope > self.noise_floor_estimate {
                // Soft-knee transition
                (self.envelope - self.noise_floor_estimate) / (self.gate_threshold - self.noise_floor_estimate)
            } else {
                0.05 // Attenuate ambient floor by 26 dB
            };

            let filtered = (x * gain * 32768.0).clamp(-32768.0, 32767.0) as i16;
            *sample = filtered;
        }
    }
}
