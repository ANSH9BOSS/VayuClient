using System;
using System.Threading.Tasks;

namespace VayuClient.Core
{
    /// <summary>
    /// Contract for deterministic ViewModel lifecycle management during page navigation.
    /// Guarantees that background tasks, timers, HTTP calls, and animations are cleanly
    /// started on activation and cancelled/disposed on deactivation.
    /// </summary>
    public interface ILifecycleViewModel : IDisposable
    {
        /// <summary>
        /// Asynchronous one-time or heavy initialization.
        /// </summary>
        Task InitializeAsync();

        /// <summary>
        /// Invoked when the user navigates TO this page.
        /// Starts timers, subscribes to events, and refreshes visible state.
        /// </summary>
        void Activate();

        /// <summary>
        /// Invoked when the user navigates AWAY from this page.
        /// Cancels background tokens, stops timers, unsubscribes events, and frees transient memory.
        /// </summary>
        void Deactivate();
    }
}
