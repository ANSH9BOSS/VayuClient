using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Modpack
{
    public interface IModpackConverterService
    {
        Task<ConversionResult> ConvertInstanceAsync(
            MinecraftInstance sourceInstance,
            ConversionOptions options,
            IProgress<ConversionProgressInfo>? progress = null,
            CancellationToken ct = default);
    }
}
