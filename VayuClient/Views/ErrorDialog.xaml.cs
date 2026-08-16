using System;
using System.IO;
using System.Windows;

namespace VayuClient.Views
{
    public partial class ErrorDialog : Window
    {
        private readonly string _fullDetails;

        public ErrorDialog(string summary, string details, string logFilePath)
        {
            InitializeComponent();
            _fullDetails = details;

            TxtSummary.Text = summary;
            TxtLogLocation.Text = $"Crash log saved to: {logFilePath}";
            TxtDetails.Text = details;
        }

        private void CopyDetails_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                Clipboard.SetText(_fullDetails);
                TxtCopyStatus.Text = "✓ Details copied to clipboard!";
            }
            catch
            {
                TxtCopyStatus.Text = "Failed to copy to clipboard.";
            }
        }

        private void Close_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        public static void ShowDialogSafe(string summary, string details, string logFilePath)
        {
            try
            {
                var app = Application.Current;
                if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess())
                {
                    app.Dispatcher.Invoke(() => ShowDialogSafe(summary, details, logFilePath));
                    return;
                }

                var dlg = new ErrorDialog(summary, details, logFilePath);
                if (app?.MainWindow != null && app.MainWindow.IsVisible)
                {
                    dlg.Owner = app.MainWindow;
                }
                dlg.ShowDialog();
            }
            catch
            {
                // Fallback to basic MessageBox if WPF window rendering fails
                try
                {
                    MessageBox.Show(
                        $"{summary}\n\nLog path: {logFilePath}\n\n{details}",
                        "VayuClient Error Notice",
                        MessageBoxButton.OK,
                        MessageBoxImage.Warning);
                }
                catch { }
            }
        }
    }
}
