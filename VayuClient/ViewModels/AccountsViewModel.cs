using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Account;
using VayuClient.Services.Authentication;
using VayuClient.Services.Profiles;

namespace VayuClient.ViewModels
{
    public partial class AccountsViewModel : ObservableObject, ILifecycleViewModel
    {
        private readonly MainViewModel _main;
        private readonly IAuthenticationService _authService;
        private readonly IProfileService _profileService;
        private readonly IAccountService _accountService;
        private CancellationTokenSource? _msAuthCts;
        private bool _disposed;

        [ObservableProperty]
        private string _newProfileUsername = string.Empty;

        [ObservableProperty]
        private bool _isCreatingProfile;

        [ObservableProperty]
        private string? _renamingProfileId;

        [ObservableProperty]
        private string _renameText = string.Empty;

        [ObservableProperty]
        private bool _isLoggingInMicrosoft;

        [ObservableProperty]
        private string _microsoftUserCode = string.Empty;

        [ObservableProperty]
        private string _microsoftAuthStatus = string.Empty;

        [ObservableProperty]
        private bool _hasOfflineProfiles;

        [ObservableProperty]
        private bool _hasMicrosoftAccounts;

        public ObservableCollection<UserProfile> OfflineProfiles { get; } = new();
        public ObservableCollection<UserProfile> MicrosoftAccounts { get; } = new();

        public AccountsViewModel(MainViewModel main)
        {
            _main = main;
            _authService = ServiceLocator.Resolve<IAuthenticationService>();
            _profileService = ServiceLocator.Resolve<IProfileService>();
            _accountService = ServiceLocator.Resolve<IAccountService>();

            LoadProfiles();
        }

        public Task InitializeAsync()
        {
            LoadProfiles();
            return Task.CompletedTask;
        }

        public void Activate()
        {
            LoadProfiles();
        }

        public void Deactivate()
        {
            if (IsLoggingInMicrosoft)
            {
                CancelMicrosoftLogin();
            }
            IsCreatingProfile = false;
            IsRenaming = false;
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
            CancelMicrosoftLogin();
        }

        private static void Dispatch(Action action)
        {
            var app = Application.Current;
            if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess() && !app.Dispatcher.HasShutdownStarted)
            {
                try { app.Dispatcher.BeginInvoke(action); } catch { action(); }
            }
            else
            {
                action();
            }
        }

        public void LoadProfiles()
        {
            try
            {
                var profiles = _profileService.GetAllProfiles();
                Dispatch(() =>
                {
                    OfflineProfiles.Clear();
                    MicrosoftAccounts.Clear();

                    foreach (var p in profiles)
                    {
                        if (p.AccountType == AccountType.Offline)
                            OfflineProfiles.Add(p);
                        else
                            MicrosoftAccounts.Add(p);
                    }

                    HasOfflineProfiles = OfflineProfiles.Count > 0;
                    HasMicrosoftAccounts = MicrosoftAccounts.Count > 0;
                });
            }
            catch { }
        }

        [RelayCommand]
        private void ShowCreateProfile()
        {
            IsCreatingProfile = true;
            NewProfileUsername = string.Empty;
        }

        [RelayCommand]
        private void CancelCreateProfile()
        {
            IsCreatingProfile = false;
            NewProfileUsername = string.Empty;
        }

        [RelayCommand]
        private void CreateProfile()
        {
            if (string.IsNullOrWhiteSpace(NewProfileUsername)) return;
            if (NewProfileUsername.Trim().Length < 3 || NewProfileUsername.Trim().Length > 16)
            {
                _main.ShowNotification("Invalid Username",
                    "Username must be between 3 and 16 characters.",
                    NotificationType.Warning);
                return;
            }

            try
            {
                string cleanName = NewProfileUsername.Trim();
                var existing = _profileService.GetAllProfiles().FirstOrDefault(p => string.Equals(p.Username, cleanName, StringComparison.OrdinalIgnoreCase));
                if (existing != null)
                {
                    _accountService.SetActiveProfile(existing.Id);
                    LoadProfiles();
                    IsCreatingProfile = false;
                    NewProfileUsername = string.Empty;
                    _main.ShowNotification("Account Switched", $"Account '{cleanName}' already exists. Switched to it.", NotificationType.Info);
                    return;
                }

                var profile = _profileService.CreateOfflineProfile(cleanName);

                // Auto-select profile
                _accountService.SetActiveProfile(profile.Id);

                LoadProfiles();
                IsCreatingProfile = false;
                NewProfileUsername = string.Empty;

                _main.ShowNotification("Profile Created",
                    $"Offline profile '{profile.Username}' has been created.",
                    NotificationType.Success);
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Error", ex.Message, NotificationType.Error);
            }
        }

        [ObservableProperty]
        private bool _isRenaming;

