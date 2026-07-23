import { createTheme } from '@mui/material/styles';

export const appTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#315c4d',
    },
    secondary: {
      main: '#8a5a44',
    },
    background: {
      default: '#f4f3ef',
      paper: '#ffffff',
    },
  },
  shape: {
    borderRadius: 6,
  },
  typography: {
    fontFamily: '"Yu Gothic UI", "Yu Gothic", "Meiryo UI", Meiryo, system-ui, sans-serif',
    h1: {
      fontSize: '1.25rem',
      fontWeight: 700,
    },
    h2: {
      fontSize: '1rem',
      fontWeight: 700,
    },
  },
  components: {
    MuiButtonBase: {
      defaultProps: {
        disableRipple: true,
      },
    },
  },
});
