# 🌌 VayuClient™

<div align="center">

![VayuClient Banner](VayuClient/Assets/Images/vayu_minecraft_hero.jpg)

### **The High-Performance, Next-Gen Native 3D Minecraft Desktop Launcher**

[![Platform](https://img.shields.io/badge/Platform-Windows%20x64-0078D6?style=for-the-badge&logo=windows)](https://github.com/ANSH9BOSS/VayuClient)
[![Framework](https://img.shields.io/badge/.NET-8.0%20WPF-512BD4?style=for-the-badge&logo=dotnet)](https://dotnet.microsoft.com/)
[![Graphics](https://img.shields.io/badge/DirectX-Direct3D%20%2F%20DWM-7C3AED?style=for-the-badge&logo=opengl)](https://github.com/ANSH9BOSS/VayuClient)
[![Release](https://img.shields.io/github/v/release/ANSH9BOSS/VayuClient?style=for-the-badge&color=22C55E)](https://github.com/ANSH9BOSS/VayuClient/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![Developer](https://img.shields.io/badge/Developer-ANSH9BOSS-F59E0B?style=for-the-badge)](https://github.com/ANSH9BOSS)

[**📥 Download Latest Installer (`VayuClientSetup.exe`)**](https://github.com/ANSH9BOSS/VayuClient/releases/latest) • [**✨ Release Notes**](https://github.com/ANSH9BOSS/VayuClient/releases) • [**📖 Documentation**](#-architecture--features) • [**⚡ Quick Start**](#-installation--quick-start)

</div>

---

## ⚡ Overview

**VayuClient™** is a next-generation, high-performance Minecraft desktop launcher engineered in native C# (.NET 8.0 WPF) and hardware-accelerated DirectX composition. Designed for both budget PCs and high-end esports setups, VayuClient delivers ultra-smooth 60+ FPS UI transitions, zero background bloat, multi-instance directory isolation, and an interactive 3D hardware cartridge Game Library.

---

## ✨ Core Features & Visual Architecture

```
                                  VAYU CLIENT ARCHITECTURE
 ┌─────────────────────────────────────────────────────────────────────────────────────────┐
 │                                                                                         │
 │   ┌───────────────────────┐   ┌───────────────────────┐   ┌─────────────────────────┐   │
 │   │  3D INSTALLATION HUB  │   │  3D CHARACTER STAGE   │   │  MODRINTH ECOSYSTEM     │   │
 │   │  - Cyber Pod Chassis  │   │  - Direct3D Mesh      │   │  - Modpacks (.mrpack)   │   │
 │   │  - Glowing Side Slits │   │  - Dynamic Poses      │   │  - Add-on Mods & Icons  │   │
 │   │  - Live Telemetry     │   │  - Head Look Tracking │   │  - Resource/Shader Pack │   │
 │   └───────────┬───────────┘   └───────────┬───────────┘   └────────────┬────────────┘   │
 │               │                           │                            │                │
 │               └───────────────────────────┼────────────────────────────┘                │
 │                                           │                                             │
 │                               ┌───────────▼───────────┐                                 │
 │                               │     CORE RUNTIME      │                                 │
 │                               │  - Multi-Instance FS  │                                 │
 │                               │  - Smart Java Engine  │                                 │
 │                               │  - GPU Enforcer (D3D) │                                 │
 │                               │  - Mojang & Mod Sync  │                                 │
 │                               └───────────────────────┘                                 │
 └─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 🚀 1. Hardware 3D Installation Manager & Game Library
- **Obsidian & Cyber Purple 3D Pods**: Futuristic hardware pod chassis featuring metallic beveled edges and directional neon purple side fin light brackets (`#C084FC` / `#A855F7`).
- **Active vs Inactive Stage Arena**: Active installations occupy the center hero stage with enhanced depth, lighting, and an `● ACTIVE` status badge, while flanking inactive pods scale down ($0.84\times$) and seamlessly morph forward on selection.
- **Live Instance Telemetry**: Real-time display of Minecraft versions (`1.21.11` through `1.0.0`, plus `26.x`), mod loaders (*Fabric, Forge, NeoForge, Quilt, Vanilla*), installed mod counts, allocated RAM, relative last played timestamps, and favorite stars.
- **Contextual Instance Tools**: Instant access to Edit Configurations, Clone Instances, Open Game Folders, Convert Versions, Custom / Concurrent Launches, and Safe Deletion.
- **Universal Modpack Converter**: Convert any instance or modpack between different Minecraft versions and mod loaders with automatic dependency resolution.

### 🧍 2. Direct3D Real-Time Player Character Stage
- **Native 3D Voxel Skin Rendering**: Renders player skins onto a multi-mesh Direct3D voxel character in real time.
- **Fluid Breathing Dynamics**: Sub-pixel rhythmic breathing bob cycles for natural character presence.
- **Expressive Interactive Poses**:
  - 💖 **Love / Cradle Pose**: Gentle head tilt, resting position, and inward arm cradle.
  - ⚔️ **Bedwars PVP Stance**: Aggressive forward-leaning battle stance with raised sword arm.
  - 👋 **Friendly Wave**: Animated greeting gesture with dynamic arm rotation.
  - 🪑 **Sitting & Relaxed**: Laid-back lobby poses for personal profile presentation.
- **Cursor Tracking**: Responsive head pitch and yaw orientation following user mouse movement across the dashboard.

### 🌌 3. Atmospheric Dynamic Wallpaper Engine
- **Fluid Crossfade Animations**: Seamless multi-stage fade transitions when switching visual themes.
- **Curated Thematic Environments**:
  - *Cyber Nether* • *Lush Caves* • *Mountain Aurora* • *End Void* • *Voxel City*
  - *Deep Cave* • *Fantasy Islands* • *Cherry Grove* • *Ocean Monument* • *Vayu Hero*
- **Adjustable Ambient Vibrancy**: User-controlled opacity and glassmorphism gradient overlays.

### 🧩 4. Complete Modrinth & Modpack Ecosystem
- **In-Jar Mod Icon Extraction**: Automatically extracts `.jar` metadata and icons for high-resolution visual previews.
- **Target Instance Selector**: Select target destination instances directly before downloading mods, shaders, or resource packs.
- **Curated Discovery Shelves**: Quick-browse carousels for popular PvP mods, optimization packs (Sodium, Iris, Lithium), and custom shaderpacks.

### 🛡️ 5. Multi-Instance Isolation & Extreme Performance
- **100% Isolated Game Directories**: Each instance maintains strictly segregated `mods/`, `config/`, `saves/`, `resourcepacks/`, and `options.txt` paths.
- **Discrete GPU Enforcer**: Automatically flags Windows to run Minecraft on dedicated NVIDIA / AMD GPUs instead of integrated graphics.
- **Smart Java Engine**: Automatically selects the correct LTS Java runtime (Java 21 for 1.20.5+, Java 17 for 1.18–1.20.4, Java 8 for legacy Minecraft), preventing bytecode mismatch crashes.
- **Potato PC Optimized**: Lightweight geometry, zero ray tracing overhead, and event-driven rendering routines.

### 🔐 6. Account Security & Session Management
- **Microsoft OAuth Authentication**: Secure MSA token exchange with encrypted local token caching.
- **Offline Profiles**: Quick-switch offline / LAN test accounts with custom usernames.
- **Discord Rich Presence (RPC)**: Real-time Discord status broadcasting active Minecraft version, mod loader, and server session.
- **Live Output Stream Dialog**: Cyber-Aero glassmorphic in-app console replacing messy external command prompt windows.

---

## 🛠️ Tech Stack & Specifications

| Component | Technology / Specification |
|---|---|
| **Language & Runtime** | C# 12 / .NET 8.0 (Windows Desktop Native) |
| **UI Framework** | Windows Presentation Foundation (WPF) with DirectWrite & ClearType |
| **Architecture** | MVVM Pattern with `CommunityToolkit.Mvvm` |
| **Graphics & 3D** | Viewport3D, Direct3D, Hardware Desktop Window Manager (DWM) |
| **JSON Serialization** | High-performance `System.Text.Json` & `Newtonsoft.Json` |
| **Packaging & Installer** | Custom Authenticode-Signed Self-Contained Windows Installer (`VayuClientSetup.exe`) |
| **APIs** | Mojang Version Manifest v2 & Modrinth API v2 |

---

## 📥 Installation & Quick Start

### Option 1: Standalone Windows Setup Installer (Recommended)
1. Download **[`VayuClientSetup.exe`](https://github.com/ANSH9BOSS/VayuClient/releases/latest)** from the latest GitHub Release.
2. Run the installer and choose your preferred destination directory.
3. Launch **VayuClient** from your Desktop or Start Menu.

### Option 2: Build from Source

#### Prerequisites:
- [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0) or higher
- Visual Studio 2022 (with *.NET Desktop Development* workload) or VS Code / JetBrains Rider

#### Build Steps:
```powershell
# 1. Clone repository
git clone https://github.com/ANSH9BOSS/VayuClient.git
cd VayuClient

# 2. Compile in Release mode
dotnet build -c Release

# 3. Launch VayuClient
dotnet run --project .\VayuClient\VayuClient.csproj -c Release

# 4. (Optional) Build signed standalone Windows Installer
powershell -ExecutionPolicy Bypass -File .\build-setup.ps1 -Bump none
```

---

## 🧪 Automated Subsystem Validation (QA)

VayuClient features an integrated diagnostic test runner validating 14 core client subsystems:

```powershell
# Run the built-in QA diagnostic suite
dotnet run --project .\VayuClient\VayuClient.csproj -c Release -- --qa
```

---

## 👨‍💻 Author & Credits

- **Creator & Lead Engineer**: **ANSH9BOSS** ([@ANSH9BOSS](https://github.com/ANSH9BOSS))
- **Repository**: [github.com/ANSH9BOSS/VayuClient](https://github.com/ANSH9BOSS/VayuClient)

---

## 📄 License

This project is open-source and licensed under the [MIT License](LICENSE).
