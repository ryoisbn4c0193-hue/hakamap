package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WindowsPackagingConfigurationTest {
  @Test
  void pinsUserInstallUpgradeIdentityRuntimeModulesAndMemoryLimit() throws Exception {
    Path script = Path.of("../packaging/windows/build.ps1").toAbsolutePath().normalize();
    String content = Files.readString(script);

    assertThat(content)
        .contains("--win-per-user-install")
        .contains("--win-menu")
        .contains("--win-shortcut")
        .contains("--win-upgrade-uuid")
        .contains("5e8c8d8f-4985-4e35-9063-320153c36f84")
        .contains("'java.instrument'")
        .contains("'java.desktop'")
        .contains("'jdk.crypto.ec'")
        .contains("java --version")
        .doesNotContain("java -version 2>&1")
        .contains("--java-options '-Xmx512m'")
        .contains("create-app-icon.ps1")
        .contains("assets/hakamap-icon.png")
        .contains("Hakamap-$Version.exe")
        .doesNotContain("--win-console");
    assertThat(content.indexOf("Remove-Item $Work"))
        .isLessThan(content.indexOf("create-app-icon.ps1"));
  }

  @Test
  void storageProbeChecksRequiredFileSystemCapabilitiesWithoutExposingPath() throws Exception {
    Path script =
        Path.of("../packaging/windows/test-storage-capabilities.ps1").toAbsolutePath().normalize();
    String content = Files.readString(script);

    assertThat(content)
        .contains("$First.Lock(0, 1)")
        .contains("[IO.File]::Replace")
        .contains("$Backup = Join-Path $Probe 'project.json.bak'")
        .contains("[IO.File]::Replace($New, $Old, $Backup)")
        .contains("$Stream.Flush($true)")
        .contains("Remove-Item $Probe")
        .contains("storageType = $StorageType")
        .doesNotContain("directory = $Root");
  }

  @Test
  void createsMultiResolutionWindowsIconFromBundledOfficialSource() throws Exception {
    Path source =
        Path.of("../packaging/windows/assets/hakamap-icon.png").toAbsolutePath().normalize();
    Path script = Path.of("../packaging/windows/create-app-icon.ps1").toAbsolutePath().normalize();
    String content = Files.readString(script);

    assertThat(Files.size(source)).isGreaterThan(0);
    assertThat(content)
        .contains("@(16, 24, 32, 48, 64, 128, 256)")
        .contains("Format32bppArgb")
        .contains("HighQualityBicubic")
        .contains("ImageFormat]::Png");
  }

  @Test
  void windowsWorkflowBuildsAndVerifiesInstallerManifest() throws Exception {
    Path workflow =
        Path.of("../.github/workflows/windows-package.yml").toAbsolutePath().normalize();
    String content = Files.readString(workflow);

    assertThat(content)
        .contains("runs-on: windows-2025")
        .contains("java-version: \"21\"")
        .contains("packaging\\windows\\build.ps1")
        .contains("Get-FileHash")
        .contains("actions/upload-artifact@v4")
        .doesNotContain("secrets.");
  }

  @Test
  void repositoryPinsTextLineEndingsAcrossWindowsAndLinux() throws Exception {
    Path attributes = Path.of("../.gitattributes").toAbsolutePath().normalize();
    String content = Files.readString(attributes);

    assertThat(content)
        .contains("* text=auto eol=lf")
        .contains("*.bat text eol=crlf")
        .contains("*.cmd text eol=crlf");
  }
}
