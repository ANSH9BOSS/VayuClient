using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Shapes;
using VayuClient.Animations;

namespace VayuClient.Controls
{
    /// <summary>
    /// Interactive animated SVG vector icon control.
    /// Provides smooth GPU-accelerated hover scaling, press compression, and color morphing.
    /// </summary>
    public class VayuVectorIcon : ContentControl
    {
        private Path? _pathElement;
        private ScaleTransform? _scaleTransform;
        private TranslateTransform? _translateTransform;

        public static readonly DependencyProperty DataProperty =
            DependencyProperty.Register(nameof(Data), typeof(Geometry), typeof(VayuVectorIcon),
                new PropertyMetadata(null, OnDataChanged));

        public static readonly DependencyProperty IconSizeProperty =
            DependencyProperty.Register(nameof(IconSize), typeof(double), typeof(VayuVectorIcon),
                new PropertyMetadata(16.0));

        public static readonly DependencyProperty IconFillProperty =
            DependencyProperty.Register(nameof(IconFill), typeof(Brush), typeof(VayuVectorIcon),
                new PropertyMetadata(Brushes.White));

        public static readonly DependencyProperty HoverFillProperty =
            DependencyProperty.Register(nameof(HoverFill), typeof(Brush), typeof(VayuVectorIcon),
                new PropertyMetadata(null));

        public static readonly DependencyProperty HoverScaleProperty =
            DependencyProperty.Register(nameof(HoverScale), typeof(double), typeof(VayuVectorIcon),
                new PropertyMetadata(1.12));

        public static readonly DependencyProperty HoverTranslateYProperty =
            DependencyProperty.Register(nameof(HoverTranslateY), typeof(double), typeof(VayuVectorIcon),
                new PropertyMetadata(-1.0));

        public static readonly DependencyProperty IsSelectedProperty =
            DependencyProperty.Register(nameof(IsSelected), typeof(bool), typeof(VayuVectorIcon),
                new PropertyMetadata(false, OnIsSelectedChanged));

        public Geometry? Data
        {
            get => (Geometry?)GetValue(DataProperty);
            set => SetValue(DataProperty, value);
        }

        public double IconSize
        {
            get => (double)GetValue(IconSizeProperty);
            set => SetValue(IconSizeProperty, value);
        }

        public Brush IconFill
        {
            get => (Brush)GetValue(IconFillProperty);
            set => SetValue(IconFillProperty, value);
        }

        public Brush? HoverFill
        {
            get => (Brush?)GetValue(HoverFillProperty);
            set => SetValue(HoverFillProperty, value);
        }

        public double HoverScale
        {
            get => (double)GetValue(HoverScaleProperty);
            set => SetValue(HoverScaleProperty, value);
        }

        public double HoverTranslateY
        {
            get => (double)GetValue(HoverTranslateYProperty);
            set => SetValue(HoverTranslateYProperty, value);
        }

        public bool IsSelected
        {
            get => (bool)GetValue(IsSelectedProperty);
            set => SetValue(IsSelectedProperty, value);
        }

        static VayuVectorIcon()
        {
            DefaultStyleKeyProperty.OverrideMetadata(typeof(VayuVectorIcon), new FrameworkPropertyMetadata(typeof(VayuVectorIcon)));
        }

        public VayuVectorIcon()
        {
            Focusable = false;
            IsHitTestVisible = false;
            Loaded += OnLoaded;
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            BuildVisualTree();
        }

        private static void OnDataChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            if (d is VayuVectorIcon icon) icon.BuildVisualTree();
        }

        private static void OnIsSelectedChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            if (d is VayuVectorIcon icon) icon.UpdateState();
        }

        private void BuildVisualTree()
        {
            if (Data == null) return;

            var transformGroup = new TransformGroup();
            _scaleTransform = new ScaleTransform(1.0, 1.0);
            _translateTransform = new TranslateTransform(0.0, 0.0);
            transformGroup.Children.Add(_scaleTransform);
            transformGroup.Children.Add(_translateTransform);

            _pathElement = new Path
            {
                Data = Data,
                Fill = IconFill,
                Width = IconSize,
                Height = IconSize,
                Stretch = Stretch.Uniform,
                RenderTransformOrigin = new Point(0.5, 0.5),
                RenderTransform = transformGroup
            };

            Content = _pathElement;
        }

        public void AnimateHover(bool isHovered)
        {
            if (_scaleTransform == null || _translateTransform == null || !AnimationEngine.IsAnimationEnabled) return;

            double targetScale = isHovered ? HoverScale : 1.0;
            double targetY = isHovered ? HoverTranslateY : 0.0;
            double durationMs = isHovered ? 160 : 140;

            var scaleAnim = new DoubleAnimation(targetScale, new Duration(TimeSpan.FromMilliseconds(durationMs)))
            {
                EasingFunction = AnimationEngine.QuarticEaseOut
            };
            var transAnim = new DoubleAnimation(targetY, new Duration(TimeSpan.FromMilliseconds(durationMs)))
            {
                EasingFunction = AnimationEngine.QuarticEaseOut
            };

            _scaleTransform.BeginAnimation(ScaleTransform.ScaleXProperty, scaleAnim);
            _scaleTransform.BeginAnimation(ScaleTransform.ScaleYProperty, scaleAnim);
            _translateTransform.BeginAnimation(TranslateTransform.YProperty, transAnim);

            if (_pathElement != null && HoverFill != null)
            {
                _pathElement.Fill = isHovered ? HoverFill : IconFill;
            }
        }

        public void AnimatePress(bool isPressed)
        {
            if (_scaleTransform == null || !AnimationEngine.IsAnimationEnabled) return;

            double targetScale = isPressed ? 0.90 : (IsMouseOver ? HoverScale : 1.0);
            double durationMs = isPressed ? 80 : 140;

            var anim = new DoubleAnimation(targetScale, new Duration(TimeSpan.FromMilliseconds(durationMs)))
            {
                EasingFunction = isPressed ? AnimationEngine.QuarticEaseOut : AnimationEngine.BackEaseOut
            };

            _scaleTransform.BeginAnimation(ScaleTransform.ScaleXProperty, anim);
            _scaleTransform.BeginAnimation(ScaleTransform.ScaleYProperty, anim);
        }

        private void UpdateState()
        {
            if (_pathElement != null)
            {
                if (IsSelected && HoverFill != null)
                    _pathElement.Fill = HoverFill;
                else
                    _pathElement.Fill = IconFill;
            }
        }
    }
}
