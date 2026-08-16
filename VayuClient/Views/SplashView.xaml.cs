using System;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Animation;

namespace VayuClient.Views
{
    public partial class SplashView : UserControl
    {
        public event EventHandler? SplashCompleted;
        private bool _isCompleted = false;

        public SplashView()
        {
            InitializeComponent();
            Loaded += OnLoaded;
            MouseDown += (s, e) => SkipSplash();
            KeyDown += (s, e) => SkipSplash();
        }

        private async void OnLoaded(object sender, RoutedEventArgs e)
        {
            StartLogoPulse();
            await RunCinematicSplashSequence();
        }

        private void StartLogoPulse()
        {
            try
            {
                var pulseAnim = new DoubleAnimation(0.95, 1.05, new Duration(TimeSpan.FromMilliseconds(900)))
                {
                    AutoReverse = true,
                    RepeatBehavior = RepeatBehavior.Forever,
                    EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut }
                };

                LogoScale.BeginAnimation(System.Windows.Media.ScaleTransform.ScaleXProperty, pulseAnim);
                LogoScale.BeginAnimation(System.Windows.Media.ScaleTransform.ScaleYProperty, pulseAnim);
            }
            catch { }
        }

        public void SkipSplash()
        {
            if (_isCompleted) return;
            _isCompleted = true;
            Dispatcher.Invoke(() =>
            {
                Visibility = Visibility.Collapsed;
                IsHitTestVisible = false;
            });
            SplashCompleted?.Invoke(this, EventArgs.Empty);
        }

        private async Task RunCinematicSplashSequence()
        {
            if (_isCompleted) return;

            try
            {
                // Stage 1: Core Engine Initialization (0% -> 25%)
                SetStage("INITIALIZING CORE ENGINE...", 25);
                await AnimateDouble(ProgressFill, WidthProperty, 0, 80, 260, new CubicEase { EasingMode = EasingMode.EaseOut });
                if (_isCompleted) return;
                await Task.Delay(140);
                if (_isCompleted) return;

                // Stage 2: Java Runtime Discovery (25% -> 55%)
                SetStage("PROBING JAVA ENVIRONMENTS...", 55);
                await AnimateDouble(ProgressFill, WidthProperty, 80, 176, 320, new CubicEase { EasingMode = EasingMode.EaseOut });
                if (_isCompleted) return;
                await Task.Delay(140);
                if (_isCompleted) return;

                // Stage 3: Instance Discovery & Isolation (55% -> 80%)
                SetStage("DISCOVERING MINECRAFT INSTANCES...", 80);
                await AnimateDouble(ProgressFill, WidthProperty, 176, 256, 280, new CubicEase { EasingMode = EasingMode.EaseOut });
                if (_isCompleted) return;
                await Task.Delay(140);
                if (_isCompleted) return;

                // Stage 4: Profile Synchronization (80% -> 100%)
                SetStage("SYNCING PLAYER PROFILES...", 100);
                await AnimateDouble(ProgressFill, WidthProperty, 256, 320, 220, new CubicEase { EasingMode = EasingMode.EaseOut });
                if (_isCompleted) return;

                SetStage("LAUNCHER READY", 100);
                await Task.Delay(160);
                if (_isCompleted) return;

                _isCompleted = true;

                // Smooth cinematic dissolve into main window
                await AnimateDouble(Root, OpacityProperty, 1.0, 0.0, 320, new CubicEase { EasingMode = EasingMode.EaseIn });

                Dispatcher.Invoke(() =>
                {
                    Visibility = Visibility.Collapsed;
                    IsHitTestVisible = false;
                });
                SplashCompleted?.Invoke(this, EventArgs.Empty);
            }
            catch
            {
                SkipSplash();
            }
        }

        private void SetStage(string text, int percent)
        {
            Dispatcher.Invoke(() =>
            {
                StatusLabel.Text = text;
                PercentLabel.Text = $"{percent}%";
            });
        }

        private Task AnimateDouble(UIElement target, DependencyProperty property, double from, double to, int durationMs, IEasingFunction? easing = null)
        {
            var tcs = new TaskCompletionSource<bool>();
            var anim = new DoubleAnimation(from, to, TimeSpan.FromMilliseconds(durationMs))
            {
                EasingFunction = easing ?? new QuadraticEase { EasingMode = EasingMode.EaseInOut }
            };
            anim.Completed += (s, e) => tcs.TrySetResult(true);
            target.BeginAnimation(property, anim);
            return tcs.Task;
        }
    }
}

