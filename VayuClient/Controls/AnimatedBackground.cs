using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;

namespace VayuClient.Controls
{
    /// <summary>
    /// Lightweight, zero-overhead static Dark Navy ambient background canvas.
    /// Free of continuous CompositionTarget rendering loops for maximum launcher performance.
    /// </summary>
    public class AnimatedBackground : Canvas
    {
        public AnimatedBackground()
        {
            ClipToBounds = true;
            Background = Brushes.Transparent;
            IsHitTestVisible = false;
            Loaded += OnLoaded;
        }

        public void Pause() { }
        public void Resume() { }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            Children.Clear();

            // Subtle top-left soft blue ambient glow (static vector, 0% CPU overhead)
            var glow1 = new Ellipse
            {
                Width = 500,
                Height = 500,
                IsHitTestVisible = false,
                Opacity = 0.06,
                Fill = new RadialGradientBrush
                {
                    GradientStops = new GradientStopCollection
                    {
                        new GradientStop(Color.FromRgb(37, 99, 235), 0.0),
                        new GradientStop(Color.FromArgb(0, 37, 99, 235), 1.0)
                    }
                }
            };
            SetLeft(glow1, -100);
            SetTop(glow1, -100);
            Children.Add(glow1);

            // Subtle bottom-right soft blue ambient glow
            var glow2 = new Ellipse
            {
                Width = 600,
                Height = 600,
                IsHitTestVisible = false,
                Opacity = 0.04,
                Fill = new RadialGradientBrush
                {
                    GradientStops = new GradientStopCollection
                    {
                        new GradientStop(Color.FromRgb(59, 130, 246), 0.0),
                        new GradientStop(Color.FromArgb(0, 59, 130, 246), 1.0)
                    }
                }
            };
            SetRight(glow2, -150);
            SetBottom(glow2, -150);
            Children.Add(glow2);
        }
    }
}
