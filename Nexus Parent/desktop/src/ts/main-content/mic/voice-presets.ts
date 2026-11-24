/**
 * @file voice-presets.ts
 * @description Preset definitions, metadata, and persistence helpers for the
 * Parent-to-Child Voice Intercom real-time voice changer.
 *
 * Hey friend! Here is how voice presets work in Nexus:
 * 1. Each preset defines a unique ID, a user-friendly name, a descriptive subtitle,
 *    an SVG icon path, and specific Digital Signal Processing (DSP) parameters.
 * 2. The 10 available presets include natural clean audio ("Original"), robotic synthesis
 *    ("Robot Male" and "Robot Female"), pitch-shifted human personas ("Human Male" and
 *    "Human Female"), scary deep/shrieking creatures ("Male Monstar" and "Female Monstar"),
 *    space/sci-fi effects ("Overall Morphed" and "Alien"), and spatial delays ("Echo").
 * 3. The parent's chosen voice is persisted across desktop app launches in localStorage
 *    so their favorite voice is ready as soon as the intercom opens.
 */

/**
 * Union of all valid voice preset identifiers.
 */
export type VoicePresetId =
  | 'original'
  | 'robot-male'
  | 'robot-female'
  | 'human-male'
  | 'human-female'
  | 'monster-male'
  | 'monster-female'
  | 'overall-morphed'
  | 'echo'
  | 'alien';

/**
 * Data contract representing a single voice transformation preset.
 */
export interface VoicePreset {
  /** Unique programmatic identifier */
  id: VoicePresetId;
  /** Primary label shown in the UI dropdown and button badges */
  name: string;
  /** Explanatory subtitle describing the acoustic timbre */
  description: string;
  /** Path to the professional vector SVG icon representing this voice */
  iconPath: string;
}

/**
 * LocalStorage key for remembering the user's selected voice preset across sessions.
 */
export const VOICE_STORAGE_KEY = 'nexus_parent_intercom_voice';

/**
 * Fallback voice preset used on fresh installs or when stored values are invalid.
 */
export const DEFAULT_VOICE_PRESET_ID: VoicePresetId = 'original';

/**
 * Complete registry of all 10 voice changer presets available for Parent-to-Child intercom.
 */
export const VOICE_PRESETS: VoicePreset[] = [
  {
    id: 'original',
    name: 'Original',
    description: 'Natural uncompressed voice',
    iconPath: 'assets/icons/voice-presets/original.svg',
  },
  {
    id: 'robot-male',
    name: 'Robot Male',
    description: 'Low-frequency cybernetic ring modulation',
    iconPath: 'assets/icons/voice-presets/robot-male.svg',
  },
  {
    id: 'robot-female',
    name: 'Robot Female',
    description: 'High-frequency digital vocoder synthesis',
    iconPath: 'assets/icons/voice-presets/robot-female.svg',
  },
  {
    id: 'human-male',
    name: 'Human Male',
    description: 'Masculine pitch shift & chest resonance',
    iconPath: 'assets/icons/voice-presets/human-male.svg',
  },
  {
    id: 'human-female',
    name: 'Human Female',
    description: 'Feminine pitch shift & bright presence',
    iconPath: 'assets/icons/voice-presets/human-female.svg',
  },
  {
    id: 'monster-male',
    name: 'Male Monstar',
    description: 'Deep demonic sub-bass pitch',
    iconPath: 'assets/icons/voice-presets/monster-male.svg',
  },
  {
    id: 'monster-female',
    name: 'Female Monstar',
    description: 'Eerie shrieking banshee resonance',
    iconPath: 'assets/icons/voice-presets/monster-female.svg',
  },
  {
    id: 'overall-morphed',
    name: 'Overall Morphed',
    description: 'Multi-formant modulated morphing',
    iconPath: 'assets/icons/voice-presets/overall-morphed.svg',
  },
  {
    id: 'echo',
    name: 'Echo',
    description: 'Spatial multi-tap feedback delay',
    iconPath: 'assets/icons/voice-presets/echo.svg',
  },
  {
    id: 'alien',
    name: 'Alien',
    description: 'Cosmic amplitude tremolo & peak filter',
    iconPath: 'assets/icons/voice-presets/alien.svg',
  },
];

/**
 * Checks whether an arbitrary string matches a valid VoicePresetId.
 *
 * @param id The string identifier to validate.
 * @returns true if id is one of the registered voice presets.
 */
export function isValidVoicePresetId(id: string): id is VoicePresetId {
  return VOICE_PRESETS.some((preset) => preset.id === id);
}

/**
 * Retrieves the full VoicePreset object by its ID, defaulting to 'original' if not found.
 *
 * @param id The preset identifier to locate.
 * @returns The matching VoicePreset object.
 */
export function getVoicePreset(id: VoicePresetId): VoicePreset {
  const match = VOICE_PRESETS.find((preset) => preset.id === id);
  if (match) {
    return match;
  }
  // Safe fallback to 'original'
  return VOICE_PRESETS[0];
}

/**
 * Retrieves the persisted voice preset ID from localStorage, falling back to 'original'.
 * Safe for execution in headless or testing environments without window.localStorage.
 *
 * @returns The saved VoicePresetId or DEFAULT_VOICE_PRESET_ID.
 */
export function getSavedVoicePresetId(): VoicePresetId {
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      const stored = window.localStorage.getItem(VOICE_STORAGE_KEY);
      if (stored && isValidVoicePresetId(stored)) {
        return stored;
      }
    }
  } catch (error) {
    console.warn('[VoicePresets] Could not read from localStorage:', error);
  }
  return DEFAULT_VOICE_PRESET_ID;
}

/**
 * Persists the chosen voice preset ID into localStorage.
 * Safe for execution in headless or testing environments.
 *
 * @param id The VoicePresetId to save.
 */
export function saveVoicePresetId(id: VoicePresetId): void {
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      window.localStorage.setItem(VOICE_STORAGE_KEY, id);
    }
  } catch (error) {
    console.warn('[VoicePresets] Could not save to localStorage:', error);
  }
}
