import { createTheme } from '@mui/material/styles';

export const appTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      dark: '#183f34',
      light: '#5e8b7b',
      main: '#285f4f',
      contrastText: '#ffffff',
    },
    secondary: {
      main: '#8a6048',
    },
    background: {
      default: '#f5f7f5',
      paper: '#ffffff',
    },
    divider: '#dce3df',
    text: {
      primary: '#17211d',
      secondary: '#5e6d66',
    },
  },
  shape: {
    borderRadius: 10,
  },
  typography: {
    fontFamily: '"Yu Gothic UI", "Yu Gothic", "Meiryo UI", Meiryo, system-ui, sans-serif',
    button: {
      fontWeight: 600,
      letterSpacing: 0,
      textTransform: 'none',
    },
    h1: {
      fontSize: '1.3rem',
      fontWeight: 700,
      letterSpacing: '0.01em',
    },
    h2: {
      fontSize: '1.05rem',
      fontWeight: 700,
    },
  },
  components: {
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
        },
      },
    },
    MuiAlert: {
      styleOverrides: {
        root: {
          alignItems: 'center',
        },
      },
    },
    MuiButton: {
      defaultProps: {
        disableElevation: true,
      },
      styleOverrides: {
        root: {
          borderRadius: 8,
          minHeight: 36,
          paddingInline: 14,
        },
      },
    },
    MuiButtonBase: {
      defaultProps: {
        disableRipple: true,
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderColor: '#dce3df',
          boxShadow: '0 8px 24px rgb(31 55 46 / 7%)',
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          backgroundColor: '#edf2ef',
          borderRadius: 8,
          fontWeight: 600,
        },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          border: '1px solid #dce3df',
          boxShadow: '0 24px 64px rgb(20 42 34 / 20%)',
        },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          marginBlock: 2,
          '&.Mui-selected': {
            backgroundColor: '#e2eee9',
          },
          '&.Mui-selected:hover': {
            backgroundColor: '#d8e8e1',
          },
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: '#ffffff',
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: '#78988c',
          },
        },
        notchedOutline: {
          borderColor: '#cbd5d0',
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
        },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          fontWeight: 600,
          minHeight: 44,
        },
      },
    },
  },
});
