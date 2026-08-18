using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Linq;
using System.Text.RegularExpressions;
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
    public enum OnboardingUiState
    {
        Welcome,
        Authenticating,
        Authenticated,
        CreatingProfile,
        ProfileSelected,
        EnteringHome,
        AuthError
    }

    public partial class OnboardingViewModel : ObservableObject
    {
        private static readonly Regex ValidUsernameRegex = new(@"^[a-zA-Z0-9_]{3,16}$", RegexOptions.Compiled);

        private readonly MainViewModel _main;
        private readonly IAuthenticationService _authService;
        private readonly IProfileService _profileService;
        private readonly IAccountService _accountService;
        private readonly ISettingsService _settingsService;
        private CancellationTokenSource? _msAuthCts;

        [ObservableProperty]
        private OnboardingUiState _uiState = OnboardingUiState.Welcome;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(IsMicrosoftLoginAvailable))]
        [NotifyPropertyChangedFor(nameof(IsOfflineAccountAvailable))]
        [NotifyPropertyChangedFor(nameof(CanContinueWithProfile))]
        private bool _isBusy;

        [ObservableProperty]
        private string _offlineUsername = string.Empty;

        [ObservableProperty]
        private string _offlineValidationMessage = string.Empty;

        [ObservableProperty]
        private bool _isLoggingInMicrosoft;

        [ObservableProperty]
        private string _microsoftUserCode = string.Empty;

        [ObservableProperty]
        private string _microsoftAuthStatus = string.Empty;

        [ObservableProperty]
        private string _microsoftVerificationUri = "https://microsoft.com/link";

        [ObservableProperty]
        private UserProfile? _authenticatedProfile;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(HasNoExistingProfiles))]
        private bool _hasExistingProfiles;

        public bool HasNoExistingProfiles => !HasExistingProfiles;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(CanContinueWithProfile))]
        private UserProfile? _selectedExistingProfile;

        [ObservableProperty]
        private string _statusMessage = string.Empty;

        [ObservableProperty]
        private string _microsoftButtonText = "🎮  Login with Microsoft";

        [ObservableProperty]
        private string _offlineButtonText = "▶  Play with Local Account";

        [ObservableProperty]
        private string _continueButtonText = "Continue with Selected Profile →";

        public bool IsMicrosoftLoginAvailable => !IsBusy && !IsLoggingInMicrosoft;
        public bool IsOfflineAccountAvailable => !IsBusy && !IsLoggingInMicrosoft;
        public bool CanContinueWithProfile => !IsBusy && SelectedExistingProfile != null && HasExistingProfiles;

        public ObservableCollection<UserProfile> ExistingProfiles { get; } = new();

        public OnboardingViewModel(MainViewModel main)
        {
            _main = main;
            _authService = ServiceLocator.Resolve<IAuthenticationService>();
            _profileService = ServiceLocator.Resolve<IProfileService>();
            _accountService = ServiceLocator.Resolve<IAccountService>();
            _settingsService = ServiceLocator.Resolve<ISettingsService>();

            CrashLogger.LogMessage("[FIRST_LAUNCH] Initializing Welcome / Onboarding Screen ViewModel");
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
                if (HasExistingProfiles)
                {
                    SelectedExistingProfile = _accountService.ActiveProfile ?? ExistingProfiles.FirstOrDefault();
                    CrashLogger.LogMessage($"[PROFILE] Loaded {ExistingProfiles.Count} existing profile(s). Default selection: {SelectedExistingProfile?.Username}");
                }
                else
                {
                    SelectedExistingProfile = null;
                    CrashLogger.LogMessage("[PROFILE] No saved profiles detected on startup.");
                }

                OnPropertyChanged(nameof(CanContinueWithProfile));
                OnPropertyChanged(nameof(HasNoExistingProfiles));
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("OnboardingViewModel.LoadExistingProfiles", ex);
            }
        }

        partial void OnSelectedExistingProfileChanged(UserProfile? value)
        {
            OnPropertyChanged(nameof(CanContinueWithProfile));
            if (value != null)
            {
                StatusMessage = string.Empty;
            }
        }

        partial void OnOfflineUsernameChanged(string value)
        {
            OfflineValidationMessage = string.Empty;
            StatusMessage = string.Empty;
        }

        [RelayCommand]
        public async Task StartMicrosoftLoginAsync()
        {
            if (IsBusy || IsLoggingInMicrosoft) return;

            IsBusy = true;
            IsLoggingInMicrosoft = true;
            UiState = OnboardingUiState.Authenticating;
            MicrosoftButtonText = "Signing in...";
            MicrosoftAuthStatus = "Connecting to Microsoft identity service...";
            MicrosoftUserCode = string.Empty;
            StatusMessage = string.Empty;

            _msAuthCts?.Cancel();
            _msAuthCts?.Dispose();
            _msAuthCts = new CancellationTokenSource();

            CrashLogger.LogMessage("[AUTH] Microsoft login initiated via MSAL.NET desktop application");

            try
            {
                var progressReporter = new Progress<string>(msg =>
                {
                    MicrosoftAuthStatus = msg;
                    CrashLogger.LogMessage($"[AUTH] {msg}");
                });

                var profile = await _authService.LoginInteractiveAsync(progressReporter, _msAuthCts.Token);
                if (profile != null && !string.IsNullOrWhiteSpace(profile.Username))
                {
                    AuthenticatedProfile = profile;
                    _accountService.SetActiveProfile(profile.Id);
                    UiState = OnboardingUiState.Authenticated;
                    MicrosoftButtonText = "Authenticated ✓";
                    MicrosoftAuthStatus = $"Welcome, {profile.Username}! Profile linked.";

                    CrashLogger.LogMessage($"[AUTH] Minecraft Java profile resolved: {profile.Username} ({profile.Uuid})");
                    CrashLogger.LogMessage($"[PROFILE] Active profile changed to {profile.Username}");

                    _main.ShowNotification("Account Linked", $"Signed in as {profile.Username}", NotificationType.Success);

                    LoadExistingProfiles();
                    SelectedExistingProfile = profile;

                    await Task.Delay(1000);
                    CompleteOnboarding();
                }
                else
                {
                    throw new InvalidOperationException("No Minecraft Java Edition profile found on this Microsoft account.");
                }
            }
            catch (OperationCanceledException)
            {
                UiState = OnboardingUiState.AuthError;
                MicrosoftAuthStatus = "Sign-in was cancelled.";
                StatusMessage = "Microsoft sign-in cancelled.";
                CrashLogger.LogMessage("[AUTH] Microsoft login cancelled by user.");
            }
            catch (TimeoutException tex)
            {
                UiState = OnboardingUiState.AuthError;
                MicrosoftAuthStatus = "Authorization timed out. Please try again.";
                StatusMessage = tex.Message;
                CrashLogger.LogMessage($"[AUTH] Microsoft login timed out: {tex.Message}");
            }
            catch (Exception ex)
            {
                UiState = OnboardingUiState.AuthError;
                MicrosoftAuthStatus = $"Sign-in error: {ex.Message}";
                StatusMessage = ex.Message;
                CrashLogger.LogException("OnboardingViewModel.MicrosoftLogin", ex);
            }
            finally
            {
                IsLoggingInMicrosoft = false;
                IsBusy = false;
                if (UiState != OnboardingUiState.Authenticated && UiState != OnboardingUiState.EnteringHome)
                {
                    MicrosoftButtonText = "🎮  Login with Microsoft";
                }
            }
        }

        [RelayCommand]
        public void CancelMicrosoftLogin()
        {
            CrashLogger.LogMessage("[AUTH] User requested Microsoft login cancellation");
            try
            {
                _msAuthCts?.Cancel();
                _msAuthCts?.Dispose();
            }
            catch { }
            finally
            {
                _msAuthCts = null;
                IsLoggingInMicrosoft = false;
                IsBusy = false;
                MicrosoftUserCode = string.Empty;
                MicrosoftAuthStatus = string.Empty;
                MicrosoftButtonText = "🎮  Login with Microsoft";
                UiState = OnboardingUiState.Welcome;
            }
        }

        [RelayCommand]
        public void RetryAfterError()
        {
            StatusMessage = string.Empty;
            MicrosoftAuthStatus = string.Empty;
            MicrosoftUserCode = string.Empty;
            UiState = OnboardingUiState.Welcome;
            IsBusy = false;
            IsLoggingInMicrosoft = false;
            MicrosoftButtonText = "🎮  Login with Microsoft";
            OfflineButtonText = "▶  Play with Local Account";
            ContinueButtonText = "Continue with Selected Profile →";
        }

        [RelayCommand]
        public async Task CreateOfflineAccountAsync()
        {
            if (IsBusy) return;

            OfflineValidationMessage = string.Empty;
            StatusMessage = string.Empty;

            if (string.IsNullOrWhiteSpace(OfflineUsername))
            {
                OfflineValidationMessage = "Please enter a player username.";
                return;
            }

            string cleanName = OfflineUsername.Trim();
            if (cleanName.Length < 3)
            {
                OfflineValidationMessage = "Username must be at least 3 characters.";
                return;
            }
            if (cleanName.Length > 16)
            {
                OfflineValidationMessage = "Username cannot exceed 16 characters.";
                return;
            }
            if (!ValidUsernameRegex.IsMatch(cleanName))
            {
                OfflineValidationMessage = "Username can only contain letters, numbers, and underscores.";
                return;
            }

            IsBusy = true;
            UiState = OnboardingUiState.CreatingProfile;
            OfflineButtonText = "Creating profile...";

            try
            {
                await Task.Run(() =>
                {
                    CrashLogger.LogMessage($"[PROFILE] Creating/Selecting offline profile for '{cleanName}'");
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
                    AuthenticatedProfile = profile;
                });

                OfflineButtonText = "Profile Ready ✓";
                _main.ShowNotification("Account Ready", $"Playing as {cleanName}", NotificationType.Success);
                CrashLogger.LogMessage($"[PROFILE] Offline profile ready for {cleanName}. Transitioning to Home.");

                await Task.Delay(400);
                CompleteOnboarding();
            }
            catch (Exception ex)
            {
                UiState = OnboardingUiState.AuthError;
                StatusMessage = $"Failed to create offline profile: {ex.Message}";
                CrashLogger.LogException("OnboardingViewModel.CreateOfflineAccount", ex);
            }
            finally
            {
                IsBusy = false;
                if (UiState != OnboardingUiState.EnteringHome)
                {
                    OfflineButtonText = "▶  Play with Local Account";
                }
            }
        }

        [RelayCommand]
        public async Task SelectExistingAccountAndContinueAsync()
        {
            if (IsBusy) return;

            if (SelectedExistingProfile == null)
            {
                StatusMessage = "Please select a profile from the dropdown list.";
                return;
            }

            var profile = _profileService.GetProfile(SelectedExistingProfile.Id);
            if (profile == null)
            {
                StatusMessage = "Selected profile was not found. Please select or create another profile.";
                LoadExistingProfiles();
                return;
            }

            IsBusy = true;
            UiState = OnboardingUiState.EnteringHome;
            ContinueButtonText = "Loading profile...";

            try
            {
                CrashLogger.LogMessage($"[PROFILE] Continuing with existing profile: {profile.Username} ({profile.AccountType})");
                _accountService.SetActiveProfile(profile.Id);
                _main.ShowNotification("Profile Selected", $"Welcome back, {profile.Username}!", NotificationType.Success);

                await Task.Delay(300);
                CompleteOnboarding();
            }
            catch (Exception ex)
            {
                UiState = OnboardingUiState.AuthError;
                StatusMessage = $"Error switching profile: {ex.Message}";
                CrashLogger.LogException("OnboardingViewModel.SelectExistingAccountAndContinue", ex);
            }
            finally
            {
                IsBusy = false;
                ContinueButtonText = "Continue with Selected Profile →";
            }
        }

        [RelayCommand]
        public void CompleteOnboarding()
        {
            try
            {
                CrashLogger.LogMessage("[NAVIGATION] Welcome screen completing. Persisting HasCompletedOnboarding = true.");
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

        [RelayCommand]
        public void CopyUserCode()
        {
            if (!string.IsNullOrEmpty(MicrosoftUserCode))
            {
                try
                {
                    Clipboard.SetText(MicrosoftUserCode);
                    MicrosoftAuthStatus = $"Code {MicrosoftUserCode} copied to clipboard!";
                }
                catch { }
            }
        }

        [RelayCommand]
        public void OpenVerificationUri()
        {
            try
            {
                Process.Start(new ProcessStartInfo(MicrosoftVerificationUri) { UseShellExecute = true });
            }
            catch { }
        }
    }
}
