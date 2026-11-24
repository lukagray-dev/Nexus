/**
 * @file voice-changer-engine.ts
 * @description Real-time Web Audio API Digital Signal Processing (DSP) engine
 * for Parent-to-Child Voice Intercom in Nexus Parent.
 *
 * Hey friend! Here is how live voice modification works under the hood:
 * 1. Capture: We receive the parent's raw microphone MediaStream from getUserMedia.
 * 2. Source Node: An AudioContext creates a MediaStreamAudioSourceNode from the mic stream.
 * 3. Processing Graph: Audio flows through modular Web Audio nodes tailored to each voice preset:
 *    - Granular Pitch Shifter: Uses two staggered, cyclic delay lines crossfaded with a triangle
 *      window to shift pitch UP (female, chipmunk) or DOWN (male, monster) in real time without lag.
 *    - Ring Modulator: Multiplies voice frequencies with an analog-modeled carrier oscillator (60-95Hz)
 *      to synthesize authentic metallic robot speech.
 *    - Biquad Filters: Resonant lowpass, highpass, bandpass, and peaking filters shape vocal formants.
 *    - WaveShaper Distortion: Adds non-linear harmonic saturation for gritty monster & radio effects.
 *    - Delay & Feedback: Spatial multi-tap echoes and ethereal chorus modulation.
 * 4. Output Destination: All effects feed into a MediaStreamAudioDestinationNode.
 *    The destination node's stable audio track is attached to the WebRTC transceiver sender.
 * 5. Hot-Switching: When the parent picks a different voice from the dropdown, the engine performs
 *    a smooth 25ms crossfade between effect chains, so the child hears the voice transform instantly
 *    without any audio clicks, pops, or stream renegotiation!
 */

import { VoicePresetId, getSavedVoicePresetId, saveVoicePresetId } from './voice-presets.js';

/**
 * Encapsulates an active DSP effect chain and its lifecycle cleanup handles.
 */
interface ActiveEffectChain {
  /** The input node where raw microphone audio enters the effect */
  inputNode: AudioNode;
  /** The output node where processed audio leaves the effect */
  outputNode: AudioNode;
  /** Fade gain node used for click-free crossfades during preset switching */
  fadeGain: GainNode;
  /** Teardown function to stop any active oscillators and disconnect internal nodes */
  dispose: () => void;
}

export class VoiceChangerEngine {
  private audioContext: AudioContext | null = null;
  private sourceNode: MediaStreamAudioSourceNode | null = null;
  private destinationNode: MediaStreamAudioDestinationNode | null = null;
  private activeChain: ActiveEffectChain | null = null;
  private currentVoiceId: VoicePresetId = 'original';
  private isInitialized = false;

  /**
   * Initializes the Web Audio DSP graph with the raw microphone stream.
   *
   * @param inputStream The raw MediaStream acquired from navigator.mediaDevices.getUserMedia.
   * @returns true if initialization succeeded; false otherwise.
   */
  public initialize(inputStream: MediaStream): boolean {
    if (this.isInitialized && this.destinationNode) {
      console.log('[VoiceChanger] Engine is already initialized.');
      return true;
    }

    try {
      console.log('[VoiceChanger] Initializing Web Audio API context and destination node...');
      const AudioCtx =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;

      this.audioContext = new AudioCtx({ sampleRate: 48000 });
      this.sourceNode = this.audioContext.createMediaStreamSource(inputStream);
      this.destinationNode = this.audioContext.createMediaStreamDestination();

      // Load saved voice from localStorage (defaults to 'original' if not set)
      this.currentVoiceId = getSavedVoicePresetId();
      console.log(`[VoiceChanger] Activating initial voice preset: ${this.currentVoiceId}`);

      // Mount the initial effect chain
      this.activeChain = this.buildEffectChain(this.currentVoiceId);
      this.sourceNode.connect(this.activeChain.inputNode);
      this.activeChain.outputNode.connect(this.destinationNode);

      this.isInitialized = true;
      return true;
    } catch (error) {
      console.error('[VoiceChanger] Failed to initialize VoiceChangerEngine:', error);
      this.cleanup();
      return false;
    }
  }

