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
using VayuClient.Services.Settings;

namespace VayuClient.ViewModels
{
    public partial class OnboardingViewModel : ObservableObject
    {
        private readonly MainViewModel _main;
        private readonly IAuthenticationService _authService;
        private readonly IProfileService _profileService;
        private readonly IAccountService _accountService;
        private readonly ISettingsService _settingsService;
        private CancellationTokenSource? _msAuthCts;

        [ObservableProperty]
        private string _offlineUsername = string.Empty;

        [ObservableProperty]
        private bool _isLoggingInMicrosoft;

        [ObservableProperty]
        private string _microsoftUserCode = string.Empty;

        [ObservableProperty]
        private string _microsoftAuthStatus = string.Empty;

        [ObservableProperty]
        private bool _hasExistingProfiles;

        [ObservableProperty]
        private UserProfile? _selectedExistingProfile;

        [ObservableProperty]
        private string _statusMessage = string.Empty;

        public ObservableCollection<UserProfile> ExistingProfiles { get; } = new();

        public OnboardingViewModel(MainViewModel main)
        {
            _main = main;
            _authService = ServiceLocator.Resolve<IAuthenticationService>();
            _profileService = ServiceLocator.Resolve<IProfileService>();
            _accountService = ServiceLocator.Resolve<IAccountService>();
            _settingsService = ServiceLocator.Resolve<ISettingsService>();

            LoadExistingProfiles();
        }

        public void LoadExistingProfiles()
        {
            try
            {
                var profiles = _profileService.GetAllProfiles();
                ExistingProfiles.Clear();
                foreach (var p in profiles)
                {
                    ExistingProfiles.Add(p);
                }

                HasExistingProfiles = ExistingProfiles.Count > 0;
                if (HasExistingProfiles && SelectedExistingProfile == null)
                {
                    SelectedExistingProfile = _accountService.ActiveProfile ?? ExistingProfiles.FirstOrDefault();
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("OnboardingViewModel.LoadExistingProfiles", ex);
            }
        }

        [RelayCommand]
        public async Task StartMicrosoftLoginAsync()
        {
            if (IsLoggingInMicrosoft) return;

            IsLoggingInMicrosoft = true;
            MicrosoftAuthStatus = "Requesting Microsoft authorization code...";
            MicrosoftUserCode = string.Empty;
            _msAuthCts = new CancellationTokenSource();

            try
            {
                var deviceCodeRes = await _authService.BeginDeviceCodeLoginAsync(_msAuthCts.Token);
                if (deviceCodeRes == null)
                {
                    MicrosoftAuthStatus = "Failed to obtain device code from Microsoft.";
                    IsLoggingInMicrosoft = false;
                    return;
                }

                MicrosoftUserCode = deviceCodeRes.UserCode;
                MicrosoftAuthStatus = $"Code: {deviceCodeRes.UserCode} (Copied to Clipboard!)";

                try
                {
                    Clipboard.SetText(deviceCodeRes.UserCode);
                }
                catch { }

                try
                {
                    Process.Start(new ProcessStartInfo(deviceCodeRes.VerificationUri) { UseShellExecute = true });
                }
                catch { }

                var profile = await _authService.CompleteDeviceCodeLoginAsync(deviceCodeRes, null, _msAuthCts.Token);
                if (profile != null)
                {
                    _accountService.SetActiveProfile(profile.Id);
                    MicrosoftAuthStatus = $"Welcome, {profile.Username}!";
                    _main.ShowNotification("Account Linked", $"Signed in as {profile.Username}", NotificationType.Success);
                    
                    await Task.Delay(800);
                    CompleteOnboarding();
                }
                else
                {
                    MicrosoftAuthStatus = "Authentication cancelled or timed out.";
                }
            }
            catch (OperationCanceledException)
            {
                MicrosoftAuthStatus = "Login cancelled.";
            }
            catch (Exception ex)
            {
                MicrosoftAuthStatus = $"Login error: {ex.Message}";
                CrashLogger.LogException("OnboardingViewModel.MicrosoftLogin", ex);
            }
            finally
            {
                IsLoggingInMicrosoft = false;
            }
        }

        [RelayCommand]
        public void CancelMicrosoftLogin()
        {
            _msAuthCts?.Cancel();
            _msAuthCts?.Dispose();
            _msAuthCts = null;
            IsLoggingInMicrosoft = false;
            MicrosoftUserCode = string.Empty;
            MicrosoftAuthStatus = string.Empty;
        }

        [RelayCommand]
        public void CreateOfflineAccount()
        {
            if (string.IsNullOrWhiteSpace(OfflineUsername))
            {
                StatusMessage = "Please enter a valid player username.";
                return;
            }

            string cleanName = OfflineUsername.Trim();
            if (cleanName.Length < 3 || cleanName.Length > 16)
            {
                StatusMessage = "Username must be between 3 and 16 characters.";
                return;
            }

            try
            {
                var existing = _profileService.GetAllProfiles().FirstOrDefault(p => string.Equals(p.Username, cleanName, StringComparison.OrdinalIgnoreCase));
                UserProfile profile;
                if (existing != null)
                {
                    profile = existing;
                    _accountService.SetActiveProfile(profile.Id);
                }
                else
                {
                    profile = _profileService.CreateOfflineProfile(cleanName);
                    _accountService.SetActiveProfile(profile.Id);
                }

                _main.ShowNotification("Account Ready", $"Playing as {profile.Username}", NotificationType.Success);
                CompleteOnboarding();
            }
            catch (Exception ex)
            {
                StatusMessage = $"Error: {ex.Message}";
                CrashLogger.LogException("OnboardingViewModel.CreateOfflineAccount", ex);
            }
        }

        [RelayCommand]
        public void SelectExistingAccountAndContinue()
        {
            if (SelectedExistingProfile != null)
            {
                _accountService.SetActiveProfile(SelectedExistingProfile.Id);
                CompleteOnboarding();
            }
            else
            {
                StatusMessage = "Please select an account or create a new one.";
            }
        }

        [RelayCommand]
        public void CompleteOnboarding()
        {
            try
            {
                var settings = _settingsService.Settings;
                settings.HasCompletedOnboarding = true;
                _settingsService.SaveSettingsSync(settings);
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("OnboardingViewModel.CompleteOnboarding", ex);
            }

            _main.FinishOnboarding();
        }
    }
}
