using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;

namespace VayuClient.Controls
{
    /// <summary>
    /// Lightweight, zero-overhead static Obsidian and Vayu-purple ambient background canvas.
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

            // Static purple ambient lighting keeps the shell connected to the Vayu visual system
            // without an idle render loop or costly image overlays.
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
                        new GradientStop(Color.FromRgb(124, 58, 237), 0.0),
                        new GradientStop(Color.FromArgb(0, 124, 58, 237), 1.0)
                    }
                }
            };
            SetLeft(glow1, -100);
            SetTop(glow1, -100);
            Children.Add(glow1);

            // A quieter counter-light adds material separation without overpowering content.
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
                        new GradientStop(Color.FromRgb(168, 85, 247), 0.0),
                        new GradientStop(Color.FromArgb(0, 168, 85, 247), 1.0)
                    }
                }
            };
            SetRight(glow2, -150);
            SetBottom(glow2, -150);
            Children.Add(glow2);
        }
    }
}