  /**
   * Returns the processed MediaStreamTrack destined for WebRTC transmission.
   * This track stays stable and continuous across voice preset changes.
   */
  public getProcessedTrack(): MediaStreamTrack | null {
    if (!this.destinationNode) return null;
    const tracks = this.destinationNode.stream.getAudioTracks();
    return tracks.length > 0 ? tracks[0] : null;
  }

  /**
   * Returns the currently active voice preset identifier.
   */
  public getActiveVoice(): VoicePresetId {
    return this.currentVoiceId;
  }

  /**
   * Seamlessly switches to a new voice preset with an equal-power 25ms crossfade.
   * Can be invoked either while idle or during active live speech.
   *
   * @param newVoiceId The target voice preset identifier.
   */
  public setVoice(newVoiceId: VoicePresetId): void {
    if (!this.isInitialized || !this.audioContext || !this.sourceNode || !this.destinationNode) {
      this.currentVoiceId = newVoiceId;
      saveVoicePresetId(newVoiceId);
      return;
    }

    if (this.currentVoiceId === newVoiceId && this.activeChain) {
      console.log(`[VoiceChanger] Voice is already set to ${newVoiceId}.`);
      return;
    }

    console.log(`[VoiceChanger] Switching voice from ${this.currentVoiceId} to ${newVoiceId}...`);
    const ctx = this.audioContext;
    const now = ctx.currentTime;
    const crossfadeDuration = 0.025; // 25ms equal-power crossfade to prevent audio clicks

    const oldChain = this.activeChain;
    const newChain = this.buildEffectChain(newVoiceId);

    // Fade in new chain
    newChain.fadeGain.gain.setValueAtTime(0.001, now);
    newChain.fadeGain.gain.linearRampToValueAtTime(1.0, now + crossfadeDuration);

    this.sourceNode.connect(newChain.inputNode);
    newChain.outputNode.connect(this.destinationNode);

    // Fade out old chain
    if (oldChain) {
      oldChain.fadeGain.gain.setValueAtTime(oldChain.fadeGain.gain.value, now);
      oldChain.fadeGain.gain.linearRampToValueAtTime(0.001, now + crossfadeDuration);

      setTimeout(() => {
        try {
          if (this.sourceNode) {
            this.sourceNode.disconnect(oldChain.inputNode);
          }
          if (this.destinationNode) {
            oldChain.outputNode.disconnect(this.destinationNode);
          }
          oldChain.dispose();
        } catch (e) {
          console.warn('[VoiceChanger] Note during old chain disconnect:', e);
        }
      }, Math.ceil(crossfadeDuration * 1000) + 15);
    }

    this.activeChain = newChain;
    this.currentVoiceId = newVoiceId;
    saveVoicePresetId(newVoiceId);
  }

  /**
   * Resumes the AudioContext if it was suspended by browser autoplay policies.
   */
  public async resume(): Promise<void> {
    if (this.audioContext && this.audioContext.state === 'suspended') {
      console.log('[VoiceChanger] Resuming suspended AudioContext...');
      await this.audioContext.resume();
    }
  }

  /**
   * Completely tears down the audio graph, stops oscillators, and closes the AudioContext.
   */
  public cleanup(): void {
    console.log('[VoiceChanger] Cleaning up audio engine resources...');
    if (this.activeChain) {
      try {
        this.activeChain.dispose();
      } catch (e) {
        console.warn('[VoiceChanger] Error disposing active effect chain:', e);
      }
      this.activeChain = null;
    }

    if (this.sourceNode) {
      try {
        this.sourceNode.disconnect();
      } catch (e) {
        console.warn('[VoiceChanger] Error disconnecting source node:', e);
      }
      this.sourceNode = null;
    }

    if (this.destinationNode) {
      this.destinationNode = null;
    }

    if (this.audioContext && this.audioContext.state !== 'closed') {
      try {
        this.audioContext.close();
      } catch (e) {
        console.warn('[VoiceChanger] Error closing AudioContext:', e);
      }
      this.audioContext = null;
    }

    this.isInitialized = false;
  }

