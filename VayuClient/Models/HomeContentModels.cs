using System;
using System.Windows.Input;
using CommunityToolkit.Mvvm.ComponentModel;

namespace VayuClient.Models
{
    public class HomeCardItem
    {
        public string Title { get; set; } = string.Empty;
        public string Description { get; set; } = string.Empty;
        public string Category { get; set; } = "NEWS";
        public string BadgeColor { get; set; } = "#38BDF8";
        public string ImagePath { get; set; } = "/Assets/Images/vayu_minecraft_hero.jpg";
        public string DateTag { get; set; } = "Recently";
        public string ActionText { get; set; } = "Learn More";
        public string ActionUrl { get; set; } = string.Empty;
        public string NavigationTarget { get; set; } = string.Empty;
    }

    public class PartneredServerCard : ObservableObject
    {
        private string _name = string.Empty;
        private string _address = string.Empty;
        private string _description = string.Empty;
        private string _versionRange = "1.8 - 26.2";
        private string _iconPath = "/Assets/Images/vayu_logo.png";
        private bool _isOnline = false;
        private int _onlinePlayers = 0;
        private int _maxPlayers = 0;
        private int _latencyMs = 0;
        private string _playerCountDisplay = "Pinging server...";
        private string _statusDisplay = "Checking...";

        public string Name
        {
            get => _name;
            set => SetProperty(ref _name, value);
        }

        public string Address
        {
            get => _address;
            set => SetProperty(ref _address, value);
        }

        public string Description
        {
            get => _description;
            set => SetProperty(ref _description, value);
        }

        public string VersionRange
        {
            get => _versionRange;
            set => SetProperty(ref _versionRange, value);
        }

        public string IconPath
        {
            get => _iconPath;
            set => SetProperty(ref _iconPath, value);
        }

        public bool IsOnline
        {
            get => _isOnline;
            set => SetProperty(ref _isOnline, value);
        }

        public int OnlinePlayers
        {
            get => _onlinePlayers;
            set => SetProperty(ref _onlinePlayers, value);
        }

        public int MaxPlayers
        {
            get => _maxPlayers;
            set => SetProperty(ref _maxPlayers, value);
        }

        public int LatencyMs
        {
            get => _latencyMs;
            set => SetProperty(ref _latencyMs, value);
        }

        public string PlayerCountDisplay
        {
            get => _playerCountDisplay;
            set => SetProperty(ref _playerCountDisplay, value);
        }

        public string StatusDisplay
        {
            get => _statusDisplay;
            set => SetProperty(ref _statusDisplay, value);
        }
    }
}
