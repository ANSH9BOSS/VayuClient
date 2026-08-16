using System;
using System.Collections.Generic;
using System.Linq;
using VayuClient.Models;
using VayuClient.Services.Profiles;

namespace VayuClient.Services.Account
{
    public class AccountService : IAccountService
    {
        private readonly IProfileService _profileService;

        public event Action<UserProfile?>? ActiveProfileChanged;

        public AccountService(IProfileService profileService)
        {
            _profileService = profileService;
            _profileService.ProfilesChanged += OnProfilesChanged;
        }

        public UserProfile? ActiveProfile
        {
            get
            {
                var profiles = _profileService.GetAllProfiles();
                var active = profiles.FirstOrDefault(p => p.IsActive);
                if (active == null && profiles.Count > 0)
                {
                    active = profiles[0];
                    active.IsActive = true;
                    _profileService.AddOrUpdateProfile(active);
                }
                return active;
            }
        }

        public IReadOnlyList<UserProfile> GetAllProfiles() => _profileService.GetAllProfiles();

        public void SetActiveProfile(string profileId)
        {
            _profileService.SetActiveProfile(profileId);
            ActiveProfileChanged?.Invoke(ActiveProfile);
        }

        private void OnProfilesChanged()
        {
            ActiveProfileChanged?.Invoke(ActiveProfile);
        }
    }
}