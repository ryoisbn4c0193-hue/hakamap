import { create } from 'zustand';

type UiState = {
  leftPanelCollapsed: boolean;
  rightPanelCollapsed: boolean;
  selectedGraveId?: string;
  propertyTab: 'basic' | 'people' | 'assets' | 'history';
  selectGrave: (graveId?: string) => void;
  setPropertyTab: (tab: UiState['propertyTab']) => void;
  resetEditor: () => void;
  toggleLeftPanel: () => void;
  toggleRightPanel: () => void;
};

export const useUiStore = create<UiState>((set) => ({
  leftPanelCollapsed: false,
  rightPanelCollapsed: false,
  propertyTab: 'basic',
  selectGrave: (selectedGraveId) => set({ selectedGraveId }),
  setPropertyTab: (propertyTab) => set({ propertyTab }),
  resetEditor: () =>
    set({
      leftPanelCollapsed: false,
      propertyTab: 'basic',
      rightPanelCollapsed: false,
      selectedGraveId: undefined,
    }),
  toggleLeftPanel: () => {
    set((state) => ({ leftPanelCollapsed: !state.leftPanelCollapsed }));
  },
  toggleRightPanel: () => {
    set((state) => ({ rightPanelCollapsed: !state.rightPanelCollapsed }));
  },
}));
