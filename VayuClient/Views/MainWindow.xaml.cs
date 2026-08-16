using System;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using VayuClient.Utilities;
using VayuClient.ViewModels;

namespace VayuClient.Views
{
    public partial class MainWindow : Window
    {
        private readonly MainViewModel _vm;
        public MainViewModel ViewModel => _vm;

        public MainWindow(MainViewModel? vm = null)
        {
            InitializeComponent();
            _vm = vm ?? new MainViewModel();
            DataContext = _vm;

            // Apply Win11 DWM effects after handle is available
            SourceInitialized += (s, e) => Win32Interop.ApplyWindowEffects(this);

            Loaded += (s, e) =>
            {
                Core.StartupProfiler.Record("MainWindow visible");
                // Background asynchronous manifest warm-up without UI blocking
                _ = Task.Run(async () =>
                {
                    try
                    {
                        var versionService = Core.ServiceLocator.Resolve<Services.Version.IVersionService>();
                        await versionService.GetManifestVersionsAsync(forceRefresh: false);
                        Core.StartupProfiler.Record("Version cache loaded");
                    }
                    catch { }
                });
            };

            ContentRendered += (s, e) =>
            {
                Core.StartupProfiler.Record("UI interactive");
                Core.StartupProfiler.Record("Startup complete");
                Core.StartupProfiler.Flush();
            };

            if (SplashOverlay != null)
            {
                SplashOverlay.SplashCompleted += Splash_Completed;
            }

            // Listen for page changes
            _vm.PropertyChanged += (s, e) =>
            {
                if (e.PropertyName == nameof(MainViewModel.CurrentPageViewModel))
                {
                    PlayPageTransition();
                }
            };
        }

        // ─── Title Bar ───

        private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ClickCount == 2)
            {
                ToggleMaximize();
            }
            else
            {
                try { DragMove(); } catch { }
            }
        }

        private void Minimize_Click(object sender, RoutedEventArgs e)
        {
            WindowState = WindowState.Minimized;
        }

        private void Maximize_Click(object sender, RoutedEventArgs e)
        {
            ToggleMaximize();
        }

        private void Close_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private void ToggleMaximize()
        {
            if (WindowState == WindowState.Maximized)
            {
                WindowState = WindowState.Normal;
                MaximizeIcon.Text = "□";
            }
            else
            {
                WindowState = WindowState.Maximized;
                MaximizeIcon.Text = "❐";
            }
        }

        // ─── Test Harness Helpers ───
        public void Minimize_Click_Test() => Minimize_Click(this, new RoutedEventArgs());
        public void Maximize_Click_Test() => Maximize_Click(this, new RoutedEventArgs());
        public void Nav_Click_Test(string page) => _vm.NavigateToCommand.Execute(page);

        // ─── Page Transition ───

        private void PlayPageTransition()
        {
            if (!IsLoaded || PageHost == null)
            {
                if (PageHost != null) PageHost.Opacity = 1.0;
                return;
            }

            try
            {
                var ease = new QuarticEase { EasingMode = EasingMode.EaseOut };

                var opacityAnim = new DoubleAnimation(0.0, 1.0, TimeSpan.FromMilliseconds(280))
                {
                    EasingFunction = ease
                };
                PageHost.BeginAnimation(UIElement.OpacityProperty, opacityAnim);

                if (PageTranslate != null)
                {
                    var translateAnim = new DoubleAnimation(16.0, 0.0, TimeSpan.FromMilliseconds(280))
                    {
                        EasingFunction = ease
                    };
                    PageTranslate.BeginAnimation(TranslateTransform.YProperty, translateAnim);
                }
            }
            catch
            {
                if (PageHost != null) PageHost.Opacity = 1.0;
            }
        }

        // ─── Splash ───

        private void Splash_Completed(object? sender, EventArgs e)
        {
            _vm.SplashCompletedCommand.Execute(null);
            if (SplashOverlay != null)
            {
                SplashOverlay.Visibility = Visibility.Collapsed;
                SplashOverlay.IsHitTestVisible = false;
            }
        }

        // ─── Window State ───

        private void Window_StateChanged(object sender, EventArgs e)
        {
            if (WindowState == WindowState.Minimized)
            {
                AnimBg?.Pause();
            }
            else
            {
                AnimBg?.Resume();
            }
        }
    }
}