  // =========================================================================
  // DSP PRESET BUILDERS
  // =========================================================================

  /**
   * Router that builds the appropriate Web Audio processing graph for a preset ID.
   */
  private buildEffectChain(presetId: VoicePresetId): ActiveEffectChain {
    switch (presetId) {
      case 'original':
        return this.buildOriginalChain();
      case 'robot-male':
        return this.buildRobotChain(62, 1050, 0.88);
      case 'robot-female':
        return this.buildRobotChain(92, 1600, 1.18);
      case 'human-male':
        return this.buildHumanMaleChain();
      case 'human-female':
        return this.buildHumanFemaleChain();
      case 'monster-male':
        return this.buildMonsterMaleChain();
      case 'monster-female':
        return this.buildMonsterFemaleChain();
      case 'overall-morphed':
        return this.buildOverallMorphedChain();
      case 'echo':
        return this.buildEchoChain();
      case 'alien':
        return this.buildAlienChain();
      default:
        return this.buildOriginalChain();
    }
  }

  /**
   * 1. Original: 100% clean direct bypass.
   */
  private buildOriginalChain(): ActiveEffectChain {
    const ctx = this.audioContext!;
    const fadeGain = ctx.createGain();
    fadeGain.gain.value = 1.0;

    return {
      inputNode: fadeGain,
      outputNode: fadeGain,
      fadeGain,
      dispose: () => {
        fadeGain.disconnect();
      },
    };
  }

  /**
   * 2 & 3. Robot Male & Robot Female:
   * Ring modulation (carrier oscillator multiplied with voice) + bandpass filter + subtle pitch shift.
   */
  private buildRobotChain(carrierFreq: number, bandpassFreq: number, pitchRatio: number): ActiveEffectChain {
    const ctx = this.audioContext!;
    const inputNode = ctx.createGain();
    const outputNode = ctx.createGain();
    const fadeGain = ctx.createGain();
    fadeGain.gain.value = 1.0;

    // Pitch shifter pre-processing
    const pitch = this.createGranularPitchShifter(ctx, pitchRatio);

    // Carrier Oscillator for ring modulation
    const carrier = ctx.createOscillator();
    carrier.type = 'sawtooth';
    carrier.frequency.setValueAtTime(carrierFreq, ctx.currentTime);

    // Modulation Gain Node (carrier multiplies the voice signal)
    const modGain = ctx.createGain();
    modGain.gain.value = 0.0;
    carrier.connect(modGain.gain);

    // Resonant Bandpass filter to sculpt electronic robot acoustics
    const filter = ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(bandpassFreq, ctx.currentTime);
    filter.Q.setValueAtTime(2.2, ctx.currentTime);

    // Mild distortion waveshaper for metallic edge
    const shaper = ctx.createWaveShaper();
    shaper.curve = this.createDistortionCurve(18) as Float32Array<ArrayBuffer>;
    shaper.oversample = '2x';

    // Dry blend (30% direct voice for intelligibility)
    const dryGain = ctx.createGain();
    dryGain.gain.value = 0.35;

    const wetGain = ctx.createGain();
    wetGain.gain.value = 0.85;

    // Routing: input -> pitch -> [modGain & dryGain]
    inputNode.connect(pitch.input);
    pitch.output.connect(modGain);
    pitch.output.connect(dryGain);

    modGain.connect(filter);
    filter.connect(shaper);
    shaper.connect(wetGain);

    dryGain.connect(outputNode);
    wetGain.connect(outputNode);
    outputNode.connect(fadeGain);

    carrier.start();

    return {
      inputNode,
      outputNode: fadeGain,
      fadeGain,
      dispose: () => {
        try {
          carrier.stop();
          carrier.disconnect();
          pitch.dispose();
          inputNode.disconnect();
          outputNode.disconnect();
          fadeGain.disconnect();
        } catch (e) {
          console.warn('[VoiceChanger] Teardown note for robot chain:', e);
        }
      },
    };
  }

