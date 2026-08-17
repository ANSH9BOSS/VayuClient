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
    /// Smooth animated gaming toggle switch control.
    /// Features GPU-accelerated thumb translation, track color morphing, and subtle glow.
    /// </summary>
    public class VayuToggleSwitch : Control
    {
        private Border? _track;
        private Ellipse? _thumb;
        private TranslateTransform? _thumbTranslate;

        public static readonly DependencyProperty IsOnProperty =
            DependencyProperty.Register(nameof(IsOn), typeof(bool), typeof(VayuToggleSwitch),
                new FrameworkPropertyMetadata(false, FrameworkPropertyMetadataOptions.BindsTwoWayByDefault, OnIsOnChanged));

        public static readonly DependencyProperty HeaderProperty =
            DependencyProperty.Register(nameof(Header), typeof(string), typeof(VayuToggleSwitch),
                new PropertyMetadata(string.Empty));

        public static readonly DependencyProperty OnBrushProperty =
            DependencyProperty.Register(nameof(OnBrush), typeof(Brush), typeof(VayuToggleSwitch),
                new PropertyMetadata(new SolidColorBrush(Color.FromRgb(37, 99, 235))));

        public static readonly DependencyProperty OffBrushProperty =
            DependencyProperty.Register(nameof(OffBrush), typeof(Brush), typeof(VayuToggleSwitch),
                new PropertyMetadata(new SolidColorBrush(Color.FromRgb(30, 41, 59))));

        public bool IsOn
        {
            get => (bool)GetValue(IsOnProperty);
            set => SetValue(IsOnProperty, value);
        }

        public string Header
        {
            get => (string)GetValue(HeaderProperty);
            set => SetValue(HeaderProperty, value);
        }

        public Brush OnBrush
        {
            get => (Brush)GetValue(OnBrushProperty);
            set => SetValue(OnBrushProperty, value);
        }

        public Brush OffBrush
        {
            get => (Brush)GetValue(OffBrushProperty);
            set => SetValue(OffBrushProperty, value);
        }

        static VayuToggleSwitch()
        {
            DefaultStyleKeyProperty.OverrideMetadata(typeof(VayuToggleSwitch), new FrameworkPropertyMetadata(typeof(VayuToggleSwitch)));
        }

        public VayuToggleSwitch()
        {
            Cursor = Cursors.Hand;
            Loaded += OnLoaded;
            MouseLeftButtonDown += OnMouseLeftButtonDown;
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            BuildVisualTree();
            UpdateVisualState(animate: false);
        }

        private void OnMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            IsOn = !IsOn;
            e.Handled = true;
        }

        private static void OnIsOnChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            if (d is VayuToggleSwitch sw)
            {
                sw.UpdateVisualState(animate: true);
            }
        }

        private void BuildVisualTree()
        {
            var grid = new Grid
            {
                Width = 44,
                Height = 24,
                HorizontalAlignment = HorizontalAlignment.Left,
                VerticalAlignment = VerticalAlignment.Center
            };

            _track = new Border
            {
                CornerRadius = new CornerRadius(12),
                Background = OffBrush,
                BorderBrush = new SolidColorBrush(Color.FromArgb(120, 59, 130, 246)),
                BorderThickness = new Thickness(1),
                Height = 24,
                Width = 44
            };

            _thumbTranslate = new TranslateTransform(2, 0);
            _thumb = new Ellipse
            {
                Width = 18,
                Height = 18,
                Fill = Brushes.White,
                HorizontalAlignment = HorizontalAlignment.Left,
                VerticalAlignment = VerticalAlignment.Center,
                RenderTransform = _thumbTranslate
            };

            grid.Children.Add(_track);
            grid.Children.Add(_thumb);

            AddVisualChild(grid);
            AddLogicalChild(grid);
        }

        private void UpdateVisualState(bool animate)
        {
            if (_track == null || _thumb == null || _thumbTranslate == null) return;

            double targetX = IsOn ? 22.0 : 2.0;
            var targetBrush = IsOn ? OnBrush : OffBrush;

            if (animate && AnimationEngine.IsAnimationEnabled)
            {
                var anim = new DoubleAnimation(targetX, new Duration(TimeSpan.FromMilliseconds(200)))
                {
                    EasingFunction = AnimationEngine.QuarticEaseOut
                };
                _thumbTranslate.BeginAnimation(TranslateTransform.XProperty, anim);
                _track.Background = targetBrush;
            }
            else
            {
                _thumbTranslate.BeginAnimation(TranslateTransform.XProperty, null);
                _thumbTranslate.X = targetX;
                _track.Background = targetBrush;
            }
        }

        protected override int VisualChildrenCount => 1;
        protected override Visual GetVisualChild(int index) => (Visual)LogicalChildren.Current;
    }
}
