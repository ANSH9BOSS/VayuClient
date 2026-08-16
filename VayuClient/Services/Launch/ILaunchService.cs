using System;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Launch
{
    public enum LaunchState
    {
        Idle,
        Preparing,
        Downloading,
        Installing,
        Launching,
        Playing,
        GameClosed,
        Failed
    }

    public interface ILaunchService
    {
        LaunchState CurrentState { get; }
        string StatusMessage { get; }
        bool IsGameRunning { get; }
        DownloadProgressInfo? CurrentProgress { get; }

        event Action<LaunchState, string>? StateChanged;
        event Action<DownloadProgressInfo>? DownloadProgressChanged;

        Task<bool> LaunchInstanceAsync(string? instanceId = null, CancellationToken ct = default);
        void KillActiveGame();
    }
}