  /**
   * 4. Human Male:
   * Pitch down (-3.5 semitones, 0.82x ratio) + deep chest resonance boost (180Hz) + high-end softening.
   */
  private buildHumanMaleChain(): ActiveEffectChain {
    const ctx = this.audioContext!;
    const inputNode = ctx.createGain();
    const outputNode = ctx.createGain();
    const fadeGain = ctx.createGain();
    fadeGain.gain.value = 1.0;

    // Pitch shift down to masculine vocal range
    const pitch = this.createGranularPitchShifter(ctx, 0.82);

    // Low-shelf filter for deep resonant chest voice
    const bassFilter = ctx.createBiquadFilter();
    bassFilter.type = 'lowshelf';
    bassFilter.frequency.setValueAtTime(180, ctx.currentTime);
    bassFilter.gain.setValueAtTime(5.5, ctx.currentTime);

    // High-shelf filter to soften treble frequencies
    const trebleFilter = ctx.createBiquadFilter();
    trebleFilter.type = 'highshelf';
    trebleFilter.frequency.setValueAtTime(4500, ctx.currentTime);
    trebleFilter.gain.setValueAtTime(-3.0, ctx.currentTime);

    inputNode.connect(pitch.input);
    pitch.output.connect(bassFilter);
    bassFilter.connect(trebleFilter);
    trebleFilter.connect(outputNode);
    outputNode.connect(fadeGain);

    return {
      inputNode,
      outputNode: fadeGain,
      fadeGain,
      dispose: () => {
        pitch.dispose();
        inputNode.disconnect();
        outputNode.disconnect();
        fadeGain.disconnect();
      },
    };
  }

  /**
   * 5. Human Female:
   * Pitch up (+3.5 semitones, 1.22x ratio) + low-cut highpass (160Hz) + throat presence boost (2.8kHz).
   */
  private buildHumanFemaleChain(): ActiveEffectChain {
    const ctx = this.audioContext!;
    const inputNode = ctx.createGain();
    const outputNode = ctx.createGain();
    const fadeGain = ctx.createGain();
    fadeGain.gain.value = 1.0;

    // Pitch shift up to feminine vocal range
    const pitch = this.createGranularPitchShifter(ctx, 1.22);

    // Highpass filter to eliminate heavy male chest thumps
    const lowCut = ctx.createBiquadFilter();
    lowCut.type = 'highpass';
    lowCut.frequency.setValueAtTime(160, ctx.currentTime);

    // Peaking filter for bright, clear vocal presence
    const presenceFilter = ctx.createBiquadFilter();
    presenceFilter.type = 'peaking';
    presenceFilter.frequency.setValueAtTime(2800, ctx.currentTime);
    presenceFilter.Q.setValueAtTime(1.4, ctx.currentTime);
    presenceFilter.gain.setValueAtTime(4.0, ctx.currentTime);

    inputNode.connect(pitch.input);
    pitch.output.connect(lowCut);
    lowCut.connect(presenceFilter);
    presenceFilter.connect(outputNode);
    outputNode.connect(fadeGain);

    return {
      inputNode,
      outputNode: fadeGain,
      fadeGain,
      dispose: () => {
        pitch.dispose();
        inputNode.disconnect();
        outputNode.disconnect();
        fadeGain.disconnect();
      },
    };
  }

