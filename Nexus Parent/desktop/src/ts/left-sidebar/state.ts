/**
 * @file state.ts
 * @description State management for Nexus Parent Left Sidebar.
 *
 * Hey friend! This manages which section is collapsed, which monitoring feature
 * is actively selected, which features are currently live/streaming, and any
 * text filter typed into the search bar!
 */

type SidebarChangeListener = () => void;

class SidebarStateManager {
  private activeFeatureId: string | null = null;
  private isMicOpen: boolean = false;
  private searchQuery = '';
  private collapsedSections: Set<string> = new Set();
  private liveFeatures: Set<string> = new Set();
  private listeners: Set<SidebarChangeListener> = new Set();

  public getActiveFeatureId(): string | null {
    return this.activeFeatureId;
  }

  public setActiveFeatureId(id: string | null): void {
    if (this.activeFeatureId !== id) {
      this.activeFeatureId = id;
      this.notify();
    }
  }

  public getIsMicOpen(): boolean {
    return this.isMicOpen;
  }

  public setMicOpen(open: boolean): void {
    if (this.isMicOpen !== open) {
      this.isMicOpen = open;
      this.notify();
    }
  }

  public toggleMicOpen(): void {
    this.setMicOpen(!this.isMicOpen);
  }

  public isFeatureLive(featureId: string): boolean {
    return this.liveFeatures.has(featureId);
  }

  public setFeatureLive(featureId: string, isLive: boolean): void {
    const wasLive = this.liveFeatures.has(featureId);
    if (isLive) {
      // Hardware fail-safe: Camera and Screen surveillance share the underlying WebRTC video transceiver.
      // They are mutually exclusive feeds. If one becomes live, ensure the other is cleared.
      if (featureId === 'camera') {
        this.liveFeatures.delete('screen');
      } else if (featureId === 'screen') {
        this.liveFeatures.delete('camera');
      }
      if (!wasLive) {
        this.liveFeatures.add(featureId);
        this.notify();
      }
    } else if (wasLive) {
      this.liveFeatures.delete(featureId);
      this.notify();
    }
  }

  public toggleFeatureLive(featureId: string): void {
    this.setFeatureLive(featureId, !this.isFeatureLive(featureId));
  }

  public getSearchQuery(): string {
    return this.searchQuery;
  }

  public setSearchQuery(query: string): void {
    const trimmed = query.trim().toLowerCase();
    if (this.searchQuery !== trimmed) {
      this.searchQuery = trimmed;
      this.notify();
    }
  }

  public isSectionCollapsed(sectionId: string): boolean {
    return this.collapsedSections.has(sectionId);
  }

  public toggleSectionCollapsed(sectionId: string): void {
    if (this.collapsedSections.has(sectionId)) {
      this.collapsedSections.delete(sectionId);
    } else {
      this.collapsedSections.add(sectionId);
    }
    this.notify();
  }

  public subscribe(listener: SidebarChangeListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify(): void {
    for (const listener of this.listeners) {
      listener();
    }
  }
}

export const sidebarState = new SidebarStateManager();
