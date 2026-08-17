using System;
using CommunityToolkit.Mvvm.ComponentModel;

namespace VayuClient.Models
{
    /// <summary>
    /// Notification severity levels, each with distinct accent colors.
    /// </summary>
    public enum NotificationType
    {
        Info,
        Success,
        Warning,
        Error
    }

    /// <summary>
    /// Data model for a dynamic glass notification.
    /// </summary>
    public class NotificationInfo : ObservableObject
    {
        public string Id { get; set; } = Guid.NewGuid().ToString();
        public string Tag { get; set; } = string.Empty;

        private string _title = string.Empty;
        public string Title
        {
            get => _title;
            set => SetProperty(ref _title, value);
        }

        private string _message = string.Empty;
        public string Message
        {
            get => _message;
            set => SetProperty(ref _message, value);
        }

        private NotificationType _type = NotificationType.Info;
        public NotificationType Type
        {
            get => _type;
            set => SetProperty(ref _type, value);
        }

        public double AutoDismissSeconds { get; set; } = 4.0;
        public DateTime CreatedAt { get; set; } = DateTime.Now;
    }
}
