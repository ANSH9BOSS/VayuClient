using VayuClient.Models;

namespace VayuClient.Services.Profiles
{
    /// <summary>
    /// Manages local offline profiles: create, read, update, delete.
    /// </summary>
    public interface IProfileService
    {
        IReadOnlyList<UserProfile> GetAllProfiles();
        UserProfile? GetProfile(string id);
        UserProfile CreateOfflineProfile(string username);
        void AddOrUpdateProfile(UserProfile profile);
        void SetActiveProfile(string profileId);
        void RenameProfile(string id, string newUsername);
        void DeleteProfile(string id);
        void SaveProfiles();
        void LoadProfiles();
        event Action? ProfilesChanged;
    }
}
