/**
 * @file types.ts
 * @description Data interfaces and types for Nexus Parent Left Sidebar.
 *
 * Hey friend! These types define the structure for sidebar monitoring categories,
 * navigation items, and state synchronization.
 */

export interface SidebarFeatureItem {
  id: string;
  label: string;
  iconClass: string;
  badge?: string | number;
}

export interface SidebarSection {
  id: string;
  title: string;
  collapsed: boolean;
  items: SidebarFeatureItem[];
}
