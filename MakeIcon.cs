using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

namespace IconGenerator
{
    public class Program
    {
        public static void Main(string[] args)
        {
            string pngPath = @"VayuClient\Assets\Images\vayu_logo.png";
            string[] outputIcoPaths = new string[]
            {
                @"VayuClient\Assets\Images\vayu_logo.ico",
                @"VayuClientSetup\Assets\Images\vayu_logo.ico"
            };

            if (!File.Exists(pngPath))
            {
                Console.WriteLine("Error: " + pngPath + " not found!");
                return;
            }

            using (Bitmap original = new Bitmap(pngPath))
            {
                Console.WriteLine("Loaded official logo: " + original.Width + "x" + original.Height);

                int[] sizes = new int[] { 16, 20, 24, 32, 40, 48, 64, 128, 256 };
                List<byte[]> pngBuffers = new List<byte[]>();

                for (int i = 0; i < sizes.Length; i++)
                {
                    int size = sizes[i];
                    using (Bitmap resized = new Bitmap(size, size, PixelFormat.Format32bppArgb))
                    {
                        using (Graphics g = Graphics.FromImage(resized))
                        {
                            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                            g.SmoothingMode = SmoothingMode.HighQuality;
                            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
                            g.CompositingQuality = CompositingQuality.HighQuality;
                            g.Clear(Color.Transparent);
                            g.DrawImage(original, 0, 0, size, size);
                        }

                        using (MemoryStream ms = new MemoryStream())
                        {
                            resized.Save(ms, ImageFormat.Png);
                            byte[] buffer = ms.ToArray();
                            pngBuffers.Add(buffer);
                            Console.WriteLine("Generated " + size + "x" + size + " PNG frame (" + buffer.Length + " bytes)");
                        }
                    }
                }

                // Write ICO structure (PNG frames inside ICO container)
                using (MemoryStream icoStream = new MemoryStream())
                {
                    using (BinaryWriter bw = new BinaryWriter(icoStream))
                    {
                        // ICONDIR
                        bw.Write((ushort)0); // Reserved
                        bw.Write((ushort)1); // Type 1 = ICO
                        bw.Write((ushort)sizes.Length); // Count

                        int offset = 6 + (16 * sizes.Length);

                        for (int i = 0; i < sizes.Length; i++)
                        {
                            int size = sizes[i];
                            byte w = size >= 256 ? (byte)0 : (byte)size;
                            byte h = size >= 256 ? (byte)0 : (byte)size;
                            byte[] data = pngBuffers[i];

                            bw.Write(w); // Width
                            bw.Write(h); // Height
                            bw.Write((byte)0); // Color palette count (0 for >=8bpp)
                            bw.Write((byte)0); // Reserved
                            bw.Write((ushort)1); // Color planes
                            bw.Write((ushort)32); // Bits per pixel
                            bw.Write((uint)data.Length); // Size of image data
                            bw.Write((uint)offset); // Offset of image data

                            offset += data.Length;
                        }

                        // Write image datas
                        for (int i = 0; i < sizes.Length; i++)
                        {
                            bw.Write(pngBuffers[i]);
                        }
                    }

                    byte[] icoBytes = icoStream.ToArray();

                    for (int j = 0; j < outputIcoPaths.Length; j++)
                    {
                        string outPath = outputIcoPaths[j];
                        string dir = Path.GetDirectoryName(outPath);
                        if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
                        File.WriteAllBytes(outPath, icoBytes);
                        Console.WriteLine("Saved true multi-resolution ICO -> " + outPath + " (" + icoBytes.Length + " bytes)");
                    }
                }
            }
        }
    }
}
