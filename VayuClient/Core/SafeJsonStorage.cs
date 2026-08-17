using System;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;

namespace VayuClient.Core
{
    /// <summary>
    /// Thread-safe, crash-resilient JSON storage engine with atomic writes,
    /// automatic backups, corruption detection, and graceful recovery.
    /// </summary>
    public static class SafeJsonStorage
    {
        private static readonly object _globalFileLock = new();

        /// <summary>
        /// Atomically serializes and writes an object to disk using a temporary file + atomic rename strategy.
        /// Automatically maintains a valid .bak backup.
        /// </summary>
        public static void SaveAtomic<T>(string filePath, T data, Formatting formatting = Formatting.Indented)
        {
            if (string.IsNullOrWhiteSpace(filePath)) return;

            lock (_globalFileLock)
            {
                var dir = Path.GetDirectoryName(filePath);
                if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }

                var tempPath = filePath + ".tmp." + Guid.NewGuid().ToString("N")[..6];
                var backupPath = filePath + ".bak";

                try
                {
                    var json = JsonConvert.SerializeObject(data, formatting);
                    
                    // Write to temp file and flush to disk
                    using (var fs = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None, 4096, FileOptions.WriteThrough))
                    using (var writer = new StreamWriter(fs, Encoding.UTF8))
                    {
                        writer.Write(json);
                        writer.Flush();
                        fs.Flush(flushToDisk: true);
                    }

                    // Create backup of existing file before replacing
                    if (File.Exists(filePath))
                    {
                        try
                        {
                            File.Copy(filePath, backupPath, overwrite: true);
                        }
                        catch { }
                    }

                    // Atomic move / replace
                    File.Move(tempPath, filePath, overwrite: true);
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException($"SafeJsonStorage.SaveAtomic failed for '{filePath}'", ex);
                    try
                    {
                        if (File.Exists(tempPath)) File.Delete(tempPath);
                    }
                    catch { }
                    throw;
                }
            }
        }

        /// <summary>
        /// Asynchronously and atomically saves data to disk with backup protection.
        /// </summary>
        public static async Task SaveAtomicAsync<T>(string filePath, T data, Formatting formatting = Formatting.Indented)
        {
            if (string.IsNullOrWhiteSpace(filePath)) return;

            var dir = Path.GetDirectoryName(filePath);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
            }

            var tempPath = filePath + ".tmp." + Guid.NewGuid().ToString("N")[..6];
            var backupPath = filePath + ".bak";

            try
            {
                var json = JsonConvert.SerializeObject(data, formatting);

                using (var fs = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None, 4096, FileOptions.WriteThrough | FileOptions.Asynchronous))
                using (var writer = new StreamWriter(fs, Encoding.UTF8))
                {
                    await writer.WriteAsync(json);
                    await writer.FlushAsync();
                    await fs.FlushAsync();
                }

                lock (_globalFileLock)
                {
                    if (File.Exists(filePath))
                    {
                        try
                        {
                            File.Copy(filePath, backupPath, overwrite: true);
                        }
                        catch { }
                    }

                    File.Move(tempPath, filePath, overwrite: true);
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogException($"SafeJsonStorage.SaveAtomicAsync failed for '{filePath}'", ex);
                try
                {
                    if (File.Exists(tempPath)) File.Delete(tempPath);
                }
                catch { }
                throw;
            }
        }

        /// <summary>
        /// Safely loads and deserializes JSON from disk. If corrupted (e.g. trailing characters or partial write),
        /// preserves a copy of the corrupted file, attempts automatic recovery from .bak, and returns fallback.
        /// </summary>
        public static T? LoadSafe<T>(string filePath, Func<T>? fallbackFactory = null) where T : class
        {
            if (string.IsNullOrWhiteSpace(filePath) || !File.Exists(filePath))
            {
                return fallbackFactory?.Invoke();
            }

            lock (_globalFileLock)
            {
                try
                {
                    var json = File.ReadAllText(filePath, Encoding.UTF8);
                    if (string.IsNullOrWhiteSpace(json))
                    {
                        return TryRecoverFromBackup(filePath, fallbackFactory);
                    }

                    var result = JsonConvert.DeserializeObject<T>(json);
                    if (result != null) return result;

                    return TryRecoverFromBackup(filePath, fallbackFactory);
                }
                catch (JsonReaderException jre)
                {
                    CrashLogger.LogMessage($"[SafeJsonStorage]: JSON corruption detected in '{filePath}': {jre.Message}. Preserving corrupted file and attempting backup recovery.");
                    PreserveCorruptedFile(filePath);
                    return TryRecoverFromBackup(filePath, fallbackFactory);
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException($"[SafeJsonStorage]: Unexpected error loading '{filePath}'", ex);
                    return TryRecoverFromBackup(filePath, fallbackFactory);
                }
            }
        }

        private static void PreserveCorruptedFile(string filePath)
        {
            try
            {
                var timestamp = DateTime.UtcNow.ToString("yyyyMMdd_HHmmss");
                var corruptPath = $"{filePath}.corrupted_{timestamp}";
                File.Copy(filePath, corruptPath, overwrite: true);
                CrashLogger.LogMessage($"[SafeJsonStorage]: Corrupted file saved to '{corruptPath}' for diagnostic safety.");
            }
            catch { }
        }

        private static T? TryRecoverFromBackup<T>(string filePath, Func<T>? fallbackFactory) where T : class
        {
            var backupPath = filePath + ".bak";
            if (File.Exists(backupPath))
            {
                try
                {
                    CrashLogger.LogMessage($"[SafeJsonStorage]: Attempting recovery from backup '{backupPath}'...");
                    var bakJson = File.ReadAllText(backupPath, Encoding.UTF8);
                    var bakResult = JsonConvert.DeserializeObject<T>(bakJson);
                    if (bakResult != null)
                    {
                        CrashLogger.LogMessage($"[SafeJsonStorage]: Successfully restored configuration from backup '{backupPath}'!");
                        // Re-save as main file
                        SaveAtomic(filePath, bakResult);
                        return bakResult;
                    }
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException($"[SafeJsonStorage]: Backup recovery failed for '{backupPath}'", ex);
                }
            }

            CrashLogger.LogMessage($"[SafeJsonStorage]: Initializing default fallback configuration for '{filePath}'.");
            var fallback = fallbackFactory?.Invoke();
            if (fallback != null)
            {
                try
                {
                    SaveAtomic(filePath, fallback);
                }
                catch { }
            }
            return fallback;
        }
    }
}
