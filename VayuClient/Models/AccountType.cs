namespace VayuClient.Models
{
    /// <summary>
    /// Defines the type of user account in VayuClient.
    /// </summary>
    public enum AccountType
    {
        /// <summary>
        /// Authenticated Microsoft/Xbox account with Minecraft ownership.
        /// </summary>
        Microsoft,

        /// <summary>
        /// Local offline profile for offline gameplay only.
        /// </summary>
        Offline
    }
}
