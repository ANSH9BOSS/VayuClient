using System;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Media3D;
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
            await Run3DFractalGrowthSequence();
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

        private async Task Run3DFractalGrowthSequence()
        {
            if (_isCompleted) return;

            try
            {
                // ─── PHASE 0: Initial Void & Central Logo Entrance (0%) ───
                await Task.Delay(50);
                if (_isCompleted) return;

                // Fade in Center Logo, Status Telemetry & Skip Label
                var logoFade = AnimateDouble(CenterLogoGroup, OpacityProperty, 0.0, 1.0, 280, AnimationEngine.QuarticEaseOut);
                var logoScaleX = AnimateDouble(CenterLogoScale, ScaleTransform.ScaleXProperty, 0.90, 1.0, 320, AnimationEngine.BackEaseOut);
                var logoScaleY = AnimateDouble(CenterLogoScale, ScaleTransform.ScaleYProperty, 0.90, 1.0, 320, AnimationEngine.BackEaseOut);
                var statusFade = AnimateDouble(StatusContainer, OpacityProperty, 0.0, 1.0, 240, AnimationEngine.QuarticEaseOut);
                var skipFade = AnimateDouble(SkipLabel, OpacityProperty, 0.0, 0.75, 300, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(logoFade, logoScaleX, logoScaleY, statusFade, skipFade);
                if (_isCompleted) return;

                // ─── STAGE 1 (0% -> 25%): "INITIALIZING CORE SYSTEMS" ───
                // Left 3D Polyhedral Cluster rotates and scales into 3D position
                SetStage("INITIALIZING CORE SYSTEMS", 25);
                var stage1Hairline = AnimateDouble(ProgressHairline, WidthProperty, 0, 40, 260, AnimationEngine.QuarticEaseOut);
                var leftScaleX = AnimateDouble(LeftScale, ScaleTransform3D.ScaleXProperty, 0.2, 1.0, 320, AnimationEngine.BackEaseOut);
                var leftScaleY = AnimateDouble(LeftScale, ScaleTransform3D.ScaleYProperty, 0.2, 1.0, 320, AnimationEngine.BackEaseOut);
                var leftScaleZ = AnimateDouble(LeftScale, ScaleTransform3D.ScaleZProperty, 0.2, 1.0, 320, AnimationEngine.BackEaseOut);
                var leftRot = AnimateDouble(LeftRot, AxisAngleRotation3D.AngleProperty, -45, 0, 320, AnimationEngine.QuarticEaseOut);
                var leftTransX = AnimateDouble(LeftTrans, TranslateTransform3D.OffsetXProperty, -3.5, 0, 320, AnimationEngine.QuarticEaseOut);
                var leftTransY = AnimateDouble(LeftTrans, TranslateTransform3D.OffsetYProperty, -1.5, 0, 320, AnimationEngine.QuarticEaseOut);
                var leftTransZ = AnimateDouble(LeftTrans, TranslateTransform3D.OffsetZProperty, -3.0, 0, 320, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(stage1Hairline, leftScaleX, leftScaleY, leftScaleZ, leftRot, leftTransX, leftTransY, leftTransZ);
                if (_isCompleted) return;
                await Task.Delay(100);
                if (_isCompleted) return;

                // ─── STAGE 2 (25% -> 50%): "LOADING PROFILE" ───
                // Right 3D Razor Wing rotates and locks in from opposite 3D depth
                SetStage("LOADING PROFILE", 50);
                var stage2Hairline = AnimateDouble(ProgressHairline, WidthProperty, 40, 80, 260, AnimationEngine.QuarticEaseOut);
                var rightScaleX = AnimateDouble(RightScale, ScaleTransform3D.ScaleXProperty, 0.2, 1.0, 320, AnimationEngine.BackEaseOut);
                var rightScaleY = AnimateDouble(RightScale, ScaleTransform3D.ScaleYProperty, 0.2, 1.0, 320, AnimationEngine.BackEaseOut);
                var rightScaleZ = AnimateDouble(RightScale, ScaleTransform3D.ScaleZProperty, 0.2, 1.0, 320, AnimationEngine.BackEaseOut);
                var rightRot = AnimateDouble(RightRot, AxisAngleRotation3D.AngleProperty, 50, 0, 320, AnimationEngine.QuarticEaseOut);
                var rightTransX = AnimateDouble(RightTrans, TranslateTransform3D.OffsetXProperty, 3.5, 0, 320, AnimationEngine.QuarticEaseOut);
                var rightTransY = AnimateDouble(RightTrans, TranslateTransform3D.OffsetYProperty, 1.2, 0, 320, AnimationEngine.QuarticEaseOut);
                var rightTransZ = AnimateDouble(RightTrans, TranslateTransform3D.OffsetZProperty, -3.0, 0, 320, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(stage2Hairline, rightScaleX, rightScaleY, rightScaleZ, rightRot, rightTransX, rightTransY, rightTransZ);
                if (_isCompleted) return;
                await Task.Delay(100);
                if (_isCompleted) return;

                // ─── STAGE 3 (50% -> 75%): "LOADING INSTANCES" ───
                // Top Crown Crystals and Foreground Shards glide into perspective
                SetStage("LOADING INSTANCES", 75);
                var stage3Hairline = AnimateDouble(ProgressHairline, WidthProperty, 80, 120, 260, AnimationEngine.QuarticEaseOut);
                var topScaleX = AnimateDouble(TopScale, ScaleTransform3D.ScaleXProperty, 0.1, 1.0, 300, AnimationEngine.BackEaseOut);
                var topScaleY = AnimateDouble(TopScale, ScaleTransform3D.ScaleYProperty, 0.1, 1.0, 300, AnimationEngine.BackEaseOut);
                var topScaleZ = AnimateDouble(TopScale, ScaleTransform3D.ScaleZProperty, 0.1, 1.0, 300, AnimationEngine.BackEaseOut);
                var topRot = AnimateDouble(TopRot, AxisAngleRotation3D.AngleProperty, -35, 0, 300, AnimationEngine.QuarticEaseOut);
                var topTransY = AnimateDouble(TopTrans, TranslateTransform3D.OffsetYProperty, 2.5, 0, 300, AnimationEngine.QuarticEaseOut);
                var topTransZ = AnimateDouble(TopTrans, TranslateTransform3D.OffsetZProperty, -2.5, 0, 300, AnimationEngine.QuarticEaseOut);

                var foreScaleX = AnimateDouble(ForeScale, ScaleTransform3D.ScaleXProperty, 0.2, 1.0, 280, AnimationEngine.BackEaseOut);
                var foreScaleY = AnimateDouble(ForeScale, ScaleTransform3D.ScaleYProperty, 0.2, 1.0, 280, AnimationEngine.BackEaseOut);
                var foreScaleZ = AnimateDouble(ForeScale, ScaleTransform3D.ScaleZProperty, 0.2, 1.0, 280, AnimationEngine.BackEaseOut);
                var foreTransZ = AnimateDouble(ForeTrans, TranslateTransform3D.OffsetZProperty, 1.5, 0, 280, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(stage3Hairline, topScaleX, topScaleY, topScaleZ, topRot, topTransY, topTransZ, foreScaleX, foreScaleY, foreScaleZ, foreTransZ);
                if (_isCompleted) return;
                await Task.Delay(100);
                if (_isCompleted) return;

                // ─── STAGE 4 (75% -> 90%): "CHECKING UPDATES" ───
                // Camera Field of View subtly tightens for cinematic 3D dolly depth
                SetStage("CHECKING UPDATES", 90);
                var stage4Hairline = AnimateDouble(ProgressHairline, WidthProperty, 120, 144, 220, AnimationEngine.QuarticEaseOut);
                var cameraFov = AnimateDouble(MainCamera, PerspectiveCamera.FieldOfViewProperty, 48, 45, 240, AnimationEngine.QuarticEaseOut);

                await Task.WhenAll(stage4Hairline, cameraFov);
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

                // ─── PHASE 6: Smooth Dissolve into Main Launcher ───
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
