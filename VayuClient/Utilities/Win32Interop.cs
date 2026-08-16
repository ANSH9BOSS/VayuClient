using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace VayuClient.Utilities
{
    /// <summary>
    /// Win32 interop for DWM backdrop, window composition, and custom resize.
    /// </summary>
    public static class Win32Interop
    {
        [DllImport("dwmapi.dll")]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

        [DllImport("dwmapi.dll")]
        private static extern int DwmExtendFrameIntoClientArea(IntPtr hwnd, ref MARGINS margins);

        [StructLayout(LayoutKind.Sequential)]
        private struct MARGINS
        {
            public int Left, Right, Top, Bottom;
        }

        private const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
        private const int DWMWA_SYSTEMBACKDROP_TYPE = 38;
        private const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;

        // Backdrop types
        private const int DWMSBT_MAINWINDOW = 2;   // Mica
        private const int DWMSBT_TRANSIENTWINDOW = 3; // Acrylic
        private const int DWMSBT_TABBEDWINDOW = 4;  // Tabbed mica

        // Corner types
        private const int DWMWCP_ROUND = 2;

        /// <summary>
        /// Apply Win11 dark mode, rounded corners, and Mica backdrop to a WPF window.
        /// </summary>
        public static void ApplyWindowEffects(Window window)
        {
            try
            {
                var hwnd = new WindowInteropHelper(window).Handle;
                if (hwnd == IntPtr.Zero) return;

                // Enable dark mode for title bar (even though hidden, affects system menus)
                int darkMode = 1;
                DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ref darkMode, sizeof(int));

                // Round corners
                int cornerPref = DWMWCP_ROUND;
                DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ref cornerPref, sizeof(int));

                // Apply Mica backdrop
                int backdropType = DWMSBT_MAINWINDOW;
                DwmSetWindowAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, ref backdropType, sizeof(int));

                // Extend frame for glass area
                var margins = new MARGINS { Left = -1, Right = -1, Top = -1, Bottom = -1 };
                DwmExtendFrameIntoClientArea(hwnd, ref margins);
            }
            catch
            {
                // Silently fail on unsupported OS versions
            }
        }

        // ─── Custom resize support ───

        [DllImport("user32.dll")]
        private static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

        private const uint WM_SYSCOMMAND = 0x0112;

        // Resize directions
        private const int SC_SIZE_HTLEFT = 0xF001;
        private const int SC_SIZE_HTRIGHT = 0xF002;
        private const int SC_SIZE_HTTOP = 0xF003;
        private const int SC_SIZE_HTTOPLEFT = 0xF004;
        private const int SC_SIZE_HTTOPRIGHT = 0xF005;
        private const int SC_SIZE_HTBOTTOM = 0xF006;
        private const int SC_SIZE_HTBOTTOMLEFT = 0xF007;
        private const int SC_SIZE_HTBOTTOMRIGHT = 0xF008;

        public enum ResizeDirection
        {
            Left = SC_SIZE_HTLEFT,
            Right = SC_SIZE_HTRIGHT,
            Top = SC_SIZE_HTTOP,
            TopLeft = SC_SIZE_HTTOPLEFT,
            TopRight = SC_SIZE_HTTOPRIGHT,
            Bottom = SC_SIZE_HTBOTTOM,
            BottomLeft = SC_SIZE_HTBOTTOMLEFT,
            BottomRight = SC_SIZE_HTBOTTOMRIGHT
        }

        public static void ResizeWindow(Window window, ResizeDirection direction)
        {
            var hwnd = new WindowInteropHelper(window).Handle;
            SendMessage(hwnd, WM_SYSCOMMAND, (IntPtr)direction, IntPtr.Zero);
        }
    }
}
