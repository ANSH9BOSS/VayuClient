using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using VayuClient.ViewModels;

namespace VayuClient.Views
{
    public partial class HomePage : UserControl
    {
        private bool _isDragging;
        private Point _startMousePoint;
        private double _startHandleX;
        private bool _isPrimaryVisible = true;
        private HomeViewModel? _subscribedVm;

        public HomePage()
        {
            InitializeComponent();
            DataContextChanged += HomePage_DataContextChanged;
            Unloaded += HomePage_Unloaded;
        }

        private void HomePage_DataContextChanged(object sender, DependencyPropertyChangedEventArgs e)
        {
            if (_subscribedVm != null)
            {
                _subscribedVm.WallpaperTransitionRequested -= OnWallpaperTransitionRequested;
                _subscribedVm = null;
            }

            if (DataContext is HomeViewModel vm)
            {
                _subscribedVm = vm;
                _subscribedVm.WallpaperTransitionRequested += OnWallpaperTransitionRequested;
            }
        }

        private void HomePage_Unloaded(object sender, RoutedEventArgs e)
        {
            if (_subscribedVm != null)
            {
                _subscribedVm.WallpaperTransitionRequested -= OnWallpaperTransitionRequested;
                _subscribedVm = null;
            }
        }

        private void HomePage_Loaded(object sender, RoutedEventArgs e)
        {
            ResetDragHandle(animated: false);
            if (_subscribedVm == null && DataContext is HomeViewModel vm)
            {
                _subscribedVm = vm;
                _subscribedVm.WallpaperTransitionRequested += OnWallpaperTransitionRequested;
            }
        }

        private void OnWallpaperTransitionRequested(string newPath)
        {
            try
            {
                var targetImage = _isPrimaryVisible ? BgImageSecondary : BgImagePrimary;
                var currentImage = _isPrimaryVisible ? BgImagePrimary : BgImageSecondary;

                if (targetImage == null || currentImage == null) return;

                var bmp = new BitmapImage();
                bmp.BeginInit();
                bmp.UriSource = new Uri(newPath, UriKind.RelativeOrAbsolute);
                bmp.CacheOption = BitmapCacheOption.OnLoad;
                bmp.EndInit();

                targetImage.Source = bmp;

                var fadeIn = new DoubleAnimation(0.55, TimeSpan.FromMilliseconds(700))
                {
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseInOut }
                };
                var fadeOut = new DoubleAnimation(0.0, TimeSpan.FromMilliseconds(700))
                {
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseInOut }
                };

                targetImage.BeginAnimation(UIElement.OpacityProperty, fadeIn);
                currentImage.BeginAnimation(UIElement.OpacityProperty, fadeOut);

                _isPrimaryVisible = !_isPrimaryVisible;
            }
            catch { }
        }

        private void PlayHandle_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.LeftButton != MouseButtonState.Pressed) return;

            // Check if ViewModel is already busy
            if (DataContext is HomeViewModel vm && vm.IsBusy) return;

            _isDragging = true;
            _startMousePoint = e.GetPosition(PlayTrackBorder);
            _startHandleX = HandleTranslate.X;

            PlayDragHandle.CaptureMouse();
            e.Handled = true;
        }

        private void PlayHandle_MouseMove(object sender, MouseEventArgs e)
        {
            if (!_isDragging) return;

            Point currentPoint = e.GetPosition(PlayTrackBorder);
            double deltaX = currentPoint.X - _startMousePoint.X;
            double newX = Math.Max(0, _startHandleX + deltaX);

            double trackWidth = PlayTrackBorder.ActualWidth > 0 ? PlayTrackBorder.ActualWidth : 480;
            double handleWidth = PlayDragHandle.ActualWidth > 0 ? PlayDragHandle.ActualWidth : 48;
            double maxDragDistance = Math.Max(100, trackWidth - handleWidth - 8);

            newX = Math.Min(newX, maxDragDistance);

            HandleTranslate.X = newX;
            DragProgressFill.Width = newX + handleWidth;

            // Fade prompt text as handle slides across
            if (DragPromptText != null)
            {
                double promptFade = Math.Max(0.0, 1.0 - (newX / (maxDragDistance * 0.45)));
                DragPromptText.Opacity = promptFade;
            }

            // If reached 88% of the drag track, automatically trigger launch
            if (newX >= maxDragDistance * 0.88)
            {
                _isDragging = false;
                PlayDragHandle.ReleaseMouseCapture();

                TriggerLaunch();
            }

            e.Handled = true;
        }

        private void PlayHandle_MouseUp(object sender, MouseButtonEventArgs e)
        {
            if (!_isDragging) return;

            _isDragging = false;
            PlayDragHandle.ReleaseMouseCapture();

            double trackWidth = PlayTrackBorder.ActualWidth > 0 ? PlayTrackBorder.ActualWidth : 480;
            double handleWidth = PlayDragHandle.ActualWidth > 0 ? PlayDragHandle.ActualWidth : 48;
            double maxDragDistance = Math.Max(100, trackWidth - handleWidth - 8);

            if (HandleTranslate.X >= maxDragDistance * 0.85)
            {
                TriggerLaunch();
            }
            else
            {
                // Smooth spring-back to resting 0 position
                ResetDragHandle(animated: true);
            }

            e.Handled = true;
        }

        private void TriggerLaunch()
        {
            ResetDragHandle(animated: false);

            if (DataContext is HomeViewModel vm && vm.PlayCommand.CanExecute(null))
            {
                vm.PlayCommand.Execute(null);
            }
        }

        private void ResetDragHandle(bool animated)
        {
            if (animated)
            {
                var handleAnim = new DoubleAnimation(0, TimeSpan.FromMilliseconds(220))
                {
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                };
                HandleTranslate.BeginAnimation(System.Windows.Media.TranslateTransform.XProperty, handleAnim);

                var fillAnim = new DoubleAnimation(0, TimeSpan.FromMilliseconds(220))
                {
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                };
                DragProgressFill.BeginAnimation(FrameworkElement.WidthProperty, fillAnim);

                if (DragPromptText != null)
                {
                    var textAnim = new DoubleAnimation(1.0, TimeSpan.FromMilliseconds(220));
                    DragPromptText.BeginAnimation(UIElement.OpacityProperty, textAnim);
                }
            }
            else
            {
                HandleTranslate.BeginAnimation(System.Windows.Media.TranslateTransform.XProperty, null);
                DragProgressFill.BeginAnimation(FrameworkElement.WidthProperty, null);

                HandleTranslate.X = 0;
                DragProgressFill.Width = 0;

                if (DragPromptText != null)
                {
                    DragPromptText.BeginAnimation(UIElement.OpacityProperty, null);
                    DragPromptText.Opacity = 1.0;
                }
            }
        }
    }
}
