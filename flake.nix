{
  description = "Geiravor development shells (host Rust, Android SDK/NDK, DHU on Linux)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    # Do not follows=nixpkgs; android-nixpkgs pins its own set.
    android-nixpkgs.url = "github:tadfisher/android-nixpkgs/stable";
    fenix = {
      url = "github:nix-community/fenix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      android-nixpkgs,
      fenix,
    }:
    let
      systems = [
        "x86_64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      ndkVersion = "28.2.13676358";
      buildToolsVersion = "36.0.0";
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      # Host Cargo (fmt, clippy, tests, rustdoc). No SDK/NDK.
      devShells = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };

          rust-host = fenix.packages.${system}.combine (
            with fenix.packages.${system};
            [
              stable.cargo
              stable.rustc
              stable.rustfmt
              stable.clippy
              stable.rust-src
              stable.rust-analyzer
            ]
          );

          rust-android = fenix.packages.${system}.combine (
            with fenix.packages.${system};
            [
              stable.cargo
              stable.rustc
              stable.rustfmt
              stable.clippy
              stable.rust-src
              stable.rust-analyzer
              targets.aarch64-linux-android.stable.rust-std
              targets.x86_64-linux-android.stable.rust-std
            ]
          );

          rustShell = pkgs.mkShell {
            packages = [
              rust-host
              pkgs.nixfmt
              pkgs.cargo-deny
            ];
          };

          # Patch upstream `stdenv.isLinux` (deprecated) before composing the SDK.
          android-nixpkgs-src = pkgs.applyPatches {
            name = "android-nixpkgs-hostPlatform-isLinux";
            src = android-nixpkgs;
            postPatch = ''
              find pkgs/android -name '*.nix' -print0 \
                | xargs -0 sed -i 's/stdenv\.isLinux/stdenv.hostPlatform.isLinux/g'
            '';
          };
          android = import android-nixpkgs-src {
            inherit pkgs;
            channel = "stable";
          };
          android-sdk = android.sdk (
            sdkPkgs: with sdkPkgs; [
              # latest (23) replaces sdkmanager with `android` CLI whose Nix wrapper fails.
              cmdline-tools-16-0
              platform-tools
              platforms-android-36
              build-tools-36-0-0
              ndk-28-2-13676358
              cmake-3-22-1
              extras-google-auto
            ]
          );

          sdkRoot = "${android-sdk}/share/android-sdk";
          ndkRoot = "${sdkRoot}/ndk/${ndkVersion}";
          aapt2 = "${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";
          # DHU 2.1 (2022-12-15) is the last extras;google;auto. On a 2026
          # clock it rejects the phone GAL cert. No 2.2 extra exists; fake
          # time inside the FHS wrap. Override with DHU_FAKETIME.
          # FAKETIME_DONT_FAKE_MONOTONIC avoids the EGL freeze.
          dhuWrapper = pkgs.writeShellScript "desktop-head-unit-wrapped" ''
            export LD_PRELOAD="${pkgs.libfaketime}/lib/libfaketime.so.1''${LD_PRELOAD:+:$LD_PRELOAD}"
            export FAKETIME="''${DHU_FAKETIME:-@2024-06-01 12:00:00}"
            export FAKETIME_DONT_FAKE_MONOTONIC=1
            echo "DHU process clock: $(date -u '+%Y-%m-%d') (FAKETIME=$FAKETIME)" >&2
            exec "${sdkRoot}/extras/google/auto/desktop-head-unit" "$@"
          '';
          dhuFhs = pkgs.buildFHSEnv {
            pname = "desktop-head-unit";
            version = "2.1";
            targetPkgs =
              p: with p; [
                SDL2
                libpng
                zlib
                libGL
                mesa
                alsa-lib
                libpulseaudio
                freetype
                fontconfig
                libx11
                libxext
                libxcursor
                libxi
                libxrandr
                libxfixes
                libxinerama
                libxxf86vm
                libxscrnsaver
                libcxx
                systemd
                vulkan-loader
                libusb1
                libfaketime
              ];
            runScript = "${dhuWrapper}";
          };

          defaultShell = pkgs.mkShell {
            packages = [
              pkgs.jdk21
              android-sdk
              rust-android
              pkgs.cargo-ndk
              pkgs.cargo-deny
              pkgs.pkg-config
              pkgs.llvmPackages.libclang
              pkgs.nixfmt
            ]
            ++ nixpkgs.lib.optionals pkgs.stdenv.hostPlatform.isLinux [ dhuFhs ];

            JAVA_HOME = "${pkgs.jdk21}";
            ANDROID_HOME = sdkRoot;
            ANDROID_SDK_ROOT = sdkRoot;
            ANDROID_NDK_HOME = ndkRoot;
            ANDROID_NDK_ROOT = ndkRoot;
            LIBCLANG_PATH = "${pkgs.llvmPackages.libclang.lib}/lib";
            GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2}";

            shellHook = ''
              export ANDROID_USER_HOME="''${XDG_CACHE_HOME:-$HOME/.cache}/geiravor/android"
              mkdir -p "$ANDROID_USER_HOME"
              if [ -f gradle/wrapper/gradle-wrapper.properties ]; then
                printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
              fi
            '';
          };
        in
        {
          rust = rustShell;
          default = defaultShell;
        }
      );
    };
}
