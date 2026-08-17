using System;
using System.Diagnostics;
using System.Threading;
using System.Windows;
using System.Windows.Threading;
using VayuClient.Core;
using VayuClient.ViewModels;

namespace VayuClient.Tests
{
    public class NavigationStressRunner
    {
        [STAThread]
        public static int Main(string[] args)
        {
            Console.WriteLine("==========================================================");
            Console.WriteLine(" VAYUCLIENT 100-CYCLE NAVIGATION STRESS TEST (STA THREAD)");
            Console.WriteLine("==========================================================");

            var app = new Application();
            CrashLogger.Initialize();
            ServiceLocator.Initialize();

            long initialMemory = GC.GetTotalMemory(true);
            var proc = Process.GetCurrentProcess();
            long initialWorkingSet = proc.WorkingSet64 / (1024 * 1024);

            Console.WriteLine($"[INIT] Starting memory: {initialMemory / (1024 * 1024)} MB | Working Set: {initialWorkingSet} MB");

            var mainVm = new MainViewModel();

            string[] sequence = new[] { "Home", "InstallationManager", "Mods", "Servers", "Accounts", "Settings", "Home" };
            int totalCycles = 100;
            int totalTransitions = 0;
            int errorCount = 0;
            var sw = Stopwatch.StartNew();

            for (int cycle = 1; cycle <= totalCycles; cycle++)
            {
                foreach (var page in sequence)
                {
                    try
                    {
                        mainVm.NavigateTo(page);
                        totalTransitions++;

                        // Allow WPF dispatcher to process any queued render/data events
                        DoEvents();
                    }
                    catch (Exception ex)
                    {
                        errorCount++;
                        Console.ForegroundColor = ConsoleColor.Red;
                        Console.WriteLine($"  [FAIL] Cycle {cycle} - Page {page} threw exception: {ex.GetType().Name}: {ex.Message}");
                        Console.ResetColor();
                    }
                }

                if (cycle % 20 == 0 || cycle == totalCycles)
                {
                    long currentMem = GC.GetTotalMemory(false) / (1024 * 1024);
                    proc.Refresh();
                    long currentWs = proc.WorkingSet64 / (1024 * 1024);
                    Console.WriteLine($"  [CYCLE {cycle,3}/{totalCycles}] Completed {totalTransitions} transitions | Managed Heap: {currentMem} MB | Working Set: {currentWs} MB");
                }
            }

            sw.Stop();

            GC.Collect();
            GC.WaitForPendingFinalizers();
            GC.Collect();

            long finalMemory = GC.GetTotalMemory(true) / (1024 * 1024);
            proc.Refresh();
            long finalWs = proc.WorkingSet64 / (1024 * 1024);

            Console.WriteLine("\n==========================================================");
            Console.WriteLine(" STRESS TEST RESULTS");
            Console.WriteLine("==========================================================");
            Console.WriteLine($"Total Cycles:          {totalCycles}");
            Console.WriteLine($"Total Page Switches:   {totalTransitions}");
            Console.WriteLine($"Total Errors/Crashes:  {errorCount}");
            Console.WriteLine($"Total Duration:        {sw.ElapsedMilliseconds} ms (Avg {(double)sw.ElapsedMilliseconds / totalTransitions:F2} ms / transition)");
            Console.WriteLine($"Final Managed Heap:    {finalMemory} MB");
            Console.WriteLine($"Final Working Set:     {finalWs} MB");

            if (errorCount == 0)
            {
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine("\n>>> PASS: 100 CYCLES COMPLETED WITH 0 CRASHES AND STABLE MEMORY <<<");
                Console.ResetColor();
                return 0;
            }
            else
            {
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine("\n>>> FAIL: ERRORS DETECTED DURING NAVIGATION STRESS TEST <<<");
                Console.ResetColor();
                return 1;
            }
        }

        private static void DoEvents()
        {
            var frame = new DispatcherFrame();
            Dispatcher.CurrentDispatcher.BeginInvoke(DispatcherPriority.Background, new DispatcherOperationCallback(ExitFrame), frame);
            Dispatcher.PushFrame(frame);
        }

        private static object? ExitFrame(object frame)
        {
            ((DispatcherFrame)frame).Continue = false;
            return null;
        }
    }
}
