using System;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using VayuClient.Animations;

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
            await RunCinematicSplashSequence();
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
                // PHASE 1: Dark Screen Initial Hold
                await Task.Delay(80);
                if (_isCompleted) return;

                // PHASE 2: Background Layer Fade In (0 -> 1.0)
                await AnimateDouble(BackgroundLayer, OpacityProperty, 0.0, 1.0, 320, AnimationEngine.QuarticEaseOut);
                if (_isCompleted) return;

                // PHASE 3: Logo Entrance (Scale 0.90 -> 1.0, Slide Up, Fade In)
                var logoFade = AnimateDouble(MainContent, OpacityProperty, 0.0, 1.0, 360, AnimationEngine.QuarticEaseOut);
                var logoScaleX = AnimateDouble(LogoScale, ScaleTransform.ScaleXProperty, 0.90, 1.0, 420, AnimationEngine.BackEaseOut);
                var logoScaleY = AnimateDouble(LogoScale, ScaleTransform.ScaleYProperty, 0.90, 1.0, 420, AnimationEngine.BackEaseOut);
                var logoSlide = AnimateDouble(ContentTranslate, TranslateTransform.YProperty, 16, 0, 380, AnimationEngine.QuarticEaseOut);
                await Task.WhenAll(logoFade, logoScaleX, logoScaleY, logoSlide);
                if (_isCompleted) return;

                // PHASE 4: Shimmer Sweep Glow across the Logo
                ShimmerSweep.Opacity = 1.0;
                await AnimateDouble(ShimmerTranslate, TranslateTransform.XProperty, 0, 90, 350, AnimationEngine.CubicEaseInOut);
                ShimmerSweep.Opacity = 0.0;
                if (_isCompleted) return;

                // PHASE 5: Show Skip prompt & progress sequence
                _ = AnimateDouble(SkipLabel, OpacityProperty, 0.0, 0.8, 200, AnimationEngine.QuarticEaseOut);

                // Stage 1: Core Engine Initialization (0% -> 25%)
                SetStage("INITIALIZING CORE ENGINE...", 25);
                await AnimateDouble(ProgressFill, WidthProperty, 0, 85, 240, AnimationEngine.QuarticEaseOut);
                if (_isCompleted) return;
                await Task.Delay(90);
                if (_isCompleted) return;

                // Stage 2: Hardware & Java Runtime Discovery (25% -> 55%)
                SetStage("PROBING JAVA & HARDWARE PROFILES...", 55);
                await AnimateDouble(ProgressFill, WidthProperty, 85, 187, 280, AnimationEngine.QuarticEaseOut);
                if (_isCompleted) return;
                await Task.Delay(90);
                if (_isCompleted) return;

                // Stage 3: Instance Manifest & Version Resolution (55% -> 80%)
                SetStage("LOADING MINECRAFT INSTANCES...", 80);
                await AnimateDouble(ProgressFill, WidthProperty, 187, 272, 260, AnimationEngine.QuarticEaseOut);
                if (_isCompleted) return;
                await Task.Delay(90);
                if (_isCompleted) return;

                // Stage 4: Profile Verification (80% -> 100%)
                SetStage("AUTHENTICATING ACTIVE PROFILE...", 100);
                await AnimateDouble(ProgressFill, WidthProperty, 272, 340, 220, AnimationEngine.QuarticEaseOut);
                if (_isCompleted) return;

                // PHASE 6: Complete
                SetStage("READY FOR GAME EXECUTION", 100);
                await Task.Delay(140);
                if (_isCompleted) return;

                _isCompleted = true;

                // PHASE 7: Dissolve into Main Launcher UI
                await AnimateDouble(Root, OpacityProperty, 1.0, 0.0, 280, AnimationEngine.QuarticEaseOut);

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

        private Task AnimateDouble(IAnimatable target, DependencyProperty property, double from, double to, int durationMs, IEasingFunction? easing = null)
        {
            var tcs = new TaskCompletionSource<bool>();
            if (!AnimationEngine.IsAnimationEnabled)
            {
                if (target is DependencyObject d) d.SetValue(property, to);
                tcs.TrySetResult(true);
                return tcs.Task;
            }

            var anim = new DoubleAnimation(from, to, TimeSpan.FromMilliseconds(durationMs * AnimationEngine.GetDurationMultiplier()))
            {
                EasingFunction = easing ?? AnimationEngine.QuarticEaseOut
            };
            anim.Completed += (s, e) => tcs.TrySetResult(true);
            target.BeginAnimation(property, anim);
            return tcs.Task;
        }
    }
}
