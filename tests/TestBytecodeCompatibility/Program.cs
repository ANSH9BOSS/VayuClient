using System;
using System.IO;
using System.IO.Compression;
using System.Text;
using VayuClient.Services.Java;
using VayuClient.Services.Launch;

namespace TestBytecodeCompatibility
{
    class Program
    {
        static int Main(string[] args)
        {
            Console.ForegroundColor = ConsoleColor.Cyan;
            Console.WriteLine("===================================================================");
            Console.WriteLine(" VAYUCLIENT — JAVA BYTECODE & MINECRAFT RUNTIME COMPATIBILITY SUITE");
            Console.WriteLine("===================================================================");
            Console.ResetColor();

            int passed = 0;
            int failed = 0;
            var validator = new VayuUiCompatibilityValidator();
            string tempDir = Path.Combine(Path.GetTempPath(), "VayuBytecodeTests_" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(tempDir);

            try
            {
                // TEST 1: Java Runtime Service Bytecode Mappings
                Console.Write("[TEST 1] Java Major -> Class File Max Bytecode Mapping: ");
                AssertEqual(52, JavaRuntimeService.GetMaxClassFileVersion(8), "Java 8 -> 52");
                AssertEqual(61, JavaRuntimeService.GetMaxClassFileVersion(17), "Java 17 -> 61");
                AssertEqual(65, JavaRuntimeService.GetMaxClassFileVersion(21), "Java 21 -> 65");
                AssertEqual(69, JavaRuntimeService.GetMaxClassFileVersion(25), "Java 25 -> 69");
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine("PASSED");
                Console.ResetColor();
                passed++;

                // TEST 2: Minecraft Version -> Required Java Major
                Console.Write("[TEST 2] Minecraft Version -> Required Java Resolver: ");
                AssertEqual(21, JavaRuntimeService.GetRequiredJavaMajorForMinecraft("1.21.4"), "1.21.4 -> Java 21");
                AssertEqual(21, JavaRuntimeService.GetRequiredJavaMajorForMinecraft("26.1"), "26.1 -> Java 21");
                AssertEqual(21, JavaRuntimeService.GetRequiredJavaMajorForMinecraft("26.2"), "26.2 -> Java 21");
                AssertEqual(17, JavaRuntimeService.GetRequiredJavaMajorForMinecraft("1.20.1"), "1.20.1 -> Java 17");
                AssertEqual(17, JavaRuntimeService.GetRequiredJavaMajorForMinecraft("1.19.4"), "1.19.4 -> Java 17");
                AssertEqual(8, JavaRuntimeService.GetRequiredJavaMajorForMinecraft("1.16.5"), "1.16.5 -> Java 8");
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine("PASSED");
                Console.ResetColor();
                passed++;

                // TEST 3: Create Synthetic Incompatible Java 25 JAR (Bytecode 69) and verify validation failure on Java 21 JVM
                Console.Write("[TEST 3] Regression: Java 25 Artifact Fails on Java 21 JVM Before Launch: ");
                string fakeJava25Jar = Path.Combine(tempDir, "vayuclient-ui-java25-fake.jar");
                CreateSyntheticJar(fakeJava25Jar, majorBytecode: 69, javaMajor: 25);
                
                var java25Info = validator.InspectArtifact(fakeJava25Jar);
                if (java25Info.BytecodeMajor != 69 || java25Info.RequiredJavaMajor != 25)
                {
                    throw new Exception($"Expected bytecode 69, got {java25Info.BytecodeMajor}");
                }

                bool validOn21 = validator.ValidateCompatibility(jvmJavaMajor: 21, fakeJava25Jar, "1.21.4", out string failureReason);
                if (validOn21)
                {
                    throw new Exception("CRITICAL FAILURE: Java 25 JAR was allowed to pass validation on Java 21 JVM!");
                }
                if (!failureReason.Contains("Java 21") || !failureReason.Contains("69"))
                {
                    throw new Exception($"Unexpected failure message: {failureReason}");
                }
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine("PASSED (Pre-Launch Abort Verified)");
                Console.ResetColor();
                passed++;

                // TEST 4: Create Synthetic Compatible Java 21 JAR (Bytecode 65) and verify validation passes on Java 21 JVM
                Console.Write("[TEST 4] Java 21 Artifact Passes on Java 21 JVM: ");
                string fakeJava21Jar = Path.Combine(tempDir, "vayuclient-ui-java21-fake.jar");
                CreateSyntheticJar(fakeJava21Jar, majorBytecode: 65, javaMajor: 21);

                bool validOn21Success = validator.ValidateCompatibility(jvmJavaMajor: 21, fakeJava21Jar, "1.21.4", out string okReason);
                if (!validOn21Success)
                {
                    throw new Exception($"Expected validation to pass, but failed: {okReason}");
                }
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine("PASSED");
                Console.ResetColor();
                passed++;

                // TEST 5: Verify Live Deployed vayuclient-hud-1.7.0-mc26.2-fabric.jar Bytecode and Manifest
                Console.Write("[TEST 5] Live Artifact Bytecode Inspection (vayuclient-hud-1.7.0-mc26.2-fabric.jar): ");
                string liveJar = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Assets", "Mods", "vayuclient-hud-1.7.0-mc26.2-fabric.jar");
                if (!File.Exists(liveJar))
                {
                    liveJar = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..", "VayuClient", "Assets", "Mods", "vayuclient-hud-1.7.0-mc26.2-fabric.jar");
                    liveJar = Path.GetFullPath(liveJar);
                }
                if (!File.Exists(liveJar))
                {
                    liveJar = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..", "vayuclient-hud-mod", "dist", "vayuclient-hud-1.7.0-mc26.2-fabric.jar");
                    liveJar = Path.GetFullPath(liveJar);
                }

                if (!File.Exists(liveJar))
                {
                    throw new FileNotFoundException($"Live artifact not found at {liveJar}");
                }

                var liveInfo = validator.InspectArtifact(liveJar);
                if (!liveInfo.IsValid)
                {
                    throw new Exception($"Live artifact inspection failed: {liveInfo.ErrorMessage}");
                }
                if (liveInfo.BytecodeMajor != 65)
                {
                    throw new Exception($"Live artifact bytecode is {liveInfo.BytecodeMajor} (Expected 65 for Java 21)!");
                }
                if (!liveInfo.HasManifest)
                {
                    throw new Exception("Live artifact is missing vayuclient-hud-manifest.json!");
                }

                bool liveValid = validator.ValidateCompatibility(21, liveJar, "26.2", out string liveFailReason);
                if (!liveValid)
                {
                    throw new Exception($"Live artifact failed validation on Java 21: {liveFailReason}");
                }
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine($"PASSED (Bytecode: {liveInfo.BytecodeMajor}, Java: {liveInfo.RequiredJavaMajor})");
                Console.ResetColor();
                passed++;

                // TEST 6: Stale Incompatible Artifact Purging
                Console.Write("[TEST 6] Stale Incompatible UI Artifact Purging: ");
                string mockModsDir = Path.Combine(tempDir, "mock_mods");
                Directory.CreateDirectory(mockModsDir);

                string staleJar = Path.Combine(mockModsDir, "vayuclient-ui-1.6.0.jar");
                string userMod = Path.Combine(mockModsDir, "sodium-fabric-0.9.2.jar");
                CreateSyntheticJar(staleJar, majorBytecode: 69, javaMajor: 25);
                File.WriteAllText(userMod, "dummy-mod-content");

                validator.PurgeIncompatibleUiMods(mockModsDir, jvmJavaMajor: 21, minecraftVersion: "26.2");

                if (File.Exists(staleJar))
                {
                    throw new Exception("Incompatible Java 25 UI JAR was not purged from mods directory!");
                }
                if (!File.Exists(userMod))
                {
                    throw new Exception("Purge logic accidentally deleted user mod!");
                }
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine("PASSED");
                Console.ResetColor();
                passed++;

                Console.WriteLine();
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine($"ALL {passed} TESTS PASSED SUCCESSFULLY! (0 Failures)");
                Console.ResetColor();
                return 0;
            }
            catch (Exception ex)
            {
                failed++;
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine($"\n[TEST FAILURE] {ex.Message}");
                Console.WriteLine(ex.StackTrace);
                Console.ResetColor();
                return 1;
            }
            finally
            {
                try { Directory.Delete(tempDir, true); } catch { }
            }
        }

        private static void CreateSyntheticJar(string jarPath, int majorBytecode, int javaMajor)
        {
            using var zip = ZipFile.Open(jarPath, ZipArchiveMode.Create);
            
            // 1. Create class entry with CAFEBABE and target major bytecode
            var classEntry = zip.CreateEntry("com/vayuclient/ui/VayuClientUI.class");
            using (var s = classEntry.Open())
            {
                byte[] classBytes = new byte[8];
                classBytes[0] = 0xCA;
                classBytes[1] = 0xFE;
                classBytes[2] = 0xBA;
                classBytes[3] = 0xBE;
                classBytes[4] = 0x00; // Minor version high
                classBytes[5] = 0x00; // Minor version low
                classBytes[6] = (byte)((majorBytecode >> 8) & 0xFF);
                classBytes[7] = (byte)(majorBytecode & 0xFF);
                s.Write(classBytes, 0, classBytes.Length);
            }

            // 2. Create manifest
            var manifestEntry = zip.CreateEntry("vayuclient-ui-manifest.json");
            using (var s = manifestEntry.Open())
            {
                string json = $"{{\"vayuUiVersion\":\"1.6.1\",\"requiredJavaMajor\":{javaMajor},\"bytecodeMajor\":{majorBytecode},\"minecraftCompatibility\":\">=1.21 <27.0\"}}";
                byte[] jsonBytes = Encoding.UTF8.GetBytes(json);
                s.Write(jsonBytes, 0, jsonBytes.Length);
            }
        }

        private static void AssertEqual<T>(T expected, T actual, string context)
        {
            if (!Equals(expected, actual))
            {
                throw new Exception($"Assertion failed in {context}: Expected '{expected}', got '{actual}'.");
            }
        }
    }
}