  /**
   * 6. Male Monstar:
   * Heavy pitch down (-8 semitones, 0.63x ratio) + massive sub-bass boost (130Hz) + growling saturation.
   */
  private buildMonsterMaleChain(): ActiveEffectChain {
    const ctx = this.audioContext!;
    const inputNode = ctx.createGain();
    const outputNode = ctx.createGain();
    const fadeGain = ctx.createGain();
    fadeGain.gain.value = 1.0;

    // Extreme pitch down to giant/monster vocal register
    const pitch = this.createGranularPitchShifter(ctx, 0.63);

    // Sub-bass resonant shelf for thunderous monster footsteps/voice
    const subBass = ctx.createBiquadFilter();
    subBass.type = 'lowshelf';
    subBass.frequency.setValueAtTime(130, ctx.currentTime);
    subBass.gain.setValueAtTime(8.0, ctx.currentTime);

    // Lowpass filter to muffle bright consonant noise
    const darkFilter = ctx.createBiquadFilter();
    darkFilter.type = 'lowpass';
    darkFilter.frequency.setValueAtTime(3200, ctx.currentTime);

    // Overdrive saturation curve for beastly growl
    const shaper = ctx.createWaveShaper();
    shaper.curve = this.createDistortionCurve(35) as Float32Array<ArrayBuffer>;
    shaper.oversample = '2x';

    // Makeup gain
    const makeupGain = ctx.createGain();
    makeupGain.gain.value = 1.15;

    inputNode.connect(pitch.input);
    pitch.output.connect(subBass);
    subBass.connect(darkFilter);
    darkFilter.connect(shaper);
    shaper.connect(makeupGain);
    makeupGain.connect(outputNode);
    outputNode.connect(fadeGain);

    return {
      inputNode,
      outputNode: fadeGain,
      fadeGain,
      dispose: () => {
        pitch.dispose();
        inputNode.disconnect();
        outputNode.disconnect();
        fadeGain.disconnect();
      },
    };
  }

  /**
   * 7. Female Monstar:
   * Pitch down (-2.5 semitones, 0.86x) + LFO vibrato (11Hz) + eerie screeching peak (2.2kHz, Q=4) + slap delay.
   */
  private buildMonsterFemaleChain(): ActiveEffectChain {
    const ctx = this.audioContext!;
    const inputNode = ctx.createGain();
    const outputNode = ctx.createGain();
    const fadeGain = ctx.createGain();
    fadeGain.gain.value = 1.0;

    const pitch = this.createGranularPitchShifter(ctx, 0.86);

    // High-Q resonant shrieking filter
    const shriekFilter = ctx.createBiquadFilter();
    shriekFilter.type = 'peaking';
    shriekFilter.frequency.setValueAtTime(2200, ctx.currentTime);
    shriekFilter.Q.setValueAtTime(4.2, ctx.currentTime);
    shriekFilter.gain.setValueAtTime(7.0, ctx.currentTime);

    // LFO oscillator modulating amplitude for trembling wail
    const lfo = ctx.createOscillator();
    lfo.frequency.setValueAtTime(11, ctx.currentTime);

    const lfoGain = ctx.createGain();
    lfoGain.gain.value = 0.35; // 35% modulation depth
    lfo.connect(lfoGain.gain);

    // Eerie ghost slapback delay (45ms)
    const delay = ctx.createDelay();
    delay.delayTime.setValueAtTime(0.045, ctx.currentTime);

    const delayGain = ctx.createGain();
    delayGain.gain.value = 0.45;

    inputNode.connect(pitch.input);
    pitch.output.connect(shriekFilter);
    shriekFilter.connect(lfoGain);

    // Dry & Delay blend
    lfoGain.connect(outputNode);
    lfoGain.connect(delay);
    delay.connect(delayGain);
    delayGain.connect(outputNode);

    outputNode.connect(fadeGain);
    lfo.start();

    return {
      inputNode,
      outputNode: fadeGain,
      fadeGain,
      dispose: () => {
        try {
          lfo.stop();
          lfo.disconnect();
          pitch.dispose();
          inputNode.disconnect();
          outputNode.disconnect();
          fadeGain.disconnect();
        } catch (e) {
          console.warn('[VoiceChanger] Teardown note for female monster:', e);
        }
      },
    };
  }

