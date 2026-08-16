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
    /// Data model for a glass notification.
    /// </summary>
    public class NotificationInfo
    {
        public string Id { get; set; } = Guid.NewGuid().ToString();
        public string Title { get; set; } = string.Empty;
        public string Message { get; set; } = string.Empty;
        public NotificationType Type { get; set; } = NotificationType.Info;
        public double AutoDismissSeconds { get; set; } = 4.0;
        public DateTime CreatedAt { get; set; } = DateTime.Now;
    }
}
