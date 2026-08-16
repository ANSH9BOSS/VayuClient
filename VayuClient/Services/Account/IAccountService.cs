using VayuClient.Models;

namespace VayuClient.Services.Account
{
    /// <summary>
    /// Manages user accounts and active profile selection.
    /// </summary>
    public interface IAccountService
    {
        UserProfile? ActiveProfile { get; }
        IReadOnlyList<UserProfile> GetAllProfiles();
        void SetActiveProfile(string profileId);
        event Action<UserProfile?>? ActiveProfileChanged;
    }
}