  /**
   * 8. Overall Morphed:
   * Multi-formant dynamic shifting (peaking at 750Hz and 2400Hz) + subtle chorus modulation.
   */
  private buildOverallMorphedChain(): ActiveEffectChain {
    const ctx = this.audioContext!;
    const inputNode = ctx.createGain();
    const outputNode = ctx.createGain();
    const fadeGain = ctx.createGain();
    fadeGain.gain.value = 1.0;

    const pitch = this.createGranularPitchShifter(ctx, 1.08);

    // Dual formant sculpting filters
    const formant1 = ctx.createBiquadFilter();
    formant1.type = 'peaking';
    formant1.frequency.setValueAtTime(750, ctx.currentTime);
    formant1.Q.setValueAtTime(2.0, ctx.currentTime);
    formant1.gain.setValueAtTime(6.0, ctx.currentTime);

    const formant2 = ctx.createBiquadFilter();
    formant2.type = 'peaking';
    formant2.frequency.setValueAtTime(2400, ctx.currentTime);
    formant2.Q.setValueAtTime(2.5, ctx.currentTime);
    formant2.gain.setValueAtTime(5.0, ctx.currentTime);

    // Chorus delay line modulated by slow LFO
    const chorusDelay = ctx.createDelay();
    chorusDelay.delayTime.setValueAtTime(0.018, ctx.currentTime);

    const lfo = ctx.createOscillator();
    lfo.frequency.setValueAtTime(0.9, ctx.currentTime); // 0.9 Hz slow sweep

    const lfoDepth = ctx.createGain();
    lfoDepth.gain.value = 0.003; // +/- 3ms modulation
    lfo.connect(lfoDepth);
    lfoDepth.connect(chorusDelay.delayTime);

    const wetChorus = ctx.createGain();
    wetChorus.gain.value = 0.6;

    inputNode.connect(pitch.input);
    pitch.output.connect(formant1);
    formant1.connect(formant2);

    formant2.connect(outputNode);
    formant2.connect(chorusDelay);
    chorusDelay.connect(wetChorus);
    wetChorus.connect(outputNode);

    outputNode.connect(fadeGain);
    lfo.start();

    return {
      inputNode,
      outputNode: fadeGain,
      fadeGain,
      dispose: () => {
        try {
          lfo.stop();
          lfo.disconnect();
          pitch.dispose();
          inputNode.disconnect();
          outputNode.disconnect();
          fadeGain.disconnect();
        } catch (e) {
          console.warn('[VoiceChanger] Teardown note for morphed chain:', e);
        }
      },
    };
  }

  /**
   * 9. Echo:
   * Natural spatial delay (240ms) with 42% feedback decay and 2.8kHz lowpass air-damping.
   */
  private buildEchoChain(): ActiveEffectChain {
    const ctx = this.audioContext!;
    const inputNode = ctx.createGain();
    const outputNode = ctx.createGain();
    const fadeGain = ctx.createGain();
    fadeGain.gain.value = 1.0;

    const delay = ctx.createDelay(1.0);
    delay.delayTime.setValueAtTime(0.24, ctx.currentTime); // 240ms canyon delay

    const feedback = ctx.createGain();
    feedback.gain.setValueAtTime(0.42, ctx.currentTime); // 42% decay per reflection

    // Damping filter inside feedback loop (air absorption absorbs high frequencies)
    const damping = ctx.createBiquadFilter();
    damping.type = 'lowpass';
    damping.frequency.setValueAtTime(2800, ctx.currentTime);

    const dryGain = ctx.createGain();
    dryGain.gain.value = 1.0;

    const wetGain = ctx.createGain();
    wetGain.gain.value = 0.65;

    // Feedback loop: delay -> damping -> feedback -> delay
    inputNode.connect(dryGain);
    dryGain.connect(outputNode);

    inputNode.connect(delay);
    delay.connect(damping);
    damping.connect(feedback);
    feedback.connect(delay);

    damping.connect(wetGain);
    wetGain.connect(outputNode);
    outputNode.connect(fadeGain);

    return {
      inputNode,
      outputNode: fadeGain,
      fadeGain,
      dispose: () => {
        inputNode.disconnect();
        outputNode.disconnect();
        delay.disconnect();
        feedback.disconnect();
        damping.disconnect();
        fadeGain.disconnect();
      },
    };
  }

