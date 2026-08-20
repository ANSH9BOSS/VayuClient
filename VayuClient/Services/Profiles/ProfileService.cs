using System.IO;
using Newtonsoft.Json;
using VayuClient.Core;
using VayuClient.Models;

namespace VayuClient.Services.Profiles
{
    /// <summary>
    /// JSON-backed local profile storage at %APPDATA%\VayuClient\profiles.json.
    /// </summary>
    public class ProfileService : IProfileService
    {
        private readonly string _profilesPath;
        private readonly List<UserProfile> _profiles = new();

        public event Action? ProfilesChanged;

        public ProfileService()
        {
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var vayuDir = Path.Combine(appData, "VayuClient");
            Directory.CreateDirectory(vayuDir);
            _profilesPath = Path.Combine(vayuDir, "profiles.json");
            LoadProfiles();
        }

        public IReadOnlyList<UserProfile> GetAllProfiles()
        {
            lock (_profiles)
            {
                return _profiles.ToList();
            }
        }

        public UserProfile? GetProfile(string id)
        {
            lock (_profiles)
            {
                return _profiles.FirstOrDefault(p => p.Id == id);
            }
        }

        public void SetActiveProfile(string profileId)
        {
            lock (_profiles)
            {
                foreach (var p in _profiles)
                {
                    p.IsActive = (p.Id == profileId);
                }
                SaveProfiles();
            }
            ProfilesChanged?.Invoke();
        }

        public UserProfile CreateOfflineProfile(string username)
        {
            string cleanName = username.Trim();
            lock (_profiles)
            {
                var existing = _profiles.FirstOrDefault(p => p.AccountType == AccountType.Offline && string.Equals(p.Username, cleanName, StringComparison.OrdinalIgnoreCase));
                if (existing != null)
                {
                    return existing;
                }

                var profile = new UserProfile
                {
                    Id = Guid.NewGuid().ToString("N"),
                    Username = cleanName,
                    UUID = Utilities.UuidGenerator.GenerateOfflineUuid(cleanName),
                    AccountType = AccountType.Offline,
                    AvatarIndex = new Random().Next(0, 9),
                    CreatedAt = DateTime.UtcNow,
                    IsActive = _profiles.Count == 0 // First profile is auto-active
                };

                _profiles.Add(profile);
                SaveProfiles();
                ProfilesChanged?.Invoke();
                return profile;
            }
        }

        public void AddOrUpdateProfile(UserProfile profile)
        {
            lock (_profiles)
            {
                var existingIndex = _profiles.FindIndex(p => p.Id == profile.Id || 
                    (p.AccountType == AccountType.Microsoft && !string.IsNullOrEmpty(profile.UUID) && p.UUID == profile.UUID) ||
                    (p.AccountType == profile.AccountType && string.Equals(p.Username, profile.Username, StringComparison.OrdinalIgnoreCase)));

                if (existingIndex >= 0)
                {
                    _profiles[existingIndex] = profile;
                }
                else
                {
                    _profiles.Add(profile);
                }

                if (profile.IsActive)
                {
                    foreach (var p in _profiles)
                    {
                        if (p.Id != profile.Id)
                        {
                            p.IsActive = false;
                        }
                    }
                }

                SaveProfiles();
            }
            ProfilesChanged?.Invoke();
        }

        public void RenameProfile(string id, string newUsername)
        {
            var profile = _profiles.FirstOrDefault(p => p.Id == id);
            if (profile == null) return;

            string clean = newUsername.Trim();
            if (_profiles.Any(p => p.Id != id && string.Equals(p.Username, clean, StringComparison.OrdinalIgnoreCase)))
            {
                throw new InvalidOperationException($"An account with username '{clean}' already exists.");
            }

            profile.Username = clean;
            SaveProfiles();
            ProfilesChanged?.Invoke();
        }

        public void DeleteProfile(string id)
        {
            var profile = _profiles.FirstOrDefault(p => p.Id == id);
            if (profile == null) return;

            bool wasActive = profile.IsActive;
            _profiles.Remove(profile);

            if (wasActive && _profiles.Count > 0)
            {
                _profiles[0].IsActive = true;
            }

            SaveProfiles();
            ProfilesChanged?.Invoke();
        }

        public void SaveProfiles()
        {
            try
            {
                lock (_profiles)
                {
                    Core.SafeJsonStorage.SaveAtomic(_profilesPath, _profiles);
                }
            }
            catch (Exception ex)
            {
                Core.CrashLogger.LogException("ProfileService.SaveProfiles", ex);
            }
        }

        public void LoadProfiles()
        {
            try
            {
                lock (_profiles)
                {
                    var loaded = Core.SafeJsonStorage.LoadSafe<List<UserProfile>>(_profilesPath, () => new List<UserProfile>());
                    _profiles.Clear();
                    if (loaded != null)
                    {
                        _profiles.AddRange(loaded);
                    }
                }
            }
            catch (Exception ex)
            {
                Core.CrashLogger.LogException("ProfileService.LoadProfiles", ex);
            }
        }
    }
}
