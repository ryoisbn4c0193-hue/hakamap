import { create } from 'zustand';

type UiState = {
  leftPanelCollapsed: boolean;
  rightPanelCollapsed: boolean;
  selectedGraveId?: string;
  selectedMapIds: readonly string[];
  propertyTab: 'basic' | 'people' | 'assets' | 'history';
  selectGrave: (graveId?: string) => void;
  selectMapIds: (graveIds: readonly string[]) => void;
  setPropertyTab: (tab: UiState['propertyTab']) => void;
  resetEditor: () => void;
  toggleLeftPanel: () => void;
  toggleRightPanel: () => void;
};

export const useUiStore = create<UiState>((set) => ({
  leftPanelCollapsed: false,
  rightPanelCollapsed: false,
  propertyTab: 'basic',
  selectedMapIds: [],
  selectGrave: (selectedGraveId) =>
    set({
      selectedGraveId,
      selectedMapIds: selectedGraveId === undefined ? [] : [selectedGraveId],
    }),
  selectMapIds: (selectedMapIds) =>
    set({
      selectedGraveId: selectedMapIds.length === 1 ? selectedMapIds[0] : undefined,
      selectedMapIds,
    }),
  setPropertyTab: (propertyTab) => set({ propertyTab }),
  resetEditor: () =>
    set({
      leftPanelCollapsed: false,
      propertyTab: 'basic',
      rightPanelCollapsed: false,
      selectedGraveId: undefined,
      selectedMapIds: [],
    }),
  toggleLeftPanel: () => {
    set((state) => ({ leftPanelCollapsed: !state.leftPanelCollapsed }));
  },
  toggleRightPanel: () => {
    set((state) => ({ rightPanelCollapsed: !state.rightPanelCollapsed }));
  },
}));