        [RelayCommand]
        private void SelectProfile(object? profileIdObj)
        {
            var profileId = profileIdObj?.ToString();
            if (string.IsNullOrEmpty(profileId)) return;

            try
            {
                _accountService.SetActiveProfile(profileId);
                LoadProfiles();

                var selected = OfflineProfiles.FirstOrDefault(p => p.Id == profileId) ?? MicrosoftAccounts.FirstOrDefault(p => p.Id == profileId);
                var name = selected?.Username ?? "Selected Profile";

                _main.ShowNotification("Profile Selected",
                    $"Now playing as '{name}'.",
                    NotificationType.Success);
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Selection Error", ex.Message, NotificationType.Error);
            }
        }

        [RelayCommand]
        private void StartRename(object? profileIdObj)
        {
            var profileId = profileIdObj?.ToString();
            if (string.IsNullOrEmpty(profileId)) return;

            RenamingProfileId = profileId;
            var profile = OfflineProfiles.FirstOrDefault(p => p.Id == profileId);
            RenameText = profile?.Username ?? string.Empty;
            IsRenaming = true;
        }

        [RelayCommand]
        private void ConfirmRename()
        {
            if (string.IsNullOrWhiteSpace(RenamingProfileId))
            {
                IsRenaming = false;
                return;
            }
            if (string.IsNullOrWhiteSpace(RenameText) || RenameText.Trim().Length < 3 || RenameText.Trim().Length > 16)
            {
                _main.ShowNotification("Invalid Username", "Username must be between 3 and 16 characters.", NotificationType.Warning);
                return;
            }

            try
            {
                _profileService.RenameProfile(RenamingProfileId, RenameText.Trim());
                LoadProfiles();
                RenamingProfileId = null;
                IsRenaming = false;
                _main.ShowNotification("Profile Renamed", $"Profile has been renamed to '{RenameText.Trim()}'.", NotificationType.Success);
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Rename Error", ex.Message, NotificationType.Error);
            }
        }

        [RelayCommand]
        private void CancelRename()
        {
            RenamingProfileId = null;
            IsRenaming = false;
            RenameText = string.Empty;
        }

        [RelayCommand]
        private void DeleteProfile(object? profileIdObj)
        {
            var profileId = profileIdObj?.ToString();
            if (string.IsNullOrEmpty(profileId)) return;

            try
            {
                _profileService.DeleteProfile(profileId);
                LoadProfiles();
                _main.ShowNotification("Deleted", "Profile has been deleted.", NotificationType.Info);

                var active = _accountService.ActiveProfile;
                if (active == null)
                {
                    var first = _profileService.GetAllProfiles().FirstOrDefault();
                    if (first != null)
                    {
                        _accountService.SetActiveProfile(first.Id);
                        LoadProfiles();
                    }
                }
            }
            catch { }
        }

        [RelayCommand]
        private async Task SignInMicrosoftAsync()
        {
            _msAuthCts?.Cancel();
            _msAuthCts = new CancellationTokenSource();

            IsLoggingInMicrosoft = true;
            MicrosoftAuthStatus = "Requesting device code from Microsoft...";

            try
            {
                var devCode = await _authService.BeginDeviceCodeLoginAsync(_msAuthCts.Token);
                MicrosoftUserCode = devCode.UserCode;
                MicrosoftAuthStatus = $"Code copied! Opening {devCode.VerificationUri}...";

                try
                {
                    Clipboard.SetText(devCode.UserCode);
                }
                catch { }

                try
                {
                    Process.Start(new ProcessStartInfo(devCode.VerificationUri) { UseShellExecute = true });
                }
                catch { }

                var progress = new Progress<string>(s =>
                {
                    MicrosoftAuthStatus = s;
                });

                var profile = await _authService.CompleteDeviceCodeLoginAsync(devCode, progress, _msAuthCts.Token);
                LoadProfiles();
                IsLoggingInMicrosoft = false;

                _main.ShowNotification("Microsoft Login Successful", $"Signed in as {profile.Username}!", NotificationType.Success);
            }
            catch (OperationCanceledException)
            {
                IsLoggingInMicrosoft = false;
            }
            catch (Exception ex)
            {
                IsLoggingInMicrosoft = false;
                CrashLogger.LogMessage($"[MicrosoftAuth]: Sign-in failed: {ex.Message}");
                var userMsg = ex.Message;
                if (userMsg.Length > 180)
                {
                    userMsg = userMsg.Substring(0, 180) + "...";
                }
                _main.ShowNotification("Microsoft Login Failed", userMsg, NotificationType.Error);
            }
        }

        [RelayCommand]
        private void CopyUserCode()
        {
            if (!string.IsNullOrEmpty(MicrosoftUserCode))
            {
                try
                {
                    Clipboard.SetText(MicrosoftUserCode);
                    _main.ShowNotification("Copied", "Device code copied to clipboard!", NotificationType.Success);
                }
                catch { }
            }
        }

        [RelayCommand]
        private void OpenVerificationLink()
        {
            try
            {
                Process.Start(new ProcessStartInfo("https://microsoft.com/link") { UseShellExecute = true });
            }
            catch { }
        }

        [RelayCommand]
        private void CancelMicrosoftLogin()
        {
            _msAuthCts?.Cancel();
            IsLoggingInMicrosoft = false;
            MicrosoftUserCode = string.Empty;
            MicrosoftAuthStatus = string.Empty;
        }
    }
}
