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
        IReadOnlyList<RunningGameSession> RunningSessions { get; }
        int RunningSessionCount { get; }
        bool IsInstanceRunning(string instanceId);

        event Action<LaunchState, string>? StateChanged;
        event Action<DownloadProgressInfo>? DownloadProgressChanged;
        event Action<RunningGameSession>? GameSessionStarted;
        event Action<RunningGameSession, int>? GameSessionExited;

        Task<bool> LaunchInstanceAsync(string? instanceId = null, CancellationToken ct = default);
        Task<bool> LaunchInstanceAsync(string? instanceId, string? customPlayerName, CancellationToken ct = default);
        void KillActiveGame();
        void KillSession(string sessionId);
        void KillInstance(string instanceId);
        void KillAllActiveGames();
    }
}
