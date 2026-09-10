using System.Windows;

namespace VayuClient.Controls
{
    /// <summary>
    /// Renders a 3D Minecraft player model. Inherits from PlayerBody3D.
    /// Can render both full running body or head-only depending on DisplayMode.
    /// </summary>
    public sealed class PlayerHead3D : PlayerBody3D
    {
        public PlayerHead3D()
        {
            // Default to FullBody for rich animated presentation across the launcher
            DisplayMode = PlayerModelMode.FullBody;
        }
    }
}