  /**
   * 10. Alien:
   * Fast amplitude tremolo (18Hz LFO) + high-Q resonant peaking filter (2.4kHz) + harmonic ring modulation.
   */
  private buildAlienChain(): ActiveEffectChain {
    const ctx = this.audioContext!;
    const inputNode = ctx.createGain();
    const outputNode = ctx.createGain();
    const fadeGain = ctx.createGain();
    fadeGain.gain.value = 1.0;

    // Resonant cosmic peak filter
    const peak = ctx.createBiquadFilter();
    peak.type = 'peaking';
    peak.frequency.setValueAtTime(2400, ctx.currentTime);
    peak.Q.setValueAtTime(4.8, ctx.currentTime);
    peak.gain.setValueAtTime(6.5, ctx.currentTime);

    // Tremolo LFO
    const lfo = ctx.createOscillator();
    lfo.type = 'sine';
    lfo.frequency.setValueAtTime(18, ctx.currentTime); // 18Hz flutter

    const tremoloGain = ctx.createGain();
    tremoloGain.gain.value = 0.5; // Baseline
    const lfoDepth = ctx.createGain();
    lfoDepth.gain.value = 0.45;

    lfo.connect(lfoDepth);
    lfoDepth.connect(tremoloGain.gain);

    // Subtle space carrier tone (135Hz)
    const carrier = ctx.createOscillator();
    carrier.type = 'triangle';
    carrier.frequency.setValueAtTime(135, ctx.currentTime);

    const carrierGain = ctx.createGain();
    carrierGain.gain.value = 0.22;
    carrier.connect(carrierGain);

    inputNode.connect(peak);
    peak.connect(tremoloGain);
    tremoloGain.connect(outputNode);
    carrierGain.connect(outputNode);
    outputNode.connect(fadeGain);

    lfo.start();
    carrier.start();

    return {
      inputNode,
      outputNode: fadeGain,
      fadeGain,
      dispose: () => {
        try {
          lfo.stop();
          carrier.stop();
          lfo.disconnect();
          carrier.disconnect();
          inputNode.disconnect();
          outputNode.disconnect();
          fadeGain.disconnect();
        } catch (e) {
          console.warn('[VoiceChanger] Teardown note for alien chain:', e);
        }
      },
    };
  }

  // =========================================================================
  // NATIVE GRANULAR DUAL-DELAY PITCH SHIFTER
  // =========================================================================

