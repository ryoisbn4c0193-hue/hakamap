import { createTheme } from '@mui/material/styles';

export const appTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      dark: '#147fb5',
      light: '#dff4fc',
      main: '#249bd3',
      contrastText: '#ffffff',
    },
    secondary: {
      main: '#147fb5',
    },
    background: {
      default: '#dff4fc',
      paper: '#ffffff',
    },
    divider: '#b9deef',
    text: {
      primary: '#162832',
      secondary: '#5b6f7a',
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
          borderColor: '#b9deef',
          boxShadow: '0 8px 24px rgb(20 127 181 / 9%)',
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          backgroundColor: '#e7f6fc',
          borderRadius: 8,
          fontWeight: 600,
        },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          border: '1px solid #b9deef',
          boxShadow: '0 24px 64px rgb(20 127 181 / 20%)',
        },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          marginBlock: 2,
          '&.Mui-selected': {
            backgroundColor: '#dff4fc',
          },
          '&.Mui-selected:hover': {
            backgroundColor: '#ccecf9',
          },
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: '#ffffff',
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: '#249bd3',
          },
        },
        notchedOutline: {
          borderColor: '#aed5e8',
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
