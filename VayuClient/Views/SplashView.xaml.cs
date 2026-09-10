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
            await RunFractalGrowthSequence();
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

        private async Task RunFractalGrowthSequence()
        {
            if (_isCompleted) return;

            try
            {
                // ─── PHASE 0: Initial Void & Central Logo Entrance (0%) ───
                await Task.Delay(60);
                if (_isCompleted) return;

                // Fade in Center Logo & Status Telemetry
                var logoFade = AnimateDouble(CenterLogoGroup, OpacityProperty, 0.0, 1.0, 300, AnimationEngine.QuarticEaseOut);
                var logoScaleX = AnimateDouble(CenterLogoScale, ScaleTransform.ScaleXProperty, 0.92, 1.0, 360, AnimationEngine.BackEaseOut);
                var logoScaleY = AnimateDouble(CenterLogoScale, ScaleTransform.ScaleYProperty, 0.92, 1.0, 360, AnimationEngine.BackEaseOut);
                var statusFade = AnimateDouble(StatusContainer, OpacityProperty, 0.0, 1.0, 260, AnimationEngine.QuarticEaseOut);
                var skipFade = AnimateDouble(SkipLabel, OpacityProperty, 0.0, 0.85, 300, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(logoFade, logoScaleX, logoScaleY, statusFade, skipFade);
                if (_isCompleted) return;

                // ─── STAGE 1 (0% -> 25%): "INITIALIZING CORE SYSTEMS" ───
                SetStage("INITIALIZING CORE SYSTEMS", 25);
                var stage1Hairline = AnimateDouble(ProgressHairline, WidthProperty, 0, 40, 240, AnimationEngine.QuarticEaseOut);
                var c1Fade = AnimateDouble(ClusterStage1, OpacityProperty, 0.0, 1.0, 260, AnimationEngine.QuarticEaseOut);
                var c1ScaleX = AnimateDouble(Cluster1Scale, ScaleTransform.ScaleXProperty, 0.5, 1.0, 280, AnimationEngine.BackEaseOut);
                var c1ScaleY = AnimateDouble(Cluster1Scale, ScaleTransform.ScaleYProperty, 0.5, 1.0, 280, AnimationEngine.BackEaseOut);
                var c1TransX = AnimateDouble(Cluster1Translate, TranslateTransform.XProperty, -40, 0, 280, AnimationEngine.QuarticEaseOut);
                var c1TransY = AnimateDouble(Cluster1Translate, TranslateTransform.YProperty, 40, 0, 280, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(stage1Hairline, c1Fade, c1ScaleX, c1ScaleY, c1TransX, c1TransY);
                if (_isCompleted) return;
                await Task.Delay(100);
                if (_isCompleted) return;

                // ─── STAGE 2 (25% -> 50%): "LOADING PROFILE" ───
                SetStage("LOADING PROFILE", 50);
                var stage2Hairline = AnimateDouble(ProgressHairline, WidthProperty, 40, 80, 260, AnimationEngine.QuarticEaseOut);
                var c2Fade = AnimateDouble(ClusterStage2, OpacityProperty, 0.0, 1.0, 260, AnimationEngine.QuarticEaseOut);
                var c2ScaleX = AnimateDouble(Cluster2Scale, ScaleTransform.ScaleXProperty, 0.5, 1.0, 280, AnimationEngine.BackEaseOut);
                var c2ScaleY = AnimateDouble(Cluster2Scale, ScaleTransform.ScaleYProperty, 0.5, 1.0, 280, AnimationEngine.BackEaseOut);
                var c2TransX = AnimateDouble(Cluster2Translate, TranslateTransform.XProperty, -20, 0, 280, AnimationEngine.QuarticEaseOut);
                var c2TransY = AnimateDouble(Cluster2Translate, TranslateTransform.YProperty, -20, 0, 280, AnimationEngine.QuarticEaseOut);
                var annotFade = AnimateDouble(AnnotationCanvas, OpacityProperty, 0.0, 1.0, 320, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(stage2Hairline, c2Fade, c2ScaleX, c2ScaleY, c2TransX, c2TransY, annotFade);
                if (_isCompleted) return;
                await Task.Delay(100);
                if (_isCompleted) return;

                // ─── STAGE 3 (50% -> 75%): "LOADING INSTANCES" ───
                SetStage("LOADING INSTANCES", 75);
                var stage3Hairline = AnimateDouble(ProgressHairline, WidthProperty, 80, 120, 260, AnimationEngine.QuarticEaseOut);
                var c3Fade = AnimateDouble(ClusterStage3, OpacityProperty, 0.0, 1.0, 280, AnimationEngine.QuarticEaseOut);
                var c3ScaleX = AnimateDouble(Cluster3Scale, ScaleTransform.ScaleXProperty, 0.5, 1.0, 300, AnimationEngine.BackEaseOut);
                var c3ScaleY = AnimateDouble(Cluster3Scale, ScaleTransform.ScaleYProperty, 0.5, 1.0, 300, AnimationEngine.BackEaseOut);
                var c3TransX = AnimateDouble(Cluster3Translate, TranslateTransform.XProperty, 30, 0, 300, AnimationEngine.QuarticEaseOut);
                var c3TransY = AnimateDouble(Cluster3Translate, TranslateTransform.YProperty, -20, 0, 300, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(stage3Hairline, c3Fade, c3ScaleX, c3ScaleY, c3TransX, c3TransY);
                if (_isCompleted) return;
                await Task.Delay(100);
                if (_isCompleted) return;

                // ─── STAGE 4 (75% -> 90%): "CHECKING UPDATES" ───
                SetStage("CHECKING UPDATES", 90);
                var stage4Hairline = AnimateDouble(ProgressHairline, WidthProperty, 120, 144, 220, AnimationEngine.QuarticEaseOut);
                var c4Fade = AnimateDouble(ClusterStage4, OpacityProperty, 0.0, 1.0, 240, AnimationEngine.QuarticEaseOut);
                var c4ScaleX = AnimateDouble(Cluster4Scale, ScaleTransform.ScaleXProperty, 0.6, 1.0, 260, AnimationEngine.BackEaseOut);
                var c4ScaleY = AnimateDouble(Cluster4Scale, ScaleTransform.ScaleYProperty, 0.6, 1.0, 260, AnimationEngine.BackEaseOut);

                await Task.WhenAll(stage4Hairline, c4Fade, c4ScaleX, c4ScaleY);
                if (_isCompleted) return;
                await Task.Delay(100);
                if (_isCompleted) return;

                // ─── STAGE 5 (90% -> 100%): "READY" ───
                SetStage("READY", 100);
                await AnimateDouble(ProgressHairline, WidthProperty, 144, 160, 160, AnimationEngine.QuarticEaseOut);
                if (_isCompleted) return;

                await Task.Delay(180);
                if (_isCompleted) return;

                _isCompleted = true;

                // ─── PHASE 6: Smooth Morph & Dissolve into Launcher ───
                var rootFade = AnimateDouble(Root, OpacityProperty, 1.0, 0.0, 240, AnimationEngine.QuarticEaseOut);
                var rootScaleX = AnimateDouble(RootScale, ScaleTransform.ScaleXProperty, 1.0, 1.03, 240, AnimationEngine.QuarticEaseOut);
                var rootScaleY = AnimateDouble(RootScale, ScaleTransform.ScaleYProperty, 1.0, 1.03, 240, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(rootFade, rootScaleX, rootScaleY);

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
