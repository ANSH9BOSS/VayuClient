using System;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;
using VayuClient.Core;
using VayuClient.Services.Settings;

namespace VayuClient.Animations
{
    /// <summary>
    /// Centralized high-performance GPU-friendly animation engine.
    /// Provides declarative attached properties, easing functions, and programmatic transitions.
    /// Automatically respects user reduced-motion and performance settings.
    /// </summary>
    public static class AnimationEngine
    {
        private static ISettingsService? _settingsService;

        private static ISettingsService SettingsService =>
            _settingsService ??= ServiceLocator.Resolve<ISettingsService>();

        public static string CurrentQuality =>
            SettingsService?.Settings?.AnimationQuality ?? "Full";

        public static bool IsAnimationEnabled
        {
            get
            {
                var q = CurrentQuality;
                return !string.Equals(q, "Off", StringComparison.OrdinalIgnoreCase) &&
                       !string.Equals(q, "Minimal", StringComparison.OrdinalIgnoreCase);
            }
        }

        public static double GetDurationMultiplier()
        {
            var q = CurrentQuality;
            if (string.Equals(q, "Off", StringComparison.OrdinalIgnoreCase)) return 0.0;
            if (string.Equals(q, "Minimal", StringComparison.OrdinalIgnoreCase)) return 0.0;
            if (string.Equals(q, "Reduced", StringComparison.OrdinalIgnoreCase)) return 0.5;
            return 1.0; // Full
        }

        #region Easing Curves

        public static readonly IEasingFunction CubicEaseInOut = new CubicEase { EasingMode = EasingMode.EaseInOut };
        public static readonly IEasingFunction QuarticEaseOut = new QuarticEase { EasingMode = EasingMode.EaseOut };
        public static readonly IEasingFunction BackEaseOut = new BackEase { EasingMode = EasingMode.EaseOut, Amplitude = 0.35 };
        public static readonly IEasingFunction CircleEaseOut = new CircleEase { EasingMode = EasingMode.EaseOut };
        public static readonly IEasingFunction ExponentialEaseOut = new ExponentialEase { EasingMode = EasingMode.EaseOut, Exponent = 6 };

        #endregion

        #region Attached Properties: FadeInOnLoaded

        public static readonly DependencyProperty FadeInOnLoadedProperty =
            DependencyProperty.RegisterAttached("FadeInOnLoaded", typeof(bool), typeof(AnimationEngine),
                new PropertyMetadata(false, OnFadeInOnLoadedChanged));

        public static bool GetFadeInOnLoaded(DependencyObject obj) => (bool)obj.GetValue(FadeInOnLoadedProperty);
        public static void SetFadeInOnLoaded(DependencyObject obj, bool value) => obj.SetValue(FadeInOnLoadedProperty, value);

        private static void OnFadeInOnLoadedChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            if (d is FrameworkElement element && (bool)e.NewValue)
            {
                element.Loaded += (s, ev) =>
                {
                    if (!IsAnimationEnabled) return;
                    int stagger = GetStaggerIndex(element);
                    double delay = stagger * 45;
                    FadeIn(element, 320, delay);
                };
            }
        }

        #endregion

        #region Attached Properties: SlideUpOnLoaded

        public static readonly DependencyProperty SlideUpOnLoadedProperty =
            DependencyProperty.RegisterAttached("SlideUpOnLoaded", typeof(double), typeof(AnimationEngine),
                new PropertyMetadata(0.0, OnSlideUpOnLoadedChanged));

        public static double GetSlideUpOnLoaded(DependencyObject obj) => (double)obj.GetValue(SlideUpOnLoadedProperty);
        public static void SetSlideUpOnLoaded(DependencyObject obj, double value) => obj.SetValue(SlideUpOnLoadedProperty, value);

        private static void OnSlideUpOnLoadedChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            if (d is FrameworkElement element && (double)e.NewValue > 0)
            {
                element.Loaded += (s, ev) =>
                {
                    if (!IsAnimationEnabled) return;
                    int stagger = GetStaggerIndex(element);
                    double distance = (double)e.NewValue;
                    double delay = stagger * 40;
                    SlideUpFadeIn(element, distance, 380, delay);
                };
            }
        }

        #endregion

        #region Attached Properties: StaggerIndex

        public static readonly DependencyProperty StaggerIndexProperty =
            DependencyProperty.RegisterAttached("StaggerIndex", typeof(int), typeof(AnimationEngine),
                new PropertyMetadata(0));

        public static int GetStaggerIndex(DependencyObject obj) => (int)obj.GetValue(StaggerIndexProperty);
        public static void SetStaggerIndex(DependencyObject obj, int value) => obj.SetValue(StaggerIndexProperty, value);

        #endregion

        #region Attached Properties: HoverScale

        public static readonly DependencyProperty HoverScaleProperty =
            DependencyProperty.RegisterAttached("HoverScale", typeof(double), typeof(AnimationEngine),
                new PropertyMetadata(1.0, OnHoverScaleChanged));

        public static double GetHoverScale(DependencyObject obj) => (double)obj.GetValue(HoverScaleProperty);
        public static void SetHoverScale(DependencyObject obj, double value) => obj.SetValue(HoverScaleProperty, value);

        private static void OnHoverScaleChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            if (d is FrameworkElement element)
            {
                element.MouseEnter += (s, ev) =>
                {
                    if (!IsAnimationEnabled) return;
                    double targetScale = (double)element.GetValue(HoverScaleProperty);
                    ScaleTo(element, targetScale, 180, BackEaseOut);
                };
                element.MouseLeave += (s, ev) =>
                {
                    if (!IsAnimationEnabled) return;
                    ScaleTo(element, 1.0, 160, QuarticEaseOut);
                };
            }
        }

        #endregion

        #region Attached Properties: PressScale

        public static readonly DependencyProperty PressScaleProperty =
            DependencyProperty.RegisterAttached("PressScale", typeof(double), typeof(AnimationEngine),
                new PropertyMetadata(1.0, OnPressScaleChanged));

        public static double GetPressScale(DependencyObject obj) => (double)obj.GetValue(PressScaleProperty);
        public static void SetPressScale(DependencyObject obj, double value) => obj.SetValue(PressScaleProperty, value);

        private static void OnPressScaleChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            if (d is FrameworkElement element)
            {
                element.PreviewMouseLeftButtonDown += (s, ev) =>
                {
                    if (!IsAnimationEnabled) return;
                    double press = (double)element.GetValue(PressScaleProperty);
                    ScaleTo(element, press, 90, QuarticEaseOut);
                };
                element.PreviewMouseLeftButtonUp += (s, ev) =>
                {
                    if (!IsAnimationEnabled) return;
                    double target = element.IsMouseOver ? (double)element.GetValue(HoverScaleProperty) : 1.0;
                    ScaleTo(element, target > 1.0 ? target : 1.0, 140, BackEaseOut);
                };
            }
        }

        #endregion

        #region Programmatic Animations

        public static void FadeIn(UIElement element, double durationMs = 280, double delayMs = 0, Action? onCompleted = null)
        {
            double mult = GetDurationMultiplier();
            if (mult == 0)
            {
                element.Opacity = 1.0;
                onCompleted?.Invoke();
                return;
            }

            element.Opacity = 0.0;
            var anim = new DoubleAnimation(0.0, 1.0, new Duration(TimeSpan.FromMilliseconds(durationMs * mult)))
            {
                BeginTime = TimeSpan.FromMilliseconds(delayMs),
                EasingFunction = QuarticEaseOut
            };
            if (onCompleted != null)
                anim.Completed += (s, e) => onCompleted();

            element.BeginAnimation(UIElement.OpacityProperty, anim);
        }

        public static void FadeOut(UIElement element, double durationMs = 220, Action? onCompleted = null)
        {
            double mult = GetDurationMultiplier();
            if (mult == 0)
            {
                element.Opacity = 0.0;
                onCompleted?.Invoke();
                return;
            }

            var anim = new DoubleAnimation(element.Opacity, 0.0, new Duration(TimeSpan.FromMilliseconds(durationMs * mult)))
            {
                EasingFunction = QuarticEaseOut
            };
            if (onCompleted != null)
                anim.Completed += (s, e) => onCompleted();

            element.BeginAnimation(UIElement.OpacityProperty, anim);
        }

        public static void SlideUpFadeIn(UIElement element, double distance = 24, double durationMs = 320, double delayMs = 0, Action? onCompleted = null)
        {
            double mult = GetDurationMultiplier();
            if (mult == 0)
            {
                element.Opacity = 1.0;
                if (element.RenderTransform is TranslateTransform tt) tt.Y = 0;
                onCompleted?.Invoke();
                return;
            }

            element.Opacity = 0.0;
            var transform = EnsureTranslateTransform(element);
            transform.Y = distance;

            var fadeAnim = new DoubleAnimation(0.0, 1.0, new Duration(TimeSpan.FromMilliseconds(durationMs * mult)))
            {
                BeginTime = TimeSpan.FromMilliseconds(delayMs),
                EasingFunction = QuarticEaseOut
            };
            var slideAnim = new DoubleAnimation(distance, 0.0, new Duration(TimeSpan.FromMilliseconds(durationMs * mult)))
            {
                BeginTime = TimeSpan.FromMilliseconds(delayMs),
                EasingFunction = BackEaseOut
            };

            if (onCompleted != null)
                fadeAnim.Completed += (s, e) => onCompleted();

            element.BeginAnimation(UIElement.OpacityProperty, fadeAnim);
            transform.BeginAnimation(TranslateTransform.YProperty, slideAnim);
        }

        public static void ScaleTo(UIElement element, double targetScale, double durationMs = 200, IEasingFunction? easing = null, Action? onCompleted = null)
        {
            double mult = GetDurationMultiplier();
            if (mult == 0)
            {
                var st = EnsureScaleTransform(element);
                st.ScaleX = targetScale;
                st.ScaleY = targetScale;
                onCompleted?.Invoke();
                return;
            }

            var scaleTransform = EnsureScaleTransform(element);
            var animX = new DoubleAnimation(targetScale, new Duration(TimeSpan.FromMilliseconds(durationMs * mult)))
            {
                EasingFunction = easing ?? QuarticEaseOut
            };
            var animY = new DoubleAnimation(targetScale, new Duration(TimeSpan.FromMilliseconds(durationMs * mult)))
            {
                EasingFunction = easing ?? QuarticEaseOut
            };

            if (onCompleted != null)
                animX.Completed += (s, e) => onCompleted();

            scaleTransform.BeginAnimation(ScaleTransform.ScaleXProperty, animX);
            scaleTransform.BeginAnimation(ScaleTransform.ScaleYProperty, animY);
        }

        public static void TranslateTo(UIElement element, double targetX, double targetY, double durationMs = 260, IEasingFunction? easing = null, Action? onCompleted = null)
        {
            double mult = GetDurationMultiplier();
            var tt = EnsureTranslateTransform(element);
            if (mult == 0)
            {
                tt.X = targetX;
                tt.Y = targetY;
                onCompleted?.Invoke();
                return;
            }

            var animX = new DoubleAnimation(targetX, new Duration(TimeSpan.FromMilliseconds(durationMs * mult)))
            {
                EasingFunction = easing ?? QuarticEaseOut
            };
            var animY = new DoubleAnimation(targetY, new Duration(TimeSpan.FromMilliseconds(durationMs * mult)))
            {
                EasingFunction = easing ?? QuarticEaseOut
            };

            if (onCompleted != null)
                animX.Completed += (s, e) => onCompleted();

            tt.BeginAnimation(TranslateTransform.XProperty, animX);
            tt.BeginAnimation(TranslateTransform.YProperty, animY);
        }

        #endregion

        #region Helpers

        private static TranslateTransform EnsureTranslateTransform(UIElement element)
        {
            if (element.RenderTransform is TranslateTransform tt)
                return tt;

            if (element.RenderTransform is TransformGroup group)
            {
                foreach (var child in group.Children)
                {
                    if (child is TranslateTransform childTt)
                        return childTt;
                }
                var newTt = new TranslateTransform();
                group.Children.Add(newTt);
                return newTt;
            }

            var singleTt = new TranslateTransform();
            element.RenderTransformOrigin = new Point(0.5, 0.5);
            element.RenderTransform = singleTt;
            return singleTt;
        }

        private static ScaleTransform EnsureScaleTransform(UIElement element)
        {
            if (element.RenderTransform is ScaleTransform st)
                return st;

            if (element.RenderTransform is TransformGroup group)
            {
                foreach (var child in group.Children)
                {
                    if (child is ScaleTransform childSt)
                        return childSt;
                }
                var newSt = new ScaleTransform(1.0, 1.0);
                group.Children.Add(newSt);
                return newSt;
            }

            var singleSt = new ScaleTransform(1.0, 1.0);
            element.RenderTransformOrigin = new Point(0.5, 0.5);
            element.RenderTransform = singleSt;
            return singleSt;
        }

        #endregion
    }
}