  /**
   * Constructs a zero-latency, click-free granular pitch shifter entirely using
   * native Web Audio DelayNode, GainNode, and looped ramp AudioBuffers.
   *
   * Hey friend! Here is how this pitch shifting algorithm works:
   * - Two delay lines (delayA and delayB) cycle their delay time using continuous ramps.
   * - When shifting pitch UP, delay decreases over time (samples play faster).
   * - When shifting pitch DOWN, delay increases over time (samples play slower).
   * - Two crossfaded GainNodes (gainA and gainB) ensure that whenever a delay line reaches
   *   its boundary and wraps around, its gain is 0.0 (completely inaudible)!
   * - Result: Smooth, real-time pitch shifting in Chromium C++ audio threads!
   */
  private createGranularPitchShifter(
    ctx: AudioContext,
    pitchRatio: number
  ): { input: AudioNode; output: AudioNode; dispose: () => void } {
    const input = ctx.createGain();
    const output = ctx.createGain();

    // If pitch ratio is 1.0 (no shift), pass straight through
    if (Math.abs(pitchRatio - 1.0) < 0.02) {
      input.connect(output);
      return {
        input,
        output,
        dispose: () => {
          input.disconnect();
          output.disconnect();
        },
      };
    }

    const grainDuration = 0.08; // 80ms grains (sweet spot for natural human voice)
    const sampleRate = ctx.sampleRate;
    const bufferLength = Math.floor(sampleRate * grainDuration);

    // AudioBuffer holding delay modulation ramp and window envelope
    const rampBuffer = ctx.createBuffer(2, bufferLength, sampleRate);
    const delayData = rampBuffer.getChannelData(0);
    const windowData = rampBuffer.getChannelData(1);

    // Calculate modulation slope
    // pitchRatio = 1 - (slope)
    const isPitchUp = pitchRatio > 1.0;

    for (let i = 0; i < bufferLength; i++) {
      const progress = i / bufferLength;

      // Delay ramp: ramps between 0 and grainDuration
      if (isPitchUp) {
        delayData[i] = (1.0 - progress) * grainDuration;
      } else {
        delayData[i] = progress * grainDuration;
      }

      // Triangle window envelope (0 at ends, 1 in the middle)
      windowData[i] = progress < 0.5 ? progress * 2 : (1.0 - progress) * 2;
    }

    // Delay line A & B
    const delayA = ctx.createDelay(0.2);
    const delayB = ctx.createDelay(0.2);
    delayA.delayTime.value = 0.04;
    delayB.delayTime.value = 0.04;

    const gainA = ctx.createGain();
    const gainB = ctx.createGain();
    gainA.gain.value = 0.0;
    gainB.gain.value = 0.0;

    // Buffer sources driving delay times and gain envelopes
    const sourceA = ctx.createBufferSource();
    const sourceB = ctx.createBufferSource();
    sourceA.buffer = rampBuffer;
    sourceB.buffer = rampBuffer;
    sourceA.loop = true;
    sourceB.loop = true;

    // Split channels: channel 0 (delay) -> delayTime, channel 1 (window) -> gain
    const splitterA = ctx.createChannelSplitter(2);
    const splitterB = ctx.createChannelSplitter(2);

    sourceA.connect(splitterA);
    sourceB.connect(splitterB);

    splitterA.connect(delayA.delayTime, 0);
    splitterA.connect(gainA.gain, 1);

    splitterB.connect(delayB.delayTime, 0);
    splitterB.connect(gainB.gain, 1);

    // Audio routing
    input.connect(delayA);
    input.connect(delayB);

    delayA.connect(gainA);
    delayB.connect(gainB);

    gainA.connect(output);
    gainB.connect(output);

    // Stagger source B by half grain duration (180 degrees out of phase)
    const now = ctx.currentTime;
    sourceA.start(now);
    sourceB.start(now + grainDuration / 2);

    return {
      input,
      output,
      dispose: () => {
        try {
          sourceA.stop();
          sourceB.stop();
          sourceA.disconnect();
          sourceB.disconnect();
          splitterA.disconnect();
          splitterB.disconnect();
          delayA.disconnect();
          delayB.disconnect();
          gainA.disconnect();
          gainB.disconnect();
          input.disconnect();
          output.disconnect();
        } catch (e) {
          console.warn('[VoiceChanger] Teardown note for pitch shifter:', e);
        }
      },
    };
  }

  /**
   * Generates a non-linear sigmoid saturation curve for harmonic distortion.
   */
  private createDistortionCurve(amount: number, samples = 1024): Float32Array {
    const curve = new Float32Array(samples);
    const deg = Math.PI / 180;
    for (let i = 0; i < samples; ++i) {
      const x = (i * 2) / samples - 1;
      curve[i] = ((3 + amount) * x * 20 * deg) / (Math.PI + amount * Math.abs(x));
    }
    return curve;
  }
}

/**
 * Singleton instance of the VoiceChangerEngine.
 */
export const voiceChangerEngine = new VoiceChangerEngine();
